package de.mio.visionmod.movement;

import de.mio.visionmod.VisionModClient;
import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class MovementHacks {
    private MovementHacks() {}

    private static final Identifier SPEED_ID    = Identifier.fromNamespaceAndPath("visionmod", "speed");
    private static final Identifier STEP_ID     = Identifier.fromNamespaceAndPath("visionmod", "step");
    private static final Identifier REACH_ID    = Identifier.fromNamespaceAndPath("visionmod", "reach");
    private static int scaffoldCooldown = 0;
    private static int surroundCooldown = 0;
    private static boolean flyWasActive = false;
    private static boolean autoWalkActive = false;

    public static void resetOnDisconnect() {
        flyWasActive     = false;
        scaffoldCooldown = 0;
        surroundCooldown = 0;
        // Release the forward key if AutoWalk was holding it, else it stays stuck.
        if (autoWalkActive) {
            try { Minecraft.getInstance().options.keyUp.setDown(false); } catch (Exception ignored) {}
            autoWalkActive = false;
        }
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min;
        return v < min ? min : (v > max ? max : v);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        VisionConfig cfg = VisionConfig.get();
        LocalPlayer p = mc.player;

        // ── NoFall (always runs, even with screen open) ───────────────────────
        if (cfg.noFallEnabled) p.fallDistance = 0f;

        // ── SafeWalk (works even with screen open) ────────────────────────────
        if (cfg.safeWalkEnabled && mc.screen == null) {
            Vec3 movement = p.getDeltaMovement();
            if (movement.horizontalDistance() > 0.01) {
                p.setShiftKeyDown(true);
            } else {
                p.setShiftKeyDown(false);
            }
        }

        // ── InvMove: allow sprint/speed even with screen open if invMoveEnabled
        boolean screenBlocked = mc.screen != null && !cfg.invMoveEnabled;

        if (!screenBlocked) {
            // ── Sprint ────────────────────────────────────────────────────────
            if (cfg.sprintEnabled && !p.isCrouching()) p.setSprinting(true);

            // ── Speed ─────────────────────────────────────────────────────────
            AttributeInstance speedAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(SPEED_ID);
                if (cfg.speedEnabled) {
                    // Clamp the multiplier: anything past ~3x gets flagged as
                    // "moved too quickly" by the (integrated) server and kicks.
                    float mult = clamp(cfg.speedMultiplier, 1.0f, 3.0f);
                    double bonus = 0.1 * (mult - 1.0);
                    speedAttr.addOrUpdateTransientModifier(
                        new AttributeModifier(SPEED_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        } else {
            // Screen open and invMove disabled: remove speed modifier
            AttributeInstance speedAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.removeModifier(SPEED_ID);
        }

        // ── Reach (entity interaction range) ──────────────────────────────────
        AttributeInstance reachAttr = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reachAttr != null) {
            reachAttr.removeModifier(REACH_ID);
            if (cfg.reachEnabled) {
                double bonus = clamp(cfg.reachDistance, 3.0f, 6.0f) - 3.0;
                reachAttr.addOrUpdateTransientModifier(
                    new AttributeModifier(REACH_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // ── AutoWalk (hold forward) ───────────────────────────────────────────
        if (cfg.autoWalkEnabled && mc.screen == null) {
            mc.options.keyUp.setDown(true);
            autoWalkActive = true;
        } else if (autoWalkActive) {
            mc.options.keyUp.setDown(false);
            autoWalkActive = false;
        }

        if (mc.screen != null) {
            // Only speed/sprint allowed with invMove when screen is open, rest skipped
            return;
        }

        // ── Fly ───────────────────────────────────────────────────────────────
        if (cfg.flyEnabled) {
            if (!p.getAbilities().mayfly) {
                p.getAbilities().mayfly = true;
                if (mc.getConnection() != null) p.onUpdateAbilities();
            }
            p.getAbilities().flyingSpeed = 0.05f * clamp(cfg.flySpeed, 0.5f, 5.0f);
            flyWasActive = true;
        } else if (flyWasActive) {
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            if (mc.getConnection() != null) p.onUpdateAbilities();
            flyWasActive = false;
        }

        // ── Step ──────────────────────────────────────────────────────────────
        AttributeInstance stepAttr = p.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            stepAttr.removeModifier(STEP_ID);
            if (cfg.stepEnabled) {
                // Step >2.5 blocks looks like teleporting to the server and trips
                // movement validation; keep the bonus within a safe range.
                double bonus = clamp(cfg.stepHeight, 0.6f, 2.5f) - 0.6;
                stepAttr.addOrUpdateTransientModifier(
                    new AttributeModifier(STEP_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // ── Jesus (walk on water) ─────────────────────────────────────────────
        if (cfg.jesusEnabled && p.isInWater()) {
            Vec3 vel = p.getDeltaMovement();
            if (vel.y < 0) p.setDeltaMovement(vel.x, 0.1, vel.z);
        }

        // ── Spider (climb walls) ──────────────────────────────────────────────
        if (cfg.spiderEnabled && p.horizontalCollision && !p.onGround()) {
            Vec3 vel = p.getDeltaMovement();
            p.setDeltaMovement(vel.x, 0.2, vel.z);
        }

        // ── FastLadder (climb faster) ─────────────────────────────────────────
        if (cfg.fastLadderEnabled && p.onClimbable()) {
            Vec3 vel = p.getDeltaMovement();
            if (vel.y > 0) p.setDeltaMovement(vel.x, 0.234, vel.z);
        }

        // ── Glide (slow descent) ──────────────────────────────────────────────
        if (cfg.glideEnabled && !p.onGround()) {
            Vec3 vel = p.getDeltaMovement();
            if (vel.y < -0.1) p.setDeltaMovement(vel.x, -0.1, vel.z);
        }

        // ── AntiVoid (hover above the void) ───────────────────────────────────
        if (cfg.antiVoidEnabled && p.getY() < -70 && p.getDeltaMovement().y < 0) {
            Vec3 vel = p.getDeltaMovement();
            p.setDeltaMovement(vel.x, 0.0, vel.z);
        }

        // ── Scaffold ──────────────────────────────────────────────────────────
        if (cfg.scaffoldEnabled && mc.level != null) {
            if (scaffoldCooldown > 0) { scaffoldCooldown--; }
            else {
                BlockPos below   = p.blockPosition().below();
                BlockPos support = below.below();
                if (mc.level.getBlockState(below).isAir()
                 && !mc.level.getBlockState(support).isAir()) {
                    int saved = p.getInventory().selected;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = p.getInventory().getItem(i);
                        if (!(stack.getItem() instanceof BlockItem)) continue;
                        p.getInventory().selected = i;
                        Vec3 hitPos = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
                        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                            new BlockHitResult(hitPos, Direction.UP, support, false));
                        p.getInventory().selected = saved;
                        scaffoldCooldown = 3;
                        break;
                    }
                }
            }
        }

        // ── Surround ──────────────────────────────────────────────────────────
        if (cfg.surroundEnabled && VisionModClient.fullyJoined && mc.level != null && mc.gameMode != null) {
            if (surroundCooldown > 0) { surroundCooldown--; }
            else {
                BlockPos feet = p.blockPosition();
                BlockPos[] cardinals = {
                    feet.north(), feet.south(), feet.east(), feet.west()
                };
                for (BlockPos pos : cardinals) {
                    BlockPos supportPos = pos.below();
                    if (!mc.level.getBlockState(pos).isAir()) continue;
                    if (mc.level.getBlockState(supportPos).isAir()) continue;

                    int saved = p.getInventory().selected;
                    boolean placed = false;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = p.getInventory().getItem(i);
                        if (!(stack.getItem() instanceof BlockItem)) continue;
                        p.getInventory().selected = i;
                        Vec3 hitPos = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                            new BlockHitResult(hitPos, Direction.UP, supportPos, false));
                        p.getInventory().selected = saved;
                        surroundCooldown = 1;
                        placed = true;
                        break;
                    }
                    if (placed) break;
                }
            }
        }
    }
}
