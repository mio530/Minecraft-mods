package de.mio.ryzeteams.model;

/**
 * A team home location, stored Bukkit-free (world name + coordinates). The
 * command/GUI layer converts this to and from a Bukkit {@code Location}.
 */
public record TeamHome(String world, double x, double y, double z, float yaw, float pitch) {
}
