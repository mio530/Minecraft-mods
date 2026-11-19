package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.service.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared services handed to every menu so they can open sub-menus consistently. */
public record GuiContext(JavaPlugin plugin, TeamManager teams, EnderchestManager ec, ChatPrompts prompts) {
}
