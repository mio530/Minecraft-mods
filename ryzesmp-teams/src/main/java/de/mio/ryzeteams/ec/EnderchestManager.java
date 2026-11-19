package de.mio.ryzeteams.ec;

import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.service.EnderchestHook;
import de.mio.ryzeteams.service.TeamManager;
import de.mio.ryzeteams.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every team's {@link TeamEnderchest}, builds the paged GUI, loads/saves
 * contents and enforces live permissions together with {@link EnderchestListener}.
 *
 * <p>Crash safety: whenever the EC is persisted, the player data of everyone who
 * changed it since the last save is flushed on the main thread first, so the
 * item can never exist both in the EC and in a player's {@code .dat} (dupe) or
 * in neither (loss) after a crash. The only remaining window is the async write
 * latency of the final SQLite statement.
 */
public final class EnderchestManager implements EnderchestHook {

    public static final int PREV_SLOT = 45;
    public static final int CLOSE_SLOT = 48;
    public static final int INFO_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private final Plugin plugin;
    private final TeamManager teams;
    private final Map<UUID, TeamEnderchest> cache = new ConcurrentHashMap<>();

    public EnderchestManager(Plugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    // ---- opening ------------------------------------------------------------

    /** Opens page {@code page} of the given team's EC for a player (main thread). */
    public void open(Player player, Team team, int page) {
        final int fpage = (page < 0 || page >= TeamEnderchest.PAGES) ? 0 : page;
        TeamEnderchest cached = cache.get(team.id());
        if (cached != null) {
            buildAndOpen(player, cached, fpage);
            return;
        }
        final UUID teamId = team.id();
        // Load contents off the main thread, then build + open on the main thread.
        teams.submitIo(() -> {
            Map<Integer, byte[]> stored = teams.repository().loadEnderchest(teamId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                TeamEnderchest ec = cache.computeIfAbsent(teamId, id -> {
                    TeamEnderchest fresh = new TeamEnderchest(id);
                    deserializeInto(fresh, stored);
                    return fresh;
                });
                if (player.isOnline() && !ec.isDiscarded()) {
                    buildAndOpen(player, ec, fpage);
                }
            });
        });
    }

    private void deserializeInto(TeamEnderchest ec, Map<Integer, byte[]> stored) {
        for (Map.Entry<Integer, byte[]> e : stored.entrySet()) {
            int slot = e.getKey();
            if (slot >= 0 && slot < TeamEnderchest.TOTAL_SLOTS && e.getValue() != null) {
                try {
                    ec.slots()[slot] = ItemStack.deserializeBytes(e.getValue());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Dropping corrupt EC item for team "
                            + ec.teamId() + " slot " + slot + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Synchronous open that loads from storage on the calling thread. Only for
     * integration tests, where deterministic (non-async) opening is required.
     */
    public void openNowForTest(Player player, Team team, int page) {
        TeamEnderchest ec = cache.computeIfAbsent(team.id(), id -> {
            TeamEnderchest fresh = new TeamEnderchest(id);
            deserializeInto(fresh, teams.repository().loadEnderchest(id));
            return fresh;
        });
        buildAndOpen(player, ec, page);
    }

    private void buildAndOpen(Player player, TeamEnderchest ec, int page) {
        Inventory inv = ec.page(page);
        if (inv == null) {
            inv = buildPage(ec, page);
            ec.setPage(page, inv);
        }
        player.openInventory(inv);
    }

    private Inventory buildPage(TeamEnderchest ec, int page) {
        EnderchestHolder holder = new EnderchestHolder(ec, page);
        Component title = Items.text("Team Enderchest  (" + (page + 1) + "/" + TeamEnderchest.PAGES + ")")
                .color(NamedTextColor.DARK_PURPLE);
        Inventory inv = Bukkit.createInventory(holder, TeamEnderchest.PAGE_SIZE, title);
        holder.setInventory(inv);

        int storage = TeamEnderchest.storageCount(page);
        int offset = TeamEnderchest.pageOffset(page);
        for (int i = 0; i < storage; i++) {
            inv.setItem(i, ec.slots()[offset + i]);
        }
        decorateNav(inv, page, storage);
        return inv;
    }

    private void decorateNav(Inventory inv, int page, int storageCount) {
        ItemStack filler = Items.of(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = storageCount; i < TeamEnderchest.PAGE_SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(INFO_SLOT, Items.of(Material.PAPER,
                "Page " + (page + 1) + " / " + TeamEnderchest.PAGES,
                "Slots " + (TeamEnderchest.pageOffset(page) + 1) + "-"
                        + (TeamEnderchest.pageOffset(page) + storageCount)));
        inv.setItem(CLOSE_SLOT, Items.of(Material.BARRIER, "Close"));
        if (page > 0) {
            inv.setItem(PREV_SLOT, Items.of(Material.ARROW, "Previous Page"));
        }
        if (page < TeamEnderchest.PAGES - 1) {
            inv.setItem(NEXT_SLOT, Items.of(Material.ARROW, "Next Page"));
        }
    }

    // ---- syncing / saving ---------------------------------------------------

    /** Copies a live page inventory's storage slots back into the canonical array. */
    void syncPage(TeamEnderchest ec, int page) {
        Inventory inv = ec.page(page);
        if (inv == null) {
            return;
        }
        int storage = TeamEnderchest.storageCount(page);
        int offset = TeamEnderchest.pageOffset(page);
        for (int i = 0; i < storage; i++) {
            ItemStack item = inv.getItem(i);
            ec.slots()[offset + i] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
    }

    void syncAllPages(TeamEnderchest ec) {
        for (int p = 0; p < TeamEnderchest.PAGES; p++) {
            syncPage(ec, p);
        }
    }

    /**
     * Records the modifying player, marks dirty and schedules a debounced save.
     * Runs next tick so Paper has already applied an allowed vanilla action.
     */
    void afterChange(TeamEnderchest ec, int page, UUID modifier) {
        ec.addModifier(modifier);
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncPage(ec, page);
            markChanged(ec);
        });
    }

    private void markChanged(TeamEnderchest ec) {
        if (ec.isDiscarded()) {
            return;
        }
        ec.markDirty();
        if (ec.isSaveScheduled()) {
            return;
        }
        ec.setSaveScheduled(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ec.setSaveScheduled(false);
            persist(ec, null);
        }, 40L);
    }

    /**
     * Flushes the EC to storage together with the player data of everyone who
     * touched it, so the two can never disagree after a crash.
     */
    private void persist(TeamEnderchest ec, UUID alsoFlush) {
        if (ec.isDiscarded()) {
            return;
        }
        // Never persist while a viewer carries an item on the cursor: that item is
        // in neither the EC nor the player's .dat, so saving now could lose it on a
        // crash. Retry shortly (only while the plugin is enabled).
        if (plugin.isEnabled() && hasViewerWithCursor(ec)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> persist(ec, alsoFlush), 20L);
            return;
        }
        syncAllPages(ec);
        Set<UUID> modifiers = ec.drainModifiers();
        for (UUID uuid : modifiers) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null && pl.isOnline()) {
                pl.saveData();
            }
        }
        if (alsoFlush != null) {
            Player pl = Bukkit.getPlayer(alsoFlush);
            if (pl != null && pl.isOnline()) {
                pl.saveData();
            }
        }
        ec.clearDirty();
        Map<Integer, byte[]> snapshot = serialize(ec);
        UUID teamId = ec.teamId();
        teams.submitIo(() -> teams.repository().saveEnderchest(teamId, snapshot));
    }

