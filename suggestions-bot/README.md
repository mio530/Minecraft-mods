# RyzeSMP – AI Suggestions Bot

An automated, AI-powered suggestions & voting bot for the **RyzeSMP** Minecraft
server. Members post ideas in a Discord **forum** channel; the bot reads them,
creates a neutral AI summary, and opens a clean vote in a separate voting
channel. Staff moderate via slash commands, and everything is logged.

- **TypeScript + discord.js v14**
- **SQLite** (via `better-sqlite3`) with migrations and a backend-agnostic store
  interface (PostgreSQL can be added later without touching the bot logic)
- **Restart-safe**: persistent components, stable custom IDs, startup recovery
- **Swappable AI**: `openai`, `anthropic`, or `none` (deterministic fallback)
- Minimal dependencies, token/resource friendly (AI runs **once** per suggestion)

---

## How it works

| Channel | Type | Purpose |
|---|---|---|
| **A – Submissions** | Forum | Members create a forum post per suggestion. |
| **B – Voting** | Text | Bot posts one voting message (👍 / 👎) per suggestion. |
| **C – Logs** | Text (staff) | New suggestions, results, and moderation decisions. |

Pipeline for each new forum post:
1. Detect the new thread → read title, body, author.
2. Store the original data and assign a unique id (`RS-001`, `RS-002`, …).
3. Create an AI summary (cached in the DB; never re-run on restart).
4. Post a voting message in the voting channel, linking the original post.
5. Log it in the staff channel; warn if it looks like a **duplicate**.

Voting results are **decision helpers only** — the bot never auto-approves.

---

## Requirements

- Node.js **20+** (tested on Node 22)
- A Discord application + bot token
- Build tools for `better-sqlite3` native module (on Debian/Ubuntu:
  `apt-get install -y python3 make g++`). Prebuilt binaries cover most
  platforms, so this is usually not needed.

### Discord setup (one time)
1. Create an application at <https://discord.com/developers/applications>.
2. **Bot** tab → add a bot, copy the **token**.
3. Enable the **Message Content Intent** (needed to read forum post bodies).
4. Invite the bot with the `bot` + `applications.commands` scopes and these
   permissions: **Manage Channels, Manage Roles, View Channel, Send Messages,
   Embed Links, Read Message History, Manage Threads**.
   (Manage Channels + Manage Roles are required so the bot can create the
   channels and set their permissions during `/suggestion setup`.)
5. Start the bot, then run **`/suggestion setup`** in your server. The bot
   creates the category and all three channels, sets permissions, adds forum
   tags, and stores the IDs automatically — **you never copy channel IDs**.

> Prefer to use existing channels? Put their IDs in `.env`
> (`SUBMISSION_CHANNEL_ID`, …). Database values from `/suggestion setup` always
> take precedence over the environment fallback.

---

## Installation

```bash
cd suggestions-bot
cp .env.example .env        # fill in DISCORD_* values (channels optional)
npm install
npm run build
npm start                   # commands auto-register on startup
```

Then in Discord run **`/suggestion setup`** (as staff) to create the channels.
`npm run deploy-commands:prod` still exists if you prefer manual registration,
but it is no longer required — the bot registers its guild commands on start.

Development mode (auto-reload, TypeScript directly):

```bash
npm run dev
```

The SQLite database and its schema are created automatically on first start
(`DATABASE_PATH`, default `./data/suggestions.db`). Migrations run on boot.

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `DISCORD_TOKEN` | ✅ | Bot token |
| `DISCORD_CLIENT_ID` | ✅ | Application (client) ID |
| `DISCORD_GUILD_ID` | ✅ | Server ID |
| `SUBMISSION_CHANNEL_ID` | – | Optional fallback; auto-set by `/suggestion setup` |
| `VOTING_CHANNEL_ID` | – | Optional fallback; auto-set by `/suggestion setup` |
| `LOG_CHANNEL_ID` | – | Optional fallback; auto-set by `/suggestion setup` |
| `STAFF_ROLE_IDS` | – | Comma-separated staff role IDs. If empty, moderation needs the **Manage Server** permission. |
| `AI_PROVIDER` | – | `openai` \| `anthropic` \| `none` (default `none`) |
| `AI_API_KEY` | if provider≠none | API key (**env only — never in code/logs**) |
| `AI_MODEL` | – | e.g. `gpt-4o-mini`, `claude-haiku-4-5` |
| `AI_RATE_LIMIT_PER_MINUTE` | – | Max AI calls/min (default 10) |
| `AI_SIMILARITY_THRESHOLD` | – | Duplicate threshold 0–1 (default 0.82) |
| `VOTING_DURATION_DAYS` | – | Auto-close after N days, `0` = never (default 7) |
| `MINIMUM_VOTES` | – | Meaningful vote count for evaluation (default 10) |
| `MINIMUM_UPVOTES` | – | Positive-hint upvote threshold (default 5) |
| `MINIMUM_POSITIVE_RATIO` | – | Positive-hint ratio 0–100 (default 60) |
| `EVALUATION_INTERVAL_MINUTES` | – | Expiry check interval (default 15) |
| `DATABASE_PATH` | – | SQLite file path (default `./data/suggestions.db`) |
| `SUGGESTION_ID_PREFIX` | – | ID prefix (default `RS`) |
| `LOG_LEVEL` | – | `debug` \| `info` \| `warn` \| `error` |

> The `.env` file is git-ignored and must never be committed. Tokens/keys are
> redacted from logs automatically.

---

