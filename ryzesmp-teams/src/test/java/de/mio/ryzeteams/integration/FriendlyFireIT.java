package de.mio.ryzeteams.integration;

import de.mio.ryzeteams.RyzeTeamsPlugin;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.service.TeamManager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Friendly-fire protection driven through real EntityDamageByEntityEvent (melee). */
class FriendlyFireIT {

    private ServerMock server;
    private TeamManager teams;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        RyzeTeamsPlugin plugin = MockBukkit.load(RyzeTeamsPlugin.class);
        teams = plugin.teamManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @SuppressWarnings("deprecation")
    private boolean melee(PlayerMock attacker, PlayerMock victim) {
        EntityDamageByEntityEvent e = new EntityDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0);
        server.getPluginManager().callEvent(e);
        return e.isCancelled();
    }

    @Test
    void sameTeamMeleeIsBlocked() {
        PlayerMock owner = server.addPlayer("Owner");
        PlayerMock member = server.addPlayer("Member");
        Team t = teams.createTeam(owner.getUniqueId(), "Owner", "Alpha");
        teams.invite(t, owner.getUniqueId(), member.getUniqueId(), "Member");
        teams.acceptInvite(member.getUniqueId(), "Member", null);

        assertTrue(melee(owner, member), "same-team damage must be cancelled");
        assertTrue(melee(member, owner), "and in both directions");
    }

    @Test
    void unrelatedPlayersAreNotAffected() {
        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");
        teams.createTeam(a.getUniqueId(), "A", "TeamA");
        // b is teamless
        assertFalse(melee(a, b), "teamless victim is not protected");
        assertFalse(melee(b, a), "teamless attacker is not protected");
    }

    @Test
    void alliesAreProtectedAndProtectionDropsOnDissolve() {
        PlayerMock oa = server.addPlayer("OA");
        PlayerMock ob = server.addPlayer("OB");
        Team a = teams.createTeam(oa.getUniqueId(), "OA", "Alpha");
        Team b = teams.createTeam(ob.getUniqueId(), "OB", "Beta");

        assertFalse(melee(oa, ob), "different teams, no alliance -> not protected");

        teams.requestAlliance(a, oa.getUniqueId(), "Beta");
        teams.acceptAlliance(b, ob.getUniqueId(), "Alpha");
        assertTrue(melee(oa, ob), "allied teams are protected");

        teams.removeAlliance(a, oa.getUniqueId(), "Beta");
        assertFalse(melee(oa, ob), "protection drops immediately when the alliance ends");
    }

    @Test
    void selfDamageIsNotBlocked() {
        PlayerMock p = server.addPlayer("Solo");
        teams.createTeam(p.getUniqueId(), "Solo", "Solo");
        assertFalse(melee(p, p), "a player damaging themselves is never blocked");
    }
}
