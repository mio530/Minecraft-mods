package de.mio.ryzeteams.service;

import java.util.UUID;

/**
 * Lets {@link TeamManager} drive enderchest views (force-close, discard) without
 * depending on Bukkit, keeping the service unit-testable. The plugin wires the
 * real {@code EnderchestManager}; tests use {@link #NOOP}.
 */
public interface EnderchestHook {

    void closeForPlayer(UUID player);

    void closeForTeam(UUID teamId);

    void discard(UUID teamId);

    EnderchestHook NOOP = new EnderchestHook() {
        @Override
        public void closeForPlayer(UUID player) {
        }

        @Override
        public void closeForTeam(UUID teamId) {
        }

        @Override
        public void discard(UUID teamId) {
        }
    };
}
