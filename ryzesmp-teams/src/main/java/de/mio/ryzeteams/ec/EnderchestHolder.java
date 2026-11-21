package de.mio.ryzeteams.ec;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder that lets the listener recognise a team-enderchest inventory and
 * recover its {@link TeamEnderchest}. The inventory reference is set right after
 * creation.
 */
public final class EnderchestHolder implements InventoryHolder {

    private final TeamEnderchest enderchest;
    private Inventory inventory;

    public EnderchestHolder(TeamEnderchest enderchest) {
        this.enderchest = enderchest;
    }

    public TeamEnderchest enderchest() {
        return enderchest;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
