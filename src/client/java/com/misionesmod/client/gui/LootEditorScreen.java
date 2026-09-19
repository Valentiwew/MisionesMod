package com.misionesmod.client.gui;

import com.misionesmod.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LootEditorScreen extends Screen {
    private final Screen parentScreen;
    private final String targetId;

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 248;

    // 27 slots para el drop
    private final NonNullList<ItemStack> lootSlots = NonNullList.withSize(27, ItemStack.EMPTY);
    private ItemStack heldStack = ItemStack.EMPTY;

    // Búsqueda y catálogo
    private EditBox searchBox;
    private final List<Item> filteredItems = new ArrayList<>();
    private int catalogPage = 0;
    private static final int ITEMS_PER_PAGE = 36; // 9 columnas x 4 filas

    private Button prevPageBtn;
    private Button nextPageBtn;

    @FunctionalInterface
    public interface LootSaveCallback {
        void onSave(List<ItemStack> items);
    }

    private final LootSaveCallback saveCallback;

    public LootEditorScreen(Screen parentScreen, String targetId, List<ItemStack> initialItems) {
        this(parentScreen, targetId, initialItems, null);
    }

    public LootEditorScreen(Screen parentScreen, String targetId, List<ItemStack> initialItems, LootSaveCallback callback) {
        super(Component.literal("Editor de Drop"));
        this.parentScreen = parentScreen;
        this.targetId = targetId;
        this.saveCallback = callback;

        if (initialItems != null) {
            for (int i = 0; i < Math.min(27, initialItems.size()); i++) {
                lootSlots.set(i, initialItems.get(i).copy());
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int gridStartX = panelX + (PANEL_WIDTH - 9 * 18) / 2;

        // Barra de búsqueda del catálogo
        int searchWidth = PANEL_WIDTH - 32;
        int searchY = panelY + 98;
        searchBox = new EditBox(this.font, panelX + 16, searchY, searchWidth, 16, Component.literal("Buscar"));
        searchBox.setHint(Component.literal("Buscar ítem para el drop..."));
        searchBox.setResponder(this::updateSearchFilter);
        this.addRenderableWidget(searchBox);

        // Paginación del catálogo
        int pageBtnY = panelY + 196;
        prevPageBtn = Button.builder(
                Component.literal("◀"),
                b -> {
                    if (catalogPage > 0) {
                        catalogPage--;
                        updatePageButtons();
                    }
                }
        ).bounds(gridStartX, pageBtnY, 26, 16).build();
        this.addRenderableWidget(prevPageBtn);

        nextPageBtn = Button.builder(
                Component.literal("▶"),
                b -> {
                    if ((catalogPage + 1) * ITEMS_PER_PAGE < filteredItems.size()) {
                        catalogPage++;
                        updatePageButtons();
                    }
                }
        ).bounds(gridStartX + 9 * 18 - 26, pageBtnY, 26, 16).build();
        this.addRenderableWidget(nextPageBtn);

        // Botones inferiores de acción
        int bottomY = panelY + 220;
        Button saveBtn = Button.builder(
                Component.literal("§aGuardar"),
                b -> saveAndClose()
        ).bounds(panelX + 14, bottomY, 72, 18).build();
        this.addRenderableWidget(saveBtn);

        Button clearBtn = Button.builder(
                Component.literal("§cVaciar"),
                b -> {
                    for (int i = 0; i < 27; i++) {
                        lootSlots.set(i, ItemStack.EMPTY);
                    }
                }
        ).bounds(panelX + 94, bottomY, 72, 18).build();
        this.addRenderableWidget(clearBtn);

        Button backBtn = Button.builder(
                Component.literal("Volver"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + 174, bottomY, 72, 18).build();
        this.addRenderableWidget(backBtn);

        updateSearchFilter("");
    }

    private void updateSearchFilter(String query) {
        filteredItems.clear();
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String name = item.getName(item.getDefaultInstance()).getString().toLowerCase(Locale.ROOT);
            String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase(Locale.ROOT);

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

    private void saveAndClose() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : lootSlots) {
            items.add(s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }

        if (saveCallback != null) {
            saveCallback.onSave(items);
        } else if ("drop".equalsIgnoreCase(targetId)) {
            List<String> ids = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (ItemStack s : lootSlots) {
                if (!s.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                    counts.add(s.getCount());
                }
            }
            ClientPlayNetworking.send(new ModPackets.SaveCustomLootPayload("drop", ids, counts));
        }

        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parentScreen);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int chestStartX = panelX + (PANEL_WIDTH - 9 * 18) / 2;
        int chestStartY = panelY + 40;

        // Comprobar clics en los 27 slots del cofre
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = chestStartX + col * 18;
                int slotY = chestStartY + row * 18;
                if (mx >= slotX && mx < slotX + 18 && my >= slotY && my < slotY + 18) {
                    int index = row * 9 + col;
                    ItemStack slotStack = lootSlots.get(index);

                    if (heldStack.isEmpty()) {
                        if (!slotStack.isEmpty()) {
                            if (btn == 0) {
                                heldStack = slotStack.copy();
                                lootSlots.set(index, ItemStack.EMPTY);
                            } else if (btn == 1) {
                                int half = (slotStack.getCount() + 1) / 2;
                                heldStack = slotStack.split(half);
                                if (slotStack.isEmpty()) {
                                    lootSlots.set(index, ItemStack.EMPTY);
                                }
                            }
                        }
                    } else {
                        if (slotStack.isEmpty()) {
                            if (btn == 0) {
                                lootSlots.set(index, heldStack);
                                heldStack = ItemStack.EMPTY;
                            } else if (btn == 1) {
                                ItemStack single = heldStack.split(1);
                                lootSlots.set(index, single);
                            }
                        } else if (ItemStack.isSameItemSameComponents(heldStack, slotStack)) {
                            if (btn == 0) {
                                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                                int transfer = Math.min(space, heldStack.getCount());
                                slotStack.grow(transfer);
                                heldStack.shrink(transfer);
                            } else if (btn == 1) {
                                if (slotStack.getCount() < slotStack.getMaxStackSize()) {
                                    slotStack.grow(1);
                                    heldStack.shrink(1);
                                }
                            }
                        } else {
                            if (btn == 0) {
                                ItemStack temp = slotStack;
                                lootSlots.set(index, heldStack);
                                heldStack = temp;
                            }
                        }
                    }
                    return true;
                }
            }
        }

        // Comprobar clics en los slots del catálogo (4 filas x 9 columnas)
        int catStartX = chestStartX;
        int catStartY = panelY + 119;
        int startIdx = catalogPage * ITEMS_PER_PAGE;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = catStartX + col * 18;
                int slotY = catStartY + row * 18;
                if (mx >= slotX && mx < slotX + 18 && my >= slotY && my < slotY + 18) {
                    int idx = startIdx + (row * 9 + col);
                    if (idx < filteredItems.size()) {
                        Item item = filteredItems.get(idx);
                        int maxStack = item.getDefaultInstance().getMaxStackSize();
                        int count = (btn == 2) ? maxStack : ((btn == 1) ? Math.min(16, maxStack) : 1);

                        if (heldStack.isEmpty()) {
                            heldStack = new ItemStack(item, count);
                        } else {
                            // Al hacer clic en cualquier ítem del catálogo con un ítem sostenido, se elimina rápidamente como en Creativo
                            heldStack = ItemStack.EMPTY;
                        }
                        return true;
                    }
                }
            }
        }

        // Clic derecho fuera del marco para soltar el ítem sostenido
        if (btn == 1 && !heldStack.isEmpty() && (mx < panelX || mx > panelX + PANEL_WIDTH || my < panelY || my > panelY + PANEL_HEIGHT)) {
            heldStack = ItemStack.EMPTY;
            return true;
        }

        return super.mouseClicked(event, isDouble);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Fondo exterior atenuado
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        Font font = this.font;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;

        // Tarjeta modal obsidian slate
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2090D16);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF334155);

        // Encabezado
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 22, 0x551E293B);
        graphics.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, 0xFF334155);
        graphics.centeredText(font, Component.literal("§e📦 EDITOR DE DROP"), centerX, panelY + 7, 0xFFFFFFFF);

        // Etiqueta Cofre
        graphics.centeredText(font, Component.literal("§7Contenido del Drop (27 slots)"), centerX, panelY + 28, 0xFFCCCCCC);

        int chestStartX = panelX + (PANEL_WIDTH - 9 * 18) / 2;
        int chestStartY = panelY + 40;
        ItemStack hoveredStack = ItemStack.EMPTY;

        // Renderizar slots del cofre de drop
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = chestStartX + col * 18;
                int slotY = chestStartY + row * 18;
                boolean isHovered = mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18;

                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, isHovered ? 0xAA2563EB : 0x441E293B);
                graphics.outline(slotX, slotY, 18, 18, isHovered ? 0xFF38BDF8 : 0xFF334155);

                int index = row * 9 + col;
                ItemStack stack = lootSlots.get(index);
                if (!stack.isEmpty()) {
                    graphics.item(stack, slotX + 1, slotY + 1);
                    graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
                    if (isHovered) {
                        hoveredStack = stack;
                    }
                }
            }
        }

        // Renderizar catálogo creativo
        int catStartX = chestStartX;
        int catStartY = panelY + 119;
        int startIdx = catalogPage * ITEMS_PER_PAGE;
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE));

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = catStartX + col * 18;
                int slotY = catStartY + row * 18;
                boolean isHovered = mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18;

                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, isHovered ? 0x771E3A8A : 0x330F172A);
                graphics.outline(slotX, slotY, 18, 18, isHovered ? 0xFF60A5FA : 0xFF1E293B);

                int idx = startIdx + (row * 9 + col);
                if (idx < filteredItems.size()) {
                    ItemStack stack = new ItemStack(filteredItems.get(idx));
                    graphics.item(stack, slotX + 1, slotY + 1);
                    if (isHovered) {
                        hoveredStack = stack;
                    }
                }
            }
        }

        int pageBtnY = panelY + 196;
        graphics.centeredText(font, Component.literal("§7Pág " + (catalogPage + 1) + " / " + totalPages), centerX, pageBtnY + 4, 0xFFAAAAAA);

        // Renderizar widgets
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Tooltip del ítem hovered
        if (heldStack.isEmpty() && !hoveredStack.isEmpty()) {
            graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
        }

        // Renderizar ítem sostenido con el cursor
        if (!heldStack.isEmpty()) {
            graphics.item(heldStack, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(font, heldStack, mouseX - 8, mouseY - 8);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