## Slash commands (`/suggestion …`)

| Subcommand | Access | Description |
|---|---|---|
| `setup` | staff | **Automatically create & configure** the category + 3 channels |
| `setup-status` | staff | Check whether channels & permissions are correct |
| `setup-repair` | staff | Create missing channels & fix permissions |
| `config` | staff | Show current configuration (no secrets) |
| `stats` | all | Suggestion statistics |
| `leaderboard` | all | Most popular suggestions |
| `view <id>` | all | Details + moderation history of a suggestion |
| `approve <id> [reason]` | staff | 🟢 Approve |
| `reject <id> [reason]` | staff | 🔴 Reject |
| `implement <id> [reason]` | staff | 🔵 Mark implemented |
| `close <id> [reason]` | staff | ⚫ Close & end voting |
| `reopen <id> [reason]` | staff | Reopen voting |

Voters use the **👍 / 👎 / ♻️ Clear** buttons. One active vote per user; they can
change or withdraw it. Buttons keep working after a restart.

---

## Automatic setup (`/suggestion setup`)

Creates (idempotently — never duplicates, never deletes existing suggestions):

- **Category `💡 SUGGESTIONS`**
- **`💡・suggestion-submissions`** (Forum) — members can create & comment on
  posts; tags **Gameplay, Events, PvP, Economy, Other**; bot can read/process.
- **`📊・suggestion-voting`** (Text) — members can view & vote (buttons) but
  **cannot send messages**; only the bot posts.
- **`📋・suggestion-logs`** (Text, private) — visible to **staff + bot only**.

Channel IDs are stored in the database, so members never copy IDs. Re-running
is safe: missing channels are created, existing ones are kept, and
`setup-repair` re-applies the correct permission overwrites. If Discord blocks
part of the setup (e.g. a rate limit), simply run it again — it resumes.

Minimal permission overwrites are applied for `@everyone`, the bot, and each
configured staff role. Voting buttons work even though members cannot post,
because Discord interactions do not require send permission.

---

## Hosting on Pterodactyl

**Easiest:** import the ready-made egg `pterodactyl/egg-ryzesmp-suggestions-bot.json`
(Admin → Nests → Import Egg). It clones only this subfolder, builds in the
Node.js 22 runtime, and exposes all Discord/AI settings as panel variables.

Otherwise, use a generic **Node.js egg** (Node 20+) and upload the
`suggestions-bot` folder (or clone the repo) into the server's root.

**Install / build** (run once, e.g. via the egg's install script or console):
```bash
npm ci && npm run build
```

**Startup command:**
```bash
node dist/index.js
```

Recommended panel settings:
- **Startup command:** `node dist/index.js`
- **Required env variables:** only `DISCORD_TOKEN`, `DISCORD_CLIENT_ID`,
  `DISCORD_GUILD_ID` (plus `STAFF_ROLE_IDS` and any `AI_*`). Channel IDs are
  **not** needed — run `/suggestion setup` after the bot starts.
- Slash commands register themselves on startup (no deploy step).
- **Required Discord bot permissions:** Manage Channels, Manage Roles,
  View Channel, Send Messages, Embed Links, Read Message History, Manage Threads.
- Keep the `data/` directory on persistent storage so the database (channel IDs,
  suggestions, votes) survives restarts and reinstalls.
- If `npm run build` can't run on startup, build locally and upload `dist/`.

The bot handles `SIGINT`/`SIGTERM` for clean shutdowns (Pterodactyl stop/restart).

---

## Tests

```bash
npm test          # run once
npm run test:watch
```

Covered: suggestion creation, AI fallback, duplicate detection, up/down votes,
vote changes & removal, duplicate events (idempotency), moderation rights &
status changes, restart recovery, automatic evaluation, **automatic channel
setup (create/idempotency/resume/permission-guard)**, and **channel-ID
resolution (DB-first, env fallback)**.

---

## Project structure

```
suggestions-bot/
├── src/
│   ├── index.ts                 # entrypoint
│   ├── config/                  # env loading & validation
│   ├── database/                # store interface, SQLite impl, migrations
│   ├── services/
│   │   ├── ai/                  # provider interface, openai, anthropic, fallback
│   │   ├── suggestions/         # create-from-forum pipeline
│   │   ├── voting/              # vote logic (one vote/user, change, remove)
│   │   ├── moderation/          # staff decisions
│   │   ├── setup/              # automatic channel creation & repair
│   │   └── evaluation.ts        # non-binding result hints
│   ├── bot/
│   │   ├── client.ts            # discord.js client + wiring
│   │   ├── context.ts           # services + Discord side effects
│   │   ├── flows.ts             # orchestration
│   │   ├── scheduler.ts         # auto-close expired votings
│   │   ├── commands/            # /suggestion
│   │   ├── components/          # embeds, buttons, custom IDs
│   │   └── events/              # ready, threadCreate, interactionCreate
│   ├── utils/                   # logger, text, locks
│   └── types/
└── tests/
```

---

## Design decisions

- **Duplicate detection runs locally** (cosine similarity over bag-of-words), so
  it costs no AI tokens and works with `AI_PROVIDER=none`.
- **AI is called exactly once** per suggestion and the result is stored, so
  restarts and vote updates never trigger new AI requests.
- **Votes are the source of truth** in a `votes` table; the `upvotes`/`downvotes`
  on a suggestion are denormalized counters recomputed inside a transaction.
- **PostgreSQL-ready**: implement `SuggestionStore` and swap `createStore()`.
