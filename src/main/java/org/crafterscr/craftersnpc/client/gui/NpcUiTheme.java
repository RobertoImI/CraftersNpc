package org.crafterscr.craftersnpc.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

final class NpcUiTheme {
    static final int PANEL = 0xE61A1E28;
    static final int PANEL_INNER = 0xCC252B38;
    static final int BORDER = 0xFF56647A;
    static final int ACCENT = 0xFF62C7B8;
    static final int TEXT = 0xFFF2F4F8;
    static final int MUTED = 0xFFB8C0CC;

    private NpcUiTheme() { }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 2, ACCENT);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.fill(x, y, x + 1, y + height, BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
    }

    static void section(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_INNER);
        graphics.fill(x, y, x + 2, y + height, ACCENT);
    }

    static void title(GuiGraphics graphics, Font font, Component title, int centerX, int y) {
        graphics.drawCenteredString(font, title, centerX, y, TEXT);
    }

    static void label(GuiGraphics graphics, Font font, String text, int x, int y) {
        graphics.drawString(font, text, x, y, MUTED, false);
    }

    static void widgets(GuiGraphics graphics, Iterable<Renderable> widgets, int mouseX, int mouseY, float partialTick) {
        for (Renderable widget : widgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
