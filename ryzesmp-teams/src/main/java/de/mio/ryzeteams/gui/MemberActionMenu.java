package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.model.Member;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import de.mio.ryzeteams.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Actions for a single member: assign role, kick, transfer ownership. */
public final class MemberActionMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;
    private final UUID teamId;
    private final UUID target;

    public MemberActionMenu(GuiContext ctx, UUID teamId, UUID target) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
        this.teamId = teamId;
        this.target = target;
    }

    @Override
    protected Component title() {
        return Items.text("Manage Member").color(NamedTextColor.DARK_AQUA);
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
        set(4, Items.head(target, m.lastKnownName(), "Role: " + team.roleOf(target).name()));

        set(11, Items.of(Material.NAME_TAG, "Assign Role", "Choose a role for this member."),
                e -> openLater(viewer, new RolePickerMenu(ctx, teamId, target)));

        set(13, Items.of(Material.BARRIER, "Kick", "Remove this member from the team."),
                e -> run(viewer, () -> teams.kick(team, viewer.getUniqueId(), m.lastKnownName()),
                        m.lastKnownName() + " was kicked.",
                        () -> openLater(viewer, new MembersMenu(ctx, teamId))));

        if (team.isOwner(viewer.getUniqueId())) {
            set(15, Items.of(Material.NETHER_STAR, "Transfer Ownership",
                            "Make " + m.lastKnownName() + " the new owner."),
                    e -> run(viewer, () -> teams.transfer(team, viewer.getUniqueId(), m.lastKnownName()),
                            m.lastKnownName() + " is now the owner.",
                            () -> closeLater(viewer)));
        }

        set(22, backButton(), e -> openLater(viewer, new MembersMenu(ctx, teamId)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void run(Player viewer, Runnable action, String success, Runnable then) {
        try {
            action.run();
            viewer.sendMessage(Msg.success(success));
            then.run();
        } catch (TeamException ex) {
            viewer.sendMessage(Msg.error(ex.getMessage()));
        }
    }
}
