package de.mio.ryzeteams.integration;

import de.mio.ryzeteams.RyzeTeamsPlugin;
import de.mio.ryzeteams.model.TeamRole;
import de.mio.ryzeteams.service.TeamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end team lifecycle through the real command executor + service + SQLite,
 * driven by simulated players. State is asserted against the live TeamManager.
 */
class CommandLifecycleIT {

    private ServerMock server;
    private RyzeTeamsPlugin plugin;
    private TeamManager teams;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RyzeTeamsPlugin.class);
        teams = plugin.teamManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void fullLifecycle() {
        PlayerMock owner = server.addPlayer("Owner");
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        owner.performCommand("team create Alpha");
        assertTrue(teams.teamOf(owner.getUniqueId()).isPresent());
        assertEquals("Alpha", teams.teamOf(owner.getUniqueId()).get().name());

        owner.performCommand("team invite Alice");
        assertEquals(1, teams.pendingInvites(alice.getUniqueId()).size());
        alice.performCommand("team accept Alpha");
        assertTrue(teams.teamOf(alice.getUniqueId()).isPresent());

        // Member Alice cannot invite (no permission) -> Bob gets no invite.
        alice.performCommand("team invite Bob");
        assertTrue(teams.pendingInvites(bob.getUniqueId()).isEmpty());

        // Promote Alice to admin, then she can invite + kick lower members.
        owner.performCommand("team role assign Alice admin");
        assertEquals(TeamRole.ADMIN_ID,
                teams.teamOf(alice.getUniqueId()).get().member(alice.getUniqueId()).get().roleId());

        alice.performCommand("team invite Bob");
        bob.performCommand("team accept Alpha");
        assertTrue(teams.teamOf(bob.getUniqueId()).isPresent());
        alice.performCommand("team kick Bob");
        assertTrue(teams.teamOf(bob.getUniqueId()).isEmpty());

        // Admin cannot kick the owner.
        alice.performCommand("team kick Owner");
        assertTrue(teams.teamOf(owner.getUniqueId()).isPresent());

        // Transfer ownership, old owner can then leave.
        owner.performCommand("team transfer Alice");
        assertTrue(teams.teamOf(alice.getUniqueId()).get().isOwner(alice.getUniqueId()));
        owner.performCommand("team leave");
        assertTrue(teams.teamOf(owner.getUniqueId()).isEmpty());

        // New owner disbands.
        alice.performCommand("team disband confirm");
        assertTrue(teams.teamByName("Alpha").isEmpty());
    }

    @Test
    void ownerCannotLeaveViaCommand() {
        PlayerMock owner = server.addPlayer("Owner");
        owner.performCommand("team create Alpha");
        owner.performCommand("team leave");
        // Still present: owner must transfer or disband.
        assertTrue(teams.teamOf(owner.getUniqueId()).isPresent());
    }

    @Test
    void duplicateNameRejectedViaCommand() {
        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");
        a.performCommand("team create Same");
        b.performCommand("team create same");
        assertTrue(teams.teamOf(b.getUniqueId()).isEmpty());
    }

    @Test
    void disbandRequiresConfirm() {
        PlayerMock owner = server.addPlayer("Owner");
        owner.performCommand("team create Alpha");
        owner.performCommand("team disband");
        assertTrue(teams.teamByName("Alpha").isPresent(), "disband without confirm must not delete");
        owner.performCommand("team disband confirm");
        assertTrue(teams.teamByName("Alpha").isEmpty());
    }
}
