package de.mio.ryzeteams.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Guards against privilege escalation via the default join role / role resolution. */
class DefaultRoleSecurityTest {

    private Team team(UUID owner) {
        Team t = new Team(UUID.randomUUID(), "Alpha", owner);
        DefaultRoles.apply(t);
        t.addMember(new Member(owner, "Owner", TeamRole.OWNER_ID, 0));
        return t;
    }

    @Test
    void defaultRoleCannotBeOwner() {
        Team t = team(UUID.randomUUID());
        t.setDefaultRoleId(TeamRole.OWNER_ID);
        assertNotEquals(TeamRole.OWNER_ID, t.defaultRoleId());
        assertEquals(TeamRole.MEMBER_ID, t.defaultRoleId());
    }

    @Test
    void nonOwnerNeverResolvesToOwnerRole() {
        UUID owner = UUID.randomUUID();
        UUID hacker = UUID.randomUUID();
        Team t = team(owner);
        // Simulate a manipulated DB row: a non-owner member carrying roleId "owner".
        t.addMember(new Member(hacker, "Hacker", TeamRole.OWNER_ID, 0));
        TeamRole resolved = t.roleOf(hacker);
        assertFalse(resolved.isOwnerRole(), "non-owner must not resolve to the owner role");
        // And therefore must not hold owner-only powers implicitly.
        assertFalse(TeamPolicy.has(t, hacker, Permission.MANAGE_ROLES)
                && TeamPolicy.has(t, hacker, Permission.EC_MANAGE)
                && t.roleOf(hacker).isOwnerRole());
    }

    @Test
    void ownerStillResolvesToOwnerRole() {
        UUID owner = UUID.randomUUID();
        Team t = team(owner);
        assertTrue(t.roleOf(owner).isOwnerRole());
    }
}
