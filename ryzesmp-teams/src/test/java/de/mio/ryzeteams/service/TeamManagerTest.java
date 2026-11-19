package de.mio.ryzeteams.service;

import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.model.TeamRole;
import de.mio.ryzeteams.storage.SqliteTeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end service tests against the real SQLite repository. */
class TeamManagerTest {

    @TempDir
    Path tmp;

    private Path dbFile;
    private SqliteTeamRepository repo;
    private TeamManager mgr;

    private final UUID owner = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dbFile = tmp.resolve("teams.db");
        repo = new SqliteTeamRepository(dbFile);
        repo.initialize();
        mgr = new TeamManager(Logger.getLogger("test"), repo, PluginConfig.defaults());
        mgr.loadAll();
    }

    @AfterEach
    void tearDown() {
        mgr.shutdown();
    }

    @Test
    void createAndLookup() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        assertEquals("Alpha", t.name());
        assertTrue(mgr.teamOf(owner).isPresent());
        assertEquals(t.id(), mgr.teamByName("alpha").orElseThrow().id());
    }

    @Test
    void duplicateNameRejected() {
        mgr.createTeam(owner, "Owner", "Alpha");
        assertThrows(TeamException.class, () -> mgr.createTeam(alice, "Alice", "alpha"));
    }

    @Test
    void reservedNameRejected() {
        assertThrows(TeamException.class, () -> mgr.createTeam(owner, "Owner", "accept"));
    }

    @Test
    void alreadyInTeamCannotCreate() {
        mgr.createTeam(owner, "Owner", "Alpha");
        assertThrows(TeamException.class, () -> mgr.createTeam(owner, "Owner", "Beta"));
    }

    @Test
    void inviteAcceptFlow() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        assertEquals(1, mgr.pendingInvites(alice).size());
        Team joined = mgr.acceptInvite(alice, "Alice", null);
        assertEquals(t.id(), joined.id());
        assertTrue(t.isMember(alice));
        assertEquals(TeamRole.MEMBER_ID, t.member(alice).orElseThrow().roleId());
        assertTrue(mgr.pendingInvites(alice).isEmpty());
    }

    @Test
    void cannotInviteWithoutPermission() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null); // alice is a plain member (no INVITE perm)
        assertThrows(TeamException.class, () -> mgr.invite(t, alice, bob, "Bob"));
    }

    @Test
    void kickRespectsOwnerProtectionAndRank() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        // promote alice to admin so she can kick members
        mgr.assignRole(t, owner, "Alice", TeamRole.ADMIN_ID);
        mgr.invite(t, alice, bob, "Bob");
        mgr.acceptInvite(bob, "Bob", null);
        // alice (admin) kicks bob (member) -> ok
        mgr.kick(t, alice, "Bob");
        assertFalse(t.isMember(bob));
        // nobody can kick the owner
        assertThrows(TeamException.class, () -> mgr.kick(t, alice, "Owner"));
    }

    @Test
    void ownerCannotLeaveButCanTransfer() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        assertThrows(TeamException.class, () -> mgr.leave(owner));
        mgr.transfer(t, owner, "Alice");
        assertTrue(t.isOwner(alice));
        // old owner is now admin and may leave
        assertDoesNotThrow(() -> mgr.leave(owner));
    }

    @Test
    void allianceRequestAndAccept() {
        Team a = mgr.createTeam(owner, "Owner", "Alpha");
        Team b = mgr.createTeam(alice, "Alice", "Beta");
        assertFalse(mgr.requestAlliance(a, owner, "Beta"));
        assertTrue(b.incomingAllianceRequests().contains(a.id()));
        mgr.acceptAlliance(b, alice, "Alpha");
        assertTrue(a.isAlliedWith(b.id()));
        assertTrue(b.isAlliedWith(a.id()));
        assertTrue(mgr.isProtected(owner, alice));
    }

    @Test
    void mutualAllianceAutoAccepts() {
        Team a = mgr.createTeam(owner, "Owner", "Alpha");
        Team b = mgr.createTeam(alice, "Alice", "Beta");
        assertFalse(mgr.requestAlliance(a, owner, "Beta"));
        assertTrue(mgr.requestAlliance(b, alice, "Alpha")); // mutual -> auto accept
        assertTrue(a.isAlliedWith(b.id()));
    }

    @Test
    void friendlyFireSameTeam() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        assertTrue(mgr.isProtected(owner, alice));
        assertFalse(mgr.isProtected(owner, bob)); // bob teamless
    }

    @Test
    void disbandClearsEverything() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        mgr.disband(t, owner);
        assertTrue(mgr.teamOf(owner).isEmpty());
        assertTrue(mgr.teamOf(alice).isEmpty());
        assertTrue(mgr.teamByName("Alpha").isEmpty());
    }

    @Test
    void onlyOwnerCanDisband() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        assertThrows(TeamException.class, () -> mgr.disband(t, alice));
    }

    @Test
    void customRoleCreationAndEscalationGuard() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.createRole(t, owner, "Builder", 350, java.util.EnumSet.of(Permission.EC_OPEN));
        assertTrue(t.hasRole("builder"));
        // owner can set permission
        mgr.setRolePermission(t, owner, "builder", Permission.EC_WITHDRAW, true);
        assertTrue(t.role("builder").orElseThrow().has(Permission.EC_WITHDRAW));
    }

    @Test
    void allianceDissolutionDropsProtectionImmediately() {
        Team a = mgr.createTeam(owner, "Owner", "Alpha");
        Team b = mgr.createTeam(alice, "Alice", "Beta");
        mgr.requestAlliance(a, owner, "Beta");
        mgr.acceptAlliance(b, alice, "Alpha");
        assertTrue(mgr.isProtected(owner, alice));
        mgr.removeAlliance(a, owner, "Beta");
        assertFalse(mgr.isProtected(owner, alice));
    }

    @Test
    void expiredInvitesPurgedOnLoad() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        // Insert an already-expired invite directly, then reload.
        long old = System.currentTimeMillis() - 10_000_000L;
        repo.saveInvite(new de.mio.ryzeteams.model.Invite(t.id(), alice, owner, old));
        mgr.loadAll();
        assertTrue(mgr.pendingInvites(alice).isEmpty());
    }

    @Test
    void ecManageRoleCanEditEcAccessButNotTeamPerms() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        // Create a "storage" role (rank above member) holding only EC_MANAGE, assign to alice.
        mgr.createRole(t, owner, "Storage", 500, java.util.EnumSet.of(Permission.EC_MANAGE));
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        mgr.assignRole(t, owner, "Alice", "storage");
        // Can toggle an EC permission on a lower role (member)...
        assertDoesNotThrow(() -> mgr.setRolePermission(t, alice, TeamRole.MEMBER_ID, Permission.EC_WITHDRAW, true));
        // ...but cannot toggle a team-category permission.
        assertThrows(TeamException.class,
                () -> mgr.setRolePermission(t, alice, TeamRole.MEMBER_ID, Permission.INVITE_MEMBERS, true));
    }

    @Test
    void persistsAcrossReload() {
        Team t = mgr.createTeam(owner, "Owner", "Alpha");
        mgr.invite(t, owner, alice, "Alice");
        mgr.acceptInvite(alice, "Alice", null);
        mgr.assignRole(t, owner, "Alice", TeamRole.ADMIN_ID);
        mgr.shutdown(); // flush IO

        SqliteTeamRepository repo2 = new SqliteTeamRepository(dbFile);
        repo2.initialize();
        TeamManager mgr2 = new TeamManager(Logger.getLogger("test"), repo2, PluginConfig.defaults());
        mgr2.loadAll();
        try {
            Team r = mgr2.teamByName("Alpha").orElseThrow();
            assertEquals(2, r.size());
            assertEquals(TeamRole.ADMIN_ID, r.member(alice).orElseThrow().roleId());
            assertTrue(r.isOwner(owner));
        } finally {
            mgr2.shutdown();
        }
    }
}
