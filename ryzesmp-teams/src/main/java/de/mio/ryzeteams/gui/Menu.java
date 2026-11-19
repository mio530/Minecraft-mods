package de.mio.ryzeteams.gui;

import de.mio.ryzeteams.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal chest-GUI base. All clicks are cancelled by {@link MenuListener}; a
 * slot only does something if a handler was registered for it. Opening another
 * menu is always deferred to the next tick (never open/close inside a click).
 */
public abstract class Menu implements InventoryHolder {

    protected final GuiContext ctx;
    protected final Plugin plugin;
    protected Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(GuiContext ctx) {
        this.ctx = ctx;
        this.plugin = ctx.plugin();
    }

    protected abstract Component title();

    protected abstract int size();

    protected abstract void build(Player viewer);

    public void open(Player viewer) {
        this.inventory = Bukkit.createInventory(this, size(), title());
        actions.clear();
        build(viewer);
        viewer.openInventory(inventory);
    }

    /** Rebuilds contents in place (no reopen) — safe to call from a click handler. */
    public void refresh(Player viewer) {
        actions.clear();
        inventory.clear();
        build(viewer);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void fillEmpty(ItemStack filler) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    protected void openLater(Player viewer, Menu next) {
        Bukkit.getScheduler().runTask(plugin, () -> next.open(viewer));
    }

    protected void closeLater(Player viewer) {
        Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory());
    }

    void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        Consumer<InventoryClickEvent> action = actions.get(e.getRawSlot());
        if (action != null && e.getWhoClicked() instanceof Player) {
            action.accept(e);
        }
    }

    protected ItemStack backButton() {
        return Items.of(org.bukkit.Material.ARROW, "Back");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
