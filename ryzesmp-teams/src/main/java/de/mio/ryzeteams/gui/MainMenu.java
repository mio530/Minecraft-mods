package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Root team menu: info and navigation to members, roles, EC and alliances. */
public final class MainMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;

    public MainMenu(GuiContext ctx) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
    }

    @Override
    protected Component title() {
        return Items.text("Team Menu").color(NamedTextColor.DARK_AQUA);
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        Team team = teams.teamOf(viewer.getUniqueId()).orElse(null);
        if (team == null) {
            closeLater(viewer);
            return;
        }
        set(10, Items.of(Material.PAPER, "Team Info",
                "Name: " + team.name(),
                "Members: " + team.size(),
                "Allies: " + team.allies().size(),
                "Your role: " + team.roleOf(viewer.getUniqueId()).name()));

        set(11, Items.of(Material.PLAYER_HEAD, "Members", "View and manage members."),
                e -> openLater(viewer, new MembersMenu(ctx, team.id())));

        set(12, Items.of(Material.COMMAND_BLOCK, "Roles", "Create, edit and assign roles."),
                e -> openLater(viewer, new RolesMenu(ctx, team.id())));

        set(13, Items.of(Material.ENDER_CHEST, "Team Enderchest", "54 shared slots (double chest)."),
                e -> {
                    if (!TeamPolicy.has(team, viewer.getUniqueId(), Permission.EC_OPEN)) {
                        viewer.sendActionBar(Component.text("You cannot open the team enderchest.")
                                .color(NamedTextColor.RED));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> ec.open(viewer, team));
                });

        set(14, Items.of(Material.ENDER_EYE, "Team-EC Access",
                "Configure which roles may use the EC."),
                e -> openLater(viewer, new EcRightsMenu(ctx, team.id())));

        set(15, Items.of(Material.GOLDEN_APPLE, "Alliances",
                "Active: " + team.allies().size(),
                "Requests: " + team.incomingAllianceRequests().size()),
                e -> openLater(viewer, new AlliancesMenu(ctx, team.id())));

        set(16, Items.of(Material.COMPARATOR, "Team Settings",
                "Home, default role, disband."),
                e -> openLater(viewer, new SettingsMenu(ctx, team.id())));

        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }
}
