package org.crafterscr.craftersnpc.client.gui;

import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.network.OpenNpcDialogueEditorPayload.PlayerReputation;
import org.crafterscr.craftersnpc.network.SaveNpcDialoguePayload;

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

public class NpcDialogueEditorScreen extends Screen {
    private final int entityId;
    private final String npcId;
    private final List<DialogueEntry> entries;
    private final List<String> bankIds;
    private final List<PlayerReputation> reputations;
    private int selectedIndex;
    private int scroll;
    private String bankValue = "";
    private EditBox bank;
    private EditBox text;
    private EditBox category;
    private EditBox weight;
    private EditBox priority;
    private EditBox cooldown;
    private EditBox minReputation;
    private EditBox maxReputation;
    private Checkbox oncePerPlayer;

    public NpcDialogueEditorScreen(int entityId, String npcId, String bankId, List<DialogueEntry> entries, List<String> bankIds, List<PlayerReputation> reputations) {
        super(Component.literal("Diálogos de NPC"));
        this.entityId = entityId;
        this.npcId = npcId;
        this.entries = new ArrayList<>(entries);
        this.bankIds = List.copyOf(bankIds);
        this.bankValue = bankId;
        this.reputations = List.copyOf(reputations);
    }

    @Override
    protected void init() {
        int left = 18;
        int editorX = width / 2 + 8;
        int top = 34;
        bank = box(editorX, top, 210, "Banco");
        bank.setValue(bankValue);
        text = box(editorX, top + 34, 210, "Texto");
        category = box(editorX, top + 68, 100, "Categoría");
        weight = box(editorX + 110, top + 68, 45, "Peso");
        priority = box(editorX + 165, top + 68, 45, "Prioridad");
        cooldown = box(editorX, top + 102, 65, "Cooldown");
        minReputation = box(editorX + 75, top + 102, 60, "Rep. mín.");
        maxReputation = box(editorX + 145, top + 102, 60, "Rep. máx.");
        oncePerPlayer = Checkbox.builder(Component.literal("Una vez por jugador"), font).pos(editorX, top + 132).selected(false).build();
        addRenderableWidget(oncePerPlayer);
        addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> applyEditor()).bounds(editorX, top + 160, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Añadir"), b -> addEntry()).bounds(editorX + 76, top + 160, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Borrar"), b -> deleteEntry()).bounds(editorX + 146, top + 160, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), b -> moveSelected(-1)).bounds(left, height - 58, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveSelected(1)).bounds(left + 28, height - 58, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, height - 28, 100, 20).build());
        loadSelected();
    }

    private EditBox box(int x, int y, int w, String label) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, w, 20, Component.literal(label));
        addRenderableWidget(box);
        return box;
    }

    private void loadSelected() {
        if (entries.isEmpty()) {
            text.setValue(""); category.setValue(DialogueEntry.GENERIC_CATEGORY); weight.setValue("1"); priority.setValue("0"); cooldown.setValue("0"); minReputation.setValue("-100"); maxReputation.setValue("100");
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
        DialogueEntry entry = entries.get(selectedIndex);
        text.setValue(entry.text()); category.setValue(entry.category()); weight.setValue(Integer.toString(entry.weight())); priority.setValue(Integer.toString(entry.priority())); cooldown.setValue(Integer.toString(entry.cooldownTicks())); minReputation.setValue(Integer.toString(entry.minReputation())); maxReputation.setValue(Integer.toString(entry.maxReputation()));
        oncePerPlayer.onPress();
        if (oncePerPlayer.selected() != entry.oncePerPlayer()) oncePerPlayer.onPress();
    }

    private DialogueEntry editorEntry() {
        return new DialogueEntry(text.getValue(), DialogueEntry.normalizeCategory(category.getValue()), parse(weight.getValue(), 1), parse(minReputation.getValue(), -100), parse(maxReputation.getValue(), 100), oncePerPlayer.selected(), parse(cooldown.getValue(), 0), parse(priority.getValue(), 0));
    }

    private int parse(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }
    private void applyEditor() { if (!entries.isEmpty()) entries.set(selectedIndex, editorEntry()); }
    private void addEntry() { if (entries.size() < CnpcEntity.MAX_DIALOGUE_PHRASES && !DialogueEntry.normalizeText(text.getValue()).isEmpty()) { entries.add(editorEntry()); selectedIndex = entries.size() - 1; loadSelected(); } }
    private void deleteEntry() { if (!entries.isEmpty()) { entries.remove(selectedIndex); selectedIndex = Math.max(0, selectedIndex - 1); loadSelected(); } }
    private void moveSelected(int delta) { int to = selectedIndex + delta; if (to >= 0 && to < entries.size()) { entries.add(to, entries.remove(selectedIndex)); selectedIndex = to; } }
    private void save() { applyEditor(); PacketDistributor.sendToServer(new SaveNpcDialoguePayload(entityId, bank.getValue(), entries)); onClose(); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 18;
        int listY = 34;
        int listW = width / 2 - 36;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= height - 70) {
            int clicked = scroll + ((int) mouseY - listY) / 24;
            if (clicked >= 0 && clicked < entries.size()) { selectedIndex = clicked; loadSelected(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, entries.size() - 1), scroll - (int) dy));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title.getString() + ": " + npcId, 18, 14, 0xFFFFFF);
        graphics.drawString(font, entries.size() + "/" + CnpcEntity.MAX_DIALOGUE_PHRASES + " frases propias | Bancos: " + (bankIds.isEmpty() ? "ninguno" : String.join(", ", bankIds)), 18, height - 70, 0xA0A0A0);
        int listX = 18, y = 34, listW = width / 2 - 36;
        int maxRows = Math.max(1, (height - 104) / 24);
        for (int i = 0; i < maxRows && scroll + i < entries.size(); i++) {
            int index = scroll + i;
            DialogueEntry entry = entries.get(index);
            int rowY = y + i * 24;
            graphics.fill(listX, rowY, listX + listW, rowY + 22, index == selectedIndex ? 0x80336699 : 0x80202020);
            graphics.drawString(font, "#" + (index + 1) + " [" + entry.category() + "] " + entry.text(), listX + 4, rowY + 3, 0xFFFFFF);
            graphics.drawString(font, "peso " + entry.weight() + " | pri " + entry.priority() + " | cd " + entry.cooldownTicks() + " | once " + entry.oncePerPlayer() + " | rep " + entry.minReputation() + ".." + entry.maxReputation(), listX + 4, rowY + 13, 0xA0A0A0);
        }
        int rx = width / 2 + 8, ry = 208;
        graphics.drawString(font, "Reputación guardada", rx, ry, 0xFFFFFF);
        for (int i = 0; i < Math.min(6, reputations.size()); i++) {
            PlayerReputation reputation = reputations.get(i);
            graphics.drawString(font, reputation.playerName() + ": " + reputation.reputation() + " rep, " + reputation.interactions() + " inter., última #" + (reputation.lastPhraseIndex() + 1), rx, ry + 14 + i * 10, 0xA0A0A0);
        }
    }
}
