package de.mio.visionmod.player;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class PlayerHacks {
    private PlayerHacks() {}

    private static int afkTimer          = 0;
    private static boolean eating        = false;
    private static int chestStealerCooldown = 0;

    public static void resetOnDisconnect(Minecraft mc) {
        afkTimer = 0;
        chestStealerCooldown = 0;
        if (eating) {
            mc.options.keyUse.setDown(false);
            eating = false;
        }
    }

    /** Switches the held hotbar slot AND notifies the server to avoid desync. */
    private static void selectSlot(Minecraft mc, int slot) {
        if (slot < 0 || slot > 8 || mc.player.getInventory().selected == slot) return;
        mc.player.getInventory().selected = slot;
        if (mc.player.connection != null) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        VisionConfig cfg = VisionConfig.get();

        // ── AutoRespawn (runs even when dead) ─────────────────────────────────
        if (cfg.autoRespawnEnabled && mc.player.isDeadOrDying()) {
            mc.player.connection.send(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }

        // ── AntiBlind ─────────────────────────────────────────────────────────
        if (cfg.antiBlindEnabled) {
            mc.player.removeEffect(MobEffects.BLINDNESS);
            mc.player.removeEffect(MobEffects.DARKNESS);
        }

        // ── AntiPoison ────────────────────────────────────────────────────────
        if (cfg.antiPoisonEnabled) {
            mc.player.removeEffect(MobEffects.POISON);
            mc.player.removeEffect(MobEffects.WITHER);
            mc.player.removeEffect(MobEffects.WEAKNESS);
            mc.player.removeEffect(MobEffects.SLOWNESS);
            mc.player.removeEffect(MobEffects.MINING_FATIGUE);
            mc.player.removeEffect(MobEffects.HUNGER);
        }

        // ── AntiAFK ───────────────────────────────────────────────────────────
        if (cfg.antiAfkEnabled) {
            afkTimer++;
            int interval = Math.max(20, cfg.antiAfkInterval);
            if (afkTimer == interval / 2) mc.player.turn(1.0, 0.0);
            if (afkTimer >= interval)     { mc.player.turn(-1.0, 0.0); afkTimer = 0; }
        } else {
            afkTimer = 0;
        }

        // ── ChestStealer ──────────────────────────────────────────────────────
        if (cfg.chestStealerEnabled && mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
            if (chestStealerCooldown > 0) {
                chestStealerCooldown--;
            } else {
                AbstractContainerMenu menu = containerScreen.getMenu();
                int containerSlots = menu.slots.size() - 36;
                for (int i = 0; i < containerSlots; i++) {
                    ItemStack stack = menu.slots.get(i).getItem();
                    if (!stack.isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(
                                menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                        chestStealerCooldown = 2;
                        break;
                    }
                }
            }
        }

        if (mc.screen != null) return;

        // ── AutoTool (switch to the fastest tool while mining) ────────────────
        if (cfg.autoToolEnabled && mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult bhr && mc.level != null) {
            var state = mc.level.getBlockState(bhr.getBlockPos());
            if (!state.isAir()) {
                int best = -1;
                float bestSpeed = 1.0f;
                for (int i = 0; i < 9; i++) {
                    float sp = mc.player.getInventory().getItem(i).getDestroySpeed(state);
                    if (sp > bestSpeed) { bestSpeed = sp; best = i; }
                }
                if (best >= 0) selectSlot(mc, best);
            }
        }

        // ── AutoEat ───────────────────────────────────────────────────────────
        if (cfg.autoEatEnabled) {
            int foodLevel = mc.player.getFoodData().getFoodLevel();
            if (foodLevel < cfg.autoEatThreshold) {
                boolean foundFood = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                        selectSlot(mc, i);
                        mc.options.keyUse.setDown(true);
                        eating = true;
                        foundFood = true;
                        break;
                    }
                }
                if (!foundFood && eating) {
                    mc.options.keyUse.setDown(false);
                    eating = false;
                }
            } else if (eating) {
                mc.options.keyUse.setDown(false);
                eating = false;
            }
        } else if (eating) {
            mc.options.keyUse.setDown(false);
            eating = false;
        }
    }
}
