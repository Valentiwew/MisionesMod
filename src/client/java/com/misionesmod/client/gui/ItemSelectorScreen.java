package com.misionesmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ItemSelectorScreen extends Screen {
    public enum FilterMode {
        ALL,
        ONLY_CRAFTABLE,
        ONLY_SMELTABLE
    }

    private final Screen parentScreen;
    private final Consumer<Item> onSelect;
    private final FilterMode filterMode;

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 224;

    private EditBox searchBox;
    private final List<Item> filteredItems = new ArrayList<>();
    private int catalogPage = 0;
    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int ITEMS_PER_PAGE = COLS * ROWS; // 54

    private Button prevPageBtn;
    private Button nextPageBtn;

    public ItemSelectorScreen(Screen parentScreen, Consumer<Item> onSelect) {
        this(parentScreen, FilterMode.ALL, onSelect);
    }

    public ItemSelectorScreen(Screen parentScreen, boolean onlyCraftable, Consumer<Item> onSelect) {
        this(parentScreen, onlyCraftable ? FilterMode.ONLY_CRAFTABLE : FilterMode.ALL, onSelect);
    }

    public ItemSelectorScreen(Screen parentScreen, FilterMode filterMode, Consumer<Item> onSelect) {
        super(Component.literal(
                filterMode == FilterMode.ONLY_CRAFTABLE ? "Seleccionar Ítem Crafteable" :
                filterMode == FilterMode.ONLY_SMELTABLE ? "Seleccionar Ítem Cocinable" : "Seleccionar Ítem"
        ));
        this.parentScreen = parentScreen;
        this.filterMode = filterMode != null ? filterMode : FilterMode.ALL;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        String hint = switch (filterMode) {
            case ONLY_CRAFTABLE -> "Buscar crafteo...";
            case ONLY_SMELTABLE -> "Buscar ítem horneable/cocinable...";
            default -> "Buscar ítem o bloque...";
        };

        searchBox = new EditBox(this.font, panelX + 16, panelY + 28, PANEL_WIDTH - 32, 17, Component.literal("Buscar"));
        searchBox.setHint(Component.literal(hint));
        searchBox.setResponder(this::updateSearchFilter);
        this.addRenderableWidget(searchBox);

        int startGridX = panelX + (PANEL_WIDTH - (COLS * 18)) / 2;
        int pageBtnY = panelY + 165;

        prevPageBtn = Button.builder(
                Component.literal("◀"),
                b -> {
                    if (catalogPage > 0) {
                        catalogPage--;
                        updatePageButtons();
                    }
                }
        ).bounds(startGridX, pageBtnY, 26, 16).build();
        this.addRenderableWidget(prevPageBtn);

        nextPageBtn = Button.builder(
                Component.literal("▶"),
                b -> {
                    if ((catalogPage + 1) * ITEMS_PER_PAGE < filteredItems.size()) {
                        catalogPage++;
                        updatePageButtons();
                    }
                }
        ).bounds(startGridX + (COLS * 18) - 26, pageBtnY, 26, 16).build();
        this.addRenderableWidget(nextPageBtn);

        Button cancelBtn = Button.builder(
                Component.literal("Cancelar"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + (PANEL_WIDTH - 80) / 2, panelY + 192, 80, 18).build();
        this.addRenderableWidget(cancelBtn);

        updateSearchFilter(searchBox.getValue());
    }

    private void updateSearchFilter(String query) {
        filteredItems.clear();
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase(Locale.ROOT);
            if (filterMode == FilterMode.ONLY_CRAFTABLE && !com.misionesmod.client.MisionesModClient.craftableItemIds.isEmpty()) {
                if (!com.misionesmod.client.MisionesModClient.craftableItemIds.contains(id)) {
                    continue;
                }
            } else if (filterMode == FilterMode.ONLY_SMELTABLE && !com.misionesmod.client.MisionesModClient.smeltableItemIds.isEmpty()) {
                if (!com.misionesmod.client.MisionesModClient.smeltableItemIds.contains(id)) {
                    continue;
                }
            }
            String name = item.getName(item.getDefaultInstance()).getString().toLowerCase(Locale.ROOT);

            if (q.isEmpty() || name.contains(q) || id.contains(q)) {
                filteredItems.add(item);
            }
        }
        catalogPage = 0;
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (prevPageBtn != null) prevPageBtn.active = catalogPage > 0;
        if (nextPageBtn != null) nextPageBtn.active = (catalogPage + 1) * ITEMS_PER_PAGE < filteredItems.size();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();

        if (btn == 0) {
            int panelX = (this.width - PANEL_WIDTH) / 2;
            int panelY = (this.height - PANEL_HEIGHT) / 2;
            int startGridX = panelX + (PANEL_WIDTH - (COLS * 18)) / 2;
            int startGridY = panelY + 50;

            int startIndex = catalogPage * ITEMS_PER_PAGE;
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int index = startIndex + (r * COLS + c);
                    if (index >= filteredItems.size()) break;

                    int x = startGridX + (c * 18);
                    int y = startGridY + (r * 18);

                    if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                        Item selected = filteredItems.get(index);
                        if (onSelect != null) {
                            onSelect.accept(selected);
                        }
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(parentScreen);
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Fondo atenuado exterior
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
        String headerTitle = switch (filterMode) {
            case ONLY_CRAFTABLE -> "ÍTEM CRAFTEABLE";
            case ONLY_SMELTABLE -> "ÍTEM COCINABLE";
            default -> "SELECCIONAR ÍTEM";
        };
        graphics.centeredText(font, Component.literal("§e📦 " + headerTitle), centerX, panelY + 7, 0xFFFFFFFF);

        // Rejilla de ítems
        int startGridX = panelX + (PANEL_WIDTH - (COLS * 18)) / 2;
        int startGridY = panelY + 50;
        int gridW = COLS * 18;
        int gridH = ROWS * 18;

        graphics.fill(startGridX - 2, startGridY - 2, startGridX + gridW + 2, startGridY + gridH + 2, 0x550F172A);
        graphics.outline(startGridX - 2, startGridY - 2, gridW + 4, gridH + 4, 0xFF1E293B);

        int startIndex = catalogPage * ITEMS_PER_PAGE;
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int index = startIndex + (r * COLS + c);
                int x = startGridX + (c * 18);
                int y = startGridY + (r * 18);

                boolean isHovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                graphics.fill(x, y, x + 18, y + 18, isHovered ? 0xAA2563EB : 0x331E293B);
                graphics.outline(x, y, 18, 18, isHovered ? 0xFF38BDF8 : 0xFF1E293B);

                if (index < filteredItems.size()) {
                    Item item = filteredItems.get(index);
                    ItemStack stack = item.getDefaultInstance();
                    graphics.item(stack, x + 1, y + 1);

                    if (isHovered) {
                        hoveredStack = stack;
                    }
                }
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE));
        int pageBtnY = panelY + 165;
        graphics.centeredText(font, Component.literal("§7Pág " + (catalogPage + 1) + " / " + totalPages), centerX, pageBtnY + 4, 0xFFAAAAAA);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (!hoveredStack.isEmpty()) {
            graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
