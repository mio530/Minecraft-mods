package de.mio.visionmod.combat;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class CombatHacks {
    private CombatHacks() {}

    private static int     killAuraCooldown    = 0;
    private static int     autoClickCooldown   = 0;
    private static int     triggerBotCooldown  = 0;
    private static int     autoTotemCooldown   = 0;
    private static int     stunSlamCooldown    = 0;
    private static int     stunSlamRestoreSlot = -1;
    private static int     stunSlamRestoreTick = 0;
    private static boolean maceDmgLaunched   = false;
    /** True while a mace-fall is in progress; FallHandlerMixin reads this to skip noFall. */
    public  static boolean suppressNoFall    = false;

    /** Clears leftover combat state so it doesn't misfire on the next join. */
    public static void resetOnDisconnect() {
        killAuraCooldown    = 0;
        autoClickCooldown   = 0;
        triggerBotCooldown  = 0;
        autoTotemCooldown   = 0;
        stunSlamCooldown    = 0;
        stunSlamRestoreSlot = -1;
        stunSlamRestoreTick = 0;
        maceDmgLaunched     = false;
        suppressNoFall      = false;
    }

    /** Switches the held hotbar slot AND tells the server, so it doesn't desync. */
    private static void selectSlot(Minecraft mc, int slot) {
        if (slot < 0 || slot > 8 || mc.player.getInventory().selected == slot) return;
        mc.player.getInventory().selected = slot;
        if (mc.player.connection != null) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        VisionConfig cfg = VisionConfig.get();
        suppressNoFall = false; // each mace method sets it if needed this tick
        tickMaceDmg(mc, cfg);
        tickMaceDmgClassic(mc, cfg);
        tickAutoMace(mc, cfg);
        tickStunSlam(mc, cfg);
        tickKillAura(mc, cfg);
        tickAutoClicker(mc, cfg);
        tickTriggerBot(mc, cfg);
        tickAutoWeapon(mc, cfg);
        tickAutoTotem(mc, cfg);
        tickAutoLog(mc, cfg);
    }

    // ── TriggerBot ──────────────────────────────────────────────────────────────
    // Attacks whatever living entity is under the crosshair, no mouse button needed.

    private static void tickTriggerBot(Minecraft mc, VisionConfig cfg) {
        if (!cfg.triggerBotEnabled || mc.screen != null || mc.gameMode == null) return;
        if (triggerBotCooldown > 0) { triggerBotCooldown--; return; }
        if (mc.player.getAttackStrengthScale(0f) < 0.9f) return;
        Entity crosshair = mc.crosshairPickEntity;
        if (crosshair instanceof LivingEntity le && le.isAlive() && le != mc.player
                && (le instanceof Player ? cfg.killAuraPlayers : cfg.killAuraMobs)) {
            mc.gameMode.attack(mc.player, crosshair);
            mc.player.swing(InteractionHand.MAIN_HAND);
            triggerBotCooldown = Math.max(1, 20 / Math.max(1, cfg.triggerBotCps));
        }
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
            suppressNoFall = true;
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

    // ── Classic Mace Damage ────────────────────────────────────────────────────
    // No auto-jump — player positions manually (or via Wind Burst). Auto-attacks
    // whenever falling with the mace in hand. Works with Wind Burst bounce combo.

    private static void tickMaceDmgClassic(Minecraft mc, VisionConfig cfg) {
        if (!cfg.maceDmgClassicEnabled || mc.screen != null || mc.gameMode == null) return;
        if (!mc.player.getMainHandItem().is(Items.MACE)) return;
        if (mc.player.getDeltaMovement().y >= -0.3) return;
        suppressNoFall = true; // let server accumulate fallDistance
        double range = cfg.killAuraRange;
        mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range),
                e -> e != mc.player && e.isAlive()
                        && mc.player.distanceTo(e) <= range
                        && (e instanceof Player ? cfg.killAuraPlayers : cfg.killAuraMobs))
                .stream().min(Comparator.comparingDouble(mc.player::distanceTo))
                .ifPresent(t -> mc.gameMode.attack(mc.player, t));
    }

    // ── Auto Mace (smooth aim) ──────────────────────────────────────────────────
    // While holding a mace, smoothly rotates the view toward the nearest target so
    // the mace slam lands. Turns at most autoMaceSpeed degrees per tick (no snap).

    private static void tickAutoMace(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoMaceEnabled || mc.screen != null || mc.player == null || mc.level == null) return;
        if (!mc.player.getMainHandItem().is(Items.MACE)) return;

        double range = cfg.autoMaceRange;
        LivingEntity target = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range),
                e -> e != mc.player && e.isAlive()
                        && mc.player.distanceTo(e) <= range
                        && (e instanceof Player ? cfg.killAuraPlayers : cfg.killAuraMobs))
                .stream().min(Comparator.comparingDouble(mc.player::distanceTo)).orElse(null);
        if (target == null) return;

        Vec3 delta = target.getEyePosition().subtract(mc.player.getEyePosition());
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float wantYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float wantPitch = (float) Math.toDegrees(-Math.atan2(delta.y, horiz));

        float step = Math.max(1f, cfg.autoMaceSpeed);
        mc.player.setYRot(approachAngle(mc.player.getYRot(), wantYaw, step));
        mc.player.setXRot(Mth.clamp(approachAngle(mc.player.getXRot(), wantPitch, step), -90f, 90f));
    }

    /** Moves cur toward target by at most maxStep degrees, on the shortest path. */
    private static float approachAngle(float cur, float target, float maxStep) {
        float diff = Mth.wrapDegrees(target - cur);
        diff = Mth.clamp(diff, -maxStep, maxStep);
        return cur + diff;
    }

    // ── StunSlam ──────────────────────────────────────────────────────────────
    // When falling >= minFall blocks and a blocking (shielded) target is in range,
    // auto-switch to the best axe in hotbar, sprint-attack to stun the shield,
    // then restore the previous slot.

    private static void tickStunSlam(Minecraft mc, VisionConfig cfg) {
        if (!cfg.stunSlamEnabled || mc.screen != null || mc.gameMode == null) {
            if (stunSlamRestoreSlot >= 0) {
                selectSlot(mc, stunSlamRestoreSlot);
                stunSlamRestoreSlot = -1;
            }
            return;
        }
        // Restore slot after attack
        if (stunSlamRestoreSlot >= 0) {
            if (--stunSlamRestoreTick <= 0) {
                selectSlot(mc, stunSlamRestoreSlot);
                stunSlamRestoreSlot = -1;
            }
            return;
        }
        if (stunSlamCooldown > 0) { stunSlamCooldown--; return; }
        if (mc.player.onGround()) return;
        if (mc.player.fallDistance < cfg.stunSlamMinFall) return;

        double range = cfg.killAuraRange;
        LivingEntity target = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range),
                e -> e != mc.player && e.isAlive() && e.isBlocking()
                        && mc.player.distanceTo(e) <= range
                        && (e instanceof Player ? cfg.killAuraPlayers : cfg.killAuraMobs))
                .stream().min(Comparator.comparingDouble(mc.player::distanceTo)).orElse(null);
        if (target == null) return;

        // Find best axe in hotbar (prefer current slot)
        int cur = mc.player.getInventory().selected;
        int axeSlot = mc.player.getInventory().getItem(cur).getItem() instanceof AxeItem ? cur : -1;
        if (axeSlot < 0) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) {
                    axeSlot = i; break;
                }
            }
        }
        if (axeSlot < 0) return;

        if (axeSlot != cur) {
            stunSlamRestoreSlot = cur;
            stunSlamRestoreTick = 3;
            selectSlot(mc, axeSlot);
        }
        // Tell the server we're sprinting BEFORE the attack packet, otherwise the
        // sprint-state command is only sent on the player's next tick and the server
        // processes this slam as a non-sprint hit (losing the sprint knockback).
        mc.player.setSprinting(true);
        if (mc.player.connection != null) {
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                    mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
        mc.gameMode.attack(mc.player, target);
        stunSlamCooldown = 20;
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

        // FOV filter — measured EYE-to-EYE (where you actually aim), not foot-to-foot.
        // Using feet made the angle to a nearby mob much larger than the crosshair
        // offset, so any FOV below ~30° filtered out even a mob you look straight at.
        if (cfg.killAuraFov < 360f) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 lookVec = mc.player.getLookAngle();
            float halfFov = cfg.killAuraFov / 2f;
            candidates = candidates.stream().filter(e -> {
                Vec3 toTarget = e.getEyePosition().subtract(eye).normalize();
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

    // ── AutoWeapon ──────────────────────────────────────────────────────────────
    // While attacking a living entity, switch to the best melee weapon in the hotbar
    // (sword preferred, then axe).

    private static void tickAutoWeapon(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoWeaponEnabled || mc.screen != null || mc.gameMode == null) return;
        if (!mc.options.keyAttack.isDown()) return;
        if (!(mc.crosshairPickEntity instanceof LivingEntity)) return;

        int sword = -1, axe = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(ItemTags.SWORDS)) { sword = i; break; }
            if (s.is(ItemTags.AXES) && axe < 0) axe = i;
        }
        int best = sword >= 0 ? sword : axe;
        if (best >= 0 && best != mc.player.getInventory().selected) {
            mc.player.getInventory().selected = best;
        }
    }

    // ── Auto Totem ────────────────────────────────────────────────────────────

    private static void tickAutoTotem(Minecraft mc, VisionConfig cfg) {
        if (!cfg.autoTotemEnabled || mc.gameMode == null) return;
        if (autoTotemCooldown > 0) { autoTotemCooldown--; return; }
        if (mc.player.getHealth() > cfg.autoTotemHpThresh) return;
        // Slot math below only holds for the player's own inventory menu. If a chest
        // or other container is open, containerMenu is that container and the clicks
        // would land on wrong slots — skip until it's closed.
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;

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
