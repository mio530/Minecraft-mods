package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.model.Member;
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

/** Picks a (non-owner) role to assign to a member; escalation is enforced by the service. */
public final class RolePickerMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;
    private final UUID teamId;
    private final UUID target;

    public RolePickerMenu(GuiContext ctx, UUID teamId, UUID target) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
        this.teamId = teamId;
        this.target = target;
    }

    @Override
    protected Component title() {
        return Items.text("Assign Role").color(NamedTextColor.DARK_AQUA);
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
        Member m = team.member(target).orElse(null);
        if (m == null) {
            openLater(viewer, new MembersMenu(ctx, teamId));
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
            boolean current = m.roleId().equals(role.id());
            set(slot, Items.of(current ? Material.LIME_DYE : Material.GRAY_DYE,
                            role.name(), "Rank: " + role.rank(), current ? "Current role" : "Click to assign"),
                    e -> run(viewer, () -> teams.assignRole(team, viewer.getUniqueId(), m.lastKnownName(), role.id()),
                            m.lastKnownName() + " is now " + role.name() + "."));
            slot++;
        }
        set(22, backButton(), e -> openLater(viewer, new MemberActionMenu(ctx, teamId, target)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void run(Player viewer, Runnable action, String success) {
        try {
            action.run();
            viewer.sendMessage(Msg.success(success));
            refresh(viewer);
        } catch (TeamException ex) {
            viewer.sendMessage(Msg.error(ex.getMessage()));
        }
    }
}
