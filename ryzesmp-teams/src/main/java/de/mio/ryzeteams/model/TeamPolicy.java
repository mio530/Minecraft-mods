package de.mio.ryzeteams.model;

import java.util.Set;
import java.util.UUID;

/**
 * The single source of truth for permission and anti-escalation rules. Every
 * rule is a pure function of ({@link Team}, actor, target/permission) with no
 * side effects and no Bukkit types, so the whole security model is covered by
 * fast unit tests.
 *
 * <p>Core invariants enforced here:
 * <ul>
 *   <li>The owner implicitly holds every permission and can never be kicked,
 *       demoted or have their role edited by anyone.</li>
 *   <li>An actor may only affect members of <em>strictly lower</em> rank.</li>
 *   <li>An actor may only grant permissions they themselves hold.</li>
 *   <li>An actor may only create/edit/assign roles ranked below their own, which
 *       also means they can never edit their own role.</li>
 * </ul>
 */
public final class TeamPolicy {

    private TeamPolicy() {
    }

    /** Effective permissions of a team member (owner → all). */
    public static Set<Permission> effectivePermissions(Team team, UUID actor) {
        requireMember(team, actor);
        return team.roleOf(actor).permissions();
    }

    public static boolean has(Team team, UUID actor, Permission permission) {
        return team.isMember(actor) && team.roleOf(actor).has(permission);
    }

    /** Numeric rank of a member; owner always ranks {@link TeamRole#OWNER_RANK}. */
    public static int rankOf(Team team, UUID actor) {
        requireMember(team, actor);
        return team.roleOf(actor).rank();
    }

    /** Throws unless the actor holds the permission. */
    public static void ensureHas(Team team, UUID actor, Permission permission) {
        if (!has(team, actor, permission)) {
            throw new TeamException("You lack the '" + permission.displayName() + "' permission.");
        }
    }

    /**
     * True if {@code actor} may perform a management action on {@code target}
     * (kick, change role). The owner may manage anyone but themselves; everyone
     * else may only manage strictly-lower-ranked, non-owner members.
     */
    public static boolean canManage(Team team, UUID actor, UUID target) {
        if (!team.isMember(actor) || !team.isMember(target)) {
            return false;
        }
        if (actor.equals(target)) {
            return false;
        }
        if (team.isOwner(target)) {
            return false;
        }
        if (team.isOwner(actor)) {
            return true;
        }
        return rankOf(team, actor) > rankOf(team, target);
    }

    public static void ensureCanKick(Team team, UUID actor, UUID target) {
        if (team.isOwner(target)) {
            throw new TeamException("The owner cannot be kicked.");
        }
        if (actor.equals(target)) {
            throw new TeamException("Use /team leave to leave your team.");
        }
        ensureHas(team, actor, Permission.KICK_MEMBERS);
        if (!canManage(team, actor, target)) {
            throw new TeamException("You cannot kick a member of equal or higher rank.");
        }
    }

    public static void ensureCanAssignRole(Team team, UUID actor, UUID target, TeamRole role) {
        if (role.isOwnerRole()) {
            throw new TeamException("Use /team transfer to hand over ownership.");
        }
        if (team.isOwner(target)) {
            throw new TeamException("The owner's role cannot be changed.");
        }
        if (actor.equals(target) && !team.isOwner(actor)) {
            throw new TeamException("You cannot change your own role.");
        }
        ensureHas(team, actor, Permission.ASSIGN_ROLES);
        if (!team.isOwner(actor)) {
            if (!canManage(team, actor, target)) {
                throw new TeamException("You cannot change the role of an equal or higher-ranked member.");
            }
            if (role.rank() >= rankOf(team, actor)) {
                throw new TeamException("You cannot assign a role at or above your own rank.");
            }
        }
    }

    /** Gate for creating or editing a role's definition (rank + permissions). */
    public static void ensureCanEditRole(Team team, UUID actor, TeamRole role) {
        if (role.isOwnerRole()) {
            throw new TeamException("The owner role cannot be edited.");
        }
        ensureHas(team, actor, Permission.MANAGE_ROLES);
        if (!team.isOwner(actor) && role.rank() >= rankOf(team, actor)) {
            throw new TeamException("You cannot edit a role at or above your own rank.");
        }
    }

    /** Gate for granting a single permission to a role the actor may edit. */
    public static void ensureCanGrant(Team team, UUID actor, Permission permission) {
        ensureHas(team, actor, Permission.MANAGE_ROLES);
        if (!team.isOwner(actor) && !has(team, actor, permission)) {
            throw new TeamException("You cannot grant a permission you do not hold yourself.");
        }
    }

    /**
     * Gate for toggling a single permission on a role. Enderchest-category
     * permissions may be edited by a holder of {@code MANAGE_ROLES} <em>or</em>
     * {@code EC_MANAGE}; team-category permissions require {@code MANAGE_ROLES}.
     * The rank rule and the "only grant what you hold" rule always apply.
     */
    public static void ensureCanManagePermission(Team team, UUID actor, TeamRole role,
                                                 Permission perm, boolean grant) {
        if (role.isOwnerRole()) {
            throw new TeamException("The owner role cannot be edited.");
        }
        if (!team.isOwner(actor)) {
            boolean manageRoles = has(team, actor, Permission.MANAGE_ROLES);
            if (perm.category() == Permission.Category.ENDERCHEST) {
                // Managing EC access is exactly what EC_MANAGE grants, so an EC admin
                // may set EC rights on lower roles even without holding them.
                if (!manageRoles && !has(team, actor, Permission.EC_MANAGE)) {
                    throw new TeamException("You need 'Manage Roles' or 'Manage EC Access'.");
                }
            } else {
                if (!manageRoles) {
                    throw new TeamException("You lack the 'Manage Roles' permission.");
                }
                // Team-management powers can never be granted beyond one's own.
                if (grant && !has(team, actor, perm)) {
                    throw new TeamException("You cannot grant a permission you do not hold yourself.");
                }
            }
            if (role.rank() >= rankOf(team, actor)) {
                throw new TeamException("You cannot edit a role at or above your own rank.");
            }
        }
    }

    public static void ensureCanManageAlliances(Team team, UUID actor) {
        ensureHas(team, actor, Permission.MANAGE_ALLIANCES);
    }

    public static void ensureCanAcceptAlliances(Team team, UUID actor) {
        ensureHas(team, actor, Permission.ACCEPT_ALLIANCES);
    }

    public static void ensureCanInvite(Team team, UUID actor) {
        ensureHas(team, actor, Permission.INVITE_MEMBERS);
    }

    private static void requireMember(Team team, UUID actor) {
        if (!team.isMember(actor)) {
            throw new TeamException("Player is not a member of this team.");
        }
    }
}
