package de.mio.ryzeteams.listener;

import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Keeps last-known names current and surfaces pending invites/alliance requests on join. */
public final class ConnectionListener implements Listener {

    private final Plugin plugin;
    private final TeamManager teams;

    public ConnectionListener(Plugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        teams.updateName(player.getUniqueId(), player.getName());

        // Notify a tick later so the message appears after vanilla join output.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int invites = teams.pendingInvites(player.getUniqueId()).size();
            if (invites > 0) {
                player.sendMessage(Msg.info("You have " + invites + " pending team invite(s). "
                        + "Use /team accept [team]."));
            }
            teams.teamOf(player.getUniqueId()).ifPresent(team -> notifyAlliances(player, team));
        }, 20L);
    }

    private void notifyAlliances(Player player, Team team) {
        List<?> requests = List.copyOf(team.incomingAllianceRequests());
        if (!requests.isEmpty() && TeamPolicy.has(team, player.getUniqueId(), Permission.ACCEPT_ALLIANCES)) {
            player.sendMessage(Msg.info("Your team has " + requests.size()
                    + " pending alliance request(s). Use /team ally accept <team>."));
        }
    }
}
