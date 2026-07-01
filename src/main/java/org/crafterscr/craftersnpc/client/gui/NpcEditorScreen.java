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
        int x = width / 2 - 110;
        int y = height / 2 - 105;
        addLabeledBox(npcId, x, y + 14);
        skinButton = Button.builder(Component.literal(displayValue(skinId, "(sin skin)")), b -> cycleSkin()).bounds(x, y + 44, 120, 20).build();
        addRenderableWidget(skinButton);
        addLabeledBox(speed, x, y + 74);
        routeButton = Button.builder(Component.literal(displayValue(routeId, "(sin ruta)")), b -> cycleRoute()).bounds(x, y + 104, 120, 20).build();
        addRenderableWidget(routeButton);
        slimModel = Checkbox.builder(Component.literal("Modelo slim"), font).pos(x + 135, y + 14).selected(this.slimModel == null ? initialSlimModel : this.slimModel.selected()).build();
        nightMode = Checkbox.builder(Component.literal("Modo nocturno"), font).pos(x + 135, y + 44).selected(this.nightMode == null ? initialNightMode : this.nightMode.selected()).build();
        addRenderableWidget(slimModel);
        addRenderableWidget(nightMode);
        addRenderableWidget(Button.builder(Component.literal("Temperamento: " + temperament.id()), b -> cycleTemperament(b)).bounds(x + 135, y + 74, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, y + 145, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, y + 145, 100, 20).build());
    }

    private void addLabeledBox(EditBox box, int x, int y) { box.setX(x); box.setY(y); addRenderableWidget(box); }
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
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 110;
        int y = height / 2 - 105;
        graphics.drawString(font, title, width / 2 - font.width(title) / 2, y - 12, 0xFFFFFF);
        graphics.drawString(font, "Id del NPC", x, y + 3, 0xA0A0A0);
        graphics.drawString(font, "Skin", x, y + 33, 0xA0A0A0);
        graphics.drawString(font, "Velocidad", x, y + 63, 0xA0A0A0);
        graphics.drawString(font, "Ruta asignada", x, y + 93, 0xA0A0A0);
    }
}
