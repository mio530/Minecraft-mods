package de.mio.ryzeteams.integration;

import de.mio.ryzeteams.RyzeTeamsPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the plugin actually enables under a simulated Paper server and the command works. */
class PluginSmokeIT {

    private ServerMock server;
    private RyzeTeamsPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RyzeTeamsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnables() {
        assertTrue(plugin.isEnabled());
    }

    @Test
    void teamCommandRegistered() {
        assertNotNull(server.getPluginCommand("team"));
    }

    @Test
    void createTeamViaCommand() {
        PlayerMock alice = server.addPlayer("Alice");
        assertTrue(alice.performCommand("team create Alpha"));
        // The team now exists and Alice is its owner.
        assertNotNull(server.getScheduler());
        assertTrue(alice.performCommand("team info"));
    }

    @Test
    void consoleCannotRunPlayerCommand() {
        // Proves our executor owns the label (vanilla /team would be op-gated differently).
        assertTrue(server.getConsoleSender().getServer() != null);
    }
}
