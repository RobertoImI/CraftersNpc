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
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 286;
    private enum Page { ITEMS, REWARDS, MESSAGES }

    private final OpenNpcGiftEditorPayload initial;
    private Page page = Page.ITEMS;
    private Checkbox enabled;
    private Checkbox consumeFavorite;
    private Checkbox consumeLiked;
    private EditBox favoriteItems;
    private EditBox likedItems;
    private EditBox rewardChance;
    private EditBox cooldownSeconds;
    private EditBox rewards;
    private EditBox favoriteMessages;
    private EditBox likedMessages;
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
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = Math.max(8, (height - PANEL_HEIGHT) / 2);
        int x = panelX + 14;
        int tabsY = panelY + 34;
        Button itemsTab = Button.builder(Component.literal("Items"), b -> switchPage(Page.ITEMS)).bounds(x, tabsY, 82, 20).build();
        Button rewardsTab = Button.builder(Component.literal("Recompensas"), b -> switchPage(Page.REWARDS)).bounds(x + 86, tabsY, 108, 20).build();
        Button messagesTab = Button.builder(Component.literal("Mensajes"), b -> switchPage(Page.MESSAGES)).bounds(x + 198, tabsY, 88, 20).build();
        itemsTab.active = page != Page.ITEMS;
        rewardsTab.active = page != Page.REWARDS;
        messagesTab.active = page != Page.MESSAGES;
        addRenderableWidget(itemsTab);
        addRenderableWidget(rewardsTab);
        addRenderableWidget(messagesTab);
        enabled = Checkbox.builder(Component.literal("Activo"), font).pos(x + 298, tabsY).selected(enabled == null ? initial.enabled() : enabled.selected()).build();
        addRenderableWidget(enabled);
        int contentY = tabsY + 34;
        if (page == Page.ITEMS) initItems(x + 8, contentY);
        if (page == Page.REWARDS) initRewards(x + 8, contentY);
        if (page == Page.MESSAGES) initMessages(x + 8, contentY);
        int footerY = panelY + PANEL_HEIGHT - 30;
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, footerY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, footerY, 100, 20).build());
    }

    private void switchPage(Page page) {
        this.page = page;
        refreshWidgets();
    }

    private void initItems(int x, int y) {
        favoriteItems = box(favoriteItems, join(initial.favoriteItems()), x, y + 12, 376, "minecraft:diamond, minecraft:emerald");
        likedItems = box(likedItems, join(initial.likedItems()), x, y + 52, 376, "minecraft:bread, minecraft:apple");
        consumeFavorite = Checkbox.builder(Component.literal("Consumir favoritos"), font).pos(x, y + 88).selected(consumeFavorite == null ? initial.consumeFavorite() : consumeFavorite.selected()).build();
        consumeLiked = Checkbox.builder(Component.literal("Consumir gustados"), font).pos(x + 190, y + 88).selected(consumeLiked == null ? initial.consumeLiked() : consumeLiked.selected()).build();
        addRenderableWidget(consumeFavorite);
        addRenderableWidget(consumeLiked);
    }

    private void initRewards(int x, int y) {
        rewardChance = box(rewardChance, String.format(Locale.ROOT, "%.2f", initial.rewardChance()), x, y + 12, 120, "0-100");
        cooldownSeconds = box(cooldownSeconds, Integer.toString(initial.cooldownSeconds()), x + 140, y + 12, 120, "segundos");
        rewards = box(rewards, rewardsText(initial.rewards()), x, y + 56, 376, "item:min:max:peso; item:min:max:peso");
    }

    private void initMessages(int x, int y) {
        favoriteMessages = box(favoriteMessages, joinMessages(initial.favoriteMessages()), x, y + 11, 376, "frase 1, frase 2");
        likedMessages = box(likedMessages, joinMessages(initial.likedMessages()), x, y + 47, 376, "frase 1, frase 2");
        unknownMessages = box(unknownMessages, joinMessages(initial.unknownMessages()), x, y + 83, 376, "frase 1, frase 2");
        cooldownMessages = box(cooldownMessages, joinMessages(initial.cooldownMessages()), x, y + 119, 376, "frase 1, frase 2");
        rewardMessages = box(rewardMessages, joinMessages(initial.rewardMessages()), x, y + 155, 183, "frase 1, frase 2");
        noRewardMessages = box(noRewardMessages, joinMessages(initial.noRewardMessages()), x + 193, y + 155, 183, "frase 1, frase 2");
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
        PacketDistributor.sendToServer(new SaveNpcGiftEditorPayload(initial.entityId(), enabled.selected(), splitCsv(value(favoriteItems)), splitCsv(value(likedItems)), parseRewards(value(rewards)), parseDouble(value(rewardChance), initial.rewardChance()), parseInt(value(cooldownSeconds), initial.cooldownSeconds()), selected(consumeFavorite, initial.consumeFavorite()), selected(consumeLiked, initial.consumeLiked()), splitMessages(value(favoriteMessages)), splitMessages(value(likedMessages)), splitMessages(value(unknownMessages)), splitMessages(value(cooldownMessages)), splitMessages(value(rewardMessages)), splitMessages(value(noRewardMessages))));
        onClose();
    }

    private boolean selected(Checkbox checkbox, boolean fallback) { return checkbox == null ? fallback : checkbox.selected(); }
    private String value(EditBox box) { return box == null ? "" : box.getValue(); }
    private String join(List<String> values) { return String.join(", ", values); }
    private String joinMessages(List<String> values) { return String.join(", ", values); }
    private List<String> splitCsv(String value) { return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isBlank()).distinct().toList(); }
    private List<String> splitMessages(String value) { return Arrays.stream(value.split("[,|]")).map(String::strip).filter(s -> !s.isBlank()).toList(); }
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
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = Math.max(8, (height - PANEL_HEIGHT) / 2);
        int x = panelX + 22;
        int y = panelY + 68;
        NpcUiTheme.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        NpcUiTheme.title(graphics, font, title, width / 2, panelY + 12);
        NpcUiTheme.section(graphics, panelX + 14, y - 8, PANEL_WIDTH - 28, page == Page.MESSAGES ? 184 : 176);
        if (page == Page.ITEMS) {
            NpcUiTheme.label(graphics, font, "Favoritos (IDs separados por coma (,))", x, y);
            NpcUiTheme.label(graphics, font, "Gustan (IDs separados por coma (,))", x, y + 40);
        } else if (page == Page.REWARDS) {
            NpcUiTheme.label(graphics, font, "Prob. recompensa %", x, y);
            NpcUiTheme.label(graphics, font, "Cooldown", x + 140, y);
            NpcUiTheme.label(graphics, font, "Recompensas · minecraft:item:min:max:peso; ...", x, y + 44);
        } else {
            NpcUiTheme.label(graphics, font, "Favoritos · separa variantes con coma (,)", x, y);
            NpcUiTheme.label(graphics, font, "Gustados", x, y + 36);
            NpcUiTheme.label(graphics, font, "Desconocidos", x, y + 72);
            NpcUiTheme.label(graphics, font, "En cooldown", x, y + 108);
            NpcUiTheme.label(graphics, font, "Con recompensa", x, y + 144);
            NpcUiTheme.label(graphics, font, "Sin recompensa", x + 193, y + 144);
        }
        NpcUiTheme.widgets(graphics, renderables, mouseX, mouseY, partialTick);
    }
}
