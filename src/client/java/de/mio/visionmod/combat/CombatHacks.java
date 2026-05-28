package de.mio.visionmod.combat;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

public final class CombatHacks {
    private CombatHacks() {}

    private static int killAuraCooldown  = 0;
    private static int autoClickCooldown = 0;
    private static int autoTotemCooldown = 0;

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        VisionConfig cfg = VisionConfig.get();
        tickKillAura(mc, cfg);
        tickAutoClicker(mc, cfg);
        tickAutoTotem(mc, cfg);
    }

    // ── Kill Aura ──────────────────────────────────────────────────────────────

    private static void tickKillAura(Minecraft mc, VisionConfig cfg) {
        if (!cfg.killAuraEnabled || mc.screen != null) return;
        if (killAuraCooldown > 0) { killAuraCooldown--; return; }

        float atkStrength = mc.player.getAttackStrengthScale(0f);
        if (atkStrength < 0.9f) return;

        double range = cfg.killAuraRange;
        LivingEntity target = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range),
                e -> {
                    if (e == mc.player || !e.isAlive()) return false;
                    if (mc.player.distanceTo(e) > range) return false;
                    if (e instanceof Player) return cfg.killAuraPlayers;
                    return cfg.killAuraMobs;
                }
        ).stream().min(Comparator.comparingDouble(mc.player::distanceTo)).orElse(null);

        if (target != null) {
            mc.gameMode.attack(mc.player, target);
            killAuraCooldown = Math.max(1, 20 / Math.max(1, cfg.killAuraCps));
        }
    }

    // ── Auto Clicker ──────────────────────────────────────────────────────────

    private static void tickAutoClicker(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoClickerEnabled || mc.screen != null) return;
        if (autoClickCooldown > 0) { autoClickCooldown--; return; }

        long win = GLFW.glfwGetCurrentContext();
        if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            Entity crosshair = mc.crosshairPickEntity;
            if (crosshair instanceof LivingEntity le && le.isAlive()) {
                mc.gameMode.attack(mc.player, crosshair);
            }
            autoClickCooldown = Math.max(1, 20 / Math.max(1, cfg.autoClickerCps));
        }
    }

    // ── Auto Totem ────────────────────────────────────────────────────────────

    private static void tickAutoTotem(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoTotemEnabled || mc.gameMode == null) return;
        if (autoTotemCooldown > 0) { autoTotemCooldown--; return; }
        if (mc.player.getHealth() > cfg.autoTotemHpThresh) return;

        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) return;

        int containerId = mc.player.containerMenu.containerId;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() != Items.TOTEM_OF_UNDYING) continue;

            // Hotbar slots 0–8 → container slots 36–44; main inv 9–35 stays as-is
            int srcSlot = i < 9 ? i + 36 : i;
            mc.gameMode.handleInventoryMouseClick(containerId, srcSlot,  0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(containerId, 45,       0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(containerId, srcSlot,  0, ClickType.PICKUP, mc.player);
            autoTotemCooldown = 10;
            break;
        }
    }
}
