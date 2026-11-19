package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.model.TeamHome;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import de.mio.ryzeteams.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Team settings: set home here, pick the default join role, disband the team. */
public final class SettingsMenu extends Menu {

    private final TeamManager teams;
    private final UUID teamId;

    public SettingsMenu(GuiContext ctx, UUID teamId) {
        super(ctx);
        this.teams = ctx.teams();
        this.teamId = teamId;
    }

    @Override
    protected Component title() {
        return Items.text("Team Settings").color(NamedTextColor.DARK_AQUA);
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        Team team = teams.teamById(teamId).orElse(null);
        if (team == null || !team.isMember(viewer.getUniqueId())) {
            closeLater(viewer);
            return;
        }
        set(10, Items.of(Material.RED_BED, "Set Team Home",
                        "Set the team home to your location.",
                        team.home().map(h -> "Current: " + h.world()).orElse("No home set")),
                e -> {
                    try {
                        Location l = viewer.getLocation();
                        teams.setHome(team, viewer.getUniqueId(),
                                new TeamHome(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(),
                                        l.getYaw(), l.getPitch()));
                        viewer.sendMessage(Msg.success("Team home set."));
                        refresh(viewer);
                    } catch (TeamException ex) {
                        viewer.sendMessage(Msg.error(ex.getMessage()));
                    }
                });

        set(12, Items.of(Material.NAME_TAG, "Default Join Role",
                        "New members join as this role.",
                        "Current: " + team.role(team.defaultRoleId()).map(r -> r.name()).orElse("?")),
                e -> openLater(viewer, new DefaultRolePickerMenu(ctx, teamId)));

        if (team.isOwner(viewer.getUniqueId())) {
            set(14, Items.of(Material.TNT, "Disband Team",
                            "Permanently delete the team and its EC.",
                            "Shift-click to confirm."),
                    e -> {
                        if (!e.isShiftClick()) {
                            viewer.sendMessage(Msg.error("Shift-click to confirm disbanding."));
                            return;
                        }
                        try {
                            teams.disband(team, viewer.getUniqueId());
                            viewer.sendMessage(Msg.success("Team disbanded."));
                            closeLater(viewer);
                        } catch (TeamException ex) {
                            viewer.sendMessage(Msg.error(ex.getMessage()));
                        }
                    });
        }

        set(22, backButton(), e -> openLater(viewer, new MainMenu(ctx)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }
}
