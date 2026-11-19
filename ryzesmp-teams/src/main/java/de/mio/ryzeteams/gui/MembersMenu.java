package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.model.Member;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Lists team members (highest rank first) and opens per-member actions. */
public final class MembersMenu extends Menu {

    private final TeamManager teams;
    private final EnderchestManager ec;
    private final UUID teamId;

    public MembersMenu(GuiContext ctx, UUID teamId) {
        super(ctx);
        this.teams = ctx.teams();
        this.ec = ctx.ec();
        this.teamId = teamId;
    }

    @Override
    protected Component title() {
        return Items.text("Members").color(NamedTextColor.DARK_AQUA);
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
        List<Member> members = team.members().stream()
                .sorted(Comparator.comparingInt((Member m) -> team.roleOf(m.uuid()).rank()).reversed())
                .toList();
        int slot = 0;
        for (Member m : members) {
            if (slot >= 45) {
                break;
            }
            boolean canManage = TeamPolicy.canManage(team, viewer.getUniqueId(), m.uuid());
            String role = team.roleOf(m.uuid()).name();
            set(slot, Items.head(m.uuid(), m.lastKnownName(),
                            "Role: " + role,
                            team.isOwner(m.uuid()) ? "Owner" : "Rank: " + team.roleOf(m.uuid()).rank(),
                            canManage ? "Click to manage" : "You cannot manage this member"),
                    e -> {
                        if (canManage) {
                            openLater(viewer, new MemberActionMenu(ctx, teamId, m.uuid()));
                        }
                    });
            slot++;
        }
        set(49, backButton(), e -> openLater(viewer, new MainMenu(ctx)));
        fillEmpty(Items.of(Material.GRAY_STAINED_GLASS_PANE, " "));
    }
}
