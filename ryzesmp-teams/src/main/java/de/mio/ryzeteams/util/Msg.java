package de.mio.ryzeteams.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Consistent, English, vanilla-client-safe chat messages (plain Components). */
public final class Msg {

    private static final Component PREFIX = Component.text("Teams ")
            .color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true)
            .append(Component.text("» ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));

    private Msg() {
    }

    public static Component info(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.GRAY));
    }

    public static Component success(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.GREEN));
    }

    public static Component error(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.RED));
    }

    public static Component accent(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.YELLOW));
    }

    public static Component plain(String text) {
        return Component.text(text).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
