package de.mio.visionmod;

import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.config.VisionConfigScreen;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.overlay.OverlayWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class VisionModClient implements ClientModInitializer {

    public static KeyMapping keyEntityEsp;
    public static KeyMapping keyOreEsp;
    public static KeyMapping keyConfig;

    @Override
    public void onInitializeClient() {
        VisionConfig.load();

        keyEntityEsp = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.visionmod.entity_esp",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.visionmod"
        ));
        keyOreEsp = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.visionmod.ore_esp",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "category.visionmod"
        ));
        keyConfig = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.visionmod.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.visionmod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (keyEntityEsp.consumeClick()) {
                VisionConfig.get().entityEspEnabled = !VisionConfig.get().entityEspEnabled;
                VisionConfig.save();
            }
            while (keyOreEsp.consumeClick()) {
                VisionConfig.get().oreEspEnabled = !VisionConfig.get().oreEspEnabled;
                VisionConfig.save();
            }
            while (keyConfig.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new VisionConfigScreen(null));
                }
            }
            EntityESP.tick(mc);
            OreESP.tick(mc);
        });

        WorldRenderEvents.END.register(ctx -> OverlayWindow.INSTANCE.onRenderEnd(ctx));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> OverlayWindow.INSTANCE.init(mc));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> OverlayWindow.INSTANCE.destroy());
    }
}
