package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class GiftIconOverlay {
    private static final ResourceLocation GIFT_ICON = ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "textures/gui/gift_icon.png");
    private static final int SIZE = 16;

    private GiftIconOverlay() {}

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.hitResult == null || minecraft.player.getMainHandItem().isEmpty()) return;
        if (!(minecraft.hitResult instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof CnpcEntity npc)) return;
        if (!npc.acceptsGiftClient(minecraft.player.getMainHandItem())) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - SIZE / 2;
        int y = graphics.guiHeight() / 2 - SIZE / 2;
        graphics.blit(GIFT_ICON, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
    }
}
