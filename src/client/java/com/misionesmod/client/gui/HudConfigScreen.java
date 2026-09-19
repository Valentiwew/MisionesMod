package com.misionesmod.client.gui;

import com.misionesmod.client.hud.ClientHudConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudConfigScreen extends Screen {

    private final Screen parentScreen;
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 135;

    private Button toggleBtn;
    private Button positionBtn;

    public HudConfigScreen(Screen parentScreen) {
        super(Component.literal("Configuración de HUD"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldW = 200;
        int fieldX = panelX + (PANEL_WIDTH - fieldW) / 2;

        toggleBtn = Button.builder(
                Component.literal("Widget en Pantalla: " + (ClientHudConfig.widgetEnabled ? "§aActivado" : "§cDesactivado")),
                b -> {
                    ClientHudConfig.widgetEnabled = !ClientHudConfig.widgetEnabled;
                    toggleBtn.setMessage(Component.literal("Widget en Pantalla: " + (ClientHudConfig.widgetEnabled ? "§aActivado" : "§cDesactivado")));
                    ClientHudConfig.save();
                }
        ).bounds(fieldX, panelY + 36, fieldW, 20).build();
        this.addRenderableWidget(toggleBtn);

        positionBtn = Button.builder(
                Component.literal("Posición: §e" + ClientHudConfig.cornerPosition.displayName),
                b -> {
                    ClientHudConfig.CornerPosition[] positions = ClientHudConfig.CornerPosition.values();
                    int nextIdx = (ClientHudConfig.cornerPosition.ordinal() + 1) % positions.length;
                    ClientHudConfig.cornerPosition = positions[nextIdx];
                    positionBtn.setMessage(Component.literal("Posición: §e" + ClientHudConfig.cornerPosition.displayName));
                    ClientHudConfig.save();
                }
        ).bounds(fieldX, panelY + 62, fieldW, 20).build();
        this.addRenderableWidget(positionBtn);

        Button closeBtn = Button.builder(
                Component.literal("§aListo"),
                b -> {
                    ClientHudConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + (PANEL_WIDTH - 100) / 2, panelY + 96, 100, 20).build();
        this.addRenderableWidget(closeBtn);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        Font font = this.font;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2090D16);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF334155);

        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 22, 0x551E293B);
        graphics.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, 0xFF334155);
        graphics.centeredText(font, Component.literal("§6§lAJUSTES DE INTERFAZ (HUD)"), centerX, panelY + 7, 0xFFFFFFFF);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
