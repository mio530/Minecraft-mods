package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import de.mio.ryzeteams.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Shows active alliances and incoming requests with accept/deny/remove actions. */
public final class AlliancesMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;
    private final UUID teamId;

    public AlliancesMenu(GuiContext ctx, UUID teamId) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
        this.teamId = teamId;
    }

    @Override
    protected Component title() {
        return Items.text("Alliances").color(NamedTextColor.DARK_AQUA);
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        Team team = teams.teamById(teamId).orElse(null);
        if (team == null || !team.isMember(viewer.getUniqueId())) {
            closeLater(viewer);
            return;
        }
        UUID me = viewer.getUniqueId();
        boolean canManage = TeamPolicy.has(team, me, Permission.MANAGE_ALLIANCES);
        boolean canAccept = TeamPolicy.has(team, me, Permission.ACCEPT_ALLIANCES);

        set(4, Items.of(Material.GOLD_INGOT, "Alliances",
                "Active: " + team.allies().size(),
                "Requests: " + team.incomingAllianceRequests().size()));

        int slot = 9;
        for (UUID allyId : team.allies()) {
            Team ally = teams.teamById(allyId).orElse(null);
            if (ally == null || slot >= 27) {
                continue;
            }
            String name = ally.name();
            set(slot, Items.of(Material.GOLDEN_APPLE, name, "Allied team",
                            canManage ? "Click to break alliance" : "Allied"),
                    e -> {
                        if (canManage) {
                            act(viewer, () -> teams.removeAlliance(team, me, name), "Alliance with " + name + " ended.");
                        }
                    });
            slot++;
        }

        slot = 36;
        for (UUID reqId : team.incomingAllianceRequests()) {
            Team from = teams.teamById(reqId).orElse(null);
            if (from == null || slot >= 53) {
                continue;
            }
            String name = from.name();
            set(slot, Items.of(Material.PAPER, "Request: " + name,
                            canAccept ? "Left-click to accept" : "Pending request",
                            canAccept ? "Right-click to deny" : ""),
                    e -> {
                        if (!canAccept) {
                            return;
                        }
                        if (e.isRightClick()) {
                            act(viewer, () -> teams.denyAlliance(team, me, name), "Denied request from " + name + ".");
                        } else {
                            act(viewer, () -> teams.acceptAlliance(team, me, name), "Allied with " + name + "!");
                        }
                    });
            slot++;
        }

        set(49, backButton(), e -> openLater(viewer, new MainMenu(ctx)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void act(Player viewer, Runnable action, String success) {
        try {
            action.run();
            viewer.sendMessage(Msg.success(success));
            refresh(viewer);
        } catch (TeamException ex) {
            viewer.sendMessage(Msg.error(ex.getMessage()));
        }
    }
}