    private Map<Integer, byte[]> serialize(TeamEnderchest ec) {
        Map<Integer, byte[]> map = new HashMap<>();
        ItemStack[] slots = ec.slots();
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = slots[i];
            if (s != null && !s.getType().isAir()) {
                map.put(i, s.serializeAsBytes());
            }
        }
        return map;
    }

    // ---- lifecycle events ---------------------------------------------------

    void onClose(TeamEnderchest ec, int page, UUID closingPlayer) {
        if (ec.isDiscarded()) {
            return;
        }
        syncPage(ec, page);
        if (plugin.isEnabled()) {
            // Defer one tick so the carried cursor item is returned to the player
            // (and captured by saveData) before we persist.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!ec.isDiscarded()) {
                    persist(ec, closingPlayer);
                }
            }, 1L);
        }
        // During disable, flushAll() performs the final synchronous save.
    }

    private boolean hasViewerWithCursor(TeamEnderchest ec) {
        for (int p = 0; p < TeamEnderchest.PAGES; p++) {
            Inventory inv = ec.page(p);
            if (inv == null) {
                continue;
            }
            for (HumanEntity viewer : inv.getViewers()) {
                ItemStack cursor = viewer.getItemOnCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Force-closes every open view of a team's EC (kick/leave/role change). */
    public void closeForTeam(UUID teamId) {
        TeamEnderchest ec = cache.get(teamId);
        if (ec == null) {
            return;
        }
        closeViews(ec);
    }

    private void closeViews(TeamEnderchest ec) {
        for (int p = 0; p < TeamEnderchest.PAGES; p++) {
            Inventory inv = ec.page(p);
            if (inv != null) {
                for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                    viewer.closeInventory();
                }
            }
        }
    }

    /** Force-closes this player's EC view if they have one open (resolves online player). */
    public void closeForPlayer(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) {
            return;
        }
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() instanceof EnderchestHolder) {
            p.closeInventory();
        }
    }

    /** Disband path: close views, stop any save and forget the EC (team is being deleted). */
    public void discard(UUID teamId) {
        TeamEnderchest ec = cache.remove(teamId);
        if (ec == null) {
            return;
        }
        ec.markDiscarded();
        closeViews(ec);
    }

    public boolean canOpen(Team team, UUID player) {
        return TeamPolicy.has(team, player, Permission.EC_OPEN);
    }

    /** Synchronous flush of all live enderchests. Called on disable via submitIo ordering. */
    public void flushAll() {
        for (TeamEnderchest ec : cache.values()) {
            if (ec.isDiscarded()) {
                continue;
            }
            syncAllPages(ec);
            Map<Integer, byte[]> snapshot = serialize(ec);
            UUID teamId = ec.teamId();
            teams.submitIo(() -> teams.repository().saveEnderchest(teamId, snapshot));
        }
    }

    /** Periodic safety-net save of any dirty enderchests (called on a timer). */
    public void autosave() {
        for (TeamEnderchest ec : cache.values()) {
            if (!ec.isDiscarded() && ec.isDirty()) {
                persist(ec, null);
            }
        }
    }

    /** Closes all open EC views (used on disable before flushing). */
    public void closeAllViews() {
        for (TeamEnderchest ec : cache.values()) {
            closeViews(ec);
        }
    }

    // ---- deferred UI actions (never open/close inside an inventory event) ----

    void scheduleClose(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    void scheduleSwitch(Player player, Team team, int page) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            returnCursor(player);
            open(player, team, page);
        });
    }

    private void returnCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            player.setItemOnCursor(null);
            for (ItemStack leftover : player.getInventory().addItem(cursor).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    Plugin plugin() {
        return plugin;
    }
}
