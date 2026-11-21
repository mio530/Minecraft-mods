package de.mio.ryzeteams.integration;

import de.mio.ryzeteams.RyzeTeamsPlugin;
import de.mio.ryzeteams.ec.EnderchestManager;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamRole;
import de.mio.ryzeteams.service.TeamManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that fire real {@link InventoryClickEvent}s at the registered
 * enderchest listener and assert dupe/loss safety and live permission enforcement.
 */
class EcInventoryIT {

    private ServerMock server;
    private RyzeTeamsPlugin plugin;
    private TeamManager teams;
    private EnderchestManager ec;
    private Team team;

    private PlayerMock owner;   // all permissions
    private PlayerMock member;  // EC_OPEN + EC_DEPOSIT only (no withdraw)
    private PlayerMock recruit; // no EC permissions

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RyzeTeamsPlugin.class);
        teams = plugin.teamManager();
        ec = plugin.enderchestManager();

        owner = server.addPlayer("Owner");
        member = server.addPlayer("Member");
        recruit = server.addPlayer("Recruit");

        team = teams.createTeam(owner.getUniqueId(), "Owner", "Alpha");
        join(member, TeamRole.MEMBER_ID);   // member default = EC_OPEN+EC_DEPOSIT
        join(recruit, TeamRole.RECRUIT_ID);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void join(PlayerMock p, String roleId) {
        teams.invite(team, owner.getUniqueId(), p.getUniqueId(), p.getName());
        teams.acceptInvite(p.getUniqueId(), p.getName(), null);
        teams.assignRole(team, owner.getUniqueId(), p.getName(), roleId);
    }

    private InventoryView openEc(Player p) {
        ec.openNowForTest(p, team);
        return p.getOpenInventory();
    }

    private InventoryClickEvent click(InventoryView view, int rawSlot, ClickType type, InventoryAction action) {
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private int countType(Inventory inv, Material mat) {
        int total = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.getType() == mat) {
                total += s.getAmount();
            }
        }
        return total;
    }

    @Test
    void ownerCanWithdraw() {
        InventoryView view = openEc(owner);
        Inventory top = view.getTopInventory();
        top.setItem(0, new ItemStack(Material.DIAMOND, 32));
        InventoryClickEvent e = click(view, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertFalse(e.isCancelled(), "owner holds EC_WITHDRAW, pickup must be allowed");
    }

    @Test
    void recruitCannotTouchEc() {
        InventoryView view = openEc(recruit);
        Inventory top = view.getTopInventory();
        top.setItem(0, new ItemStack(Material.DIAMOND, 32));
        InventoryClickEvent e = click(view, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertTrue(e.isCancelled(), "recruit has no EC permissions");
    }

    @Test
    void memberWithoutWithdrawCannotPickUpButCanPlace() {
        InventoryView view = openEc(member);
        Inventory top = view.getTopInventory();
        top.setItem(0, new ItemStack(Material.DIAMOND, 16));
        // withdraw blocked
        InventoryClickEvent pickup = click(view, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertTrue(pickup.isCancelled());
        // deposit allowed (place into an empty slot)
        InventoryClickEvent place = click(view, 1, ClickType.LEFT, InventoryAction.PLACE_ALL);
        assertFalse(place.isCancelled());
    }

    @Test
    void ecIsSingleDoubleChest() {
        InventoryView view = openEc(owner);
        assertEquals(54, view.getTopInventory().getSize(), "EC is one 54-slot double chest, shown at once");
    }

    @Test
    void collectAndCloneCancelled() {
        InventoryView view = openEc(owner);
        assertTrue(click(view, 0, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        assertTrue(click(view, 0, ClickType.MIDDLE, InventoryAction.CLONE_STACK).isCancelled());
    }

    @Test
    void shiftClickDepositConservesItems() {
        InventoryView view = openEc(owner);
        Inventory top = view.getTopInventory();
        int before = countType(top, Material.EMERALD);
        assertEquals(0, before);
        // Put an item in a bottom (player) raw slot and shift-click it into the EC.
        int bottomRaw = top.getSize(); // first player-inventory raw slot
        view.setItem(bottomRaw, new ItemStack(Material.EMERALD, 20));

        InventoryClickEvent e = click(view, bottomRaw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertTrue(e.isCancelled(), "shift-click is re-implemented, so the raw event is cancelled");

        // The 20 emeralds now live in the EC storage, not duplicated, not lost.
        assertEquals(20, countType(top, Material.EMERALD), "items must be conserved into the EC");
        ItemStack leftInPlayer = view.getItem(bottomRaw);
        assertTrue(leftInPlayer == null || leftInPlayer.getType().isAir(), "player slot emptied, no dupe");
    }

    @Test
    void shiftClickDepositBlockedWithoutPermission() {
        InventoryView view = openEc(recruit);
        Inventory top = view.getTopInventory();
        int bottomRaw = top.getSize();
        view.setItem(bottomRaw, new ItemStack(Material.EMERALD, 20));
        InventoryClickEvent e = click(view, bottomRaw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertTrue(e.isCancelled());
        assertEquals(0, countType(top, Material.EMERALD), "nothing deposited without permission");
        assertEquals(20, view.getItem(bottomRaw).getAmount(), "player keeps the item");
    }

    @Test
    void sharedInventoryIsSameObjectForTwoViewers() {
        InventoryView v1 = openEc(owner);
        InventoryView v2 = openEc(member);
        assertSame(v1.getTopInventory(), v2.getTopInventory(),
                "all viewers share one backing inventory (no per-viewer copies -> no dupes)");
    }

    @Test
    void permissionChangeClosesOpenEc() {
        openEc(member);
        assertTrue(member.getOpenInventory().getTopInventory().getHolder()
                instanceof de.mio.ryzeteams.ec.EnderchestHolder);
        // Revoke an EC permission on the member role -> open EC views are force-closed.
        teams.setRolePermission(team, owner.getUniqueId(), TeamRole.MEMBER_ID, Permission.EC_DEPOSIT, false);
        // closeForTeam runs synchronously from the service call; the EC view is gone.
        Inventory top = member.getOpenInventory().getTopInventory();
        assertFalse(top != null && top.getHolder() instanceof de.mio.ryzeteams.ec.EnderchestHolder,
                "EC view should be force-closed after an EC permission change");
    }

    @Test
    void customDepositOnlyRoleBehaviour() {
        PlayerMock dep = server.addPlayer("Dep");
        teams.invite(team, owner.getUniqueId(), dep.getUniqueId(), "Dep");
        teams.acceptInvite(dep.getUniqueId(), "Dep", null);
        teams.createRole(team, owner.getUniqueId(), "Depositor", 350,
                EnumSet.of(Permission.EC_OPEN, Permission.EC_DEPOSIT));
        teams.assignRole(team, owner.getUniqueId(), "Dep", "depositor");

        InventoryView view = openEc(dep);
        Inventory top = view.getTopInventory();
        top.setItem(0, new ItemStack(Material.GOLD_INGOT, 8));
        assertTrue(click(view, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled(), "no withdraw");
        assertFalse(click(view, 2, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(), "deposit allowed");
    }
}
