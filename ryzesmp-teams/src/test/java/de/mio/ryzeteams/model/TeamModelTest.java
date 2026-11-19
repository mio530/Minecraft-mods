package de.mio.ryzeteams.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Structural tests for the Team aggregate (roles, members, transfer, cleanup). */
class TeamModelTest {

    private Team newTeam(UUID owner) {
        Team t = new Team(UUID.randomUUID(), "Alpha", owner);
        DefaultRoles.apply(t);
        t.addMember(new Member(owner, "OwnerGuy", TeamRole.OWNER_ID, 0));
        return t;
    }

    @Test
    void ownerRoleHoldsAllPermissions() {
        UUID owner = UUID.randomUUID();
        Team t = newTeam(owner);
        for (Permission p : Permission.values()) {
            assertTrue(TeamPolicy.has(t, owner, p), "owner should hold " + p);
        }
    }

    @Test
    void defaultRolesAreCreated() {
        Team t = newTeam(UUID.randomUUID());
        assertTrue(t.hasRole(TeamRole.OWNER_ID));
        assertTrue(t.hasRole(TeamRole.ADMIN_ID));
        assertTrue(t.hasRole(TeamRole.MODERATOR_ID));
        assertTrue(t.hasRole(TeamRole.MEMBER_ID));
        assertTrue(t.hasRole(TeamRole.RECRUIT_ID));
        assertEquals(TeamRole.MEMBER_ID, t.defaultRoleId());
    }

    @Test
    void removeRoleReassignsMembersToDefault() {
        Team t = newTeam(UUID.randomUUID());
        UUID member = UUID.randomUUID();
        t.addMember(new Member(member, "Builder1", TeamRole.MODERATOR_ID, 0));
        t.removeRole(TeamRole.MODERATOR_ID);
        assertFalse(t.hasRole(TeamRole.MODERATOR_ID));
        assertEquals(TeamRole.MEMBER_ID, t.member(member).orElseThrow().roleId());
    }

    @Test
    void cannotDeleteOwnerOrDefaultRole() {
        Team t = newTeam(UUID.randomUUID());
        assertThrows(TeamException.class, () -> t.removeRole(TeamRole.OWNER_ID));
        assertThrows(TeamException.class, () -> t.removeRole(TeamRole.MEMBER_ID));
    }

    @Test
    void removingOwnerIsRejected() {
        UUID owner = UUID.randomUUID();
        Team t = newTeam(owner);
        assertThrows(TeamException.class, () -> t.removeMember(owner));
    }

    @Test
    void transferOwnershipSwapsRoles() {
        UUID owner = UUID.randomUUID();
        UUID heir = UUID.randomUUID();
        Team t = newTeam(owner);
        t.addMember(new Member(heir, "Heir", TeamRole.ADMIN_ID, 0));
        t.transferOwnership(heir);
        assertTrue(t.isOwner(heir));
        assertEquals(TeamRole.OWNER_ID, t.member(heir).orElseThrow().roleId());
        assertEquals(TeamRole.ADMIN_ID, t.member(owner).orElseThrow().roleId());
        assertTrue(TeamPolicy.has(t, heir, Permission.MANAGE_ROLES));
    }

    @Test
    void roleWithoutPermissionDoesNotHaveIt() {
        Team t = newTeam(UUID.randomUUID());
        UUID recruit = UUID.randomUUID();
        t.addMember(new Member(recruit, "New", TeamRole.RECRUIT_ID, 0));
        assertFalse(TeamPolicy.has(t, recruit, Permission.EC_OPEN));
        assertFalse(TeamPolicy.has(t, recruit, Permission.INVITE_MEMBERS));
    }

    @Test
    void customRoleGrantAndRevoke() {
        TeamRole r = new TeamRole("builder", "Builder", 300, EnumSet.noneOf(Permission.class));
        r.grant(Permission.EC_OPEN);
        assertTrue(r.has(Permission.EC_OPEN));
        r.revoke(Permission.EC_OPEN);
        assertFalse(r.has(Permission.EC_OPEN));
    }
}
