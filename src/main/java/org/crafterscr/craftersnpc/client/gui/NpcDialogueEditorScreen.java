package org.crafterscr.craftersnpc.client.gui;

import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.network.SaveNpcDialoguePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.FormattedCharSequence;

public class NpcDialogueEditorScreen extends Screen {
    private final int entityId;
    private final String npcId;
    private final List<DialogueEntry> entries;
    private final List<String> bankIds;
    private int selectedIndex;
    private int scroll;
    private String bankValue = "";
    private Button bankButton;
    private EditBox text;

    public NpcDialogueEditorScreen(int entityId, String npcId, String bankId, List<DialogueEntry> entries, List<String> bankIds) {
        super(Component.literal("Diálogos de NPC"));
        this.entityId = entityId;
        this.npcId = npcId;
        this.entries = new ArrayList<>(entries);
        this.bankIds = List.copyOf(bankIds);
        this.bankValue = bankId;
    }

    @Override
    protected void init() {
        int left = 18;
        int editorX = width / 2 + 8;
        int top = 34;
        bankButton = Button.builder(Component.literal(displayBank()), b -> cycleBank()).bounds(editorX, top, 210, 20).build();
        addRenderableWidget(bankButton);
        text = box(editorX, top + 34, 210, "Texto");
        addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> applyEditor()).bounds(editorX, top + 68, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Añadir"), b -> addEntry()).bounds(editorX + 76, top + 68, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Borrar"), b -> deleteEntry()).bounds(editorX + 146, top + 68, 64, 20).build());
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
            text.setValue("");
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
        text.setValue(entries.get(selectedIndex).text());
    }

    private String displayBank() { return bankValue == null || bankValue.isBlank() ? "Banco: (sin banco)" : "Banco: " + bankValue; }

    private void cycleBank() {
        List<String> options = new ArrayList<>();
        options.add("");
        for (String bankId : bankIds) if (!options.contains(bankId)) options.add(bankId);
        int index = Math.max(0, options.indexOf(bankValue));
        bankValue = options.get((index + 1) % options.size());
        bankButton.setMessage(Component.literal(displayBank()));
    }

    private DialogueEntry editorEntry() { return new DialogueEntry(text.getValue()); }
    private void applyEditor() { if (!entries.isEmpty()) entries.set(selectedIndex, editorEntry()); }
    private void addEntry() { if (entries.size() < CnpcEntity.MAX_DIALOGUE_PHRASES && !DialogueEntry.normalizeText(text.getValue()).isEmpty()) { entries.add(editorEntry()); selectedIndex = entries.size() - 1; loadSelected(); } }
    private void deleteEntry() { if (!entries.isEmpty()) { entries.remove(selectedIndex); selectedIndex = Math.max(0, selectedIndex - 1); loadSelected(); } }
    private void moveSelected(int delta) { int to = selectedIndex + delta; if (to >= 0 && to < entries.size()) { entries.add(to, entries.remove(selectedIndex)); selectedIndex = to; } }
    private void save() { applyEditor(); PacketDistributor.sendToServer(new SaveNpcDialoguePayload(entityId, bankValue, entries)); onClose(); }

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
            List<FormattedCharSequence> lines = font.split(Component.literal("#" + (index + 1) + " " + entry.text()), listW - 8);
            if (!lines.isEmpty()) graphics.drawString(font, lines.get(0), listX + 4, rowY + 2, 0xFFFFFF);
            if (lines.size() > 1) graphics.drawString(font, lines.get(1), listX + 4, rowY + 12, 0xDDDDDD);
        }
    }
}
