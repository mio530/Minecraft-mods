package de.mio.ryzeteams.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A role within a team. Roles carry a stable {@code id}, a display {@code name},
 * a numeric {@code rank} (higher = more powerful) and an explicit set of
 * {@link Permission}s.
 *
 * <p>The owner role is special: it always sits at {@link #OWNER_RANK} and
 * implicitly holds every permission regardless of its stored set. Rank drives
 * all anti-escalation checks (an actor may only affect strictly lower ranks).
 */
public final class TeamRole {

    public static final int OWNER_RANK = 1000;
    public static final int ADMIN_RANK = 800;
    public static final int MODERATOR_RANK = 600;
    public static final int MEMBER_RANK = 400;
    public static final int RECRUIT_RANK = 200;

    public static final String OWNER_ID = "owner";
    public static final String ADMIN_ID = "admin";
    public static final String MODERATOR_ID = "moderator";
    public static final String MEMBER_ID = "member";
    public static final String RECRUIT_ID = "recruit";

    private final String id;
    private String name;
    private int rank;
    private final EnumSet<Permission> permissions;

    public TeamRole(String id, String name, int rank, Set<Permission> permissions) {
        this.id = normalizeId(id);
        this.name = name;
        this.rank = rank;
        this.permissions = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            throw new TeamException("Role id must not be null.");
        }
        String id = raw.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (id.isEmpty()) {
            throw new TeamException("Role id must contain at least one letter or digit.");
        }
        return id;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int rank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public boolean isOwnerRole() {
        return OWNER_ID.equals(id);
    }

    /** True if this role holds the permission. The owner role holds all. */
    public boolean has(Permission permission) {
        return isOwnerRole() || permissions.contains(permission);
    }

    public Set<Permission> permissions() {
        if (isOwnerRole()) {
            return Collections.unmodifiableSet(EnumSet.allOf(Permission.class));
        }
        return Collections.unmodifiableSet(permissions);
    }

    /** Grants a permission. No-op for the owner role (already holds all). */
    public void grant(Permission permission) {
        if (!isOwnerRole()) {
            permissions.add(permission);
        }
    }

    /** Revokes a permission. No-op for the owner role (cannot be weakened). */
    public void revoke(Permission permission) {
        if (!isOwnerRole()) {
            permissions.remove(permission);
        }
    }

    public void setPermissions(Set<Permission> perms) {
        if (isOwnerRole()) {
            return;
        }
        permissions.clear();
        if (perms != null) {
            permissions.addAll(perms);
        }
    }

    @Override
    public String toString() {
        return "TeamRole{" + id + ", rank=" + rank + ", perms=" + permissions + '}';
    }
}
