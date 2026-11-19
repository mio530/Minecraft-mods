# RyzeTeams — End-to-End Test Report

Local, fully isolated QA of `RyzeTeams-1.0.0.jar`. **The production RyzeSMP server was never
touched.** All testing ran against a disposable local Paper instance and in-memory/temp data.

## Environment

| Item | Value |
|---|---|
| Plugin version | 1.0.0 |
| Paper version / build | **1.21.11 build 132** (STABLE, released 2026-05-11) — same build as the production SMP |
| Paper jar sha256 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` (verified bit-identical to the official PaperMC `fill` API download) |
| Download source | `https://fill-data.papermc.io/...` (official PaperMC CDN) |
| VirusTotal | API key not available in this environment → scanned by SHA256 integrity against the official publisher instead; the hash above can be looked up at virustotal.com/gui/search |
| Java (test host) | 21.0.12.1 LTS (compile target 21) |
| Java (production container) | 25 (Paper 1.21.11 requires 21+) |
| Test server | isolated dir, `level-name=ryzeteams_test_world`, `server-port=25599`, `online-mode=false`, separate `teams.db` |
| EULA | accepted only for the local disposable instance, with explicit user approval |

## Build & automated tests

- `./gradlew build` → **BUILD SUCCESSFUL**, no compiler or Gradle warnings.
- **156 automated tests, 0 failures.**

| Suite | Tests | Kind | What it covers |
|---|---:|---|---|
| `model.EcClickPolicyTest` | 80 | unit | Every `InventoryAction` → safe decision; no mutation without permission |
| `model.TeamPolicyTest` | 18 | unit | Anti-escalation: kick/assign/grant/edit boundaries, rank order |
| `service.TeamManagerTest` | 18 | unit (real SQLite) | create/invite/accept/kick/leave/transfer/disband, alliances, EC_MANAGE, invite expiry, reload persistence |
| `model.TeamModelTest` | 8 | unit | roles, transfer, role-delete reassignment, owner protection |
| `storage.SqlitePersistenceTest` | 7 | unit (real SQLite) | round-trips, unique name, EC bytes, cascade delete, reopen |
| `model.DefaultRoleSecurityTest` | 3 | unit | default-role never owner; non-owner never resolves to owner role |
| `integration.EcInventoryIT` | 10 | **integration (MockBukkit, real events)** | real `InventoryClickEvent` dupe/loss + permission enforcement |
| `integration.CommandLifecycleIT` | 4 | integration | full `/team` lifecycle via simulated players + live state |
| `integration.FriendlyFireIT` | 4 | integration | real `EntityDamageByEntityEvent` protection |
| `integration.PluginSmokeIT` | 4 | integration | plugin enables under simulated Paper, command registered |

## Live server results

Captured from the isolated Paper 1.21.11 boot:

```
[SpigotLibraryLoader] [RyzeTeams] Loading 1 libraries... please wait
[SpigotLibraryLoader] Downloading .../org/xerial/sqlite-jdbc/3.47.1.0/sqlite-jdbc-3.47.1.0.jar
[SpigotLibraryLoader] [RyzeTeams] Loaded library .../sqlite-jdbc-3.47.1.0.jar
[RyzeTeams] Enabling RyzeTeams v1.0.0
[RyzeTeams] Loaded 0 team(s).
[RyzeTeams] RyzeTeams enabled (Paper 1.21.11).
Done (28.191s)!
# console `team` and `t`:
Teams » Only players can use team commands.     (both labels -> our executor)
# clean shutdown:
[RyzeTeams] Disabling RyzeTeams v1.0.0
[RyzeTeams] RyzeTeams disabled.
# restart (2nd boot): sqlite lib loaded from cache, DB reopened cleanly:
[RyzeTeams] Loaded 0 team(s).
Done (18.640s)!
```

- `plugins/RyzeTeams/` contained `config.yml`, `teams.db`, `teams.db-wal`, `teams.db-shm` (WAL active).
- No errors, no stack traces, no `SQLException` on either boot.

## Results table

