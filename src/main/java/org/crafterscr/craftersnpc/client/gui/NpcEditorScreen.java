package org.crafterscr.craftersnpc.client.gui;

import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.network.SaveNpcEditorPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NpcEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 238;
    private final int entityId;
    private final boolean initialSlimModel;
    private final boolean initialNightMode;
    private final boolean initialDamageEnabled;
    private final List<String> skinOptions;
    private final List<String> routeOptions;
    private EditBox npcId;
    private String skinId;
    private EditBox speed;
    private String routeId;
    private Checkbox slimModel;
    private Checkbox nightMode;
    private CnpcEntity.Temperament temperament;
    private Button skinButton;
    private Button routeButton;

    public NpcEditorScreen(int entityId, String npcId, String skinId, boolean slimModel, double speed,
                           String temperament, String routeId, boolean nightMode, boolean damageEnabled,
                           List<String> skinIds, List<String> routeIds) {
        super(Component.literal("Editor de NPC"));
        this.entityId = entityId;
        this.initialSlimModel = slimModel;
        this.initialNightMode = nightMode;
        this.temperament = CnpcEntity.Temperament.fromId(temperament);
        this.initialDamageEnabled = damageEnabled;
        this.skinId = skinId == null || skinId.isBlank() ? "steve" : skinId;
        this.routeId = routeId == null ? "" : routeId;
        this.skinOptions = optionsWithCurrent(this.skinId, skinIds, false);
        this.routeOptions = optionsWithCurrent(this.routeId, routeIds, true);
        this.npcId = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal("Id"));
        this.npcId.setValue(npcId);
        this.speed = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal("Velocidad"));
        this.speed.setValue(String.format(Locale.ROOT, "%.2f", speed));
    }

    private static List<String> optionsWithCurrent(String current, List<String> values, boolean includeEmpty) {
        List<String> options = new ArrayList<>();
        if (includeEmpty) options.add("");
        if (current != null && !current.isBlank()) options.add(current);
        for (String value : values) if (value != null && !options.contains(value)) options.add(value);
        if (options.isEmpty()) options.add(includeEmpty ? "" : "steve");
        return options;
    }

    @Override
    protected void init() {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = Math.max(8, (height - PANEL_HEIGHT) / 2);
        int x = panelX + 18;
        int y = panelY + 38;
        int columnWidth = 152;
        addLabeledBox(npcId, x, y + 12, columnWidth);
        skinButton = Button.builder(Component.literal(displayValue(skinId, "(sin skin)")), b -> cycleSkin()).bounds(x, y + 48, columnWidth, 20).build();
        addRenderableWidget(skinButton);
        addLabeledBox(speed, x, y + 84, columnWidth);
        routeButton = Button.builder(Component.literal(displayValue(routeId, "(sin ruta)")), b -> cycleRoute()).bounds(x, y + 120, columnWidth, 20).build();
        addRenderableWidget(routeButton);
        int rightX = x + 174;
        slimModel = Checkbox.builder(Component.literal("Modelo slim"), font).pos(rightX, y + 12).selected(this.slimModel == null ? initialSlimModel : this.slimModel.selected()).build();
        nightMode = Checkbox.builder(Component.literal("Modo nocturno"), font).pos(rightX, y + 48).selected(this.nightMode == null ? initialNightMode : this.nightMode.selected()).build();
        addRenderableWidget(slimModel);
        addRenderableWidget(nightMode);
        addRenderableWidget(Button.builder(Component.literal("Temperamento: " + temperament.id()), b -> cycleTemperament(b)).bounds(rightX, y + 84, 150, 20).build());
        int footerY = panelY + PANEL_HEIGHT - 32;
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, footerY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, footerY, 100, 20).build());
    }

    private void addLabeledBox(EditBox box, int x, int y, int width) { box.setX(x); box.setY(y); box.setWidth(width); addRenderableWidget(box); }
    private String displayValue(String value, String emptyLabel) { return value == null || value.isBlank() ? emptyLabel : value; }
    private void cycleSkin() { skinId = nextOption(skinOptions, skinId); skinButton.setMessage(Component.literal(displayValue(skinId, "(sin skin)"))); }
    private void cycleRoute() { routeId = nextOption(routeOptions, routeId); routeButton.setMessage(Component.literal(displayValue(routeId, "(sin ruta)"))); }
    private String nextOption(List<String> options, String current) { return options.get((Math.max(0, options.indexOf(current)) + 1) % options.size()); }

    private void cycleTemperament(Button button) {
        CnpcEntity.Temperament[] values = CnpcEntity.Temperament.values();
        temperament = values[(temperament.ordinal() + 1) % values.length];
        button.setMessage(Component.literal("Temperamento: " + temperament.id()));
    }

    private void save() {
        double parsedSpeed;
        try { parsedSpeed = Double.parseDouble(speed.getValue()); } catch (NumberFormatException ignored) { parsedSpeed = CnpcEntity.DEFAULT_WALK_SPEED; }
        PacketDistributor.sendToServer(new SaveNpcEditorPayload(entityId, npcId.getValue(), skinId, slimModel.selected(), parsedSpeed,
                temperament.id(), routeId, nightMode.selected(), initialDamageEnabled));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = Math.max(8, (height - PANEL_HEIGHT) / 2);
        int x = panelX + 18;
        int y = panelY + 38;
        NpcUiTheme.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        NpcUiTheme.title(graphics, font, title, width / 2, panelY + 12);
        NpcUiTheme.section(graphics, x - 7, y - 7, 166, 154);
        NpcUiTheme.section(graphics, x + 167, y - 7, 168, 118);
        NpcUiTheme.label(graphics, font, "Id del NPC", x, y);
        NpcUiTheme.label(graphics, font, "Skin", x, y + 36);
        NpcUiTheme.label(graphics, font, "Velocidad", x, y + 72);
        NpcUiTheme.label(graphics, font, "Ruta asignada", x, y + 108);
        NpcUiTheme.widgets(graphics, renderables, mouseX, mouseY, partialTick);
    }
}
