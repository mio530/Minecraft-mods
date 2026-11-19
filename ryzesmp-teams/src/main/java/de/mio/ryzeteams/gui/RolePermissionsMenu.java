package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
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

import java.util.UUID;

/** Toggles each permission for a role. Also serves as the team-EC access editor. */
public final class RolePermissionsMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;
    private final UUID teamId;
    private final String roleId;

    public RolePermissionsMenu(GuiContext ctx, UUID teamId, String roleId) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
        this.teamId = teamId;
        this.roleId = roleId;
    }

    @Override
    protected Component title() {
        return Items.text("Role Permissions").color(NamedTextColor.DARK_AQUA);
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        Team team = teams.teamById(teamId).orElse(null);
        if (team == null) {
            closeLater(viewer);
            return;
        }
        TeamRole role = team.role(roleId).orElse(null);
        if (role == null) {
            openLater(viewer, new RolesMenu(ctx, teamId));
            return;
        }
        set(4, Items.of(Material.NAME_TAG, role.name(), "Rank: " + role.rank()));
        if (!role.isOwnerRole()) {
            set(2, Items.of(Material.RED_DYE, "Lower rank (-50)", "Current: " + role.rank()),
                    e -> changeRank(viewer, role.rank() - 50));
            set(6, Items.of(Material.LIME_DYE, "Raise rank (+50)", "Current: " + role.rank()),
                    e -> changeRank(viewer, role.rank() + 50));
        }

        int slot = 18;
        for (Permission perm : Permission.values()) {
            boolean granted = role.has(perm);
            boolean locked = role.isOwnerRole();
            set(slot, Items.of(
                            granted ? Material.LIME_DYE : Material.GRAY_DYE,
                            (granted ? "✔ " : "✘ ") + perm.displayName(),
                            perm.description(),
                            perm.category() == Permission.Category.ENDERCHEST ? "Category: Enderchest" : "Category: Team",
                            locked ? "Owner role (locked)" : (granted ? "Click to revoke" : "Click to grant")),
                    e -> {
                        if (locked) {
                            return;
                        }
                        try {
                            teams.setRolePermission(team, viewer.getUniqueId(), roleId, perm, !granted);
                            refresh(viewer);
                        } catch (TeamException ex) {
                            viewer.sendMessage(Msg.error(ex.getMessage()));
                        }
                    });
            slot++;
            if (slot % 9 == 8) {
                slot++; // leave edge column tidy
            }
        }
        set(49, backButton(), e -> openLater(viewer, new RolesMenu(ctx, teamId)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void changeRank(Player viewer, int newRank) {
        Team team = teams.teamById(teamId).orElse(null);
        if (team == null) {
            return;
        }
        try {
            teams.setRoleRank(team, viewer.getUniqueId(), roleId, newRank);
            refresh(viewer);
        } catch (TeamException ex) {
            viewer.sendMessage(Msg.error(ex.getMessage()));
        }
    }
}
