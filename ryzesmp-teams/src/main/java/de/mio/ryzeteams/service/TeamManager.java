package de.mio.ryzeteams.service;

import de.mio.ryzeteams.model.DefaultRoles;
import de.mio.ryzeteams.model.Invite;
import de.mio.ryzeteams.model.Member;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.model.TeamHome;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.model.TeamRole;
import de.mio.ryzeteams.storage.TeamRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates all team operations. Mutations run on the server main thread
 * (fast, in-memory) and every persistent change is snapshotted via
 * {@link Team#copy()} and written on a single background thread, so the main
 * thread never blocks on IO and the writer never shares mutable state.
 *
 * <p>Security lives in {@link TeamPolicy}; this class wires those checks to the
 * live caches and to persistence. It throws {@link TeamException} with a
 * player-facing English message for every rejected action.
 */
public final class TeamManager {

    /** Team names that would clash with subcommand literals. */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "accept", "deny", "remove", "list", "create", "invite", "leave",
            "kick", "info", "members", "disband", "home", "sethome", "reload",
            "role", "roles", "ally", "allies", "ec", "ecs", "help", "transfer", "permissions");

    private final Logger logger;
    private final TeamRepository repository;
    private PluginConfig config;
    private EnderchestHook ecHook = EnderchestHook.NOOP;

    private final Map<UUID, Team> teamsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameLowerToId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToTeam = new ConcurrentHashMap<>();
    // invitee -> (teamId -> invite)
    private final Map<UUID, Map<UUID, Invite>> invites = new ConcurrentHashMap<>();

    private final ExecutorService io =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "RyzeTeams-IO");
                t.setDaemon(false);
                return t;
            });

    public TeamManager(Logger logger, TeamRepository repository, PluginConfig config) {
        this.logger = logger;
        this.repository = repository;
        this.config = config;
    }

    public void setConfig(PluginConfig config) {
        this.config = config;
    }

    public void setEnderchestHook(EnderchestHook ecHook) {
        this.ecHook = ecHook == null ? EnderchestHook.NOOP : ecHook;
    }

    public PluginConfig config() {
        return config;
    }

    /** Loads all teams + invites into memory. Call once on enable (main thread). */
    public void loadAll() {
        teamsById.clear();
        nameLowerToId.clear();
        playerToTeam.clear();
        invites.clear();
        for (Team team : repository.loadAllTeams()) {
            index(team);
        }
        long now = System.currentTimeMillis();
        for (Invite invite : repository.loadInvites()) {
            if (invite.isExpired(now, config.inviteExpiryMillis())) {
                submitIo(() -> repository.deleteInvite(invite.teamId(), invite.invitee()));
                continue;
            }
            invites.computeIfAbsent(invite.invitee(), k -> new ConcurrentHashMap<>())
                    .put(invite.teamId(), invite);
        }
        logger.info("Loaded " + teamsById.size() + " team(s).");
    }

    private void index(Team team) {
        teamsById.put(team.id(), team);
        nameLowerToId.put(team.name().toLowerCase(Locale.ROOT), team.id());
        for (Member m : team.members()) {
            playerToTeam.put(m.uuid(), team.id());
        }
    }

    // ---- lookups ------------------------------------------------------------

    public Optional<Team> teamOf(UUID player) {
        UUID id = playerToTeam.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(teamsById.get(id));
    }

    public Optional<Team> teamById(UUID id) {
        return Optional.ofNullable(teamsById.get(id));
    }

    public Optional<Team> teamByName(String name) {
        UUID id = nameLowerToId.get(name.toLowerCase(Locale.ROOT));
        return id == null ? Optional.empty() : Optional.ofNullable(teamsById.get(id));
    }

    public Collection<Team> allTeams() {
        return List.copyOf(teamsById.values());
    }

    public Team requireTeamOf(UUID player) {
        return teamOf(player).orElseThrow(() -> new TeamException("You are not in a team."));
    }

    // ---- creation / disband -------------------------------------------------

    public Team createTeam(UUID owner, String ownerName, String name) {
        if (teamOf(owner).isPresent()) {
            throw new TeamException("You are already in a team. Leave it first.");
        }
        validateName(name);
        if (teamByName(name).isPresent()) {
            throw new TeamException("A team with that name already exists.");
        }
        Team team = new Team(UUID.randomUUID(), name, owner);
        DefaultRoles.apply(team);
        if (team.hasRole(config.defaultJoinRole())) {
            team.setDefaultRoleId(config.defaultJoinRole());
        }
        team.addMember(new Member(owner, ownerName, TeamRole.OWNER_ID, System.currentTimeMillis()));
        index(team);
        saveTeamAsync(team);
        return team;
    }

    public void disband(Team team, UUID actor) {
        if (!team.isOwner(actor)) {
            throw new TeamException("Only the owner can disband the team.");
        }
        // Close EC views and forget the EC so no save fires on the deleted team.
        ecHook.discard(team.id());
        // Break alliances in other teams' memory.
        for (UUID allyId : new ArrayList<>(team.allies())) {
            teamById(allyId).ifPresent(a -> {
                a.removeAlly(team.id());
                saveTeamAsyncNoName(a);
            });
        }
        for (UUID reqId : new ArrayList<>(team.outgoingAllianceRequests())) {
            teamById(reqId).ifPresent(t -> t.clearAllianceRequest(team.id()));
        }
        for (UUID reqId : new ArrayList<>(team.incomingAllianceRequests())) {
            teamById(reqId).ifPresent(t -> t.clearAllianceRequest(team.id()));
        }
        // Drop caches.
        for (Member m : team.members()) {
            playerToTeam.remove(m.uuid());
            invites.remove(m.uuid());
        }
        nameLowerToId.remove(team.name().toLowerCase(Locale.ROOT));
        teamsById.remove(team.id());
        UUID id = team.id();
        submitIo(() -> repository.deleteTeam(id));
    }

    // ---- invites ------------------------------------------------------------

    public void invite(Team team, UUID actor, UUID target, String targetName) {
        TeamPolicy.ensureCanInvite(team, actor);
        if (team.isMember(target)) {
            throw new TeamException(targetName + " is already in your team.");
        }
        if (teamOf(target).isPresent()) {
            throw new TeamException(targetName + " is already in another team.");
        }
        if (config.maxMembers() > 0 && team.size() >= config.maxMembers()) {
            throw new TeamException("Your team is full.");
        }
        Invite invite = new Invite(team.id(), target, actor, System.currentTimeMillis());
        invites.computeIfAbsent(target, k -> new ConcurrentHashMap<>()).put(team.id(), invite);
        submitIo(() -> repository.saveInvite(invite));
    }

    public List<Invite> pendingInvites(UUID player) {
        Map<UUID, Invite> map = invites.get(player);
        if (map == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<Invite> valid = new ArrayList<>();
        for (Invite i : new ArrayList<>(map.values())) {
            if (i.isExpired(now, config.inviteExpiryMillis()) || teamById(i.teamId()).isEmpty()) {
                map.remove(i.teamId());
                submitIo(() -> repository.deleteInvite(i.teamId(), player));
            } else {
                valid.add(i);
            }
        }
        return valid;
    }

    public Team acceptInvite(UUID player, String playerName, String teamNameOrNull) {
        if (teamOf(player).isPresent()) {
            throw new TeamException("You are already in a team.");
        }
        List<Invite> valid = pendingInvites(player);
        if (valid.isEmpty()) {
            throw new TeamException("You have no pending team invites.");
        }
        Invite chosen;
        if (teamNameOrNull == null) {
            if (valid.size() > 1) {
                throw new TeamException("You have multiple invites. Use /team accept <team>.");
            }
            chosen = valid.get(0);
        } else {
            Team t = teamByName(teamNameOrNull).orElseThrow(() -> new TeamException("Unknown team."));
            chosen = valid.stream().filter(i -> i.teamId().equals(t.id())).findFirst()
                    .orElseThrow(() -> new TeamException("You have no invite from that team."));
        }
        Team team = teamById(chosen.teamId()).orElseThrow(() -> new TeamException("That team no longer exists."));
        if (config.maxMembers() > 0 && team.size() >= config.maxMembers()) {
            throw new TeamException("That team is full.");
        }
        team.addMember(new Member(player, playerName, team.defaultRoleId(), System.currentTimeMillis()));
        playerToTeam.put(player, team.id());
        invites.remove(player);
        submitIo(() -> repository.deleteInvitesForInvitee(player));
        saveTeamAsync(team);
        return team;
    }

    public void denyInvite(UUID player, String teamNameOrNull) {
        List<Invite> valid = pendingInvites(player);
        if (valid.isEmpty()) {
            throw new TeamException("You have no pending team invites.");
        }
        if (teamNameOrNull == null) {
            invites.remove(player);
            submitIo(() -> repository.deleteInvitesForInvitee(player));
            return;
        }
        Team t = teamByName(teamNameOrNull).orElseThrow(() -> new TeamException("Unknown team."));
        Map<UUID, Invite> map = invites.get(player);
        if (map != null) {
            map.remove(t.id());
        }
        submitIo(() -> repository.deleteInvite(t.id(), player));
    }

    // ---- membership ---------------------------------------------------------

    public Team leave(UUID player) {
        Team team = requireTeamOf(player);
        if (team.isOwner(player)) {
            throw new TeamException("The owner cannot leave. Use /team transfer or /team disband.");
        }
        team.removeMember(player);
        playerToTeam.remove(player);
        ecHook.closeForPlayer(player);
        saveTeamAsync(team);
        return team;
    }

    public Member kick(Team team, UUID actor, String targetName) {
        Member target = memberByName(team, targetName)
                .orElseThrow(() -> new TeamException("No member named '" + targetName + "'."));
        TeamPolicy.ensureCanKick(team, actor, target.uuid());
        team.removeMember(target.uuid());
        playerToTeam.remove(target.uuid());
        ecHook.closeForPlayer(target.uuid());
        saveTeamAsync(team);
        return target;
    }

    public void transfer(Team team, UUID actor, String targetName) {
        if (!team.isOwner(actor)) {
            throw new TeamException("Only the owner can transfer ownership.");
        }
        Member target = memberByName(team, targetName)
                .orElseThrow(() -> new TeamException("No member named '" + targetName + "'."));
        if (team.isOwner(target.uuid())) {
            throw new TeamException("That player is already the owner.");
        }
        team.transferOwnership(target.uuid());
        ecHook.closeForTeam(team.id());
        saveTeamAsync(team);
    }

    public void setHome(Team team, UUID actor, TeamHome home) {
        TeamPolicy.ensureHas(team, actor, Permission.SET_HOME);
        team.setHome(home);
        saveTeamAsync(team);
    }

    // ---- roles --------------------------------------------------------------

    public TeamRole createRole(Team team, UUID actor, String name, int rank, Set<Permission> perms) {
        TeamPolicy.ensureHas(team, actor, Permission.MANAGE_ROLES);
        String id = TeamRole.normalizeId(name);
        if (team.hasRole(id)) {
            throw new TeamException("A role with that id already exists.");
        }
        if (rank <= 0 || rank >= TeamRole.OWNER_RANK) {
            throw new TeamException("Role rank must be between 1 and " + (TeamRole.OWNER_RANK - 1) + ".");
        }
        if (!team.isOwner(actor) && rank >= TeamPolicy.rankOf(team, actor)) {
            throw new TeamException("You cannot create a role at or above your own rank.");
        }
        for (Permission p : perms) {
            TeamPolicy.ensureCanGrant(team, actor, p);
        }
        TeamRole role = new TeamRole(id, name, rank, perms);
        team.putRole(role);
        saveTeamAsync(team);
        return role;
    }

    public void deleteRole(Team team, UUID actor, String roleId) {
        TeamRole role = team.role(roleId).orElseThrow(() -> new TeamException("Unknown role."));
        TeamPolicy.ensureCanEditRole(team, actor, role);
        team.removeRole(role.id());
        ecHook.closeForTeam(team.id());
        saveTeamAsync(team);
    }

    public void assignRole(Team team, UUID actor, String targetName, String roleId) {
        Member target = memberByName(team, targetName)
                .orElseThrow(() -> new TeamException("No member named '" + targetName + "'."));
        TeamRole role = team.role(roleId).orElseThrow(() -> new TeamException("Unknown role."));
        TeamPolicy.ensureCanAssignRole(team, actor, target.uuid(), role);
        target.setRoleId(role.id());
        // EC access may have changed for the target: close their view live.
        ecHook.closeForPlayer(target.uuid());
        saveTeamAsync(team);
    }

    public void setRolePermission(Team team, UUID actor, String roleId, Permission perm, boolean grant) {
        TeamRole role = team.role(roleId).orElseThrow(() -> new TeamException("Unknown role."));
        TeamPolicy.ensureCanManagePermission(team, actor, role, perm, grant);
        if (grant) {
            role.grant(perm);
        } else {
            role.revoke(perm);
        }
        // EC access rules may have changed for a whole role: close all views.
        if (perm.category() == Permission.Category.ENDERCHEST) {
            ecHook.closeForTeam(team.id());
        }
        saveTeamAsync(team);
    }

    public void setRoleRank(Team team, UUID actor, String roleId, int newRank) {
        TeamRole role = team.role(roleId).orElseThrow(() -> new TeamException("Unknown role."));
        TeamPolicy.ensureCanEditRole(team, actor, role);
        if (newRank <= 0 || newRank >= TeamRole.OWNER_RANK) {
            throw new TeamException("Role rank must be between 1 and " + (TeamRole.OWNER_RANK - 1) + ".");
        }
        if (!team.isOwner(actor) && newRank >= TeamPolicy.rankOf(team, actor)) {
            throw new TeamException("You cannot raise a role to or above your own rank.");
        }
        role.setRank(newRank);
        ecHook.closeForTeam(team.id());
        saveTeamAsync(team);
    }

    public void setDefaultRole(Team team, UUID actor, String roleId) {
        TeamRole role = team.role(roleId).orElseThrow(() -> new TeamException("Unknown role."));
        if (role.isOwnerRole()) {
            throw new TeamException("The owner role cannot be a join default.");
        }
        if (!team.isOwner(actor)) {
            TeamPolicy.ensureHas(team, actor, Permission.MANAGE_ROLES);
            if (role.rank() >= TeamPolicy.rankOf(team, actor)) {
                throw new TeamException("You cannot set a default role at or above your own rank.");
            }
        }
        team.setDefaultRoleId(role.id());
        saveTeamAsync(team);
    }

    // ---- alliances ----------------------------------------------------------

    /** @return true if the request immediately formed a mutual alliance. */
    public boolean requestAlliance(Team from, UUID actor, String targetName) {
        TeamPolicy.ensureCanManageAlliances(from, actor);
        Team to = teamByName(targetName).orElseThrow(() -> new TeamException("Unknown team."));
        if (to.id().equals(from.id())) {
            throw new TeamException("You cannot ally your own team.");
        }
        if (from.isAlliedWith(to.id())) {
            throw new TeamException("You are already allied with that team.");
        }
        // Mutual request -> auto accept.
        if (from.incomingAllianceRequests().contains(to.id())) {
            formAlliance(from, to);
            return true;
        }
        if (from.outgoingAllianceRequests().contains(to.id())) {
            throw new TeamException("You already have a pending request to that team.");
        }
        long now = System.currentTimeMillis();
        from.addOutgoingAllianceRequest(to.id());
        to.addIncomingAllianceRequest(from.id());
        submitIo(() -> repository.addAllianceRequest(from.id(), to.id(), now));
        return false;
    }

    public void acceptAlliance(Team team, UUID actor, String fromName) {
        TeamPolicy.ensureCanAcceptAlliances(team, actor);
        Team from = teamByName(fromName).orElseThrow(() -> new TeamException("Unknown team."));
        if (!team.incomingAllianceRequests().contains(from.id())) {
            throw new TeamException("No alliance request from that team.");
        }
        formAlliance(team, from);
    }

    public void denyAlliance(Team team, UUID actor, String fromName) {
        TeamPolicy.ensureCanAcceptAlliances(team, actor);
        Team from = teamByName(fromName).orElseThrow(() -> new TeamException("Unknown team."));
        if (!team.incomingAllianceRequests().contains(from.id())) {
            throw new TeamException("No alliance request from that team.");
        }
        team.clearAllianceRequest(from.id());
        from.clearAllianceRequest(team.id());
        submitIo(() -> repository.removeAllianceRequest(from.id(), team.id()));
    }

    public void removeAlliance(Team team, UUID actor, String otherName) {
        TeamPolicy.ensureCanManageAlliances(team, actor);
        Team other = teamByName(otherName).orElseThrow(() -> new TeamException("Unknown team."));
        if (!team.isAlliedWith(other.id())) {
            throw new TeamException("You are not allied with that team.");
        }
        team.removeAlly(other.id());
        other.removeAlly(team.id());
        submitIo(() -> repository.removeAlliance(team.id(), other.id()));
    }

    private void formAlliance(Team a, Team b) {
        a.addAlly(b.id());
        b.addAlly(a.id());
        a.clearAllianceRequest(b.id());
        b.clearAllianceRequest(a.id());
        submitIo(() -> {
            repository.removeAllianceRequest(a.id(), b.id());
            repository.removeAllianceRequest(b.id(), a.id());
            repository.addAlliance(a.id(), b.id());
        });
    }

    // ---- friendly fire ------------------------------------------------------

    /** True if damage between two players should be blocked by team rules. */
    public boolean isProtected(UUID attacker, UUID victim) {
        if (attacker.equals(victim)) {
            return false;
        }
        Optional<Team> ta = teamOf(attacker);
        Optional<Team> tv = teamOf(victim);
        if (ta.isEmpty() || tv.isEmpty()) {
            return false;
        }
        Team a = ta.get();
        Team v = tv.get();
        if (a.id().equals(v.id())) {
            return config.friendlyFireSameTeam();
        }
        return config.friendlyFireAllies() && a.isAlliedWith(v.id());
    }

    // ---- helpers ------------------------------------------------------------

    public Optional<Member> memberByName(Team team, String name) {
        return team.members().stream()
                .filter(m -> m.lastKnownName().equalsIgnoreCase(name))
                .findFirst();
    }

    public void updateName(UUID player, String name) {
        teamOf(player).flatMap(t -> t.member(player)).ifPresent(m -> {
            if (!m.lastKnownName().equals(name)) {
                m.setLastKnownName(name);
                teamOf(player).ifPresent(this::saveTeamAsync);
            }
        });
    }

    private void validateName(String name) {
        if (name.length() < config.minNameLength() || name.length() > config.maxNameLength()) {
            throw new TeamException("Team name must be " + config.minNameLength() + "-"
                    + config.maxNameLength() + " characters.");
        }
        if (!config.namePattern().matcher(name).matches()) {
            throw new TeamException("Team name contains invalid characters (letters, digits, _ only).");
        }
        if (RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            throw new TeamException("That team name is reserved.");
        }
    }

    // ---- persistence plumbing ----------------------------------------------

    public void saveTeamAsync(Team team) {
        // Re-index name in case it changed (rename not exposed yet, but safe).
        nameLowerToId.put(team.name().toLowerCase(Locale.ROOT), team.id());
        saveTeamAsyncNoName(team);
    }

    private void saveTeamAsyncNoName(Team team) {
        Team snapshot = team.copy();
        submitIo(() -> repository.saveTeam(snapshot));
    }

    public void submitIo(Runnable task) {
        io.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "RyzeTeams persistence task failed", e);
            }
        });
    }

    public TeamRepository repository() {
        return repository;
    }

    /** Flushes pending IO and closes storage. Call on disable (blocks briefly). */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Persistence tasks did not finish within 15s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        repository.close();
    }
}
