package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.network.WandWaitAdjustPayload;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class RouteWandKeybinds {
    private static final String CATEGORY = "key.categories.craftersnpc";
    private static final KeyMapping WAIT_UP = new KeyMapping("key.craftersnpc.wait_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY);
    private static final KeyMapping WAIT_DOWN = new KeyMapping("key.craftersnpc.wait_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY);

    private RouteWandKeybinds() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RouteWandKeybinds::registerKeys);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(WAIT_UP);
        event.register(WAIT_DOWN);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }

        while (WAIT_UP.consumeClick()) {
            PacketDistributor.sendToServer(new WandWaitAdjustPayload(1));
        }

        while (WAIT_DOWN.consumeClick()) {
            PacketDistributor.sendToServer(new WandWaitAdjustPayload(-1));
        }
    }
}
