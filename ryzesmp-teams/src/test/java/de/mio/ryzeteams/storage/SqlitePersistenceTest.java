package de.mio.ryzeteams.storage;

import de.mio.ryzeteams.model.DefaultRoles;
import de.mio.ryzeteams.model.Invite;
import de.mio.ryzeteams.model.Member;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamHome;
import de.mio.ryzeteams.model.TeamRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests for the real SQLite repository (round-trips, migrations, EC). */
class SqlitePersistenceTest {

    @TempDir
    Path tmp;

    private SqliteTeamRepository repo;
    private Path dbFile;

    @BeforeEach
    void setUp() {
        dbFile = tmp.resolve("teams.db");
        repo = new SqliteTeamRepository(dbFile);
        repo.initialize();
    }

    @AfterEach
    void tearDown() {
        repo.close();
    }

    private Team sampleTeam(UUID owner, String name) {
        Team t = new Team(UUID.randomUUID(), name, owner);
        DefaultRoles.apply(t);
        t.addMember(new Member(owner, "OwnerGuy", TeamRole.OWNER_ID, 123L));
        return t;
    }

    @Test
    void teamRoundTrip() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team t = sampleTeam(owner, "Alpha");
        t.addMember(new Member(member, "Bob", TeamRole.MODERATOR_ID, 456L));
        t.setHome(new TeamHome("world", 1.5, 64.0, -2.5, 90f, 10f));
        // custom role with specific perms
        t.putRole(new TeamRole("builder", "Builder", 350, EnumSet.of(Permission.EC_OPEN, Permission.EC_WITHDRAW)));

        repo.saveTeam(t);

        List<Team> loaded = repo.loadAllTeams();
        assertEquals(1, loaded.size());
        Team r = loaded.get(0);
        assertEquals(t.id(), r.id());
        assertEquals("Alpha", r.name());
        assertEquals(owner, r.owner());
        assertEquals(2, r.size());
        assertEquals(TeamRole.MODERATOR_ID, r.member(member).orElseThrow().roleId());
        assertTrue(r.home().isPresent());
        assertEquals("world", r.home().get().world());
        assertEquals(64.0, r.home().get().y());
        TeamRole builder = r.role("builder").orElseThrow();
        assertEquals(350, builder.rank());
        assertEquals(EnumSet.of(Permission.EC_OPEN, Permission.EC_WITHDRAW), builder.permissions());
        // owner still implicitly holds all
        assertTrue(r.roleOf(owner).has(Permission.MANAGE_ROLES));
    }

    @Test
    void uniqueTeamNameEnforcedByDatabase() {
        UUID o1 = UUID.randomUUID();
        Team a = sampleTeam(o1, "Same");
        repo.saveTeam(a);
        Team b = sampleTeam(UUID.randomUUID(), "same"); // different case, same lower
        assertThrows(StorageException.class, () -> repo.saveTeam(b));
    }

    @Test
    void enderchestRoundTripAndReplace() {
        UUID owner = UUID.randomUUID();
        Team t = sampleTeam(owner, "ECTeam");
        repo.saveTeam(t);

        Map<Integer, byte[]> slots = new HashMap<>();
        slots.put(0, new byte[]{1, 2, 3});
        slots.put(44, new byte[]{9, 9});
        slots.put(80, new byte[]{7});
        repo.saveEnderchest(t.id(), slots);

        Map<Integer, byte[]> loaded = repo.loadEnderchest(t.id());
        assertEquals(3, loaded.size());
        assertArrayEquals(new byte[]{1, 2, 3}, loaded.get(0));
        assertArrayEquals(new byte[]{7}, loaded.get(80));

        // replace with fewer slots -> old ones gone
        Map<Integer, byte[]> fewer = new HashMap<>();
        fewer.put(5, new byte[]{42});
        repo.saveEnderchest(t.id(), fewer);
        Map<Integer, byte[]> loaded2 = repo.loadEnderchest(t.id());
        assertEquals(1, loaded2.size());
        assertArrayEquals(new byte[]{42}, loaded2.get(5));
    }

    @Test
    void invitesRoundTrip() {
        UUID team = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        // need a team row for consistency, but invites table has no FK; still save a team
        Team t = sampleTeam(inviter, "InvTeam");
        repo.saveTeam(t);
        repo.saveInvite(new Invite(t.id(), invitee, inviter, 1000L));
        assertEquals(1, repo.loadInvites().size());
        repo.deleteInvite(t.id(), invitee);
        assertTrue(repo.loadInvites().isEmpty());
    }

    @Test
    void allianceAndRequestRoundTrip() {
        UUID oa = UUID.randomUUID();
        UUID ob = UUID.randomUUID();
        Team a = sampleTeam(oa, "A");
        Team b = sampleTeam(ob, "B");
        repo.saveTeam(a);
        repo.saveTeam(b);

        repo.addAllianceRequest(a.id(), b.id(), 1L);
        List<Team> loaded = repo.loadAllTeams();
        Team la = find(loaded, a.id());
        Team lb = find(loaded, b.id());
        assertTrue(la.outgoingAllianceRequests().contains(b.id()));
        assertTrue(lb.incomingAllianceRequests().contains(a.id()));

        repo.removeAllianceRequest(a.id(), b.id());
        repo.addAlliance(a.id(), b.id());
        loaded = repo.loadAllTeams();
        la = find(loaded, a.id());
        lb = find(loaded, b.id());
        assertTrue(la.isAlliedWith(b.id()));
        assertTrue(lb.isAlliedWith(a.id()));
        assertTrue(la.outgoingAllianceRequests().isEmpty());
    }

    @Test
    void deleteTeamCascades() {
        UUID owner = UUID.randomUUID();
        Team t = sampleTeam(owner, "Doomed");
        repo.saveTeam(t);
        repo.saveEnderchest(t.id(), Map.of(0, new byte[]{1}));
        repo.deleteTeam(t.id());
        assertTrue(repo.loadAllTeams().isEmpty());
        assertTrue(repo.loadEnderchest(t.id()).isEmpty());
    }

    @Test
    void reopeningDatabaseKeepsData() {
        UUID owner = UUID.randomUUID();
        Team t = sampleTeam(owner, "Persist");
        repo.saveTeam(t);
        repo.close();

        SqliteTeamRepository repo2 = new SqliteTeamRepository(dbFile);
        repo2.initialize();
        try {
            assertEquals(1, repo2.loadAllTeams().size());
        } finally {
            repo2.close();
        }
    }

    private Team find(List<Team> teams, UUID id) {
        return teams.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }
}
