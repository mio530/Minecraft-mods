package de.mio.ryzeteams.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One-shot chat input prompts for GUIs (e.g. "type the new role name"). */
public final class ChatPrompts implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompts(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Captures the player's next chat line and runs {@code callback} on the main thread. */
    public void await(Player player, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Type the value in chat, or 'cancel' to abort."));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = pending.remove(e.getPlayer().getUniqueId());
        if (cb == null) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (text.equalsIgnoreCase("cancel")) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
