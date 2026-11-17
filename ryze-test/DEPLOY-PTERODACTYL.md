# Deploy the RyzeSMP Test Client on Pterodactyl + connect ChatGPT

This runs as its own Node egg, completely separate from `ryzesmp-suggestions-bot`.
Nothing here changes the suggestions bot.

---

## 0. Prerequisites

- A **dedicated Microsoft/Minecraft Java account** for the test bot (do not use
  your personal one). You will log it in yourself; the service never sees the
  password.
- That account **whitelisted** on RyzeSMP (and ideally limited to a test
  world / low-permission rank — avoid giving it op).
- A Pterodactyl server created from `egg-ryze-test.json`.
- A way to expose the dashboard over **HTTPS** (required for ChatGPT's MCP
  connector and for secure WebSockets). A reverse proxy such as Caddy or Nginx
  in front of the container port is the normal way.

**Resource estimate:** ~1 vCPU and **512 MB–1 GB RAM** for one session
(`MAX_SESSIONS=1`). Disk ~300 MB (node_modules). GPU not required.

---

## 1. Import the egg & create the server

1. Panel → **Admin → Nests → Import Egg** → upload `egg-ryze-test.json`.
2. Create a new server using it on `ryze-node-1`.
3. Give it **1 allocation** (one port). Suggested: `25572` (25571 is free by
   default in your range; pick any free one and note it).
4. Limits: **1 GB RAM**, **1 CPU core is plenty**, **1 GB disk**.

## 2. Configure variables (Startup tab)

| Variable | Set to |
|---|---|
| `GIT_ADDRESS` | `https://github.com/mio530/Minecraft-mods` |
| `BRANCH` | `claude/ryzesmp-web-minecraft-testing-9wsbkh` |
| `PORT` | **leave EMPTY** — it auto-uses the primary allocation port |
| `DASHBOARD_PASSWORD` | a strong password (min 6) |
| `JWT_SECRET` | `openssl rand -hex 32` |
| `MCP_TOKEN` | `openssl rand -hex 24` |
| `MCP_ALLOW_SESSIONS` | `true` (lets Claude Code start sessions) |
| `MC_HOST` / `MC_PORT` | `203.0.113.10` / `25565` (Velocity) |
| `MC_VERSION` | `1.21.11` |
| `MC_AUTH` | `microsoft` |
| `SAFETY_MODE` | `safe` |

> Generate secrets locally, e.g. `openssl rand -hex 32`. Never reuse the Discord
> bot token or any other secret here.

## 3. HTTPS reverse proxy (example: Caddy)

Point a subdomain (e.g. `test.ryzesmp.org`) at the container port. WebSockets
must pass through.

```caddy
test.ryzesmp.org {
    reverse_proxy 203.0.113.10:25572
}
```

Caddy handles TLS and WebSocket upgrades automatically. For Nginx, add the
standard `Upgrade`/`Connection` headers on the `location /`.

## 4. Install & start

1. **Reinstall** the server once (pulls the branch into `/home/container`).
2. **Start**. The console shows `ryze-test listening` when ready.
3. Open `https://test.ryzesmp.org`.

## 5. Authorize the Minecraft account (one time)

1. Log in to the dashboard with `DASHBOARD_PASSWORD`.
2. Click **Login starten** in the Microsoft banner.
3. A code appears — open `microsoft.com/link` **on your own device**, sign in
   with the **test account**, enter the code. The banner disappears when done.
   Tokens are cached in `.auth-cache/` (survives restarts; **your password is
   never stored**).

## 6. Start a test session

- Click **Session starten**. The bot connects to RyzeSMP; the live radar/HUD
  starts updating. Move with WASD / the on-screen D-pad, drag the view to look,
  type chat/commands.

---

## 7. Connect ChatGPT (MCP custom connector)

ChatGPT can call this service through a **remote MCP connector**. You need a
ChatGPT plan where custom connectors / developer mode are available
(Plus/Pro/Business, "Settings → Connectors → Advanced / Developer mode").

1. ChatGPT → **Settings → Connectors → Add / Create** → choose **MCP /
   custom connector**.
2. **URL:** `https://test.ryzesmp.org/mcp`
3. **Authentication:** Bearer token → paste your `MCP_TOKEN`.
4. Save. ChatGPT lists the tools (`get_player_status`, `get_game_screenshot`, …).

> Do not consider ChatGPT "connected" until it has actually listed the tools and
> a test call (e.g. `get_server_status`) returns data. The owner must have an
> active session running (step 6) for gameplay tools to work; `start_test_session`
> can also start one if the Microsoft login is already authorized.

### How the AI should test (observe/act loop)

1. `get_server_status` / `get_player_status` — observe.
2. `get_game_screenshot` — see the state.
3. One `execute_player_action` (it returns before+after observations).
4. Verify the change, then `run_gameplay_test` to record a pass/fail.
Dangerous commands return `approvalRequired=true` and wait for you to approve in
the dashboard.

---

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `microsoft_login_required` on start | Do step 5 first (dashboard MS login). |
| Bot kicked immediately | Test account not whitelisted, or wrong `MC_AUTH`. Whitelist it; keep `MC_AUTH=microsoft` for online-mode. |
| `unsupported protocol` / can't connect | Server version newer than mineflayer supports. Check `MC_VERSION`; update `mineflayer` when a newer one ships. |
| Device code expired | Click **Login starten** again for a fresh code. |
| ChatGPT can't reach `/mcp` | Needs public **HTTPS**; check the reverse proxy and that `MCP_TOKEN` matches. |
| Screenshot is a schematic, not 3D | Expected default (GPU-free). Set `VIEWER_ENABLED=true` only if native canvas/gl is installed. |
| `rate_limit_exceeded` | Raise `ACTION_RATE_PER_MIN` or slow the AI down. |
| `Invalid configuration: { PORT: ['Number must be greater than 0'] }` | Fixed: leave the `HTTP Port` variable **empty** so it uses the allocation's `SERVER_PORT`. Reinstall to pull the latest branch. The app binds `0.0.0.0:<SERVER_PORT>`. |
| `EADDRNOTAVAIL ... 203.0.113.10:<port>` | Fixed: the listener now always binds `0.0.0.0` (never `SERVER_IP`). Reinstall to pull the latest branch. |
| `ERR_MODULE_NOT_FOUND: Cannot find package 'fastify'` | Dependencies did not install completely. Fixed: the egg now installs without suppressing errors and verifies runtime deps. **Reinstall** the server (Settings → Reinstall) — that runs a clean `npm ci` + build + production install. Do not just restart. |
| Dashboard works but WS view frozen | Reverse proxy not forwarding WebSocket upgrades. |

## 9. Keeping the suggestions bot safe

This service and the bot are **different Pterodactyl servers** with different
ports, data and secrets. They share only the git repo (different subfolders:
`suggestions-bot/` vs `ryze-test/`). Updating one never affects the other.
