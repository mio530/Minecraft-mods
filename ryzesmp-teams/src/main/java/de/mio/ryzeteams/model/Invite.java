package de.mio.ryzeteams.model;

import java.util.UUID;

/**
 * A pending invitation of a player into a team. Persisted so invites survive
 * restarts; expiry is enforced by the service using {@link #createdAt} against
 * a configurable TTL.
 */
public record Invite(UUID teamId, UUID invitee, UUID inviter, long createdAt) {

    public boolean isExpired(long now, long ttlMillis) {
        return ttlMillis > 0 && now - createdAt > ttlMillis;
    }
}
