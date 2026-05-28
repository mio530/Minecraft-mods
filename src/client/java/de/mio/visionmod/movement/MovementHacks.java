package de.mio.visionmod.movement;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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

    private static final ResourceLocation SPEED_ID    = ResourceLocation.fromNamespaceAndPath("visionmod", "speed");
    private static final ResourceLocation STEP_ID     = ResourceLocation.fromNamespaceAndPath("visionmod", "step");
    private static int scaffoldCooldown = 0;
    private static boolean flyWasActive = false;

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        VisionConfig cfg = VisionConfig.get();
        LocalPlayer p = mc.player;

        // ── NoFall (always runs, even with screen open) ───────────────────────
        if (cfg.noFallEnabled) p.fallDistance = 0f;

        if (mc.screen != null) return;

        // ── Sprint ────────────────────────────────────────────────────────────
        if (cfg.sprintEnabled && !p.isCrouching()) p.setSprinting(true);

        // ── Fly ───────────────────────────────────────────────────────────────
        if (cfg.flyEnabled) {
            if (!p.getAbilities().mayfly) {
                p.getAbilities().mayfly = true;
                p.onUpdateAbilities();
            }
            p.getAbilities().flyingSpeed = 0.05f * cfg.flySpeed;
            flyWasActive = true;
        } else if (flyWasActive) {
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            flyWasActive = false;
        }

        // ── Speed ─────────────────────────────────────────────────────────────
        AttributeInstance speedAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_ID);
            if (cfg.speedEnabled) {
                double bonus = 0.1 * (cfg.speedMultiplier - 1.0);
                speedAttr.addOrUpdateTransientModifier(
                    new AttributeModifier(SPEED_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // ── Step ──────────────────────────────────────────────────────────────
        AttributeInstance stepAttr = p.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            stepAttr.removeModifier(STEP_ID);
            if (cfg.stepEnabled) {
                double bonus = cfg.stepHeight - 0.6;
                stepAttr.addOrUpdateTransientModifier(
                    new AttributeModifier(STEP_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // ── Jesus (walk on water) ─────────────────────────────────────────────
        if (cfg.jesusEnabled && p.isInWater()) {
            Vec3 vel = p.getDeltaMovement();
            if (vel.y < 0) p.setDeltaMovement(vel.x, 0.1, vel.z);
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
    }
}
