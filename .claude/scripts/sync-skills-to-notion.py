#!/usr/bin/env python3
"""Syncs .claude/skills/*/SKILL.md to Notion page.

Triggered by Claude Code PostToolUse hook on Write/Edit.
Reads hook stdin JSON to detect if a SKILL.md was changed.
Exits immediately if not a skill file. Use --force to sync all.
"""

import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path


PARENT_PAGE_ID = "32b3462f68a981719106c6b1d82f906c"
SKILLS_DIR = Path(__file__).parent.parent / "skills"
NOTION_API = "https://api.notion.com/v1"
NOTION_VERSION = "2022-06-28"


def load_token() -> str:
    token = os.environ.get("NOTION_SKILLS_TOKEN", "")
    if token:
        return token
    env_file = Path(__file__).parent.parent.parent / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("NOTION_SKILLS_TOKEN="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    return ""


def is_skill_change() -> bool:
    if sys.stdin.isatty():
        return False
    try:
        data = json.load(sys.stdin)
        path = data.get("tool_input", {}).get("file_path", "")
        normalized = path.replace("\\", "/")
        return ".claude/skills" in normalized and normalized.endswith("SKILL.md")
    except Exception:
        return False


def notion_request(method: str, path: str, token: str, data=None):
    url = f"{NOTION_API}{path}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Notion-Version": NOTION_VERSION,
        "Content-Type": "application/json",
    }
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"  Notion {method} {path} -> {e.code}: {e.read().decode()}", file=sys.stderr)
        return None


def get_existing_child_pages(token: str) -> dict:
    result = notion_request("GET", f"/blocks/{PARENT_PAGE_ID}/children?page_size=100", token)
    if not result:
        return {}
    return {
        block["child_page"]["title"]: block["id"]
        for block in result.get("results", [])
        if block.get("type") == "child_page"
    }


def parse_frontmatter(content: str) -> tuple:
    if not content.startswith("---"):
        return {}, content
    end = content.find("---", 3)
    if end == -1:
        return {}, content
    fm = {}
    for line in content[3:end].strip().splitlines():
        if ":" in line:
            key, _, val = line.partition(":")
            fm[key.strip()] = val.strip().strip('"').strip("'")
    return fm, content[end + 3:].strip()


SKILL_ICONS = {
    "kotlin": "🎯",
    "telegram": "🤖",
    "database": "🗄️",
    "postgresql": "🐘",
    "unit-test": "🧪",
    "docker": "🐳",
    "ci-cd": "⚙️",
    "debug": "🐛",
    "architecture": "🏛️",
    "dependency": "🔗",
    "git": "🌿",
    "code-review": "🔍",
    "changelog": "📝",
}


def skill_icon(name: str) -> str:
    for key, emoji in SKILL_ICONS.items():
        if key in name:
            return emoji
    return "📦"


def build_blocks(description: str, body: str) -> list:
    blocks = []
    if description:
        blocks.append({
            "object": "block",
            "type": "callout",
            "callout": {
                "rich_text": [{"type": "text", "text": {"content": description[:2000]}}],
                "icon": {"emoji": "🎯"},
                "color": "blue_background",
            },
        })
        blocks.append({"object": "block", "type": "divider", "divider": {}})
    # Use 1900 chars per block (Notion limit is 2000, but emoji/special chars
    # may count as more in Notion's internal encoding)
    chunk_size = 1900
    for i in range(0, min(len(body), 8000), chunk_size):
        blocks.append({
            "object": "block",
            "type": "paragraph",
            "paragraph": {
                "rich_text": [{"type": "text", "text": {"content": body[i:i + chunk_size]}}]
            },
        })
    return blocks


def clear_blocks(page_id: str, token: str):
    result = notion_request("GET", f"/blocks/{page_id}/children?page_size=100", token)
    if not result:
        return
    for block in result.get("results", []):
        notion_request("DELETE", f"/blocks/{block['id']}", token)


def sync_skill(name: str, description: str, body: str, existing: dict, token: str):
    blocks = build_blocks(description, body)
    if name in existing:
        page_id = existing[name]
        notion_request("PATCH", f"/pages/{page_id}", token, {"icon": {"type": "emoji", "emoji": skill_icon(name)}})
        clear_blocks(page_id, token)
        ok = notion_request("PATCH", f"/blocks/{page_id}/children", token, {"children": blocks})
        print(f"  ~ {name} (updated)" if ok else f"  ! {name} (update failed)", file=sys.stderr if not ok else sys.stdout)
    else:
        ok = notion_request("POST", "/pages", token, {
            "parent": {"page_id": PARENT_PAGE_ID},
            "icon": {"type": "emoji", "emoji": skill_icon(name)},
            "properties": {"title": {"title": [{"text": {"content": name}}]}},
            "children": blocks,
        })
        print(f"  + {name} (created)" if ok else f"  ! {name} (create failed)", file=sys.stderr if not ok else sys.stdout)


LAST_SYNC_FILE = Path(__file__).parent / ".skills-last-sync"


def get_changed_skills() -> list[Path]:
    if not LAST_SYNC_FILE.exists():
        return sorted(SKILLS_DIR.rglob("SKILL.md"))
    last_sync = LAST_SYNC_FILE.stat().st_mtime
    return sorted(p for p in SKILLS_DIR.rglob("SKILL.md") if p.stat().st_mtime > last_sync)


def main():
    force = "--force" in sys.argv
    if not force and not is_skill_change():
        # Called from Gradle — incremental mode
        skill_files = get_changed_skills()
        if not skill_files:
            print("No changed skills, skipping.")
            sys.exit(0)
    elif force:
        skill_files = sorted(SKILLS_DIR.rglob("SKILL.md"))
    else:
        # Hook mode — single changed file from stdin already validated
        skill_files = sorted(SKILLS_DIR.rglob("SKILL.md"))

    token = load_token()
    if not token:
        print("Error: NOTION_SKILLS_TOKEN not set in env or .env", file=sys.stderr)
        sys.exit(1)

    print(f"Syncing {len(skill_files)} skill(s) to Notion...")
    existing = get_existing_child_pages(token)

    for skill_md in skill_files:
        content = skill_md.read_text(encoding="utf-8")
        fm, body = parse_frontmatter(content)
        sync_skill(fm.get("name", skill_md.parent.name), fm.get("description", ""), body, existing, token)

    LAST_SYNC_FILE.touch()
    print("Done.")


if __name__ == "__main__":
    main()
