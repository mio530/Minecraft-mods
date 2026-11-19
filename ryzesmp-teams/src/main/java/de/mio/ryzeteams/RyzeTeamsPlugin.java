package de.mio.ryzeteams;

import de.mio.ryzeteams.command.TeamCommand;
import de.mio.ryzeteams.ec.EnderchestListener;
import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.gui.ChatPrompts;
import de.mio.ryzeteams.gui.GuiContext;
import de.mio.ryzeteams.gui.MenuListener;
import de.mio.ryzeteams.listener.ConnectionListener;
import de.mio.ryzeteams.listener.FriendlyFireListener;
import de.mio.ryzeteams.service.PluginConfig;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.storage.SqliteTeamRepository;
import de.mio.ryzeteams.storage.TeamRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * Entry point. Wires storage, services, listeners and commands, and guarantees a
 * clean, data-safe shutdown (close EC views, flush, then close storage).
 */
public class RyzeTeamsPlugin extends JavaPlugin {

    private TeamRepository repository;
    private TeamManager teamManager;
    private EnderchestManager enderchestManager;
    private int autosaveTask = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginConfig config = PluginConfig.from(getConfig());

        Path dbFile = getDataFolder().toPath().resolve("teams.db");
        repository = new SqliteTeamRepository(dbFile);
        teamManager = new TeamManager(getLogger(), repository, config);
        try {
            repository.initialize();
            teamManager.loadAll();
        } catch (RuntimeException e) {
            getLogger().severe("Failed to start RyzeTeams storage: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        enderchestManager = new EnderchestManager(this, teamManager);
        teamManager.setEnderchestHook(enderchestManager);

        ChatPrompts prompts = new ChatPrompts(this);
        GuiContext gui = new GuiContext(this, teamManager, enderchestManager, prompts);

        // Commands
        PluginCommand teamCmd = getCommand("team");
        if (teamCmd != null) {
            TeamCommand executor = new TeamCommand(gui);
            teamCmd.setExecutor(executor);
            teamCmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'team' missing from plugin.yml");
        }

        // Listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new EnderchestListener(teamManager, enderchestManager), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(prompts, this);
        pm.registerEvents(new FriendlyFireListener(teamManager), this);
        pm.registerEvents(new ConnectionListener(this, teamManager), this);

        // Periodic autosave safety net.
        long ticks = Math.max(60L, config.autosaveSeconds()) * 20L;
        autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (enderchestManager != null) {
                enderchestManager.autosave();
            }
        }, ticks, ticks).getTaskId();

        getLogger().info("RyzeTeams enabled (Paper 1.21.11).");
    }

    /** Accessors for integration tests (and potential sibling plugins). */
    public TeamManager teamManager() {
        return teamManager;
    }

    public EnderchestManager enderchestManager() {
        return enderchestManager;
    }

    @Override
    public void onDisable() {
        if (autosaveTask != -1) {
            getServer().getScheduler().cancelTask(autosaveTask);
        }
        if (enderchestManager != null) {
            // Order matters: close views (fires close->save) while the IO executor
            // is still alive, then flush, then drain + close storage.
            enderchestManager.closeAllViews();
            enderchestManager.flushAll();
        }
        if (teamManager != null) {
            teamManager.shutdown();
        }
        getLogger().info("RyzeTeams disabled.");
    }
}
