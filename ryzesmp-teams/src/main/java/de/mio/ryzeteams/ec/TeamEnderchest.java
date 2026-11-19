package de.mio.ryzeteams.ec;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state for one team's 81-slot enderchest. The canonical contents are
 * the {@code slots} array; the two 54-slot page {@link Inventory} objects are
 * built lazily and shared by every viewer of that page (so Bukkit itself keeps
 * concurrent viewers in sync, exactly like a vanilla chest — no per-viewer
 * copies, hence no dupes).
 */
public final class TeamEnderchest {

    public static final int PAGE_SIZE = 54;
    public static final int TOTAL_SLOTS = 81;
    public static final int PAGE1_STORAGE = 45;
    public static final int PAGE2_STORAGE = 36;
    public static final int PAGES = 2;

    private final UUID teamId;
    private final ItemStack[] slots = new ItemStack[TOTAL_SLOTS];
    private final Inventory[] pages = new Inventory[PAGES];
    // Players who changed the contents since the last persist. Their player data
    // is flushed together with the EC so a crash can never desync the two.
    private final Set<UUID> modifiers = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;
    private volatile boolean saveScheduled;
    private volatile boolean discarded;

    public TeamEnderchest(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID teamId() {
        return teamId;
    }

    public ItemStack[] slots() {
        return slots;
    }

    public Inventory page(int index) {
        return pages[index];
    }

    public void setPage(int index, Inventory inv) {
        pages[index] = index == 0 || index == 1 ? inv : null;
    }

    public static int storageCount(int page) {
        return page == 0 ? PAGE1_STORAGE : PAGE2_STORAGE;
    }

    public static int pageOffset(int page) {
        return page == 0 ? 0 : PAGE1_STORAGE;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean isSaveScheduled() {
        return saveScheduled;
    }

    public void setSaveScheduled(boolean scheduled) {
        this.saveScheduled = scheduled;
    }

    public void addModifier(UUID uuid) {
        modifiers.add(uuid);
    }

    public Set<UUID> drainModifiers() {
        Set<UUID> copy = Set.copyOf(modifiers);
        modifiers.clear();
        return copy;
    }

    public boolean isDiscarded() {
        return discarded;
    }

    public void markDiscarded() {
        this.discarded = true;
    }
}
