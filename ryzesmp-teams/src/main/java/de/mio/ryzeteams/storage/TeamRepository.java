package de.mio.ryzeteams.storage;

import de.mio.ryzeteams.model.Invite;
import de.mio.ryzeteams.model.Team;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence boundary for the team system. Implementations must be safe to call
 * from a background thread; the service layer never touches storage on the main
 * thread. All writes are atomic at the granularity of a single method call.
 */
public interface TeamRepository extends AutoCloseable {

    /** Opens resources and runs migrations. Called once on enable. */
    void initialize();

    /** Loads every team with members, roles, permissions, home, allies and requests. */
    List<Team> loadAllTeams();

    /** Upserts a team and its members/roles/permissions/home atomically. */
    void saveTeam(Team team);

    /** Deletes a team and all data referencing it (members, roles, EC, alliances...). */
    void deleteTeam(UUID teamId);

    // ---- enderchest (stored as per-slot Bukkit item bytes) ------------------

    Map<Integer, byte[]> loadEnderchest(UUID teamId);

    /** Replaces the full EC contents for a team atomically (null/empty clears). */
    void saveEnderchest(UUID teamId, Map<Integer, byte[]> slots);

    // ---- invites ------------------------------------------------------------

    List<Invite> loadInvites();

    void saveInvite(Invite invite);

    void deleteInvite(UUID teamId, UUID invitee);

    void deleteInvitesForInvitee(UUID invitee);

    // ---- alliances ----------------------------------------------------------

    void addAlliance(UUID a, UUID b);

    void removeAlliance(UUID a, UUID b);

    void addAllianceRequest(UUID from, UUID to, long createdAt);

    void removeAllianceRequest(UUID from, UUID to);

    @Override
    void close();
}
