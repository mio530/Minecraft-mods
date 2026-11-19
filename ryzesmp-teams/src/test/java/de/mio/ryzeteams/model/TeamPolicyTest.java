package de.mio.ryzeteams.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive anti-escalation and permission-boundary tests. */
class TeamPolicyTest {

    private UUID owner;
    private UUID admin;
    private UUID mod;
    private UUID member;
    private UUID recruit;
    private Team team;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        admin = UUID.randomUUID();
        mod = UUID.randomUUID();
        member = UUID.randomUUID();
        recruit = UUID.randomUUID();

        team = new Team(UUID.randomUUID(), "Alpha", owner);
        DefaultRoles.apply(team);
        team.addMember(new Member(owner, "Owner", TeamRole.OWNER_ID, 0));
        team.addMember(new Member(admin, "Admin", TeamRole.ADMIN_ID, 0));
        team.addMember(new Member(mod, "Mod", TeamRole.MODERATOR_ID, 0));
        team.addMember(new Member(member, "Member", TeamRole.MEMBER_ID, 0));
        team.addMember(new Member(recruit, "Recruit", TeamRole.RECRUIT_ID, 0));
    }

    // ---- kicking ------------------------------------------------------------

    @Test
    void ownerCannotBeKickedByAnyone() {
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanKick(team, admin, owner));
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanKick(team, mod, owner));
    }

    @Test
    void adminCanKickLowerButNotEqualOrHigher() {
        assertDoesNotThrow(() -> TeamPolicy.ensureCanKick(team, admin, mod));
        assertDoesNotThrow(() -> TeamPolicy.ensureCanKick(team, admin, member));
        // another admin
        UUID admin2 = UUID.randomUUID();
        team.addMember(new Member(admin2, "Admin2", TeamRole.ADMIN_ID, 0));
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanKick(team, admin, admin2));
    }

    @Test
    void memberWithoutKickPermCannotKick() {
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanKick(team, member, recruit));
    }

    @Test
    void cannotKickYourself() {
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanKick(team, admin, admin));
    }

    @Test
    void ownerCanKickAnyNonOwner() {
        assertDoesNotThrow(() -> TeamPolicy.ensureCanKick(team, owner, admin));
        assertDoesNotThrow(() -> TeamPolicy.ensureCanKick(team, owner, recruit));
    }

    // ---- role assignment ----------------------------------------------------

    @Test
    void cannotAssignRoleAtOrAboveOwnRank() {
        TeamRole adminRole = team.role(TeamRole.ADMIN_ID).orElseThrow();
        // admin assigning admin-rank role to a member → blocked (>= own rank)
        assertThrows(TeamException.class,
                () -> TeamPolicy.ensureCanAssignRole(team, admin, member, adminRole));
    }

    @Test
    void adminCanAssignLowerRole() {
        TeamRole modRole = team.role(TeamRole.MODERATOR_ID).orElseThrow();
        assertDoesNotThrow(() -> TeamPolicy.ensureCanAssignRole(team, admin, member, modRole));
    }

    @Test
    void cannotChangeOwnRole() {
        TeamRole modRole = team.role(TeamRole.MODERATOR_ID).orElseThrow();
        assertThrows(TeamException.class,
                () -> TeamPolicy.ensureCanAssignRole(team, admin, admin, modRole));
    }

    @Test
    void cannotAssignOwnerRoleViaAssign() {
        TeamRole ownerRole = team.role(TeamRole.OWNER_ID).orElseThrow();
        assertThrows(TeamException.class,
                () -> TeamPolicy.ensureCanAssignRole(team, owner, admin, ownerRole));
    }

    @Test
    void ownersTargetRoleCannotBeChanged() {
        TeamRole modRole = team.role(TeamRole.MODERATOR_ID).orElseThrow();
        assertThrows(TeamException.class,
                () -> TeamPolicy.ensureCanAssignRole(team, owner, owner, modRole));
    }

    // ---- granting permissions ----------------------------------------------

    @Test
    void cannotGrantPermissionYouDoNotHold() {
        // Give member MANAGE_ROLES but not MANAGE_ALLIANCES, then try to grant alliances.
        team.role(TeamRole.MEMBER_ID).orElseThrow().grant(Permission.MANAGE_ROLES);
        assertThrows(TeamException.class,
                () -> TeamPolicy.ensureCanGrant(team, member, Permission.MANAGE_ALLIANCES));
    }

    @Test
    void canGrantPermissionYouHold() {
        team.role(TeamRole.MEMBER_ID).orElseThrow().grant(Permission.MANAGE_ROLES);
        team.role(TeamRole.MEMBER_ID).orElseThrow().grant(Permission.EC_WITHDRAW);
        assertDoesNotThrow(() -> TeamPolicy.ensureCanGrant(team, member, Permission.EC_WITHDRAW));
    }

    @Test
    void ownerCanGrantAnything() {
        assertDoesNotThrow(() -> TeamPolicy.ensureCanGrant(team, owner, Permission.MANAGE_ALLIANCES));
    }

    // ---- editing roles ------------------------------------------------------

    @Test
    void cannotEditRoleAtOrAboveOwnRank() {
        TeamRole adminRole = team.role(TeamRole.ADMIN_ID).orElseThrow();
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanEditRole(team, admin, adminRole));
    }

    @Test
    void canEditLowerRole() {
        TeamRole modRole = team.role(TeamRole.MODERATOR_ID).orElseThrow();
        assertDoesNotThrow(() -> TeamPolicy.ensureCanEditRole(team, admin, modRole));
    }

    @Test
    void ownerRoleNeverEditable() {
        TeamRole ownerRole = team.role(TeamRole.OWNER_ID).orElseThrow();
        assertThrows(TeamException.class, () -> TeamPolicy.ensureCanEditRole(team, owner, ownerRole));
    }

    // ---- permission gates ---------------------------------------------------

    @Test
    void ensureHasThrowsWhenMissing() {
        assertThrows(TeamException.class,
                () -> TeamPolicy.ensureHas(team, recruit, Permission.EC_OPEN));
        assertDoesNotThrow(() -> TeamPolicy.ensureHas(team, member, Permission.EC_OPEN));
    }

    @Test
    void rankOrdering() {
        assertTrue(TeamPolicy.rankOf(team, owner) > TeamPolicy.rankOf(team, admin));
        assertTrue(TeamPolicy.rankOf(team, admin) > TeamPolicy.rankOf(team, mod));
        assertTrue(TeamPolicy.rankOf(team, mod) > TeamPolicy.rankOf(team, member));
        assertTrue(TeamPolicy.rankOf(team, member) > TeamPolicy.rankOf(team, recruit));
    }
}
