package org.crafterscr.craftersnpc.client.gui;

import org.crafterscr.craftersnpc.dialogue.DialogueBankStorage;
import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.network.SaveNpcDialoguePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.FormattedCharSequence;

public class NpcDialogueEditorScreen extends Screen {
    private final int entityId;
    private final String npcId;
    private final List<DialogueEntry> entries;
    private final List<String> bankIds;
    private final Map<String, List<DialogueEntry>> bankEntries;
    private int selectedIndex;
    private int scroll;
    private boolean editingBank;
    private String bankValue = "";
    private Button bankButton;
    private Button modeButton;
    private MultiLineEditBox text;

    public NpcDialogueEditorScreen(int entityId, String npcId, String bankId, List<DialogueEntry> entries, List<String> bankIds, Map<String, List<DialogueEntry>> bankEntries) {
        super(Component.literal("Diálogos de NPC"));
        this.entityId = entityId;
        this.npcId = npcId;
        this.entries = new ArrayList<>(entries);
        this.bankIds = List.copyOf(bankIds);
        this.bankEntries = new HashMap<>();
        bankEntries.forEach((id, bankPhrases) -> this.bankEntries.put(id, new ArrayList<>(bankPhrases)));
        this.bankValue = bankId;
    }

    @Override
    protected void init() {
        int left = 18;
        int editorX = width / 2 + 8;
        int top = 34;
        int editorWidth = Math.max(180, width / 2 - 26);
        bankButton = Button.builder(Component.literal(displayBank()), b -> cycleBank()).bounds(editorX, top, editorWidth, 20).build();
        addRenderableWidget(bankButton);
        modeButton = Button.builder(Component.literal(displayMode()), b -> toggleMode()).bounds(editorX, top + 24, editorWidth, 20).build();
        addRenderableWidget(modeButton);
        text = new MultiLineEditBox(Minecraft.getInstance().font, editorX, top + 62, editorWidth, 80, Component.literal("Texto"), Component.literal("Escribe una frase larga o párrafo"));
        text.setCharacterLimit(CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH);
        addRenderableWidget(text);
        int smallGap = 6;
        int smallWidth = (editorWidth - smallGap * 2) / 3;
        addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> applyEditor()).bounds(editorX, top + 148, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Añadir"), b -> addEntry()).bounds(editorX + smallWidth + smallGap, top + 148, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Borrar"), b -> deleteEntry()).bounds(editorX + (smallWidth + smallGap) * 2, top + 148, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), b -> moveSelected(-1)).bounds(left, height - 58, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveSelected(1)).bounds(left + 28, height - 58, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(width / 2 - 105, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(width / 2 + 5, height - 28, 100, 20).build());
        loadSelected();
    }

    private List<DialogueEntry> activeEntries() {
        if (!editingBank) return entries;
        return bankEntries.computeIfAbsent(bankValue, id -> new ArrayList<>());
    }

    private void resetSelection() { selectedIndex = 0; scroll = 0; loadSelected(); }
    private void loadSelected() {
        List<DialogueEntry> active = activeEntries();
        if (active.isEmpty()) { text.setValue(""); return; }
        selectedIndex = Math.max(0, Math.min(selectedIndex, active.size() - 1));
        text.setValue(active.get(selectedIndex).text());
    }

    private String displayBank() { return bankValue == null || bankValue.isBlank() ? "Banco: (sin banco)" : "Banco: " + bankValue; }
    private String displayMode() { return editingBank ? "Editando frases del banco" : "Editando frases propias"; }

    private void cycleBank() {
        List<String> options = new ArrayList<>();
        options.add("");
        for (String bankId : bankIds) if (!options.contains(bankId)) options.add(bankId);
        int index = Math.max(0, options.indexOf(bankValue));
        bankValue = options.get((index + 1) % options.size());
        if (bankValue.isBlank()) editingBank = false;
        bankButton.setMessage(Component.literal(displayBank()));
        modeButton.setMessage(Component.literal(displayMode()));
        resetSelection();
    }

    private void toggleMode() {
        if (bankValue == null || bankValue.isBlank()) return;
        editingBank = !editingBank;
        modeButton.setMessage(Component.literal(displayMode()));
        resetSelection();
    }

    private DialogueEntry editorEntry() { return new DialogueEntry(text.getValue()); }
    private void applyEditor() { List<DialogueEntry> active = activeEntries(); if (!active.isEmpty()) active.set(selectedIndex, editorEntry()); }
    private void addEntry() { List<DialogueEntry> active = activeEntries(); if (active.size() < activeLimit() && !DialogueEntry.normalizeText(text.getValue()).isEmpty()) { active.add(editorEntry()); selectedIndex = active.size() - 1; loadSelected(); } }
    private int activeLimit() { return editingBank ? DialogueBankStorage.MAX_BANK_PHRASES : CnpcEntity.MAX_DIALOGUE_PHRASES; }
    private void deleteEntry() { List<DialogueEntry> active = activeEntries(); if (!active.isEmpty()) { active.remove(selectedIndex); selectedIndex = Math.max(0, selectedIndex - 1); loadSelected(); } }
    private void moveSelected(int delta) { List<DialogueEntry> active = activeEntries(); int to = selectedIndex + delta; if (to >= 0 && to < active.size()) { active.add(to, active.remove(selectedIndex)); selectedIndex = to; } }
    private void save() { applyEditor(); PacketDistributor.sendToServer(new SaveNpcDialoguePayload(entityId, bankValue, entries, bankEntries)); onClose(); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 18;
        int listY = 34;
        int listW = width / 2 - 36;
        List<DialogueEntry> active = activeEntries();
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= height - 70) {
            int clicked = scroll + ((int) mouseY - listY) / 24;
            if (clicked >= 0 && clicked < active.size()) { selectedIndex = clicked; loadSelected(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, activeEntries().size() - 1), scroll - (int) dy));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        List<DialogueEntry> active = activeEntries();
        int listX = 18;
        int listW = width / 2 - 36;
        int editorX = width / 2 + 8;
        int editorWidth = Math.max(180, width / 2 - 26);
        NpcUiTheme.panel(graphics, 10, 8, width - 20, height - 44);
        NpcUiTheme.title(graphics, font, Component.literal(title.getString() + ": " + npcId), width / 2, 15);
        NpcUiTheme.section(graphics, listX - 6, 30, listW + 12, height - 104);
        NpcUiTheme.section(graphics, editorX - 6, 30, editorWidth + 12, 180);
        String label = editingBank ? "frases del banco " + bankValue : "frases propias";
        String status = active.size() + "/" + activeLimit() + " " + label + " · Bancos: " + (bankIds.isEmpty() ? "ninguno" : String.join(", ", bankIds));
        NpcUiTheme.label(graphics, font, font.plainSubstrByWidth(status, width - 36), 18, height - 70);
        NpcUiTheme.label(graphics, font, "Texto (máx. " + CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH + " caracteres)", editorX, 84);
        int y = 34;
        int maxRows = Math.max(1, (height - 104) / 24);
        for (int i = 0; i < maxRows && scroll + i < active.size(); i++) {
            int index = scroll + i;
            DialogueEntry entry = active.get(index);
            int rowY = y + i * 24;
            graphics.fill(listX, rowY, listX + listW, rowY + 22, index == selectedIndex ? 0x80336699 : 0x80202020);
            List<FormattedCharSequence> lines = font.split(Component.literal("#" + (index + 1) + " " + entry.text()), listW - 8);
            if (!lines.isEmpty()) graphics.drawString(font, lines.get(0), listX + 4, rowY + 2, 0xFFFFFF);
            if (lines.size() > 1) graphics.drawString(font, lines.get(1), listX + 4, rowY + 12, 0xDDDDDD);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