| Category | Status | Evidence |
|---|---|---|
| Production build / `.jar` | **PASS** | clean build, 126 KB jar |
| Automated unit tests | **PASS** | 134/134 |
| Automated integration tests (real Bukkit events) | **PASS** | 22/22 |
| Plugin loads on live Paper 1.21.11 b132 | **PASS** | startup log |
| SQLite driver via Paper `libraries:` | **PASS** | SpigotLibraryLoader log (download + cache on restart) |
| `teams.db` creation + WAL | **PASS** | files present, no corruption |
| Config load | **PASS** | `config.yml` generated |
| Command registration + `/team` & `/t` label ownership | **PASS** | console output + `help team` (alias `t`) |
| Clean shutdown + restart round-trip | **PASS** | disable log, 2nd boot reopens DB |
| Team lifecycle (create/invite/accept/kick/leave/transfer/disband) | **PASS** | CommandLifecycleIT + TeamManagerTest |
| Owner protection (no kick/demote/remove) | **PASS** | TeamPolicyTest + CommandLifecycleIT |
| Roles: defaults, custom create/delete/rank, assignment | **PASS** | TeamModelTest + TeamManagerTest |
| Privilege-escalation prevention | **PASS** | TeamPolicyTest (grant/assign/edit boundaries) |
| `EC_MANAGE` documented behaviour | **PASS** | TeamManagerTest.ecManageRole... |
| Default role never Owner | **PASS** | DefaultRoleSecurityTest |
| Team EC permission enforcement (open/deposit/withdraw) | **PASS** | EcInventoryIT (real events) + EcClickPolicyTest |
| Team EC dupe/loss across click types | **PASS** | EcInventoryIT: shift-click conserves; collect/clone/nav cancelled; place/pickup gated |
| Shared EC inventory (one object, many viewers) | **PASS** | EcInventoryIT.sharedInventoryIsSameObject |
| EC force-close on perm change / kick / leave / disband | **PASS** | EcInventoryIT + service wiring |
| SQLite persistence round-trip (with data) | **PASS** | TeamManagerTest.persistsAcrossReload (real SQLite) |
| Friendly fire: same-team melee | **PASS** | FriendlyFireIT |
| Friendly fire: allies + immediate drop on dissolve | **PASS** | FriendlyFireIT |
| Friendly fire: unrelated players unaffected, self-damage allowed | **PASS** | FriendlyFireIT |
| Friendly fire: projectiles / tridents / TNT / pets / harmful potions | **NOT TESTED (live)** | logic unit-reasoned + compile-verified; MockBukkit cannot spawn these entities, and no game client was available |
| In-game GUI clicking (menus) & tab-completion | **NOT TESTED (live)** | requires a real client; command→menu-open paths and `MenuListener` cancel are verified, individual menu button handlers are not click-tested |
| Real human multiplayer inventory manipulation | **NOT TESTED (live)** | no Minecraft game client in this environment; equivalent coverage via simulated Bukkit events (EcInventoryIT) |
| Hard-crash mid-write recovery | **NOT TESTED (live)** | not killed mid-transaction; design uses WAL + atomic single-statement replaces + player-data flush before EC save (see README) |

## Bugs found & fixed during this QA

1. **NPE in `EnderchestManager.closeForPlayer`** — when a player with no open container had their
   role/permissions changed, `getOpenInventory().getTopInventory()` can be `null`, causing an
   `Unhandled exception executing command 'team'`. **Found by** `CommandLifecycleIT.fullLifecycle`
   (role assignment path). **Fixed** with a null guard; covered by that integration test.

(One testability change, not a bug: the plugin main class was made non-`final` so MockBukkit can
proxy it; no runtime effect.)

## Remaining limitations

- Live, human-client testing of GUI clicks, tab-completion and the non-melee friendly-fire vectors
  (projectiles, TNT, pets, splash potions) was not possible without a Minecraft game client in this
  environment. The underlying logic is unit-tested (EcClickPolicy, TeamPolicy, isProtected) and, for
  inventory and melee, exercised through real Bukkit events via MockBukkit. A short play-test on the
  isolated server before production is still recommended for final UX confirmation.
- Hard-crash-during-write was not simulated; durability rests on SQLite WAL, atomic replace
  statements, and flushing modifier player-data before each EC save.

## Deliverables

- Source: `ryzesmp-teams/src/...`
- Final jar: `ryzesmp-teams/build/libs/RyzeTeams-1.0.0.jar`
- Docs: `README.md`, this `TESTREPORT.md`

**Not deployed.** Awaiting explicit deployment approval.
