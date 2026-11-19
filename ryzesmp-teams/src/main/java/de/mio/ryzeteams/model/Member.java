package de.mio.ryzeteams.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A team member: a player identity (UUID + last-known name, so offline players
 * can be listed and kicked) plus the id of the role they currently hold.
 */
public final class Member {

    private final UUID uuid;
    private String lastKnownName;
    private String roleId;
    private final long joinedAt;

    public Member(UUID uuid, String lastKnownName, String roleId, long joinedAt) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.lastKnownName = lastKnownName;
        this.roleId = TeamRole.normalizeId(roleId);
        this.joinedAt = joinedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastKnownName() {
        return lastKnownName == null ? uuid.toString().substring(0, 8) : lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            this.lastKnownName = lastKnownName;
        }
    }

    public String roleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = TeamRole.normalizeId(roleId);
    }

    public long joinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Member other && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
