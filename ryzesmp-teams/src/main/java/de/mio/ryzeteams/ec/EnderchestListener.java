package de.mio.ryzeteams.ec;

import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamPolicy;
import de.mio.ryzeteams.service.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Enforces the team enderchest's dupe protection and per-role deposit/withdraw
 * permissions on the single 54-slot shared inventory. Normal single-slot actions
 * are left to Paper's inventory engine (and resynced next tick); shift-click from
 * the player inventory is re-implemented so it moves only into real storage, and
 * multi-slot / creative actions that could dupe are cancelled.
 */
public final class EnderchestListener implements Listener {

    private final TeamManager teams;
    private final EnderchestManager ecManager;

    public EnderchestListener(TeamManager teams, EnderchestManager ecManager) {
        this.teams = teams;
        this.ecManager = ecManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof EnderchestHolder h)) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        TeamEnderchest ec = h.enderchest();
        Team team = teams.teamById(ec.teamId()).orElse(null);
        if (team == null || !team.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            ecManager.scheduleClose(p);
            return;
        }
        UUID uuid = p.getUniqueId();
        boolean canWithdraw = TeamPolicy.has(team, uuid, Permission.EC_WITHDRAW);
        boolean canDeposit = TeamPolicy.has(team, uuid, Permission.EC_DEPOSIT);

        Inventory clicked = e.getClickedInventory();
        InventoryAction action = e.getAction();

        // ---- click inside the EC (top inventory, all 54 slots are storage) ----
        if (clicked != null && clicked.equals(top)) {
            switch (EcClickPolicy.classifyTop(action, false, canWithdraw, canDeposit)) {
                case ALLOW -> ecManager.afterChange(ec, uuid);
                case DENY -> deny(e, p, denyVerb(action));
                case CANCEL -> e.setCancelled(true);
                case IGNORE -> {
                    // safe no-op
                }
                default -> e.setCancelled(true);
            }
            return;
        }

        // ---- click inside the player's own inventory (bottom) ----
        if (clicked != null) {
            switch (EcClickPolicy.classifyBottom(action, canDeposit)) {
                case REIMPLEMENT_SHIFT_IN -> {
                    e.setCancelled(true);
                    shiftIntoStorage(e, top);
                    ecManager.afterChange(ec, uuid);
                }
                case DENY -> deny(e, p, "put items into");
                case CANCEL -> e.setCancelled(true);
                default -> {
                    // rearranging one's own inventory is always fine
                }
            }
        }
        // rawSlot == -999 (drop outside) uses the player's cursor only; allow it.
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof EnderchestHolder h)) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        TeamEnderchest ec = h.enderchest();
        Team team = teams.teamById(ec.teamId()).orElse(null);
        if (team == null || !team.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            ecManager.scheduleClose(p);
            return;
        }
        int topSize = top.getSize();
        boolean touchesTop = false;
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                touchesTop = true;
                break;
            }
        }
        if (touchesTop) {
            if (!TeamPolicy.has(team, p.getUniqueId(), Permission.EC_DEPOSIT)) {
                e.setCancelled(true);
                deny(null, p, "put items into");
                return;
            }
            ecManager.afterChange(ec, p.getUniqueId());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EnderchestHolder h) {
            ecManager.onClose(h.enderchest(), e.getPlayer().getUniqueId());
        }
    }

    // ---- helpers ------------------------------------------------------------

    private static String denyVerb(InventoryAction action) {
        return switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME -> "put items into";
            case SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> "swap items in";
            default -> "take items from";
        };
    }

    /** Re-implemented shift-click player->EC across all 54 storage slots. */
    private void shiftIntoStorage(InventoryClickEvent e, Inventory top) {
        ItemStack moving = e.getCurrentItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        ItemStack remaining = moving.clone();
        int max = remaining.getMaxStackSize();
        int size = top.getSize();
        // 1) merge into existing similar stacks
        for (int i = 0; i < size && remaining.getAmount() > 0; i++) {
            ItemStack existing = top.getItem(i);
            if (existing != null && existing.isSimilar(remaining) && existing.getAmount() < max) {
                int add = Math.min(max - existing.getAmount(), remaining.getAmount());
                existing.setAmount(existing.getAmount() + add);
                top.setItem(i, existing);
                remaining.setAmount(remaining.getAmount() - add);
            }
        }
        // 2) fill empty slots
        for (int i = 0; i < size && remaining.getAmount() > 0; i++) {
            ItemStack existing = top.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                int add = Math.min(max, remaining.getAmount());
                ItemStack placed = remaining.clone();
                placed.setAmount(add);
                top.setItem(i, placed);
                remaining.setAmount(remaining.getAmount() - add);
            }
        }
        e.setCurrentItem(remaining.getAmount() > 0 ? remaining : null);
    }

    private void deny(InventoryClickEvent e, Player p, String what) {
        if (e != null) {
            e.setCancelled(true);
        }
        p.sendActionBar(Component.text("You are not allowed to " + what + " the team enderchest.")
                .color(NamedTextColor.RED));
    }
}
