package org.crafterscr.craftersnpc.client.gui;

import org.crafterscr.craftersnpc.gift.NpcGiftReward;
import org.crafterscr.craftersnpc.network.OpenNpcGiftEditorPayload;
import org.crafterscr.craftersnpc.network.SaveNpcGiftEditorPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class NpcGiftEditorScreen extends Screen {
    private enum Page { ITEMS, REWARDS, MESSAGES }

    private final OpenNpcGiftEditorPayload initial;
    private Page page = Page.ITEMS;
    private Checkbox enabled;
    private Checkbox consumeFavorite;
    private Checkbox consumeLiked;
    private Checkbox consumeDisliked;
    private EditBox favoriteItems;
    private EditBox likedItems;
    private EditBox dislikedItems;
    private EditBox rewardChance;
    private EditBox cooldownSeconds;
    private EditBox rewards;
    private EditBox favoriteMessages;
    private EditBox likedMessages;
    private EditBox dislikedMessages;
    private EditBox unknownMessages;
    private EditBox cooldownMessages;
    private EditBox rewardMessages;
    private EditBox noRewardMessages;

    public NpcGiftEditorScreen(OpenNpcGiftEditorPayload initial) {
        super(Component.literal("Regalos de " + initial.npcId()));
        this.initial = initial;
    }

    @Override
    protected void init() {
        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();
        int x = width / 2 - 170;
        int y = height / 2 - 112;
        addRenderableWidget(Button.builder(Component.literal("Items"), b -> switchPage(Page.ITEMS)).bounds(x, y, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recompensas"), b -> switchPage(Page.REWARDS)).bounds(x + 80, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Mensajes"), b -> switchPage(Page.MESSAGES)).bounds(x + 190, y, 85, 20).build());
        enabled = Checkbox.builder(Component.literal("Sistema activo"), font).pos(x + 280, y + 2).selected(enabled == null ? initial.enabled() : enabled.selected()).build();
        addRenderableWidget(enabled);
        if (page == Page.ITEMS) initItems(x, y + 32);
        if (page == Page.REWARDS) initRewards(x, y + 32);
        if (page == Page.MESSAGES) initMessages(x, y + 32);
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, y + 220, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, y + 220, 100, 20).build());
    }

    private void switchPage(Page page) {
        this.page = page;
        refreshWidgets();
    }

    private void initItems(int x, int y) {
        favoriteItems = box(favoriteItems, join(initial.favoriteItems()), x, y + 14, 330, "minecraft:diamond, minecraft:emerald");
        likedItems = box(likedItems, join(initial.likedItems()), x, y + 54, 330, "minecraft:bread, minecraft:apple");
        dislikedItems = box(dislikedItems, join(initial.dislikedItems()), x, y + 94, 330, "minecraft:rotten_flesh");
        consumeFavorite = Checkbox.builder(Component.literal("Consumir favoritos"), font).pos(x, y + 126).selected(consumeFavorite == null ? initial.consumeFavorite() : consumeFavorite.selected()).build();
        consumeLiked = Checkbox.builder(Component.literal("Consumir gustados"), font).pos(x + 130, y + 126).selected(consumeLiked == null ? initial.consumeLiked() : consumeLiked.selected()).build();
        consumeDisliked = Checkbox.builder(Component.literal("Consumir disgustados"), font).pos(x + 260, y + 126).selected(consumeDisliked == null ? initial.consumeDisliked() : consumeDisliked.selected()).build();
        addRenderableWidget(consumeFavorite);
        addRenderableWidget(consumeLiked);
        addRenderableWidget(consumeDisliked);
    }

    private void initRewards(int x, int y) {
        rewardChance = box(rewardChance, String.format(Locale.ROOT, "%.2f", initial.rewardChance()), x, y + 14, 90, "0-100");
        cooldownSeconds = box(cooldownSeconds, Integer.toString(initial.cooldownSeconds()), x + 110, y + 14, 90, "segundos");
        rewards = box(rewards, rewardsText(initial.rewards()), x, y + 58, 340, "item:min:max:peso; item:min:max:peso");
    }

    private void initMessages(int x, int y) {
        favoriteMessages = box(favoriteMessages, joinMessages(initial.favoriteMessages()), x, y + 14, 340, "frase 1 | frase 2");
        likedMessages = box(likedMessages, joinMessages(initial.likedMessages()), x, y + 44, 340, "frase 1 | frase 2");
        dislikedMessages = box(dislikedMessages, joinMessages(initial.dislikedMessages()), x, y + 74, 340, "frase 1 | frase 2");
        unknownMessages = box(unknownMessages, joinMessages(initial.unknownMessages()), x, y + 104, 340, "frase 1 | frase 2");
        cooldownMessages = box(cooldownMessages, joinMessages(initial.cooldownMessages()), x, y + 134, 340, "frase 1 | frase 2");
        rewardMessages = box(rewardMessages, joinMessages(initial.rewardMessages()), x, y + 164, 165, "frase 1 | frase 2");
        noRewardMessages = box(noRewardMessages, joinMessages(initial.noRewardMessages()), x + 175, y + 164, 165, "frase 1 | frase 2");
    }

    private EditBox box(EditBox existing, String value, int x, int y, int width, String hint) {
        EditBox box = existing == null ? new EditBox(Minecraft.getInstance().font, x, y, width, 20, Component.literal(hint)) : existing;
        box.setX(x);
        box.setY(y);
        box.setWidth(width);
        if (existing == null) {
            box.setMaxLength(2048);
            box.setValue(value);
        }
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        PacketDistributor.sendToServer(new SaveNpcGiftEditorPayload(initial.entityId(), enabled.selected(), splitCsv(value(favoriteItems)), splitCsv(value(likedItems)), splitCsv(value(dislikedItems)), parseRewards(value(rewards)), parseDouble(value(rewardChance), initial.rewardChance()), parseInt(value(cooldownSeconds), initial.cooldownSeconds()), selected(consumeFavorite, initial.consumeFavorite()), selected(consumeLiked, initial.consumeLiked()), selected(consumeDisliked, initial.consumeDisliked()), splitMessages(value(favoriteMessages)), splitMessages(value(likedMessages)), splitMessages(value(dislikedMessages)), splitMessages(value(unknownMessages)), splitMessages(value(cooldownMessages)), splitMessages(value(rewardMessages)), splitMessages(value(noRewardMessages))));
        onClose();
    }

    private boolean selected(Checkbox checkbox, boolean fallback) { return checkbox == null ? fallback : checkbox.selected(); }
    private String value(EditBox box) { return box == null ? "" : box.getValue(); }
    private String join(List<String> values) { return String.join(", ", values); }
    private String joinMessages(List<String> values) { return String.join(" | ", values); }
    private List<String> splitCsv(String value) { return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isBlank()).distinct().toList(); }
    private List<String> splitMessages(String value) { return Arrays.stream(value.split("\\|")).map(String::strip).filter(s -> !s.isBlank()).toList(); }
    private double parseDouble(String value, double fallback) { try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return fallback; } }
    private int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }

    private String rewardsText(List<NpcGiftReward> values) {
        return String.join("; ", values.stream().map(r -> r.item() + ":" + r.min() + ":" + r.max() + ":" + r.weight()).toList());
    }

    private List<NpcGiftReward> parseRewards(String value) {
        List<NpcGiftReward> parsed = new ArrayList<>();
        for (String entry : value.split(";")) {
            String[] parts = entry.strip().split(":");
            if (parts.length < 4) continue;
            String item = parts[0] + ":" + parts[1];
            try {
                int min = Math.max(1, Integer.parseInt(parts[2]));
                int max = Math.max(min, Integer.parseInt(parts[3]));
                int weight = parts.length >= 5 ? Math.max(1, Integer.parseInt(parts[4])) : 1;
                parsed.add(new NpcGiftReward(item, min, max, weight));
            } catch (NumberFormatException ignored) { }
        }
        return parsed;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 170;
        int y = height / 2 - 112;
        graphics.drawString(font, title, width / 2 - font.width(title) / 2, y - 13, 0xFFFFFF);
        if (page == Page.ITEMS) {
            graphics.drawString(font, "Favoritos (IDs separados por coma)", x, y + 23, 0xA0FFA0);
            graphics.drawString(font, "Gustan (IDs separados por coma)", x, y + 63, 0xA0A0FF);
            graphics.drawString(font, "Disgustan (IDs separados por coma)", x, y + 103, 0xFFA0A0);
        } else if (page == Page.REWARDS) {
            graphics.drawString(font, "Prob. recompensa %", x, y + 23, 0xA0A0A0);
            graphics.drawString(font, "Cooldown", x + 110, y + 23, 0xA0A0A0);
            graphics.drawString(font, "Recompensas: minecraft:item:min:max:peso; ...", x, y + 67, 0xA0A0A0);
        } else {
            graphics.drawString(font, "Mensajes separados con | para cada resultado", x, y + 23, 0xA0A0A0);
            graphics.drawString(font, "favorite", x + 345, y + 47, 0xA0FFA0);
            graphics.drawString(font, "liked", x + 345, y + 77, 0xA0A0FF);
            graphics.drawString(font, "disliked", x + 345, y + 107, 0xFFA0A0);
            graphics.drawString(font, "unknown", x + 345, y + 137, 0xA0A0A0);
            graphics.drawString(font, "cooldown", x + 345, y + 167, 0xA0A0A0);
            graphics.drawString(font, "reward / noReward", x, y + 187, 0xA0A0A0);
        }
    }
}
