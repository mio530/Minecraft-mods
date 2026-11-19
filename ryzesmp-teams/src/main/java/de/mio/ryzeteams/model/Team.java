package de.mio.ryzeteams.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Aggregate root for a single team. Holds identity, members, roles, home,
 * alliances and alliance requests. All data is Bukkit-free so the whole
 * aggregate is unit-testable.
 *
 * <p>Mutation methods here perform structural validation (nulls, unknown ids,
 * owner protection) but <em>not</em> permission/escalation checks — those live
 * in {@link TeamPolicy} and are applied by the service layer before calling
 * these methods, so the rules are tested in one place.
 */
public final class Team {

    private final UUID id;
    private String name;
    private UUID owner;
    private TeamHome home;

    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final Map<String, TeamRole> roles = new LinkedHashMap<>();
    private final Set<UUID> allies = new TreeSet<>();
    private final Set<UUID> outgoingAllianceRequests = new TreeSet<>();
    private final Set<UUID> incomingAllianceRequests = new TreeSet<>();

    private String defaultRoleId = TeamRole.MEMBER_ID;

    public Team(UUID id, String name, UUID owner) {
        this.id = java.util.Objects.requireNonNull(id, "id");
        this.name = java.util.Objects.requireNonNull(name, "name");
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
    }

    // ---- identity -----------------------------------------------------------

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = java.util.Objects.requireNonNull(name, "name");
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public Optional<TeamHome> home() {
        return Optional.ofNullable(home);
    }

    public void setHome(TeamHome home) {
        this.home = home;
    }

    public String defaultRoleId() {
        return defaultRoleId;
    }

    public void setDefaultRoleId(String roleId) {
        String rid = TeamRole.normalizeId(roleId);
        // The owner role can never be a join default (would grant every permission
        // to new members). Fall back to the member role if asked for owner.
        if (rid.equals(TeamRole.OWNER_ID)) {
            rid = TeamRole.MEMBER_ID;
        }
        if (roles.containsKey(rid)) {
            this.defaultRoleId = rid;
        }
    }

    // ---- roles --------------------------------------------------------------

    public void putRole(TeamRole role) {
        roles.put(role.id(), role);
    }

    public Optional<TeamRole> role(String id) {
        return Optional.ofNullable(roles.get(TeamRole.normalizeId(id)));
    }

    public Collection<TeamRole> roles() {
        return Collections.unmodifiableCollection(roles.values());
    }

    public boolean hasRole(String id) {
        return roles.containsKey(TeamRole.normalizeId(id));
    }

    /** Removes a role and moves its members to the default role. */
    public void removeRole(String id) {
        String rid = TeamRole.normalizeId(id);
        if (rid.equals(TeamRole.OWNER_ID)) {
            throw new TeamException("The owner role cannot be deleted.");
        }
        if (!roles.containsKey(rid)) {
            throw new TeamException("Unknown role: " + id);
        }
        if (rid.equals(defaultRoleId)) {
            throw new TeamException("Cannot delete the default join role. Set another default first.");
        }
        for (Member m : members.values()) {
            if (m.roleId().equals(rid)) {
                m.setRoleId(defaultRoleId);
            }
        }
        roles.remove(rid);
    }

    /** The role a member currently holds (never null for a real member). */
    public TeamRole roleOf(UUID uuid) {
        Member m = members.get(uuid);
        if (m == null) {
            throw new TeamException("Player is not a member of this team.");
        }
        if (isOwner(uuid)) {
            return roles.get(TeamRole.OWNER_ID);
        }
        TeamRole r = roles.get(m.roleId());
        // A non-owner must never resolve to the owner role (guards against a
        // manipulated roleId granting every permission).
        if (r == null || r.isOwnerRole()) {
            return roles.get(defaultRoleId);
        }
        return r;
    }

    // ---- members ------------------------------------------------------------

    public void addMember(Member member) {
        members.put(member.uuid(), member);
    }

    public Optional<Member> member(UUID uuid) {
        return Optional.ofNullable(members.get(uuid));
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public Collection<Member> members() {
        return Collections.unmodifiableCollection(members.values());
    }

    public int size() {
        return members.size();
    }

    public void removeMember(UUID uuid) {
        if (isOwner(uuid)) {
            throw new TeamException("The owner cannot be removed. Transfer ownership or disband the team.");
        }
        members.remove(uuid);
    }

    /** Transfers ownership: old owner drops to admin, new owner takes owner role. */
    public void transferOwnership(UUID newOwner) {
        if (!isMember(newOwner)) {
            throw new TeamException("The new owner must be a team member.");
        }
        UUID oldOwner = this.owner;
        this.owner = newOwner;
        members.get(newOwner).setRoleId(TeamRole.OWNER_ID);
        Member old = members.get(oldOwner);
        if (old != null) {
            old.setRoleId(TeamRole.ADMIN_ID);
        }
    }

    // ---- alliances ----------------------------------------------------------

    public Set<UUID> allies() {
        return Collections.unmodifiableSet(allies);
    }

    public boolean isAlliedWith(UUID teamId) {
        return allies.contains(teamId);
    }

    public void addAlly(UUID teamId) {
        allies.add(teamId);
        outgoingAllianceRequests.remove(teamId);
        incomingAllianceRequests.remove(teamId);
    }

    public void removeAlly(UUID teamId) {
        allies.remove(teamId);
    }

    public Set<UUID> outgoingAllianceRequests() {
        return Collections.unmodifiableSet(outgoingAllianceRequests);
    }

    public Set<UUID> incomingAllianceRequests() {
        return Collections.unmodifiableSet(incomingAllianceRequests);
    }

    public void addOutgoingAllianceRequest(UUID teamId) {
        outgoingAllianceRequests.add(teamId);
    }

    public void addIncomingAllianceRequest(UUID teamId) {
        incomingAllianceRequests.add(teamId);
    }

    public void clearAllianceRequest(UUID teamId) {
        outgoingAllianceRequests.remove(teamId);
        incomingAllianceRequests.remove(teamId);
    }

    /**
     * Deep copy of this aggregate. Used to snapshot a team on the main thread so
     * the persistence worker can serialize it on a background thread without any
     * shared mutable state (and thus no data race).
     */
    public Team copy() {
        Team c = new Team(id, name, owner);
        c.home = home;
        c.defaultRoleId = defaultRoleId;
        for (TeamRole r : roles.values()) {
            c.putRole(new TeamRole(r.id(), r.name(), r.rank(), r.permissions()));
        }
        for (Member m : members.values()) {
            c.addMember(new Member(m.uuid(), m.lastKnownName(), m.roleId(), m.joinedAt()));
        }
        c.allies.addAll(allies);
        c.outgoingAllianceRequests.addAll(outgoingAllianceRequests);
        c.incomingAllianceRequests.addAll(incomingAllianceRequests);
        return c;
    }

    @Override
    public String toString() {
        return "Team{" + name + " (" + id + "), owner=" + owner + ", members=" + members.size() + '}';
    }
}
