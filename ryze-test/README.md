# RyzeSMP Test Client + AI Remote-Control System

A browser-accessible, remotely-controlled **real Minecraft client** for RyzeSMP,
plus a secure **MCP interface** so an authorized AI (ChatGPT) can run an
observe → act → verify testing loop against the live (or a test) server.

This module is **self-contained** and does **not** touch the existing
`suggestions-bot/`. It runs as its own Pterodactyl Node egg.

---

## What this is (honestly)

There is no legal/technical way to run a full *vanilla* Minecraft Java client
inside a browser. Instead this uses the proven, legitimate approach:

- A **server-side protocol-level client** (`mineflayer`) authenticated with
  **your own Microsoft account** via the official OAuth **device-code flow**
  (you log in on your own device at `microsoft.com/link`; your password is
  never seen or stored — only the resulting tokens are cached).
- Both a **human** (browser, PC/iPad/phone, touch + keyboard) and **ChatGPT**
  (via MCP) observe and control the *same* client — a unified
  observe/act loop.
- The shared "screen" is a **live schematic** (radar + HUD + chat) rendered
  from real game state, GPU-free, works on every device. An optional real 3D
  frame (`prismarine-viewer`) can be enabled where native rendering deps are
  installed.

**Limitations (not over-claimed):**
- The default view is a game-state schematic, not a photoreal vanilla frame.
- `mineflayer` supports Minecraft up to 1.21.11 (what RyzeSMP runs today); newer
  server versions may need a library update before the bot can connect.
- Resource-pack/shader visuals are not reproduced.

---

## Architecture

```
Browser (PC / iPad / mobile, touch+keyboard)
   │  HTTPS + WSS  (owner JWT)
   ▼
ryze-test service (Node/TypeScript, Fastify)      ── Pterodactyl Node egg
   ├─ auth/microsoft  : MS OAuth device-code (prismarine-auth), tokens cached
   ├─ auth/sessions   : dashboard JWT + MCP bearer token (constant-time)
   ├─ mc/bot          : mineflayer client — observe() / act()
   ├─ mc/viewer       : schematic SVG (+ optional 3D render)
   ├─ mc/sessionMgr   : lifecycle, timeouts, dangerous-command approval
   ├─ security        : RBAC (observe/control/admin), rate limit, SQLite audit
   ├─ api/rest + ws   : dashboard API + live view/control channel
   ├─ mcp/server      : MCP (HTTP) tools for ChatGPT
   └─ public/         : responsive dashboard (no build step)
   │
   ▼ node-minecraft-protocol (1.21.11)
RyzeSMP  (Velocity :25565 → SMP/Lobby)
```

## Security model

- **Authenticated only** — dashboard needs the owner password; MCP needs a
  bearer token. No unauthenticated control endpoint.
- **Separate scopes** — `observe` (read/screenshot) vs `control` (move/chat) vs
  `admin` (start/stop sessions, approve dangerous ops). ChatGPT gets
  observe+control, **not** admin.
- **Dangerous commands** (`/op`, `/stop`, `/ban`, …) are blocked in `safe`
  mode and require explicit owner approval in the dashboard.
- **Rate limiting**, **session idle timeout**, and an **append-only audit log**
  of every AI/owner action (SQLite).
- **No secrets in the browser or logs** — tokens/passwords come from env vars
  and are redacted in logs.

## MCP tools exposed to ChatGPT

`get_server_status`, `start_test_session`, `stop_test_session`,
`get_player_status`, `get_game_screenshot`, `inspect_inventory`,
`send_chat_message`, `execute_player_action`, `get_test_results`,
`run_gameplay_test`.

## Local run

```bash
cp .env.example .env    # set JWT_SECRET, DASHBOARD_PASSWORD, MCP_TOKEN
npm install
npm run build
npm start               # dashboard on http://localhost:25571
```

Open the dashboard → log in → **Login starten** (Microsoft) → enter the code at
`microsoft.com/link` → **Session starten**.

See `DEPLOY-PTERODACTYL.md` for the full server deployment and ChatGPT setup.
