package de.mio.visionmod.player;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;

public final class PlayerHacks {
    private PlayerHacks() {}

    private static int afkTimer    = 0;
    private static boolean eating  = false;

    public static void resetOnDisconnect(Minecraft mc) {
        afkTimer = 0;
        if (eating) {
            mc.options.keyUse.setDown(false);
            eating = false;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        VisionConfig cfg = VisionConfig.get();

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

        if (mc.screen != null) return;

        // ── AutoEat ───────────────────────────────────────────────────────────
        if (cfg.autoEatEnabled) {
            int foodLevel = mc.player.getFoodData().getFoodLevel();
            if (foodLevel < cfg.autoEatThreshold) {
                boolean foundFood = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                        mc.player.getInventory().selected = i;
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
