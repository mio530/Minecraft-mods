package de.mio.ryzeteams.ec;

import org.bukkit.event.inventory.InventoryAction;

/**
 * Pure decision function for enderchest clicks. Extracted from the listener so
 * every {@link InventoryAction} (including future ones) can be exhaustively
 * unit-tested: the safety property is that without the matching permission no
 * action may ever resolve to a mutation ({@link Decision#ALLOW} or
 * {@link Decision#REIMPLEMENT_SHIFT_IN}).
 */
public final class EcClickPolicy {

    public enum Decision {
        /** Let Paper apply the action, then resync. */
        ALLOW,
        /** Cancel and tell the player they lack the permission. */
        DENY,
        /** Cancel silently (multi-slot/creative actions that could dupe). */
        CANCEL,
        /** Cancel and run the controlled shift-into-storage move. */
        REIMPLEMENT_SHIFT_IN,
        /** Cancel and treat as a navigation-row click. */
        NAV,
        /** Do nothing (safe no-op / own-inventory rearrange). */
        IGNORE
    }

    private EcClickPolicy() {
    }

    /** Classifies a click whose clicked slot is inside the EC (top inventory). */
    public static Decision classifyTop(InventoryAction action, boolean navSlot,
                                       boolean canWithdraw, boolean canDeposit) {
        if (navSlot) {
            return Decision.NAV;
        }
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY ->
                    canWithdraw ? Decision.ALLOW : Decision.DENY;
            case PLACE_ALL, PLACE_ONE, PLACE_SOME ->
                    canDeposit ? Decision.ALLOW : Decision.DENY;
            case SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD ->
                    (canWithdraw && canDeposit) ? Decision.ALLOW : Decision.DENY;
            case COLLECT_TO_CURSOR, CLONE_STACK, UNKNOWN -> Decision.CANCEL;
            case NOTHING -> Decision.IGNORE;
            // PLACE from an empty cursor etc. never reaches here; anything else is cancelled.
            default -> Decision.CANCEL;
        };
    }

    /** Classifies a click whose clicked slot is in the player's own inventory (bottom). */
    public static Decision classifyBottom(InventoryAction action, boolean canDeposit) {
        return switch (action) {
            case MOVE_TO_OTHER_INVENTORY -> canDeposit ? Decision.REIMPLEMENT_SHIFT_IN : Decision.DENY;
            case COLLECT_TO_CURSOR -> Decision.CANCEL;
            default -> Decision.IGNORE;
        };
    }
}
