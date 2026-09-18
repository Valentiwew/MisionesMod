package com.misionesmod.client.gui;

import com.misionesmod.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class TriggerDropScreen extends Screen {
    private final Screen parentScreen;

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 225;

    private EditBox xBox;
    private EditBox zBox;

    private static final String[] TIERS = {"comun", "raro", "epico", "legendario", "personalizado"};
    private static final String[] TIER_NAMES = {"Común", "Raro", "Épico", "Legendario", "Personalizado"};
    private int selectedTierIndex = 0; // "comun" por defecto
    private Button tierButton;
    private Button editLootButton;

    private static final String[] TIMER_OPTIONS = {
            "Inmediato",
            "En 5 minutos",
            "En 10 minutos",
            "En 15 minutos",
            "En 30 minutos",
            "Cada 15 min (Bucle)",
            "Cada 30 min (Bucle)",
            "Cada 60 min (Bucle)"
    };
    private static final int[] TIMER_MINUTES = {0, 5, 10, 15, 30, 15, 30, 60};
    private static final boolean[] TIMER_RECURRING = {false, false, false, false, false, true, true, true};
    private int selectedTimerIndex = 0;
    private Button timerButton;

    public TriggerDropScreen(Screen parentScreen) {
        super(Component.literal("Lanzar Drop (Admin)"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        Player player = Minecraft.getInstance().player;
        BlockPos currentPos = player != null ? player.blockPosition() : BlockPos.ZERO;

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldWidth = 206;
        int fieldX = panelX + (PANEL_WIDTH - fieldWidth) / 2;

        // 1. Coordenadas X y Z (con espacio claro debajo de la etiqueta de Y=29)
        int coordWidth = 98;
        int coordY = panelY + 41;
        xBox = new EditBox(this.font, fieldX, coordY, coordWidth, 18, Component.literal("X"));
        xBox.setValue(String.valueOf(currentPos.getX() + 25));
        this.addRenderableWidget(xBox);

        zBox = new EditBox(this.font, fieldX + 108, coordY, coordWidth, 18, Component.literal("Z"));
        zBox.setValue(String.valueOf(currentPos.getZ() + 25));
        this.addRenderableWidget(zBox);

        // 2. Selector de Tier de Drop
        int tierY = panelY + 77;
        tierButton = Button.builder(
                Component.literal("Tipo de Drop: §e" + TIER_NAMES[selectedTierIndex]),
                b -> {
                    selectedTierIndex = (selectedTierIndex + 1) % TIERS.length;
                    tierButton.setMessage(Component.literal("Tipo de Drop: §e" + TIER_NAMES[selectedTierIndex]));
                    updateLootButtonVisibility();
                }
        ).bounds(fieldX, tierY, fieldWidth, 19).build();
        this.addRenderableWidget(tierButton);

        // 3. Selector de Temporizador
        int timerY = panelY + 114;
        timerButton = Button.builder(
                Component.literal("Caída: §e" + TIMER_OPTIONS[selectedTimerIndex]),
                b -> {
                    selectedTimerIndex = (selectedTimerIndex + 1) % TIMER_OPTIONS.length;
                    timerButton.setMessage(Component.literal("Caída: §e" + TIMER_OPTIONS[selectedTimerIndex]));
                }
        ).bounds(fieldX, timerY, fieldWidth, 19).build();
        this.addRenderableWidget(timerButton);

        // 4. Botón editar loot creativo (si es personalizado)
        int editLootY = panelY + 138;
        editLootButton = Button.builder(
                Component.literal("§e📦 Editar Loot en Creativo"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new LootEditorScreen(this, "drop", new java.util.ArrayList<>()));
                    }
                }
        ).bounds(fieldX, editLootY, fieldWidth, 19).build();
        this.addRenderableWidget(editLootButton);
        updateLootButtonVisibility();

        // 5. Botones de acción centrados con margen inferior
        int actionY = panelY + PANEL_HEIGHT - 28;
        Button launchButton = Button.builder(
                Component.literal("§6Lanzar"),
                b -> launchDrop()
        ).bounds(panelX + (PANEL_WIDTH / 2) - 105, actionY, 100, 20).build();
        this.addRenderableWidget(launchButton);

        Button cancelButton = Button.builder(
                Component.literal("Cancelar"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + (PANEL_WIDTH / 2) + 5, actionY, 100, 20).build();
        this.addRenderableWidget(cancelButton);
    }

    private void updateLootButtonVisibility() {
        if (editLootButton != null) {
            editLootButton.visible = TIERS[selectedTierIndex].equalsIgnoreCase("personalizado");
        }
    }

    private void launchDrop() {
        int x = 0, z = 0;
        try { x = Integer.parseInt(xBox.getValue().trim()); } catch (Exception ignored) {}
        try { z = Integer.parseInt(zBox.getValue().trim()); } catch (Exception ignored) {}

        String tier = TIERS[selectedTierIndex];
        BlockPos pos = new BlockPos(x, 64, z);
        int delay = TIMER_MINUTES[selectedTimerIndex];
        boolean recurring = TIMER_RECURRING[selectedTimerIndex];

        ClientPlayNetworking.send(new ModPackets.TriggerDropPayload(pos, tier, delay, recurring));

        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        Font font = this.font;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldWidth = 206;
        int fieldX = panelX + (PANEL_WIDTH - fieldWidth) / 2;

        // Modal Obsidian Slate centrado
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2090D16);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF334155);

        // Barra de encabezado
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 22, 0xFF0F172A);
        graphics.fill(panelX, panelY + 22, panelX + PANEL_WIDTH, panelY + 23, 0xFF1E293B);
        graphics.centeredText(font, Component.literal("§6§lLANZAR DROP (ADMIN)"), panelX + PANEL_WIDTH / 2, panelY + 7, 0xFFFFFFFF);

        // Etiquetas con interlineado claro por encima de cada control
        graphics.text(font, Component.literal("§7Coordenadas del Drop (X / Z):"), fieldX, panelY + 29, 0xFF94A3B8);
        graphics.text(font, Component.literal("§7Tipo de Drop:"), fieldX, panelY + 65, 0xFF94A3B8);
        graphics.text(font, Component.literal("§7Temporizador de Caída:"), fieldX, panelY + 102, 0xFF94A3B8);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
