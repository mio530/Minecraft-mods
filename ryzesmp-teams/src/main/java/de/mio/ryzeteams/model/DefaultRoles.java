package de.mio.ryzeteams.model;

import java.util.EnumSet;

/**
 * Builds the five default roles for a new team. Permission defaults follow the
 * spec's example (owner/admin full; moderator and member open+deposit; recruit
 * none) and are fully editable afterwards via the role GUI/commands.
 */
public final class DefaultRoles {

    private DefaultRoles() {
    }

    public static void apply(Team team) {
        // Owner role: permission set is irrelevant (owner implicitly holds all),
        // but we store an explicit full set for clarity in storage.
        team.putRole(new TeamRole(TeamRole.OWNER_ID, "Owner", TeamRole.OWNER_RANK,
                EnumSet.allOf(Permission.class)));

        team.putRole(new TeamRole(TeamRole.ADMIN_ID, "Team Admin", TeamRole.ADMIN_RANK,
                EnumSet.of(
                        Permission.INVITE_MEMBERS, Permission.KICK_MEMBERS,
                        Permission.ASSIGN_ROLES, Permission.MANAGE_ROLES,
                        Permission.SET_HOME, Permission.MANAGE_ALLIANCES, Permission.ACCEPT_ALLIANCES,
                        Permission.EC_OPEN, Permission.EC_DEPOSIT, Permission.EC_WITHDRAW, Permission.EC_MANAGE)));

        team.putRole(new TeamRole(TeamRole.MODERATOR_ID, "Moderator", TeamRole.MODERATOR_RANK,
                EnumSet.of(
                        Permission.INVITE_MEMBERS,
                        Permission.EC_OPEN, Permission.EC_DEPOSIT)));

        team.putRole(new TeamRole(TeamRole.MEMBER_ID, "Member", TeamRole.MEMBER_RANK,
                EnumSet.of(Permission.EC_OPEN, Permission.EC_DEPOSIT)));

        team.putRole(new TeamRole(TeamRole.RECRUIT_ID, "Recruit", TeamRole.RECRUIT_RANK,
                EnumSet.noneOf(Permission.class)));

        team.setDefaultRoleId(TeamRole.MEMBER_ID);
    }
}
