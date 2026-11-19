package de.mio.ryzeteams.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Granular team permissions. Roles hold an explicit set of these; there is no
 * hard-coded "if role == admin" logic anywhere in the plugin, only permission
 * checks. The team owner implicitly holds every permission (see
 * {@link TeamPolicy}).
 *
 * <p>This enum is deliberately free of any Bukkit type so the permission model
 * stays unit-testable. The {@code icon} is a vanilla {@code Material} name that
 * the GUI layer resolves; the model never imports it.
 */
public enum Permission {

    INVITE_MEMBERS(Category.TEAM, "Invite Members", "Invite players to the team.", "WRITABLE_BOOK"),
    KICK_MEMBERS(Category.TEAM, "Kick Members", "Remove members below your rank.", "BARRIER"),
    ASSIGN_ROLES(Category.TEAM, "Assign Roles", "Assign existing roles to members.", "NAME_TAG"),
    MANAGE_ROLES(Category.TEAM, "Manage Roles", "Create, delete and edit roles and their permissions.", "COMMAND_BLOCK"),
    SET_HOME(Category.TEAM, "Set Team Home", "Set and move the team home.", "RED_BED"),
    MANAGE_ALLIANCES(Category.TEAM, "Manage Alliances", "Send and cancel alliance requests.", "GOLDEN_APPLE"),
    ACCEPT_ALLIANCES(Category.TEAM, "Accept Alliances", "Accept or deny incoming alliance requests.", "GOLD_INGOT"),

    EC_OPEN(Category.ENDERCHEST, "Open Team EC", "Open and view the team enderchest.", "ENDER_CHEST"),
    EC_DEPOSIT(Category.ENDERCHEST, "Deposit Items", "Put items into the team enderchest.", "HOPPER"),
    EC_WITHDRAW(Category.ENDERCHEST, "Withdraw Items", "Take items out of the team enderchest.", "CHEST_MINECART"),
    EC_MANAGE(Category.ENDERCHEST, "Manage EC Access", "Edit which roles may use the team enderchest.", "ENDER_EYE");

    /** Grouping used purely for GUI layout. */
    public enum Category { TEAM, ENDERCHEST }

    private final Category category;
    private final String displayName;
    private final String description;
    private final String icon;

    Permission(Category category, String displayName, String description, String icon) {
        this.category = category;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public Category category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Vanilla {@code Material} name for the GUI icon. */
    public String icon() {
        return icon;
    }

    /** Case-insensitive lookup by enum name, used by commands and storage. */
    public static Optional<Permission> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String n = name.trim().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.name().equals(n)).findFirst();
    }
}
