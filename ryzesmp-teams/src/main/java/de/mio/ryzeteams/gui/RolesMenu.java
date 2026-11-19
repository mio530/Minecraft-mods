package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
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
import java.util.EnumSet;
import java.util.UUID;

/** Lists roles and opens the permission editor for each. */
public final class RolesMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;
    private final UUID teamId;

    public RolesMenu(GuiContext ctx, UUID teamId) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
        this.teamId = teamId;
    }

    @Override
    protected Component title() {
        return Items.text("Roles").color(NamedTextColor.DARK_AQUA);
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
        int slot = 0;
        for (TeamRole role : team.roles().stream()
                .sorted(Comparator.comparingInt(TeamRole::rank).reversed())
                .toList()) {
            if (slot >= 45) {
                break;
            }
            boolean isDefault = role.id().equals(team.defaultRoleId());
            boolean deletable = !role.isOwnerRole() && !isDefault;
            set(slot, Items.of(role.isOwnerRole() ? Material.NETHER_STAR : Material.COMMAND_BLOCK,
                            role.name(),
                            "Rank: " + role.rank(),
                            "Permissions: " + role.permissions().size(),
                            isDefault ? "Default join role" : "",
                            role.isOwnerRole() ? "Owner role (locked)" : "Left-click: edit permissions",
                            deletable ? "Shift-click: delete role" : ""),
                    e -> {
                        if (e.isShiftClick() && deletable) {
                            try {
                                teams.deleteRole(team, viewer.getUniqueId(), role.id());
                                viewer.sendMessage(Msg.success("Deleted role '" + role.name() + "'."));
                                refresh(viewer);
                            } catch (TeamException ex) {
                                viewer.sendMessage(Msg.error(ex.getMessage()));
                            }
                        } else {
                            openLater(viewer, new RolePermissionsMenu(ctx, teamId, role.id()));
                        }
                    });
            slot++;
        }
        set(48, Items.of(Material.WRITABLE_BOOK, "Create role", "Click, then type a role name in chat."),
                e -> {
                    closeLater(viewer);
                    ctx.prompts().await(viewer, name -> createRole(viewer, name));
                });
        set(49, backButton(), e -> openLater(viewer, new MainMenu(ctx)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void createRole(Player viewer, String name) {
        Team team = teams.teamById(teamId).orElse(null);
        if (team == null) {
            return;
        }
        try {
            TeamRole role = teams.createRole(team, viewer.getUniqueId(), name,
                    TeamRole.MEMBER_RANK, EnumSet.noneOf(de.mio.ryzeteams.model.Permission.class));
            viewer.sendMessage(Msg.success("Created role '" + role.name() + "'. Configure its permissions now."));
            new RolePermissionsMenu(ctx, teamId, role.id()).open(viewer);
        } catch (TeamException ex) {
            viewer.sendMessage(Msg.error(ex.getMessage()));
        }
    }
}
