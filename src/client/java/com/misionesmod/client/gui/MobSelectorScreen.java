package com.misionesmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.*;
import java.util.function.Consumer;

public class MobSelectorScreen extends Screen {
    private final Screen parentScreen;
    private final Consumer<List<String>> onSave;
    private final Set<String> selectedMobIds = new HashSet<>();

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 224;

    private EditBox searchBox;
    private final List<EntityType<?>> filteredEntities = new ArrayList<>();
    private int catalogPage = 0;
    private static final int ROWS = 6;

    private Button prevPageBtn;
    private Button nextPageBtn;

    public MobSelectorScreen(Screen parentScreen, List<String> initialSelected, Consumer<List<String>> onSave) {
        super(Component.literal("Seleccionar Mobs"));
        this.parentScreen = parentScreen;
        this.onSave = onSave;
        if (initialSelected != null) {
            this.selectedMobIds.addAll(initialSelected);
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        searchBox = new EditBox(this.font, panelX + 16, panelY + 28, PANEL_WIDTH - 32, 17, Component.literal("Buscar"));
        searchBox.setHint(Component.literal("Buscar mob (ej: zombie, mutant)..."));
        searchBox.setResponder(this::updateSearchFilter);
        this.addRenderableWidget(searchBox);

        int pageBtnY = panelY + 168;

        prevPageBtn = Button.builder(
                Component.literal("◀"),
                b -> {
                    if (catalogPage > 0) {
                        catalogPage--;
                        updatePageButtons();
                    }
                }
        ).bounds(panelX + 24, pageBtnY, 26, 16).build();
        this.addRenderableWidget(prevPageBtn);

        nextPageBtn = Button.builder(
                Component.literal("▶"),
                b -> {
                    if ((catalogPage + 1) * ROWS < filteredEntities.size()) {
                        catalogPage++;
                        updatePageButtons();
                    }
                }
        ).bounds(panelX + PANEL_WIDTH - 24 - 26, pageBtnY, 26, 16).build();
        this.addRenderableWidget(nextPageBtn);

        Button saveBtn = Button.builder(
                Component.literal("§a✔ Guardar"),
                b -> {
                    if (onSave != null) {
                        onSave.accept(new ArrayList<>(selectedMobIds));
                    }
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + 24, panelY + 192, 96, 18).build();
        this.addRenderableWidget(saveBtn);

        Button cancelBtn = Button.builder(
                Component.literal("Cancelar"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + PANEL_WIDTH - 24 - 96, panelY + 192, 96, 18).build();
        this.addRenderableWidget(cancelBtn);

        updateSearchFilter(searchBox.getValue());
    }

    private void updateSearchFilter(String query) {
        filteredEntities.clear();
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            // Filtrar no criaturas (ítems, flechas, botes, etc.)
            if (type.getCategory() == MobCategory.MISC) continue;

            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().toLowerCase(Locale.ROOT);
            String name = type.getDescription().getString().toLowerCase(Locale.ROOT);

            if (q.isEmpty() || name.contains(q) || id.contains(q)) {
                filteredEntities.add(type);
            }
        }
        catalogPage = 0;
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (prevPageBtn != null) prevPageBtn.active = catalogPage > 0;
        if (nextPageBtn != null) nextPageBtn.active = (catalogPage + 1) * ROWS < filteredEntities.size();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();

        if (btn == 0) {
            int panelX = (this.width - PANEL_WIDTH) / 2;
            int panelY = (this.height - PANEL_HEIGHT) / 2;
            int rowStartY = panelY + 50;

            int startIndex = catalogPage * ROWS;
            for (int r = 0; r < ROWS; r++) {
                int index = startIndex + r;
                if (index >= filteredEntities.size()) break;

                int y = rowStartY + (r * 19);
                if (mx >= panelX + 16 && mx <= panelX + PANEL_WIDTH - 16 && my >= y && my < y + 17) {
                    EntityType<?> type = filteredEntities.get(index);
                    String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                    if (selectedMobIds.contains(id)) {
                        selectedMobIds.remove(id);
                    } else {
                        selectedMobIds.add(id);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        Font font = this.font;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;

        // Tarjeta modal estilo obsidian slate
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2090D16);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF334155);

        // Encabezado
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 22, 0x551E293B);
        graphics.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, 0xFF334155);
        graphics.centeredText(font, Component.literal("§e🧟 SELECCIONAR MOBS DE INCURSIÓN"), centerX, panelY + 7, 0xFFFFFFFF);

        // Lista de filas de mobs
        int rowStartY = panelY + 50;
        int startIndex = catalogPage * ROWS;

        for (int r = 0; r < ROWS; r++) {
            int index = startIndex + r;
            int y = rowStartY + (r * 19);

            if (index < filteredEntities.size()) {
                EntityType<?> type = filteredEntities.get(index);
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                String name = type.getDescription().getString();
                boolean selected = selectedMobIds.contains(id);
                boolean isHovered = mouseX >= panelX + 16 && mouseX <= panelX + PANEL_WIDTH - 16 && mouseY >= y && mouseY < y + 17;

                int rowBg = selected ? (isHovered ? 0xAA1E3A8A : 0x661E293B) : (isHovered ? 0x442563EB : 0x330F172A);
                int border = selected ? 0xFF38BDF8 : (isHovered ? 0xFF64748B : 0xFF1E293B);

                graphics.fill(panelX + 16, y, panelX + PANEL_WIDTH - 16, y + 17, rowBg);
                graphics.outline(panelX + 16, y, PANEL_WIDTH - 32, 17, border);

                String check = selected ? "§a✔ " : "§8◻ ";
                graphics.text(font, Component.literal(check + "§f" + name), panelX + 22, y + 5, 0xFFFFFFFF);

                // ID recortado a la derecha en gris
                String cleanId = id.contains(":") ? id.split(":")[1] : id;
                if (font.width(cleanId) < 70) {
                    graphics.text(font, Component.literal("§7" + cleanId), panelX + PANEL_WIDTH - 22 - font.width(cleanId), y + 5, 0xFFAAAAAA);
                }
            } else {
                graphics.fill(panelX + 16, y, panelX + PANEL_WIDTH - 16, y + 17, 0x110F172A);
                graphics.outline(panelX + 16, y, PANEL_WIDTH - 32, 17, 0x221E293B);
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) filteredEntities.size() / ROWS));
        int pageBtnY = panelY + 168;
        graphics.centeredText(font, Component.literal("§7Pág " + (catalogPage + 1) + " / " + totalPages + " (" + selectedMobIds.size() + " elegidos)"), centerX, pageBtnY + 4, 0xFFAAAAAA);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
