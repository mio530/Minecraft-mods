package de.mio.ryzeteams.ec;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state for one team's shared enderchest — a single double-chest
 * (54 slots) shown all at once. The canonical contents are the {@code slots}
 * array; the one {@link Inventory} object is built lazily and shared by every
 * viewer (so Bukkit itself keeps concurrent viewers in sync, exactly like a
 * vanilla chest — no per-viewer copies, hence no dupes).
 */
public final class TeamEnderchest {

    /** Double-chest size: 6 rows x 9 columns, all usable storage. */
    public static final int SIZE = 54;

    private final UUID teamId;
    private final ItemStack[] slots = new ItemStack[SIZE];
    private Inventory inventory;
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

    public Inventory inventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
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
