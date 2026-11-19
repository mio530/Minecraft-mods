package de.mio.ryzeteams.ec;

import de.mio.ryzeteams.ec.EcClickPolicy.Decision;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive safety tests over every {@link InventoryAction}. The key invariant:
 * without the matching permission, no action may resolve to a mutation.
 */
class EcClickPolicyTest {

    @ParameterizedTest
    @EnumSource(InventoryAction.class)
    void withoutAnyPermissionNoTopActionMutates(InventoryAction action) {
        Decision d = EcClickPolicy.classifyTop(action, false, false, false);
        assertNotEquals(Decision.ALLOW, d, action + " must not ALLOW without permission");
        assertNotEquals(Decision.REIMPLEMENT_SHIFT_IN, d, action + " must not move items without permission");
    }

    @ParameterizedTest
    @EnumSource(InventoryAction.class)
    void withoutDepositNoBottomActionMovesIntoEc(InventoryAction action) {
        Decision d = EcClickPolicy.classifyBottom(action, false);
        assertNotEquals(Decision.REIMPLEMENT_SHIFT_IN, d, action + " must not deposit without permission");
        assertNotEquals(Decision.ALLOW, d);
    }

    @ParameterizedTest
    @EnumSource(InventoryAction.class)
    void navSlotAlwaysNav(InventoryAction action) {
        assertEquals(Decision.NAV, EcClickPolicy.classifyTop(action, true, true, true));
    }

    @Test
    void withdrawAllowsPickup() {
        assertEquals(Decision.ALLOW, EcClickPolicy.classifyTop(InventoryAction.PICKUP_ALL, false, true, false));
        assertEquals(Decision.DENY, EcClickPolicy.classifyTop(InventoryAction.PICKUP_ALL, false, false, false));
    }

    @Test
    void depositAllowsPlace() {
        assertEquals(Decision.ALLOW, EcClickPolicy.classifyTop(InventoryAction.PLACE_ALL, false, false, true));
        assertEquals(Decision.DENY, EcClickPolicy.classifyTop(InventoryAction.PLACE_ALL, false, false, false));
    }

    @Test
    void swapNeedsBoth() {
        assertEquals(Decision.DENY, EcClickPolicy.classifyTop(InventoryAction.SWAP_WITH_CURSOR, false, true, false));
        assertEquals(Decision.DENY, EcClickPolicy.classifyTop(InventoryAction.SWAP_WITH_CURSOR, false, false, true));
        assertEquals(Decision.ALLOW, EcClickPolicy.classifyTop(InventoryAction.SWAP_WITH_CURSOR, false, true, true));
    }

    @Test
    void collectAndCloneAlwaysCancelled() {
        assertEquals(Decision.CANCEL, EcClickPolicy.classifyTop(InventoryAction.COLLECT_TO_CURSOR, false, true, true));
        assertEquals(Decision.CANCEL, EcClickPolicy.classifyTop(InventoryAction.CLONE_STACK, false, true, true));
    }

    @Test
    void shiftFromPlayerDepositsWhenAllowed() {
        assertEquals(Decision.REIMPLEMENT_SHIFT_IN,
                EcClickPolicy.classifyBottom(InventoryAction.MOVE_TO_OTHER_INVENTORY, true));
        assertEquals(Decision.DENY,
                EcClickPolicy.classifyBottom(InventoryAction.MOVE_TO_OTHER_INVENTORY, false));
    }
}
