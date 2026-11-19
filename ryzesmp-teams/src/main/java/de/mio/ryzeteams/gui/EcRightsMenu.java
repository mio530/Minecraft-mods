package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.model.Permission;
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
import java.util.List;
import java.util.UUID;

/**
 * Team-EC access editor: a role × {open, deposit, withdraw, manage} matrix.
 * Each cell toggles that EC permission for that role (gated by MANAGE_ROLES or
 * EC_MANAGE in the service).
 */
public final class EcRightsMenu extends Menu {

    private static final List<Permission> COLS = List.of(
            Permission.EC_OPEN, Permission.EC_DEPOSIT, Permission.EC_WITHDRAW, Permission.EC_MANAGE);

    private final TeamManager teams;
    private final UUID teamId;

    public EcRightsMenu(GuiContext ctx, UUID teamId) {
        super(ctx);
        this.teams = ctx.teams();
        this.teamId = teamId;
    }

    @Override
    protected Component title() {
        return Items.text("Team-EC Access").color(NamedTextColor.DARK_AQUA);
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
        // Header: column legend.
        for (int c = 0; c < COLS.size(); c++) {
            set(2 + c, Items.of(Items.material(COLS.get(c).icon(), Material.PAPER), COLS.get(c).displayName()));
        }

        List<TeamRole> roles = team.roles().stream()
                .filter(r -> !r.isOwnerRole())
                .sorted(Comparator.comparingInt(TeamRole::rank).reversed())
                .toList();

        int row = 1;
        for (TeamRole role : roles) {
            if (row >= 5) {
                break;
            }
            int base = row * 9;
            set(base, Items.of(Material.COMMAND_BLOCK, role.name(), "Rank: " + role.rank()));
            for (int c = 0; c < COLS.size(); c++) {
                Permission perm = COLS.get(c);
                boolean granted = role.has(perm);
                set(base + 2 + c, Items.of(granted ? Material.LIME_DYE : Material.GRAY_DYE,
                                (granted ? "✔ " : "✘ ") + perm.displayName(),
                                "Role: " + role.name(),
                                granted ? "Click to revoke" : "Click to grant"),
                        e -> {
                            try {
                                teams.setRolePermission(team, viewer.getUniqueId(), role.id(), perm, !granted);
                                refresh(viewer);
                            } catch (TeamException ex) {
                                viewer.sendMessage(Msg.error(ex.getMessage()));
                            }
                        });
            }
            row++;
        }

        set(49, backButton(), e -> openLater(viewer, new MainMenu(ctx)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }
}
