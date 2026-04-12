# SDLC Workflow: Notion + Claude Code

> Повний цикл від ідеї до смерджу. Документ описує як налаштований процес у проекті Spovishun і як відтворити його у будь-якому іншому проекті.

---

## Зміст

1. [Огляд флоу](#1-огляд-флоу)
2. [Що потрібно для старту](#2-що-потрібно-для-старту)
3. [Фаза 1 — Планування задачі (Notion)](#3-фаза-1--планування-задачі-notion)
4. [Фаза 2 — Старт задачі (Claude Code + хук)](#4-фаза-2--старт-задачі-claude-code--хук)
5. [Фаза 3 — Планування імплементації (Plan Mode)](#5-фаза-3--планування-імплементації-plan-mode)
6. [Фаза 4 — Імплементація](#6-фаза-4--імплементація)
7. [Фаза 5 — Code Review](#7-фаза-5--code-review)
8. [Фаза 6 — Commit та Pull Request](#8-фаза-6--commit-та-pull-request)
9. [Фаза 7 — CI тести та мердж](#9-фаза-7--ci-тести-та-мердж)
10. [Структура файлів конфігурації](#10-структура-файлів-конфігурації)
11. [Як перенести флоу в інший проект](#11-як-перенести-флоу-в-інший-проект)
12. [Довідник тригерних слів](#12-довідник-тригерних-слів)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Огляд флоу

```
┌─────────────────────────────────────────────────────────────────────┐
│  PLANNING (будь-де: мобайл, десктоп, веб)                           │
│                                                                     │
│  Описую ідею в чаті Claude  →  Claude створює структуровану задачу  │
│  за шаблоном та додає на Notion board                               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │  Перетягую картку в "To do"
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  START (Claude Code у терміналі)                                    │
│                                                                     │
│  "start new task"  →  хук notion-task-inject.js спрацьовує:        │
│    • бере першу задачу зі статусом "To do"                         │
│    • завантажує її контекст                                         │
│    • ставить статус "In progress" у Notion                          │
│    • git checkout develop && git pull && git checkout -b <branch>   │
│    • вмикає Plan Mode (Opus — найкраща модель)                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  PLAN MODE                                                          │
│                                                                     │
│  Claude будує детальний план → ти аппрувуєш / коригуєш             │
│  → план зберігається в .dev-context/<branch>_prd/plan.md           │
│  → виходимо з Plan Mode (звичайна модель для економії токенів)      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  IMPLEMENTATION                                                     │
│                                                                     │
│  Claude пише код по плану                                           │
│  PostToolUse хук: після кожного Edit .kt файлу → ./gradlew test    │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  REVIEW → COMMIT → PR → CI → MERGE                                  │
│                                                                     │
│  /code-reviewer → /commit → /git-workflow-pr-writing → CI зелений  │
│  → merge to develop → (release) → merge to main                    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Що потрібно для старту

### Інструменти

| Інструмент | Призначення |
|---|---|
| Claude Code CLI | Основний агент (IDE розширення або термінал) |
| Node.js 18+ | Виконання хук-скриптів `.js` |
| Git | Управління гілками |
| Notion workspace | Дошка задач |
| Notion Integration | API-токен для читання/запису Notion |

### Змінні середовища (`.env`)

```bash
# Notion (обов'язково для хуків)
NOTION_SKILLS_TOKEN=ntn_ваш_integration_token

# або альтернативна назва
NOTION_TOKEN=ntn_ваш_integration_token
```

> **Як отримати токен:** Notion → Settings → Connections → Develop or manage integrations → New integration → Internal → скопіювати "Internal Integration Secret". Потім у кореневій сторінці board відкрити "..." → Connections → підключити інтеграцію.

### ID дошки задач

Відкрити дошку Notion у браузері. URL виглядає як:
```
https://www.notion.so/workspace/3193462f68a980d69ec9c7ccc6329b88?v=...
                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                це DATABASE_ID — скопіювати його
```

---

## 3. Фаза 1 — Планування задачі (Notion)

### 3.1 Де відбувається

Будь-де: Claude.ai на мобайлі, у десктопному Claude, у веб-інтерфейсі — будь-яка сесія з активним скілом `notion-spovishun-task-manager`.

### 3.2 Шаблон задачі

Кожна задача в Notion має строго визначену структуру:

```markdown
**Назва:** feature/spovishun-{N}: {короткий опис}

**🎯 Мета**
Що саме треба зробити і навіщо.

**🌿 Назва гілки**
feature/spovishun-{N}-short-description

**📋 Кроки**
- Крок 1
- Крок 2
- Крок 3

**✅ Definition of Done**
> Конкретні умови завершення задачі. Тести зелені. Feature працює.

---
🤖 [закрита секція з AI-промптом для агента]
```

**Правила нумерації:**
- `{N}` — наступний порядковий номер,ніколи не повторюється
- Назва гілки: max 3 слова у kebab-case після номера

### 3.3 Як створити задачу

1. Написати Claude вільним текстом: *"Хочу додати можливість X, щоб користувач міг Y"*
2. Claude активує скіл `notion-spovishun-task-manager`
3. Задача структурується за шаблоном
4. Claude додає задачу на дошку через Notion MCP

### 3.4 Статуси дошки

```
Not started → To do → In progress → Done
```

- **Not started** — ідея, ще не готова до роботи
- **To do** — готова до взяття в роботу (черга)
- **In progress** — активна розробка (хук змінює автоматично)
- **Done** — завершено і смерджено

> Після створення задачі вручну перетягти картку з "Not started" у **"To do"**.

---

## 4. Фаза 2 — Старт задачі (Claude Code + хук)

### 4.1 Тригер

У терміналі Claude Code написати одне з:

```
start new task
```
або (українська):
```
почати нову задачу
```

### 4.2 Що відбувається всередині хука `notion-task-inject.js`

```
Отримати промпт від Claude Code
         │
         ▼
Чи містить "start new task"? ──Ні──► exit(0) (нічого не робити)
         │ Так
         ▼
Завантажити NOTION_SKILLS_TOKEN з .env
         │
         ▼
POST /v1/databases/{DATABASE_ID}/query
  filter: Status == "To do" OR "In progress"
  page_size: 1   ← бере ПЕРШУ задачу
         │
         ▼
GET /v1/blocks/{pageId}/children  ← читає контент сторінки
         │
         ▼
Витягнути назву гілки з блоку "🌿 Назва гілки"
         │
         ▼
PATCH /v1/pages/{id} → Status: "In progress"
         │
         ▼
git checkout develop
git pull origin develop
git checkout -b feature/spovishun-{N}-...
         │
         ▼
Записати кеш у .dev-context/{branch}_prd/
  ├── context.md    ← текст задачі
  ├── branch.txt    ← назва гілки
  └── session.lock  ← {ppid}:{timestamp}
         │
         ▼
Повернути в Claude Code additionalContext:
  • текст задачі
  • інструкцію: "Enter Plan Mode IMMEDIATELY"
```

### 4.3 Кешування контексту

Хук кешує контекст задачі у `.dev-context/` — Notion API викликається лише **один раз** за весь час існування гілки. При наступних сесіях на тій самій гілці контекст читається з файлу без мережевих запитів.

```
.dev-context/
└── feature-spovishun-18-member-role-system_prd/
    ├── context.md    ← текст задачі (з Notion)
    ├── branch.txt    ← "feature/spovishun-18-member-role-system"
    ├── plan.md       ← затверджений план (зберігається після Plan Mode)
    └── session.lock  ← "{ppid}:{timestamp}" (TTL: 12 годин)
```

**Session lock** запобігає подвійній ін'єкції в одній сесії. Захист від Windows PID reuse: lock вважається невалідним після 12 годин.

### 4.4 Оновлення контексту

Якщо задача змінилась у Notion після старту:

```
reread task
```
або:
```
перечитати задачу
```

Хук примусово перезавантажить контекст з Notion і оновить кеш.

---

## 5. Фаза 3 — Планування імплементації (Plan Mode)

### 5.1 Автоматичний вхід

Хук передає інструкцію:
```
⚠️ IMPORTANT: You MUST call the EnterPlanMode tool immediately before doing anything else.
Build a detailed implementation plan based on the task above.
Do NOT write any code until the plan is approved.
```

Claude Code автоматично переходить у **Plan Mode** — використовує найкращу модель (Opus) для архітектурного аналізу.

### 5.2 Що Claude робить у Plan Mode

1. Читає CLAUDE.md для розуміння архітектурних правил проекту
2. Досліджує релевантні файли (читає, але не пише)
3. Будує покроковий план з урахуванням:
   - Чистої архітектури шарів
   - Конвенцій іменування
   - Існуючих паттернів
   - Definition of Done задачі
4. Виводить план для аппрувалу

### 5.3 Аппрувал плану

- Якщо план влаштовує — написати "approve" або просто Enter
- Якщо треба скоригувати — написати що змінити, план перебудується
- Після аппрувалу Claude виходить з Plan Mode і починає імплементацію (звичайна модель — економія токенів)

---

## 6. Фаза 4 — Імплементація

### 6.1 Автоматичний контроль якості

Хук `PostToolUse` у `settings.local.json`:

```json
{
  "matcher": "Edit",
  "hooks": [{
    "type": "command",
    "command": "python ... exit(0 if f.endswith('.kt') else 1) && ./gradlew test",
    "timeout": 120
  }]
}
```

Після кожної правки `.kt` файлу автоматично запускаються тести. Якщо тести падають — Claude бачить помилки і виправляє одразу.

### 6.2 Захист від commit .env

Хук `PreToolUse`:

```json
{
  "matcher": "Bash",
  "hooks": [{
    "command": "python ... detect .env in git add/commit ... block"
  }]
}
```

Блокує будь-яку спробу додати `.env` у git.

### 6.3 Обмеження через контекст задачі

Хук після ін'єкції контексту додає:
```
*Work within the scope of this task. Do not go beyond what is described.*
```

Claude не виходить за рамки задачі і не робить незапланованих рефакторингів.

---

## 7. Фаза 5 — Code Review

Після завершення імплементації запустити мульти-агентний рев'ю:

```
/pr-review-toolkit:review-pr
```

Або окремі агенти:

| Команда | Що перевіряє |
|---|---|
| `/pr-review-toolkit:code-reviewer` | Стиль, архітектура, CLAUDE.md |
| `/pr-review-toolkit:pr-test-analyzer` | Покриття тестами, edge cases |
| `/pr-review-toolkit:silent-failure-hunter` | Error handling, silent failures |
| `/pr-review-toolkit:type-design-analyzer` | Дизайн типів, інваріанти |
| `/pr-review-toolkit:comment-analyzer` | Точність документації |

---

## 8. Фаза 6 — Commit та Pull Request

### 8.1 Commit

Використовувати скіл:

```
/commit
```

Скіл автоматично:
1. Запускає `git status` і `git diff`
2. Читає `git log` для відповідності стилю
3. Формує повідомлення у форматі: `type: short description`
4. Додає `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`

**Формат commit повідомлення:**
```
feat: add member role system with ADMIN/MODERATOR/MEMBER hierarchy
```

Типи: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `ci`, `build`, `perf`

### 8.2 Pull Request

```
/git-workflow-pr-writing
```

PR створюється з:
- Коротким заголовком (до 70 символів)
- Summary (що зроблено і навіщо)
- Test plan (чекліст перевірки)
- Посиланням на задачу в Notion

---

## 9. Фаза 7 — CI тести та мердж

1. PR відкрито → GitHub Actions запускає CI pipeline
2. CI: компіляція → unit tests → build jar
3. Зелений CI → мердж у `develop`
4. При релізі: `develop` → `main` (production)

---

## 10. Структура файлів конфігурації

```
project-root/
├── CLAUDE.md                          ← правила проекту для Claude
├── .env                               ← токени (gitignored)
├── .env.example                       ← шаблон змінних
├── .gitignore                         ← includes: .dev-context/, .env
│
└── .claude/
    ├── settings.json                  ← MCP servers + UserPromptSubmit хук
    ├── settings.local.json            ← permissions + PreToolUse/PostToolUse хуки
    │
    ├── hooks/
    │   └── notion-task-inject.js      ← головний хук: Notion → Claude Code
    │
    ├── scripts/
    │   └── sync-skills-to-notion.py   ← синк документації скілів у Notion
    │
    └── skills/
        ├── notion-spovishun-task-manager/SKILL.md
        ├── notion-task-to-code/SKILL.md
        ├── notion-page-builder/SKILL.md
        ├── notion-workflow-spovishun/SKILL.md
        ├── commit/SKILL.md
        ├── git-workflow-pr-writing/SKILL.md
        ├── code-reviewer/SKILL.md
        └── ... (інші скіли)
```

### `settings.json` — мінімальна конфігурація

```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"]
    }
  },
  "hooks": {
    "UserPromptSubmit": [{
      "matcher": "",
      "hooks": [{
        "type": "command",
        "command": "node .claude/hooks/notion-task-inject.js",
        "timeout": 30
      }]
    }]
  }
}
```

### `settings.local.json` — локальні правила (не комітити)

```json
{
  "permissions": {
    "allow": ["Bash(*)", "Glob", "Write", "...Notion MCP tools..."],
    "deny": ["Bash(rm *)", "Bash(git push --force *)"],
    "ask": ["Bash(git push *)"]
  },
  "hooks": {
    "PreToolUse": [{
      "matcher": "Bash",
      "hooks": [{ "command": "...block .env in git..." }]
    }],
    "PostToolUse": [{
      "matcher": "Edit",
      "hooks": [{ "command": "...run tests on .kt edit...", "timeout": 120 }]
    }]
  }
}
```

---

## 11. Як перенести флоу в інший проект

### Крок 1 — Підготувати Notion дошку

1. Створити базу даних у Notion з полями:
   - `Name` (title)
   - `Status` (status) зі станами: `Not started`, `To do`, `In progress`, `Done`
2. Скопіювати DATABASE_ID з URL дошки

### Крок 2 — Отримати Notion API токен

1. Notion → Settings → Connections → Develop or manage integrations
2. New integration → Internal
3. Скопіювати "Internal Integration Secret"
4. Підключити інтеграцію до кореневої сторінки дошки (... → Connections)

### Крок 3 — Скопіювати хук

1. Скопіювати `.claude/hooks/notion-task-inject.js` у проект
2. Змінити `DATABASE_ID` на ID своєї дошки (рядок 24)
3. При потребі адаптувати `TRIGGER_WORDS` (рядки 29–34)

### Крок 4 — Налаштувати `.env`

```bash
NOTION_SKILLS_TOKEN=ntn_ваш_токен
```

### Крок 5 — Налаштувати `settings.json`

```json
{
  "hooks": {
    "UserPromptSubmit": [{
      "matcher": "",
      "hooks": [{
        "type": "command",
        "command": "node .claude/hooks/notion-task-inject.js",
        "timeout": 30
      }]
    }]
  }
}
```

### Крок 6 — Додати `.gitignore` записи

```gitignore
.env
.dev-context/
```

### Крок 7 — Адаптувати шаблон задачі

У скілі `notion-spovishun-task-manager` або аналогічному:
- Змінити префікс гілки (`feature/spovishun-N` → `feature/yourproject-N`)
- Адаптувати шаблон задачі під специфіку проекту

### Крок 8 — Налаштувати PostToolUse хук під технологію

```json
"PostToolUse": [{
  "matcher": "Edit",
  "hooks": [{
    "command": "...detect .ext file... && run tests",
    "timeout": 120
  }]
}]
```

Замінити `.kt` і `./gradlew test` на потрібні розширення і команду тестів.

### Крок 9 — Написати CLAUDE.md

Обов'язково включити:
- Архітектурні обмеження
- Конвенції іменування файлів
- Команди запуску/тестування
- Правила написання commit повідомлень

---

## 12. Довідник тригерних слів

| Слово/фраза | Дія хука |
|---|---|
| `start new task` | Старт задачі: Notion → branch → Plan Mode |
| `почати нову задачу` | те саме (українська) |
| `implement` | Ін'єкція контексту активної задачі |
| `refactor` | Ін'єкція контексту активної задачі |
| `реалізуй` | Ін'єкція контексту активної задачі |
| `розроби` | Ін'єкція контексту активної задачі |
| `задача` | Ін'єкція контексту активної задачі |
| `таск` | Ін'єкція контексту активної задачі |
| `фіча` | Ін'єкція контексту активної задачі |
| `reread task` | Примусове оновлення кешу з Notion |
| `перечитати задачу` | те саме (українська) |
| `update task context` | те саме |

---

## 13. Troubleshooting

### Хук не спрацьовує

```bash
# Перевірити що Node.js доступний
node --version

# Перевірити що токен є
grep NOTION_SKILLS_TOKEN .env

# Запустити хук вручну
echo '{"prompt":"start new task"}' | node .claude/hooks/notion-task-inject.js
```

### "NOTION_SKILLS_TOKEN not set"

Перевірити що файл `.env` знаходиться у корені проекту (де запускається Claude Code), і містить:
```
NOTION_SKILLS_TOKEN=ntn_...
```

### Хук знаходить не ту задачу

Хук бере **першу** задачу зі статусом "To do" або "In progress". Якщо їх кілька — переконатись що потрібна задача стоїть першою (Notion сортує за часом створення). Або тимчасово перенести непотрібні в "Not started".

### Branch вже існує

```bash
git branch -D feature/spovishun-N-...
```
Або хук при `start new task` повідомить `"Already on <branch>"` якщо вже на правильній гілці.

### Контекст застарів після зміни задачі в Notion

```
reread task
```

### `./gradlew test` займає занадто довго після кожного Edit

У `settings.local.json` збільшити `timeout` або звузити matcher:
```json
"command": "...exit(0 if 'src/main' in f and f.endswith('.kt') else 1)... && ./gradlew test"
```
