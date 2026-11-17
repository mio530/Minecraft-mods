# Claude Code as the primary MCP controller

Claude Code (this agent) is the **primary** AI client that connects to the
ryze-test MCP server and drives the Minecraft bot. ChatGPT is supported as an
**optional second** client against the same endpoint. Both use the same MCP
HTTP transport and bearer token.

## Why the bot runs on Pterodactyl, not in Claude's sandbox

Verified facts about Claude Code's execution sandbox (measured, not assumed):

- Raw TCP to `203.0.113.10:25565` (RyzeSMP) → **ECONNREFUSED** (egress blocked).
- Xbox/Minecraft auth hosts (`user.auth.xboxlive.com`,
  `api.minecraftservices.com`, `sessionserver.mojang.com`) → **blocked**.

So the Minecraft bot **cannot** run inside Claude's sandbox. It must run where
RyzeSMP and the Microsoft auth hosts are reachable — the **Pterodactyl
deployment**. Claude then connects to that server's **public MCP endpoint over
HTTPS** and controls the bot remotely. This is the only honest, working
topology.

```
Claude Code (MCP client)  ──HTTPS──▶  ryze-test on Pterodactyl  ──TCP──▶  RyzeSMP
   src/mcp/cli.js / client.ts            /mcp  (bearer token)        mineflayer bot
```

## One-time setup (owner, cannot be automated)

1. Create the Pterodactyl server from `egg-ryze-test.json`, set secrets,
   expose `https://<host>/mcp` (see `DEPLOY-PTERODACTYL.md`). Set
   `MCP_ALLOW_SESSIONS=true` so Claude can start/stop sessions itself.
2. In the dashboard, complete the **Microsoft device login** with the test
   account (your password is never stored). After this, Claude can start
   sessions autonomously — the token is cached on the server.

## Connecting Claude Code natively (`.mcp.json`) — primary mechanism

The repo ships a project-scoped `.mcp.json` (at the repo root) registering the
`ryze-test` MCP server over HTTP. **No secrets are in the file** — it reads two
environment variables:

```jsonc
{
  "mcpServers": {
    "ryze-test": {
      "type": "http",
      "url": "${RYZE_MCP_URL}",
      "headers": { "Authorization": "Bearer ${RYZE_MCP_TOKEN}" }
    }
  }
}
```

To make Claude Code use it:

1. In the environment Claude Code runs in, set:
   `RYZE_MCP_URL=https://<host>/mcp` and `RYZE_MCP_TOKEN=<token>`.
2. Start Claude Code in this repo and **approve** the project MCP server when
   prompted (project `.mcp.json` servers require a one-time approval). Verify
   with `claude mcp list` → `ryze-test … ✓ connected`.
3. Claude Code now has native `mcp__ryze-test__*` tools (auto-discovered) and
   calls them directly — e.g. `get_player_status`, `execute_player_action`.

> This config is **detected and validated** by Claude Code today
> (`claude mcp get ryze-test` shows the HTTP server, pending approval). It
> becomes active in a Claude Code session once the env vars are set, the server
> is reachable, and the project server is approved. A running session will not
> hot-load it — start a fresh session after setting the vars.

### Local stdio alternative (offline/dev only)

For a local server Claude Code spawns itself (no HTTP, no RyzeSMP):

```bash
claude mcp add ryze-test-local -- \
  env ALLOW_INSECURE=1 MC_AUTH=offline node ryze-test/dist/mcp/stdio.js
```

The bot still can't reach RyzeSMP from a blocked sandbox; use this only to
exercise read-only tools and the simulated loop.

## Connecting with the bundled CLI (fallback)

Claude's sandbox must be allowed to reach `<host>` over HTTPS. If it is blocked
(as papermc/mojang are), Claude requests that the host be added to its
environment's network allowlist (owner approves once). Then:

```bash
# from the ryze-test folder, after `npm run build`
export MCP_URL="https://<host>/mcp"
export MCP_TOKEN="<your MCP_TOKEN>"

npm run mcp -- "$MCP_URL" "$MCP_TOKEN" list                 # list tools
npm run mcp -- "$MCP_URL" "$MCP_TOKEN" status               # server status
npm run mcp -- "$MCP_URL" "$MCP_TOKEN" call start_test_session '{}'
npm run mcp -- "$MCP_URL" "$MCP_TOKEN" call get_player_status '{}'
npm run mcp -- "$MCP_URL" "$MCP_TOKEN" call execute_player_action '{"type":"move","direction":"forward","durationMs":800}'
npm run mcp -- "$MCP_URL" "$MCP_TOKEN" screenshot /tmp/view.svg
```

`client.ts` exposes the same thing programmatically (`connectMcp(url, token)`)
so Claude can run the full observe → act → verify loop in code.

## Autonomous test/fix loop

1. `start_test_session` → bot joins RyzeSMP.
2. `get_player_status` + `get_game_screenshot` → observe.
3. `execute_player_action` / `send_chat_message` / `run_gameplay_test` → act.
   Each action returns before+after observations to verify the effect.
4. On a failing RyzeSMP feature test: inspect the relevant plugin/config,
   adjust `ryzesmp-tests.json` or fix the code, redeploy, re-run.
5. Dangerous server commands return `approvalRequired` and wait for the owner.

## Acceptance matrix (Phase 7) — honest status

Legend: **Impl** = implemented · **Local** = locally tested · **Sim** = tested
against the simulated-protocol bot · **RealMC** = tested against real RyzeSMP ·
**ByClaude** = exercised by Claude Code through its own MCP connection.

| # | Acceptance step | Status |
|---|---|---|
| — | MCP client (official SDK, HTTP transport, env auth, reconnect, discovery) | Impl + Local |
| — | `.mcp.json` native Claude Code config | Impl + detected by `claude mcp` |
| 1 | Testing service deployed | ⏳ needs you (create Pterodactyl server) |
| 2 | Microsoft test account authenticated | ⏳ needs you (device login) |
| 3 | Bot joins real RyzeSMP | ⏳ blocked here (sandbox egress), runs on Pterodactyl |
| 4 | Claude Code connects to MCP server | Impl + Local/Sim (real HTTP client ⇄ server); RealMC ⏳ |
| 5 | Claude discovers MCP tools | ✅ Sim + ByClaude-equivalent (listTools over HTTP) |
| 6 | Real player status | Sim ✅ / RealMC ⏳ |
| 7 | Real screenshot | Sim ✅ / RealMC ⏳ |
| 8 | Harmless movement action | Sim ✅ / RealMC ⏳ |
| 9 | Observe changed position | Sim ✅ (before/after delta) / RealMC ⏳ |
| 10 | Test chat message | Sim ✅ / RealMC ⏳ |
| 11 | Read server response | Sim ✅ / RealMC ⏳ |
| 12 | RyzeSMP-specific test | Impl + Sim ✅ / RealMC ⏳ |
| 13 | Record results | ✅ (SQLite audit + test_results) |

Nothing marked **Sim** is claimed as **RealMC**. The RealMC column completes
once you do steps 1–2 and expose the endpoint; then Claude runs the loop and
fills it in for real.

## Verified locally (what proves Claude's side works)

`npm test` includes an end-to-end suite where Claude Code's **real MCP client**
connects to the **real MCP server over HTTP** and drives a simulated-protocol
bot: lists tools, reads status/inventory, captures a screenshot, executes an
action and observes the before/after result, and confirms the dangerous-command
gate — 20/20 tests green. Only the Minecraft protocol is simulated (sandbox
egress is blocked); the client, transport, server, service and observe/act loop
are the real ones used in production.
