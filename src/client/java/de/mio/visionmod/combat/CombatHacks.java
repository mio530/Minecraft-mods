package de.mio.visionmod.combat;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class CombatHacks {
    private CombatHacks() {}

    private static int     killAuraCooldown  = 0;
    private static int     autoClickCooldown = 0;
    private static int     autoTotemCooldown = 0;
    public  static boolean maceDmgLaunched   = false; // read by FallHandlerMixin

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        VisionConfig cfg = VisionConfig.get();
        tickMaceDmg(mc, cfg);
        tickKillAura(mc, cfg);
        tickAutoClicker(mc, cfg);
        tickAutoTotem(mc, cfg);
        tickAutoLog(mc, cfg);
    }

    // ── Mace Damage ───────────────────────────────────────────────────────────

    private static void tickMaceDmg(Minecraft mc, VisionConfig cfg) {
        if (!cfg.maceDmgEnabled || mc.screen != null || mc.gameMode == null) {
            maceDmgLaunched = false;
            return;
        }
        if (!mc.player.getMainHandItem().is(Items.MACE)) {
            maceDmgLaunched = false;
            return;
        }
        Vec3 motion = mc.player.getDeltaMovement();
        if (!maceDmgLaunched && mc.player.onGround()) {
            mc.player.setDeltaMovement(motion.x, 2.8, motion.z);
            maceDmgLaunched = true;
            return;
        }
        if (maceDmgLaunched) {
            if (motion.y < -0.5) {
                double range = cfg.killAuraRange;
                List<LivingEntity> cands = mc.level.getEntitiesOfClass(LivingEntity.class,
                        mc.player.getBoundingBox().inflate(range),
                        e -> e != mc.player && e.isAlive()
                                && mc.player.distanceTo(e) <= range
                                && (e instanceof Player ? cfg.killAuraPlayers : cfg.killAuraMobs));
                cands.stream().min(Comparator.comparingDouble(mc.player::distanceTo))
                        .ifPresent(t -> {
                            mc.gameMode.attack(mc.player, t);
                            maceDmgLaunched = false;
                        });
            } else if (mc.player.onGround()) {
                maceDmgLaunched = false;
            }
        }
    }

    // ── Kill Aura ──────────────────────────────────────────────────────────────

    private static void tickKillAura(Minecraft mc, VisionConfig cfg) {
        if (!cfg.killAuraEnabled || mc.screen != null || mc.gameMode == null) return;
        if (killAuraCooldown > 0) { killAuraCooldown--; return; }

        float atkStrength = mc.player.getAttackStrengthScale(0f);
        if (atkStrength < 0.9f) return;

        double range = cfg.killAuraRange;
        List<LivingEntity> candidates = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range),
                e -> {
                    if (e == mc.player || !e.isAlive()) return false;
                    if (mc.player.distanceTo(e) > range) return false;
                    if (e instanceof Player) return cfg.killAuraPlayers;
                    return cfg.killAuraMobs;
                }
        );

        // FOV filter
        if (cfg.killAuraFov < 360f) {
            Vec3 lookVec = mc.player.getLookAngle();
            float halfFov = cfg.killAuraFov / 2f;
            candidates = candidates.stream().filter(e -> {
                Vec3 toTarget = e.position().subtract(mc.player.position()).normalize();
                double dot = lookVec.dot(toTarget);
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
                return angle <= halfFov;
            }).toList();
        }

        // Priority selection
        LivingEntity target = switch (cfg.killAuraPriority) {
            case "LowestHP"  -> candidates.stream()
                    .min(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
            case "HighestHP" -> candidates.stream()
                    .max(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
            default          -> candidates.stream()  // "Nearest"
                    .min(Comparator.comparingDouble(mc.player::distanceTo)).orElse(null);
        };

        if (target != null) {
            // Rotation
            if (cfg.killAuraRotate) {
                Vec3 delta = target.getEyePosition().subtract(mc.player.getEyePosition());
                double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
                double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                double pitch = Math.toDegrees(-Math.atan2(delta.y, horiz));
                mc.player.setYRot((float) yaw);
                mc.player.setXRot((float) pitch);
            }
            mc.gameMode.attack(mc.player, target);
            killAuraCooldown = Math.max(1, 20 / Math.max(1, cfg.killAuraCps));
        }
    }

    // ── Auto Clicker ──────────────────────────────────────────────────────────

    private static void tickAutoClicker(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoClickerEnabled || mc.screen != null || mc.gameMode == null) return;
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

    // ── AutoLog ───────────────────────────────────────────────────────────────

    private static void tickAutoLog(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoLogEnabled) return;
        if (mc.player == null) return;
        if (mc.player.getHealth() > cfg.autoLogHp) return;
        if (mc.getConnection() == null) return;
        mc.getConnection().getConnection().disconnect(
                net.minecraft.network.chat.Component.literal("AutoLog"));
    }
}
