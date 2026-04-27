# Notion Upload — Diagram Screenshot Flow

**When to use:** after generating a diagram HTML, you have a target Notion page ID and want to publish the screenshot + source file.

Output goes to `docs/diagrams/` (gitignored — local only).

---

## Prerequisites

- `NOTION_TOKEN` in project `.env`
- Google Chrome installed (headless)
- `docs/diagrams/` directory (create with `mkdir -p docs/diagrams` if absent)

---

## Step 1 — Take screenshot

Find Chrome and convert the file path to a Windows-style URL (git-bash has POSIX paths, Chrome needs `C:/...`):

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
HTML_PATH="docs/diagrams/<name>.html"
PNG_PATH="docs/diagrams/<name>.png"

# Convert git-bash path to Windows-style for file:// URL
WIN_URL="file:///$(pwd | sed 's|^/\([a-z]\)/|\U\1:/|')/$HTML_PATH"

# Determine window height from SVG viewBox + ~400px for page chrome (header + cards + footer)
VIEWBOX_H=$(grep -o 'viewBox="[^"]*"' "$HTML_PATH" | head -1 | awk '{print $4}' | tr -d '"')
WIN_H=$(( ${VIEWBOX_H:-800} + 400 ))

"$CHROME" --headless=new --screenshot="$PNG_PATH" --window-size=1400,$WIN_H "$WIN_URL" 2>/dev/null
```

> If Chrome is elsewhere: `where chrome` on Windows CMD, or check `/c/Program Files (x86)/...`.

---

## Step 2 — Load Notion token

```bash
export $(grep NOTION_TOKEN .env | head -1 | xargs)
```

---

## Step 3 — Upload PNG

```bash
# Create upload object
PNG_UPLOAD=$(curl -s -X POST https://api.notion.com/v1/file_uploads \
  -H "Authorization: Bearer $NOTION_TOKEN" \
  -H "Notion-Version: 2022-06-28" \
  -H "Content-Type: application/json" \
  -d "{\"filename\":\"<name>.png\",\"content_type\":\"image/png\",\"mode\":\"single_part\"}")
PNG_ID=$(echo "$PNG_UPLOAD" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Send bytes
curl -s -X POST "https://api.notion.com/v1/file_uploads/$PNG_ID/send" \
  -H "Authorization: Bearer $NOTION_TOKEN" \
  -H "Notion-Version: 2022-06-28" \
  -F "file=@$PNG_PATH;type=image/png" > /dev/null
```

---

## Step 4 — Upload HTML source

```bash
HTML_UPLOAD=$(curl -s -X POST https://api.notion.com/v1/file_uploads \
  -H "Authorization: Bearer $NOTION_TOKEN" \
  -H "Notion-Version: 2022-06-28" \
  -H "Content-Type: application/json" \
  -d "{\"filename\":\"<name>.html\",\"content_type\":\"text/html\",\"mode\":\"single_part\"}")
HTML_ID=$(echo "$HTML_UPLOAD" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

curl -s -X POST "https://api.notion.com/v1/file_uploads/$HTML_ID/send" \
  -H "Authorization: Bearer $NOTION_TOKEN" \
  -H "Notion-Version: 2022-06-28" \
  -F "file=@$HTML_PATH;type=text/html" > /dev/null
```

---

## Step 5 — Append to Notion page

```bash
curl -s -X PATCH "https://api.notion.com/v1/blocks/<PAGE_ID>/children" \
  -H "Authorization: Bearer $NOTION_TOKEN" \
  -H "Notion-Version: 2022-06-28" \
  -H "Content-Type: application/json" \
  -d "{\"children\":[
    {\"type\":\"image\",\"image\":{\"type\":\"file_upload\",\"file_upload\":{\"id\":\"$PNG_ID\"}}},
    {\"type\":\"file\",\"file\":{\"type\":\"file_upload\",\"file_upload\":{\"id\":\"$HTML_ID\"},\"name\":\"<name>.html\"}}
  ]}"
```

The response should be `{"object":"list",...}`. Check for `"type":"image"` in the results to confirm the image was added.

---

## PAGE_ID — Architecture database entries

| Diagram | Target page | Page ID |
|---|---|---|
| Clean Architecture layer stack | Architecture Diagram | `3193462f-68a9-819b-a9a9-e00e46fedba9` |
| Command flow | Command Flow | `34f3462f-68a9-8185-b218-c0027df5d7f8` |

For new diagrams: either create a new entry in the Architecture DB (`c4cea10d5e4d4ad6a4f226e1022eb49a`) using `mcp__claude_ai_Notion__notion-create-pages`, or append to the relevant existing page.

---

## Naming convention

| Type | HTML filename | PNG filename |
|---|---|---|
| Architecture / layer stack | `<slug>-architecture.html` | `<slug>-architecture.png` |
| Flowchart | `<slug>-flowchart.html` | `<slug>-flowchart.png` |
| Sequence | `<slug>-sequence.html` | `<slug>-sequence.png` |
| ER model | `<slug>-er.html` | `<slug>-er.png` |
| State machine | `<slug>-state.html` | `<slug>-state.png` |
