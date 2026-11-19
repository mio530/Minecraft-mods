package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.model.TeamRole;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import de.mio.ryzeteams.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.UUID;

/** Picks the role new members join with. */
public final class DefaultRolePickerMenu extends Menu {

    private final TeamManager teams;
    private final UUID teamId;

    public DefaultRolePickerMenu(GuiContext ctx, UUID teamId) {
        super(ctx);
        this.teams = ctx.teams();
        this.teamId = teamId;
    }

    @Override
    protected Component title() {
        return Items.text("Default Join Role").color(NamedTextColor.DARK_AQUA);
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        Team team = teams.teamById(teamId).orElse(null);
        if (team == null) {
            closeLater(viewer);
            return;
        }
        int slot = 0;
        for (TeamRole role : team.roles().stream()
                .filter(r -> !r.isOwnerRole())
                .sorted(Comparator.comparingInt(TeamRole::rank).reversed())
                .toList()) {
            if (slot >= 18) {
                break;
            }
            boolean current = role.id().equals(team.defaultRoleId());
            set(slot, Items.of(current ? Material.LIME_DYE : Material.GRAY_DYE, role.name(),
                            "Rank: " + role.rank(), current ? "Current default" : "Click to set default"),
                    e -> {
                        try {
                            teams.setDefaultRole(team, viewer.getUniqueId(), role.id());
                            viewer.sendMessage(Msg.success("Default join role set to " + role.name() + "."));
                            refresh(viewer);
                        } catch (TeamException ex) {
                            viewer.sendMessage(Msg.error(ex.getMessage()));
                        }
                    });
            slot++;
        }
        set(22, backButton(), e -> openLater(viewer, new SettingsMenu(ctx, teamId)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }
}
