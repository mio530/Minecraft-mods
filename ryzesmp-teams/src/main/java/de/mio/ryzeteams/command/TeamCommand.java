package de.mio.ryzeteams.command;

import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.gui.AlliancesMenu;
import de.mio.ryzeteams.gui.GuiContext;
import de.mio.ryzeteams.gui.MainMenu;
import de.mio.ryzeteams.gui.MembersMenu;
import de.mio.ryzeteams.gui.RolePermissionsMenu;
import de.mio.ryzeteams.gui.RolesMenu;
import de.mio.ryzeteams.model.Member;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamException;
import de.mio.ryzeteams.model.TeamHome;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.model.TeamRole;
import de.mio.ryzeteams.service.PluginConfig;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Full {@code /team} (alias {@code /t}) command tree. Every mutating action is
 * delegated to {@link TeamManager}, which performs the server-side validation
 * and throws {@link TeamException} with a player-facing message.
 */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "create", "invite", "accept", "deny", "leave", "kick", "info", "list",
            "members", "disband", "home", "sethome", "reload", "role", "permissions",
            "ec", "ecs", "ally", "allies", "transfer", "help");

    private final JavaPlugin plugin;
    private final TeamManager teams;
    private final EnderchestManager ec;
    private final GuiContext gui;

    public TeamCommand(GuiContext gui) {
        this.gui = gui;
        this.plugin = gui.plugin();
        this.teams = gui.teams();
        this.ec = gui.ec();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.error("Only players can use team commands."));
            return true;
        }
        try {
            dispatch(player, args);
        } catch (TeamException ex) {
            player.sendMessage(Msg.error(ex.getMessage()));
        }
        return true;
    }

    private void dispatch(Player p, String[] args) {
        if (args.length == 0) {
            if (teams.teamOf(p.getUniqueId()).isPresent()) {
                new MainMenu(gui).open(p);
            } else {
                help(p);
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(p, args);
            case "invite" -> invite(p, args);
            case "accept" -> accept(p, args);
            case "deny" -> deny(p, args);
            case "leave" -> leave(p);
            case "kick" -> kick(p, args);
            case "info" -> info(p, args);
            case "list" -> list(p);
            case "members" -> new MembersMenu(gui, require(p).id()).open(p);
            case "disband" -> disband(p, args);
            case "home" -> home(p);
            case "sethome" -> sethome(p);
            case "role" -> role(p, args);
            case "permissions", "perms" -> permissions(p, args);
            case "ec", "ecs" -> openEc(p);
            case "ally" -> ally(p, args);
            case "allies" -> new AlliancesMenu(gui, require(p).id()).open(p);
            case "transfer" -> transfer(p, args);
            case "help" -> help(p);
            default -> help(p);
        }
    }

    // ---- subcommands --------------------------------------------------------

    private void create(Player p, String[] args) {
        if (args.length < 2) {
            throw new TeamException("Usage: /team create <name>");
        }
        Team team = teams.createTeam(p.getUniqueId(), p.getName(), args[1]);
        p.sendMessage(Msg.success("Team '" + team.name() + "' created. You are the owner."));
    }

    private void invite(Player p, String[] args) {
        Team team = require(p);
        if (args.length < 2) {
            throw new TeamException("Usage: /team invite <player>");
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            throw new TeamException("That player must be online to be invited.");
        }
        teams.invite(team, p.getUniqueId(), target.getUniqueId(), target.getName());
        p.sendMessage(Msg.success("Invited " + target.getName() + " to the team."));
        target.sendMessage(Msg.info(p.getName() + " invited you to team '" + team.name()
                + "'. Use /team accept " + team.name()));
    }

    private void accept(Player p, String[] args) {
        String teamName = args.length >= 2 ? args[1] : null;
        Team team = teams.acceptInvite(p.getUniqueId(), p.getName(), teamName);
        p.sendMessage(Msg.success("You joined team '" + team.name() + "'."));
        notifyTeam(team, Msg.info(p.getName() + " joined the team."), p.getUniqueId());
    }

    private void deny(Player p, String[] args) {
        teams.denyInvite(p.getUniqueId(), args.length >= 2 ? args[1] : null);
        p.sendMessage(Msg.info("Invite declined."));
    }

    private void leave(Player p) {
        Team team = teams.leave(p.getUniqueId());
        p.sendMessage(Msg.info("You left team '" + team.name() + "'."));
        notifyTeam(team, Msg.info(p.getName() + " left the team."), p.getUniqueId());
    }

    private void kick(Player p, String[] args) {
        Team team = require(p);
        if (args.length < 2) {
            throw new TeamException("Usage: /team kick <player>");
        }
        Member kicked = teams.kick(team, p.getUniqueId(), args[1]);
        p.sendMessage(Msg.success("Kicked " + kicked.lastKnownName() + " from the team."));
        Player online = plugin.getServer().getPlayer(kicked.uuid());
        if (online != null) {
            online.sendMessage(Msg.error("You were kicked from team '" + team.name() + "'."));
        }
    }

    private void info(Player p, String[] args) {
        Team team = args.length >= 2
                ? teams.teamByName(args[1]).orElseThrow(() -> new TeamException("Unknown team."))
                : require(p);
        p.sendMessage(Msg.accent("=== Team " + team.name() + " ==="));
        p.sendMessage(Msg.plain("Owner: " + ownerName(team)));
        p.sendMessage(Msg.plain("Members: " + team.size()));
        p.sendMessage(Msg.plain("Roles: " + team.roles().size()));
        p.sendMessage(Msg.plain("Allies: " + alliesNames(team)));
        team.member(p.getUniqueId()).ifPresent(m ->
                p.sendMessage(Msg.plain("Your role: " + team.roleOf(p.getUniqueId()).name())));
    }

    private void list(Player p) {
        List<Team> all = new ArrayList<>(teams.allTeams());
        if (all.isEmpty()) {
            p.sendMessage(Msg.info("There are no teams yet."));
            return;
        }
        p.sendMessage(Msg.accent("Teams (" + all.size() + "):"));
        all.stream().limit(60).forEach(t ->
                p.sendMessage(Msg.plain("- " + t.name() + " (" + t.size() + " members)")));
    }

    private void disband(Player p, String[] args) {
        Team team = require(p);
        if (!team.isOwner(p.getUniqueId())) {
            throw new TeamException("Only the owner can disband the team.");
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            p.sendMessage(Msg.error("This permanently deletes the team and its enderchest."));
            p.sendMessage(Msg.error("Type /team disband confirm to proceed."));
            return;
        }
        notifyTeam(team, Msg.error("Team '" + team.name() + "' was disbanded."), null);
        teams.disband(team, p.getUniqueId());
        p.sendMessage(Msg.success("Team disbanded."));
    }

    private void home(Player p) {
        Team team = require(p);
        TeamHome h = team.home().orElseThrow(() -> new TeamException("Your team has no home set."));
        World world = plugin.getServer().getWorld(h.world());
        if (world == null) {
            throw new TeamException("The team home world is not loaded.");
        }
        p.teleportAsync(new Location(world, h.x(), h.y(), h.z(), h.yaw(), h.pitch()));
        p.sendMessage(Msg.success("Teleporting to team home..."));
    }

    private void sethome(Player p) {
        Team team = require(p);
        Location l = p.getLocation();
        teams.setHome(team, p.getUniqueId(),
                new TeamHome(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        p.sendMessage(Msg.success("Team home set to your location."));
    }

    private void role(Player p, String[] args) {
        Team team = require(p);
        if (args.length < 2) {
            throw new TeamException("Usage: /team role <list|create|delete|assign|default>");
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                p.sendMessage(Msg.accent("Roles:"));
                team.roles().forEach(r -> p.sendMessage(Msg.plain("- " + r.name()
                        + " (id " + r.id() + ", rank " + r.rank() + ", " + r.permissions().size() + " perms)")));
            }
            case "create" -> {
                if (args.length < 3) {
                    throw new TeamException("Usage: /team role create <name> [rank]");
                }
                int rank = args.length >= 4 ? parseInt(args[3], TeamRole.MEMBER_RANK) : TeamRole.MEMBER_RANK;
                TeamRole r = teams.createRole(team, p.getUniqueId(), args[2], rank, java.util.EnumSet.noneOf(Permission.class));
                p.sendMessage(Msg.success("Created role '" + r.name() + "'. Edit its permissions in /team permissions."));
            }
            case "delete" -> {
                if (args.length < 3) {
                    throw new TeamException("Usage: /team role delete <name>");
                }
                teams.deleteRole(team, p.getUniqueId(), TeamRole.normalizeId(args[2]));
                p.sendMessage(Msg.success("Role deleted. Its members moved to the default role."));
            }
            case "assign" -> {
                if (args.length < 4) {
                    throw new TeamException("Usage: /team role assign <player> <role>");
                }
                teams.assignRole(team, p.getUniqueId(), args[2], TeamRole.normalizeId(args[3]));
                p.sendMessage(Msg.success("Assigned " + args[2] + " to role " + args[3] + "."));
            }
            case "default" -> {
                if (args.length < 3) {
                    throw new TeamException("Usage: /team role default <role>");
                }
                teams.setDefaultRole(team, p.getUniqueId(), TeamRole.normalizeId(args[2]));
                p.sendMessage(Msg.success("Default join role set to " + args[2] + "."));
            }
            default -> throw new TeamException("Usage: /team role <list|create|delete|assign|default>");
        }
    }

    private void permissions(Player p, String[] args) {
        Team team = require(p);
        if (args.length >= 2) {
            String roleId = TeamRole.normalizeId(args[1]);
            if (!team.hasRole(roleId)) {
                throw new TeamException("Unknown role.");
            }
            new RolePermissionsMenu(gui, team.id(), roleId).open(p);
        } else {
            new RolesMenu(gui, team.id()).open(p);
        }
    }

    private void openEc(Player p) {
        Team team = require(p);
        if (!TeamPolicy.has(team, p.getUniqueId(), Permission.EC_OPEN)) {
            throw new TeamException("You are not allowed to open the team enderchest.");
        }
        ec.open(p, team, 0);
    }

    private void ally(Player p, String[] args) {
        Team team = require(p);
        if (args.length < 2) {
            throw new TeamException("Usage: /team ally <team|accept|deny|remove> ...");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "accept" -> {
                String name = requireArg(args, 2, "Usage: /team ally accept <team>");
                teams.acceptAlliance(team, p.getUniqueId(), name);
                p.sendMessage(Msg.success("Alliance with '" + name + "' is now active."));
                teams.teamByName(name).ifPresent(other ->
                        notifyTeam(other, Msg.success("Alliance with '" + team.name() + "' is now active."), null));
            }
            case "deny" -> {
                String name = requireArg(args, 2, "Usage: /team ally deny <team>");
                teams.denyAlliance(team, p.getUniqueId(), name);
                p.sendMessage(Msg.info("Denied alliance request from '" + name + "'."));
            }
            case "remove" -> {
                String name = requireArg(args, 2, "Usage: /team ally remove <team>");
                teams.removeAlliance(team, p.getUniqueId(), name);
                p.sendMessage(Msg.info("Alliance with '" + name + "' ended."));
                teams.teamByName(name).ifPresent(other ->
                        notifyTeam(other, Msg.info("Alliance with '" + team.name() + "' ended."), null));
            }
            default -> {
                boolean mutual = teams.requestAlliance(team, p.getUniqueId(), args[1]);
                Team other = teams.teamByName(args[1]).orElseThrow(() -> new TeamException("Unknown team."));
                if (mutual) {
                    p.sendMessage(Msg.success("Alliance with '" + other.name() + "' is now active."));
                    notifyTeam(other, Msg.success("Alliance with '" + team.name() + "' is now active."), null);
                } else {
                    p.sendMessage(Msg.success("Alliance request sent to '" + other.name() + "'."));
                    notifyAllianceManagers(other, team.name());
                }
            }
        }
    }

    private void transfer(Player p, String[] args) {
        Team team = require(p);
        if (args.length < 2) {
            throw new TeamException("Usage: /team transfer <player>");
        }
        teams.transfer(team, p.getUniqueId(), args[1]);
        p.sendMessage(Msg.success("Ownership transferred to " + args[1] + "."));
        notifyTeam(team, Msg.info(args[1] + " is now the team owner."), p.getUniqueId());
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("ryzeteams.admin")) {
            sender.sendMessage(Msg.error("You lack permission: ryzeteams.admin"));
            return;
        }
        plugin.reloadConfig();
        teams.setConfig(PluginConfig.from(plugin.getConfig()));
        sender.sendMessage(Msg.success("RyzeTeams configuration reloaded."));
    }

    // ---- helpers ------------------------------------------------------------

    private Team require(Player p) {
        return teams.teamOf(p.getUniqueId())
                .orElseThrow(() -> new TeamException("You are not in a team. Use /team create <name>."));
    }

    private String requireArg(String[] args, int index, String usage) {
        if (args.length <= index) {
            throw new TeamException(usage);
        }
        return args[index];
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String ownerName(Team team) {
        return team.member(team.owner()).map(Member::lastKnownName).orElse(team.owner().toString());
    }

    private String alliesNames(Team team) {
        if (team.allies().isEmpty()) {
            return "none";
        }
        List<String> names = new ArrayList<>();
        for (UUID id : team.allies()) {
            teams.teamById(id).ifPresent(t -> names.add(t.name()));
        }
        return String.join(", ", names);
    }

    private void notifyTeam(Team team, Component message, UUID except) {
        for (Member m : team.members()) {
            if (except != null && m.uuid().equals(except)) {
                continue;
            }
            Player online = plugin.getServer().getPlayer(m.uuid());
            if (online != null) {
                online.sendMessage(message);
            }
        }
    }

    private void notifyAllianceManagers(Team target, String fromName) {
        Component msg = Msg.info("Team '" + fromName + "' requested an alliance. "
                + "Use /team ally accept " + fromName);
        for (Member m : target.members()) {
            Player online = plugin.getServer().getPlayer(m.uuid());
            if (online != null && TeamPolicy.has(target, m.uuid(), Permission.ACCEPT_ALLIANCES)) {
                online.sendMessage(msg);
            }
        }
    }

    private void help(Player p) {
        p.sendMessage(Msg.accent("RyzeTeams commands (/team or /t):"));
        String[] lines = {
                "/team create <name> — create a team",
                "/team invite <player> | accept [team] | deny [team]",
                "/team leave | kick <player> | transfer <player> | disband confirm",
                "/team info [team] | list | members",
                "/team home | sethome",
                "/team role <list|create|delete|assign|default> ...",
                "/team permissions [role] — edit role/EC permissions (GUI)",
                "/team ec — open the 81-slot team enderchest",
                "/team ally <team|accept|deny|remove> | allies"
        };
        for (String l : lines) {
            p.sendMessage(Msg.plain(l));
        }
    }

    // ---- tab completion -----------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Team team = teams.teamOf(p.getUniqueId()).orElse(null);
        if (args.length == 2) {
            switch (sub) {
                case "invite" -> {
                    return onlinePlayerNames(args[1]);
                }
                case "kick", "transfer" -> {
                    return team == null ? List.of() : filter(memberNames(team), args[1]);
                }
                case "accept", "deny" -> {
                    return filter(inviteTeamNames(p), args[1]);
                }
                case "info", "ally" -> {
                    return filter(allTeamNames(), args[1]);
                }
                case "role" -> {
                    return filter(List.of("list", "create", "delete", "assign", "default"), args[1]);
                }
                case "permissions" -> {
                    return team == null ? List.of() : filter(roleIds(team), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && sub.equals("role")) {
            String op = args[1].toLowerCase(Locale.ROOT);
            if (op.equals("assign")) {
                return team == null ? List.of() : filter(memberNames(team), args[2]);
            }
            if (op.equals("delete") || op.equals("default")) {
                return team == null ? List.of() : filter(roleIds(team), args[2]);
            }
        }
        if (args.length == 4 && sub.equals("role") && args[1].equalsIgnoreCase("assign")) {
            return team == null ? List.of() : filter(roleIds(team), args[3]);
        }
        if (args.length == 3 && sub.equals("ally")) {
            return filter(allTeamNames(), args[2]);
        }
        return List.of();
    }

    private List<String> onlinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        plugin.getServer().getOnlinePlayers().forEach(pl -> names.add(pl.getName()));
        return filter(names, prefix);
    }

    private List<String> memberNames(Team team) {
        List<String> names = new ArrayList<>();
        team.members().forEach(m -> names.add(m.lastKnownName()));
        return names;
    }

    private List<String> roleIds(Team team) {
        List<String> ids = new ArrayList<>();
        team.roles().forEach(r -> ids.add(r.id()));
        return ids;
    }

    private List<String> allTeamNames() {
        List<String> names = new ArrayList<>();
        teams.allTeams().forEach(t -> names.add(t.name()));
        return names;
    }

    private List<String> inviteTeamNames(Player p) {
        List<String> names = new ArrayList<>();
        teams.pendingInvites(p.getUniqueId()).forEach(i ->
                teams.teamById(i.teamId()).ifPresent(t -> names.add(t.name())));
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(o);
            }
        }
        return out;
    }

    @SuppressWarnings("unused")
    private Component dummy() {
        return Component.text("").color(NamedTextColor.WHITE);
    }
}
