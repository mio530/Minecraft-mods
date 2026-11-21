# RyzeTeams

An advanced, production-ready **team system** for **RyzeSMP** — a Paper plugin for
**Minecraft 1.21.11** (Paper build 132, Java 21 compile target, runs on the server's Java 25).

Teams with a hierarchical, fully configurable role/permission system, a 54-slot shared
team enderchest with strict dupe protection, team alliances, server-side friendly-fire
protection, GUIs for everything, and SQLite persistence with migrations.

> This plugin is a normal Paper/Bukkit plugin (not a Fabric mod). RyzeSMP runs Paper, so a
> Fabric mod would not load.

---

## Installation

1. Build the jar (or use the prebuilt one):
   ```powershell
   .\gradlew build
   ```
   The jar is written to `build/libs/RyzeTeams-1.0.0.jar`.
2. Drop `RyzeTeams-1.0.0.jar` into the server's `plugins/` folder.
3. Start the server once. On first run it:
   - downloads its single runtime library (`org.xerial:sqlite-jdbc`) via Paper's
     `libraries:` mechanism (no shaded jars, small footprint),
   - creates `plugins/RyzeTeams/config.yml` and `plugins/RyzeTeams/teams.db`.
4. Done. No external database required.

### Requirements
- Paper **1.21.11** (or a close 1.21.x; `api-version` is `1.21`).
- Java **21+** (the RyzeSMP container ships Java 25).

---

## Commands

Everything works under **`/team`** and the alias **`/t`**.

| Command | Description |
|---|---|
| `/team` | Open the team GUI (or show help if you have no team) |
| `/team create <name>` | Create a team (you become the owner) |
| `/team invite <player>` | Invite an online player |
| `/team accept [team]` | Accept an invite (name required if you have several) |
| `/team deny [team]` | Decline an invite |
| `/team leave` | Leave your team (owner must transfer or disband) |
| `/team kick <player>` | Kick a lower-ranked member (works on offline members by name) |
| `/team transfer <player>` | Hand ownership to another member (owner only) |
| `/team disband confirm` | Permanently delete the team and its enderchest (owner only) |
| `/team info [team]` | Show team information |
| `/team list` | List all teams |
| `/team members` | Open the member-management GUI |
| `/team home` / `/team sethome` | Teleport to / set the team home |
| `/team ec` / `/team ecs` | Open the shared team enderchest (54-slot double chest) |
| `/team role list` | List roles |
| `/team role create <name> [rank]` | Create a custom role |
| `/team role delete <name>` | Delete a role (its members move to the default role) |
| `/team role assign <player> <role>` | Assign a role to a member |
| `/team role default <role>` | Set the role new members join with |
| `/team permissions [role]` | Open the role/EC permission editor |
| `/team ally <team>` | Request an alliance |
| `/team ally accept/deny/remove <team>` | Manage alliances |
| `/team allies` | Open the alliances GUI |
| `/team reload` | Reload config (`ryzeteams.admin`) |

All actions are validated server-side; missing permissions return a clear English message.

---

## Roles & permissions

Five default roles, ranked high → low. **Rank** drives all anti-escalation checks.

| Role | Rank | Default permissions |
|---|---|---|
| Owner | 1000 | **all** (implicit, cannot be edited) |
| Team Admin | 800 | all team + EC management |
| Moderator | 600 | invite, EC open + deposit |
| Member | 400 | EC open + deposit |
| Recruit | 200 | none |

Owners and admins can create custom roles (e.g. *Builder*, *PvP*, *Trusted*,
*Storage Manager*) and configure each permission individually in the GUI
(`/team permissions`).

### Granular permissions
`INVITE_MEMBERS`, `KICK_MEMBERS`, `ASSIGN_ROLES`, `MANAGE_ROLES`, `SET_HOME`,
`MANAGE_ALLIANCES`, `ACCEPT_ALLIANCES`, `EC_OPEN`, `EC_DEPOSIT`, `EC_WITHDRAW`, `EC_MANAGE`.

### Security guarantees (the anti-escalation rules)
Enforced centrally in `TeamPolicy` and covered by exhaustive unit tests:
- The **owner is immune**: cannot be kicked, demoted, or have their role edited.
- A member may only act on members of **strictly lower rank**.
- A member may only **grant permissions they hold themselves** — never escalate.
- A member may only create/edit/assign roles **ranked below their own**, so they can
  never edit their own role or promote someone to their level.
- The **owner role can never be a join default**, and a non-owner can never resolve to
  the owner role even via a manipulated database row.

---

## Team enderchest (54 slots) — EC permissions & dupe protection

Each team has one shared enderchest the size of a **double chest (54 slots)**, shown all at
once in a single screen (no pages). `/team ec` and `/team ecs` open the same chest.

### Per-role EC permissions
Configured per team via `/team permissions` (the `EC_MANAGE` permission lets a role edit
EC access):

| Right | Permission |
|---|---|
| Open the team EC | `EC_OPEN` |
| Deposit items | `EC_DEPOSIT` |
| Withdraw items | `EC_WITHDRAW` |
| Manage EC access | `EC_MANAGE` |

Example defaults: Owner/Admin full access · Moderator & Member open + deposit ·
Recruit no access. Every setting is editable in-game.

### How dupe protection works
- **One shared inventory object per team.** Every viewer sees the *same* Bukkit inventory,
  exactly like a vanilla chest with several viewers — there are no per-viewer copies to
  desync, and Bukkit's own inventory engine keeps concurrent viewers consistent.
- **All inventory interaction is single-threaded** (the server main thread), so there is no
  concurrent mutation within a tick.
- **Every click type is classified by a pure, exhaustively-tested policy**
  (`EcClickPolicy`): pickup, place, swap-with-cursor, number-key/hotbar swap, offhand swap,
  shift-click, double-click collect, creative clone, drop and drag. The safety invariant,
  proven over *every* `InventoryAction` by unit tests, is that **without the matching
  permission no action can ever move an item**. Shift-click from the player inventory is
  re-implemented into the chest's own storage, and multi-slot / creative actions that could
  duplicate items (double-click collect, creative clone) are cancelled.
- **Crash consistency.** When the EC is saved, the player data of everyone who changed it
  since the last save is flushed on the main thread *first*. This guarantees an item can
  never end up both in the EC and in a player's `.dat` (dupe) or in neither (loss) after a
  crash. The only remaining window is the async latency of the final SQLite write.
- **Writes are atomic**: each save replaces a team's EC rows inside a single SQLite
  transaction, off the main thread.
- Opening/closing inventories is never done inside an inventory event (deferred to the next
  tick), avoiding a well-known Bukkit dupe/corruption source.
- Kicking, leaving, role/permission changes and disbanding **force-close** affected EC views.

---

## Friendly fire

Members of the same team — and members of **allied** teams — cannot damage each other.
Handled server-side via:
- `EntityDamageByEntityEvent` with attacker resolution through the damage source
  (`getCausingEntity()`), covering **melee, arrows, tridents, other projectiles, TNT** and
  tamed pets (mapped to their owner);
- `EntityCombustByEntityEvent` (fire from e.g. flaming arrows);
- `PotionSplashEvent` and `AreaEffectCloudApplyEvent` for **harmful** splash/lingering
  potions (protected team mates are excluded from the effect).

Players outside any team are never affected. When an alliance ends, protection stops
immediately. Toggle via `config.yml` (`friendly-fire.same-team`, `friendly-fire.allies`).

---

## Alliances

- `/team ally <team>` sends a request; the target team's owner and members with
  `ACCEPT_ALLIANCES` are notified (and again when they next log in).
- `/team ally accept|deny <team>` resolves a request; `remove` ends an active alliance.
- **Mutual requests auto-accept.** Alliances are persisted and symmetric.

---

## Persistence (SQLite)

- A single `plugins/RyzeTeams/teams.db` SQLite database (WAL, `synchronous=NORMAL`).
- Stores: teams, members (with last-known names for offline handling), roles & permissions,
  enderchest contents (as version-safe item bytes), alliances, alliance requests, invites
  and team homes.
- **Schema migrations** are versioned (`schema_version`) so future updates evolve the schema
  in order.
- All IO runs on a dedicated single background thread; the main thread never blocks on the
  database. Team data is snapshotted (deep-copied) before each write, so the writer never
  shares mutable state. On shutdown the plugin closes EC views, flushes, then drains the
  write queue and closes the database.

### Configuration (`config.yml`)
```yaml
team:
  min-name-length: 3
  max-name-length: 16
  name-pattern: "^[A-Za-z0-9_]+$"
  default-join-role: member
  max-members: 0        # 0 = unlimited
invites:
  expiry-seconds: 120    # 0 = never
enderchest:
  total-slots: 54
friendly-fire:
  same-team: true
  allies: true
persistence:
  autosave-seconds: 300
```

---

## Testing results

`.\gradlew test` — **134 tests, 0 failures**. Coverage:
- **Domain & roles**: default roles, grant/revoke, role deletion → member reassignment,
  owner protection, ownership transfer.
- **Anti-escalation (`TeamPolicy`)**: kicking, role assignment, permission granting and role
  editing boundaries, rank ordering, default-role security holes.
- **Enderchest click policy**: *every* `InventoryAction` is asserted to never mutate without
  the matching permission; nav-slot and collect/clone handling.
- **Persistence (real SQLite)**: full team round-trip, unique name enforcement, EC
  round-trip + atomic replace, invites, alliances & requests, cascade delete, reopen-keeps-data.
- **Service end-to-end (real SQLite)**: create/invite/accept, permission-gated invite,
  kick with rank/owner protection, owner-cannot-leave + transfer, alliance request/accept,
  mutual auto-accept, friendly-fire resolution, disband, persistence across a reload.

Production build (`.\gradlew build`) is clean with no compiler warnings.

---

## Known limitations

- **Verified on a live isolated Paper 1.21.11 (build 132) server:** plugin load, the
  `libraries:` SQLite driver resolving at runtime, `teams.db`/WAL creation, config load,
  `/team`+`/t` label ownership, and a clean shutdown + restart round-trip (see
  `TESTREPORT.md`). Real Bukkit inventory-click and melee friendly-fire behaviour is covered
  by MockBukkit integration tests firing genuine events.
- **Not exercised with a real game client** (none available in the build environment):
  in-game GUI clicking and tab-completion, and the non-melee friendly-fire vectors
  (projectiles, TNT, pets, splash potions) — their logic is unit-tested but not click/entity
  tested live. A short play-test on an isolated server is recommended before production.
- **Hard-crash-during-write** was not simulated; durability rests on SQLite WAL, atomic
  replace statements, and flushing each modifier's player data before the EC is saved.
- `/team ec` is a single 54-slot double-chest screen shown all at once (no pages).
- Splash/area potion protection covers a curated set of harmful effect types; purely custom
  harmful effects added by other plugins may not be recognised.
- The plugin replaces the unqualified `/team` for players; vanilla scoreboard team
  management remains available as `/minecraft:team`.

---

## Project layout

```
src/main/java/de/mio/ryzeteams/
  model/        Pure domain (Team, Member, TeamRole, Permission, TeamPolicy) — no Bukkit
  storage/      TeamRepository + SqliteTeamRepository (migrations)
  service/      TeamManager (orchestration, caching, async persistence), PluginConfig
  ec/           TeamEnderchest, EnderchestManager, EnderchestListener, EcClickPolicy
  gui/          Menu framework + Main/Members/Roles/Permissions/Alliances menus
  listener/     FriendlyFireListener, ConnectionListener
  command/      TeamCommand (/team + /t, all subcommands, tab completion)
  util/         Items, Msg
```

Licensed under the repository's LICENSE.
