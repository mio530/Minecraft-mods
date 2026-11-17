# Security review — ryze-test

Scope: the `ryze-test/` module only. The Suggestions Bot is untouched.

## Controls in place

| Area | Implementation | Status |
|---|---|---|
| Authentication | Dashboard: password → 12 h JWT. MCP: static bearer token. | ✅ |
| Constant-time compare | Password + MCP token compared with `timingSafeEqual`. | ✅ |
| RBAC | Scopes `observe` / `control` / `session` / `admin`. AI gets observe+control(+session); `ai()` hard-strips `admin`. | ✅ |
| JWT | Signed with `JWT_SECRET`, 12 h expiry, `kind=owner` claim verified. | ✅ |
| MCP token | From env only; never logged; required on `/mcp` (401 otherwise). | ✅ |
| Rate limiting | Sliding-window per principal, `ACTION_RATE_PER_MIN`. | ✅ |
| Audit log | Append-only SQLite of every observe/control/test/approval action. | ✅ |
| Session timeout | Idle sessions closed after `SESSION_TIMEOUT_MS` (default 15 min). | ✅ |
| Owner approval | Dangerous commands + destructive tests require owner (`admin`). | ✅ |
| Secret handling | All secrets from env; pino redaction; `.env`, `.auth-cache`, `data/` git-ignored; no secrets in `.mcp.json`. | ✅ |
| Default-secret guard | Server refuses to start with default secrets unless `ALLOW_INSECURE=1`. | ✅ (added) |
| MS auth storage | OAuth tokens only (never password), cached under `AUTH_CACHE_DIR`, git-ignored. | ✅ |

## Residual notes / hardening backlog

- **WS auth via query token**: the dashboard passes the owner JWT as `?token=`.
  Encrypted under WSS, but avoid logging full URLs on any proxy in front.
- **MCP DNS-rebinding**: the StreamableHTTP server does not set `allowedHosts`.
  Risk is low (bearer required). Can be tightened once the public host is fixed.
- **Security headers / CORS**: none set (server-to-server + same-origin dashboard).
  Add a reverse-proxy CSP/HSTS in production.
- **Single shared MCP token**: fine for one controller; rotate by changing the
  env var. For multiple clients, issue per-client tokens later.

## Discord bot token (reported)

During setup the suggestions-bot Pterodactyl egg returned a **Discord bot token
in plaintext** in its `DISCORD_TOKEN` variable (visible via the panel API). It
is **not printed here**.

**Recommendation: rotate it.** In the Discord Developer Portal → your
application → Bot → **Reset Token**, then update the `DISCORD_TOKEN` variable on
the `ryzesmp-suggestions-bot` server and restart it. Pterodactyl stores egg
variables in plaintext, so treat any value that has been viewed as exposed.
This does not affect ryze-test (it has its own secrets).
