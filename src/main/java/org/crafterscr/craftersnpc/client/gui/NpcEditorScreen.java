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

import java.util.Locale;

public class NpcEditorScreen extends Screen {
    private final int entityId;
    private final boolean initialSlimModel;
    private final boolean initialNightMode;
    private final boolean initialDamageEnabled;
    private EditBox npcId;
    private EditBox skinId;
    private EditBox speed;
    private EditBox routeId;
    private Checkbox slimModel;
    private Checkbox nightMode;
    private Checkbox damageEnabled;
    private CnpcEntity.Temperament temperament;

    public NpcEditorScreen(int entityId, String npcId, String skinId, boolean slimModel, double speed,
                           String temperament, String routeId, boolean nightMode, boolean damageEnabled) {
        super(Component.literal("Editor de NPC"));
        this.entityId = entityId;
        this.initialSlimModel = slimModel;
        this.initialNightMode = nightMode;
        this.temperament = CnpcEntity.Temperament.fromId(temperament);
        this.initialDamageEnabled = damageEnabled;
        this.npcId = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal("Id"));
        this.npcId.setValue(npcId);
        this.skinId = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal("Skin"));
        this.skinId.setValue(skinId);
        this.speed = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal("Velocidad"));
        this.speed.setValue(String.format(Locale.ROOT, "%.2f", speed));
        this.routeId = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal("Ruta"));
        this.routeId.setValue(routeId);
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        int y = height / 2 - 105;
        addLabeledBox(npcId, x, y + 14);
        addLabeledBox(skinId, x, y + 44);
        addLabeledBox(speed, x, y + 74);
        addLabeledBox(routeId, x, y + 104);
        slimModel = Checkbox.builder(Component.literal("Modelo slim"), font).pos(x + 135, y + 14).selected(this.slimModel == null ? initialSlimModel : this.slimModel.selected()).build();
        nightMode = Checkbox.builder(Component.literal("Modo nocturno"), font).pos(x + 135, y + 44).selected(this.nightMode == null ? initialNightMode : this.nightMode.selected()).build();
        damageEnabled = Checkbox.builder(Component.literal("Daño habilitado"), font).pos(x + 135, y + 74).selected(damageEnabled == null ? initialDamageEnabled : damageEnabled.selected()).build();
        addRenderableWidget(slimModel);
        addRenderableWidget(nightMode);
        addRenderableWidget(damageEnabled);
        addRenderableWidget(Button.builder(Component.literal("Temperamento: " + temperament.id()), b -> cycleTemperament(b)).bounds(x + 135, y + 104, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, y + 145, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, y + 145, 100, 20).build());
    }

    private void addLabeledBox(EditBox box, int x, int y) {
        box.setX(x);
        box.setY(y);
        addRenderableWidget(box);
    }

    private void cycleTemperament(Button button) {
        CnpcEntity.Temperament[] values = CnpcEntity.Temperament.values();
        temperament = values[(temperament.ordinal() + 1) % values.length];
        button.setMessage(Component.literal("Temperamento: " + temperament.id()));
    }

    private void save() {
        double parsedSpeed;
        try {
            parsedSpeed = Double.parseDouble(speed.getValue());
        } catch (NumberFormatException ignored) {
            parsedSpeed = CnpcEntity.DEFAULT_WALK_SPEED;
        }
        PacketDistributor.sendToServer(new SaveNpcEditorPayload(entityId, npcId.getValue(), skinId.getValue(), slimModel.selected(), parsedSpeed,
                temperament.id(), routeId.getValue(), nightMode.selected(), damageEnabled.selected()));
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
