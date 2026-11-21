package de.mio.ryzeteams.service;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.regex.Pattern;

/** Typed, cached view over config.yml. Read once on load/reload, never per event. */
public final class PluginConfig {

    private final int minNameLength;
    private final int maxNameLength;
    private final Pattern namePattern;
    private final String defaultJoinRole;
    private final int maxMembers;
    private final long inviteExpiryMillis;
    private final int ecTotalSlots;
    private final boolean ffSameTeam;
    private final boolean ffAllies;
    private final long autosaveSeconds;

    private PluginConfig(int minNameLength, int maxNameLength, Pattern namePattern, String defaultJoinRole,
                         int maxMembers, long inviteExpiryMillis, int ecTotalSlots, boolean ffSameTeam,
                         boolean ffAllies, long autosaveSeconds) {
        this.minNameLength = minNameLength;
        this.maxNameLength = maxNameLength;
        this.namePattern = namePattern;
        this.defaultJoinRole = defaultJoinRole;
        this.maxMembers = maxMembers;
        this.inviteExpiryMillis = inviteExpiryMillis;
        this.ecTotalSlots = ecTotalSlots;
        this.ffSameTeam = ffSameTeam;
        this.ffAllies = ffAllies;
        this.autosaveSeconds = autosaveSeconds;
    }

    public static PluginConfig from(FileConfiguration c) {
        return new PluginConfig(
                c.getInt("team.min-name-length", 3),
                c.getInt("team.max-name-length", 16),
                Pattern.compile(c.getString("team.name-pattern", "^[A-Za-z0-9_]+$")),
                c.getString("team.default-join-role", "member"),
                c.getInt("team.max-members", 0),
                c.getLong("invites.expiry-seconds", 120L) * 1000L,
                c.getInt("enderchest.total-slots", 54),
                c.getBoolean("friendly-fire.same-team", true),
                c.getBoolean("friendly-fire.allies", true),
                c.getLong("persistence.autosave-seconds", 300L));
    }

    /** Hard-coded defaults, usable without a Bukkit server (tests). */
    public static PluginConfig defaults() {
        return new PluginConfig(3, 16, Pattern.compile("^[A-Za-z0-9_]+$"), "member",
                0, 120_000L, 54, true, true, 300L);
    }

    public int minNameLength() {
        return minNameLength;
    }

    public int maxNameLength() {
        return maxNameLength;
    }

    public Pattern namePattern() {
        return namePattern;
    }

    public String defaultJoinRole() {
        return defaultJoinRole;
    }

    public int maxMembers() {
        return maxMembers;
    }

    public long inviteExpiryMillis() {
        return inviteExpiryMillis;
    }

    public int ecTotalSlots() {
        return ecTotalSlots;
    }

    public boolean friendlyFireSameTeam() {
        return ffSameTeam;
    }

    public boolean friendlyFireAllies() {
        return ffAllies;
    }

    public long autosaveSeconds() {
        return autosaveSeconds;
    }
}
