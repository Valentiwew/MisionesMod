package com.misionesmod.client.gui;

import com.misionesmod.client.MisionesModClient;
import com.misionesmod.client.hud.WaypointHudRenderer;
import com.misionesmod.mission.Mission;
import com.misionesmod.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

import java.util.List;

public class MisionesScreen extends Screen {
    private int currentTab = 0; // 0 = Pendientes, 1 = Completadas
    private int selectedMissionIndex = 0;

    private Button tabPendingButton;
    private Button tabCompletedButton;

    private Button trackButton;
    private Button editButton;
    private Button deleteButton;
    private Button dropButton;
    private Button createMissionButton;
    private boolean isAdmin;

    // Dimensiones amplias, centradas y proporcionadas
    private static final int PANEL_WIDTH = 416;
    private static final int PANEL_HEIGHT = 240;

    public MisionesScreen() {
        super(Component.literal("Panel de Misiones"));
    }

    @Override
    protected void init() {
        super.init();

        // Sonido de pasar página de libro al abrir
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }

        rebuildWidgets();
    }

    @Override
    public void onClose() {
        // Sonido de cerrar libro al salir del menú
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PUT, 1.0f));
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (MisionesModClient.OPEN_MENU_KEY != null && MisionesModClient.OPEN_MENU_KEY.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean lastAdminState = false;

    public void refreshMissions() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        Player player = Minecraft.getInstance().player;
        boolean currentAdmin = player != null && Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        if (currentAdmin != lastAdminState) {
            lastAdminState = currentAdmin;
            rebuildWidgets();
        }
    }

    private List<Mission> getDisplayedMissions() {
        List<Mission> all = MisionesModClient.clientMissions;
        List<Mission> filtered = new java.util.ArrayList<>();
        for (Mission m : all) {
            if (currentTab == 0 && !m.isCompleted()) {
                filtered.add(m);
            } else if (currentTab == 1 && m.isCompleted()) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        List<Mission> allMissions = MisionesModClient.clientMissions;
        int pendingCount = 0;
        int completedCount = 0;
        for (Mission m : allMissions) {
            if (m.isCompleted()) completedCount++;
            else pendingCount++;
        }

        List<Mission> missions = getDisplayedMissions();
        if (selectedMissionIndex >= missions.size()) {
            selectedMissionIndex = Math.max(0, missions.size() - 1);
        }

        // Columna Izquierda: Pestañas
        int listX = panelX + 7;
        int tabY = panelY + 24;
        int tabWidth = 66;

        tabPendingButton = Button.builder(
                Component.literal((currentTab == 0 ? "§e§l" : "§7") + "⏳ " + pendingCount),
                b -> {
                    currentTab = 0;
                    selectedMissionIndex = 0;
                    rebuildWidgets();
                }
        ).bounds(listX, tabY, tabWidth, 16).build();
        this.addRenderableWidget(tabPendingButton);

        tabCompletedButton = Button.builder(
                Component.literal((currentTab == 1 ? "§a§l" : "§7") + "✔ " + completedCount),
                b -> {
                    currentTab = 1;
                    selectedMissionIndex = 0;
                    rebuildWidgets();
                }
        ).bounds(listX + tabWidth + 4, tabY, tabWidth, 16).build();
        this.addRenderableWidget(tabCompletedButton);

        // Lista de misiones (tarjetas)
        int listY = tabY + 19;
        int itemWidth = 136;
        int itemHeight = 21;
        int maxToShow = Math.min(8, missions.size());

        for (int i = 0; i < maxToShow; i++) {
            final int index = i;
            Mission mission = missions.get(i);
            String rawTitle = (mission.getTitle() != null && !mission.getTitle().isBlank()) ? mission.getTitle() : "Misión #" + (i + 1);
            if (rawTitle.length() > 16) rawTitle = rawTitle.substring(0, 15) + "…";

            String displayTitle;
            if (currentTab == 1) {
                // Completadas: estilo grisáceo/archivado
                displayTitle = (index == selectedMissionIndex ? "§7▶ §8✔ " : "  §8✔ ") + "§7" + rawTitle;
            } else {
                // Pendientes: colores vivos con ícono
                String icon = "CRAFTEO".equalsIgnoreCase(mission.getObjectiveType()) ? "⛏ " :
                        ("OBTENCION".equalsIgnoreCase(mission.getObjectiveType()) ? "📦 " :
                        ("INCURSION".equalsIgnoreCase(mission.getObjectiveType()) ? "🧟 " : "🧭 "));
                displayTitle = (index == selectedMissionIndex ? "§6▶ §e" : "  §f") + icon + rawTitle;
            }

            Button missionBtn = Button.builder(
                    Component.literal(displayTitle),
                    b -> {
                        selectedMissionIndex = index;
                        rebuildWidgets();
                    }
            ).bounds(listX, listY + (i * (itemHeight + 2)), itemWidth, itemHeight).build();

            this.addRenderableWidget(missionBtn);
        }

        // Panel Derecho: Acciones de la misión seleccionada
        int rightPanelX = panelX + 154;
        int actionBtnY = panelY + PANEL_HEIGHT - 24;

        Player player = Minecraft.getInstance().player;
        this.isAdmin = player != null && Commands.LEVEL_GAMEMASTERS.check(player.permissions());

        trackButton = Button.builder(
                Component.translatable("gui.misionesmod.btn.track"),
                b -> toggleTrackCurrentMission()
        ).bounds(rightPanelX, actionBtnY, 115, 18).build();
        this.addRenderableWidget(trackButton);

        if (isAdmin) {
            editButton = Button.builder(
                    Component.literal("§e✏"),
                    b -> editCurrentMission()
            ).bounds(rightPanelX + 120, actionBtnY, 54, 18).build();
            this.addRenderableWidget(editButton);

            deleteButton = Button.builder(
                    Component.literal("§c✕"),
                    b -> deleteCurrentMission()
            ).bounds(rightPanelX + 178, actionBtnY, 54, 18).build();
            this.addRenderableWidget(deleteButton);
        }

        // Botones de Administrador integrados en la barra superior del panel (visibles en GUI Scale 5)
        if (isAdmin) {
            createMissionButton = Button.builder(
                    Component.literal("§e+ Nueva Misión"),
                    b -> {
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(new CreateMissionScreen(this));
                        }
                    }
            ).bounds(panelX + PANEL_WIDTH - 208, panelY + 2, 101, 16).build();
            this.addRenderableWidget(createMissionButton);

            dropButton = Button.builder(
                    Component.literal("§6Lanzar Drop..."),
                    b -> {
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(new TriggerDropScreen(this));
                        }
                    }
            ).bounds(panelX + PANEL_WIDTH - 104, panelY + 2, 101, 16).build();
            this.addRenderableWidget(dropButton);
        }

        Button hudConfigButton = Button.builder(
                Component.literal("§b⚙ HUD"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new HudConfigScreen(this));
                    }
                }
        ).bounds(panelX + PANEL_WIDTH - (isAdmin ? 262 : 54), panelY + 2, 50, 16).build();
        this.addRenderableWidget(hudConfigButton);

        updateActionButtons();
    }

    private ItemStack[] resolveRecipeGrid(Item targetItem, Player player) {
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < 9; i++) grid[i] = ItemStack.EMPTY;
        if (targetItem == null || targetItem == Items.AIR || player == null || player.level() == null) {
            return grid;
        }

        try {
            ContextMap context = SlotDisplayContext.fromLevel(player.level());
            if (player instanceof net.minecraft.client.player.LocalPlayer localPlayer) {
                var recipeBook = localPlayer.getRecipeBook();
                if (recipeBook != null) {
                    for (RecipeCollection col : recipeBook.getCollections()) {
                        for (RecipeDisplayEntry entry : col.getRecipes()) {
                            RecipeDisplay display = entry.display();
                            SlotDisplay resultDisplay = display.result();
                            ItemStack resultStack = resultDisplay.resolveForFirstStack(context);
                            if (resultStack.is(targetItem)) {
                                if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                                    int w = shaped.width();
                                    int h = shaped.height();
                                    List<SlotDisplay> ingredients = shaped.ingredients();
                                    for (int row = 0; row < h && row < 3; row++) {
                                        for (int colIdx = 0; colIdx < w && colIdx < 3; colIdx++) {
                                            int srcIdx = row * w + colIdx;
                                            if (srcIdx < ingredients.size()) {
                                                grid[row * 3 + colIdx] = ingredients.get(srcIdx).resolveForFirstStack(context);
                                            }
                                        }
                                    }
                                    return grid;
                                } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                                    List<SlotDisplay> ingredients = shapeless.ingredients();
                                    for (int idx = 0; idx < ingredients.size() && idx < 9; idx++) {
                                        grid[idx] = ingredients.get(idx).resolveForFirstStack(context);
                                    }
                                    return grid;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return grid;
    }

    private ItemStack[] resolveFurnaceRecipe(Item targetItem, Player player) {
        ItemStack[] result = new ItemStack[] { ItemStack.EMPTY, new ItemStack(Items.COAL), new ItemStack(Items.FURNACE) };
        if (targetItem == null || targetItem == Items.AIR) {
            return result;
        }

        try {
            if (player != null && player.level() != null) {
                ContextMap context = SlotDisplayContext.fromLevel(player.level());
                if (player instanceof net.minecraft.client.player.LocalPlayer localPlayer) {
                    var recipeBook = localPlayer.getRecipeBook();
                    if (recipeBook != null) {
                        for (RecipeCollection col : recipeBook.getCollections()) {
                            for (RecipeDisplayEntry entry : col.getRecipes()) {
                                RecipeDisplay display = entry.display();
                                if (display instanceof FurnaceRecipeDisplay furnaceDisplay) {
                                    ItemStack resStack = furnaceDisplay.result().resolveForFirstStack(context);
                                    if (resStack.is(targetItem)) {
                                        ItemStack ingStack = furnaceDisplay.ingredient().resolveForFirstStack(context);
                                        ItemStack fuelStack = furnaceDisplay.fuel().resolveForFirstStack(context);
                                        ItemStack stationStack = furnaceDisplay.craftingStation().resolveForFirstStack(context);

                                        if (!ingStack.isEmpty()) result[0] = ingStack;
                                        if (!fuelStack.isEmpty()) result[1] = fuelStack;
                                        if (!stationStack.isEmpty()) result[2] = stationStack;
                                        return result;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (result[0].isEmpty()) {
            Item fallback = getCommonSmeltIngredient(targetItem);
            if (fallback != null && fallback != Items.AIR) {
                result[0] = new ItemStack(fallback);
            }
        }

        return result;
    }

    private Item getCommonSmeltIngredient(Item target) {
        if (target == null) return Items.AIR;
        if (target == Items.IRON_INGOT) return Items.RAW_IRON;
        if (target == Items.GOLD_INGOT) return Items.RAW_GOLD;
        if (target == Items.COPPER_INGOT) return Items.RAW_COPPER;
        if (target == Items.COOKED_BEEF) return Items.BEEF;
        if (target == Items.COOKED_PORKCHOP) return Items.PORKCHOP;
        if (target == Items.COOKED_MUTTON) return Items.MUTTON;
        if (target == Items.COOKED_CHICKEN) return Items.CHICKEN;
        if (target == Items.COOKED_RABBIT) return Items.RABBIT;
        if (target == Items.COOKED_COD) return Items.COD;
        if (target == Items.COOKED_SALMON) return Items.SALMON;
        if (target == Items.BAKED_POTATO) return Items.POTATO;
        if (target == Items.DRIED_KELP) return Items.KELP;
        if (target == Items.STONE) return Items.COBBLESTONE;
        if (target == Items.SMOOTH_STONE) return Items.STONE;
        if (target == Items.DEEPSLATE) return Items.COBBLED_DEEPSLATE;
        if (target == Items.GLASS) return Items.SAND;
        if (target == Items.BRICK) return Items.CLAY_BALL;
        if (target == Items.TERRACOTTA) return Items.CLAY;
        if (target == Items.CHARCOAL) return Items.OAK_LOG;
        if (target == Items.NETHERITE_SCRAP) return Items.ANCIENT_DEBRIS;
        return Items.AIR;
    }

    private void updateActionButtons() {
        List<Mission> missions = getDisplayedMissions();
        boolean hasSelection = !missions.isEmpty() && selectedMissionIndex < missions.size();

        if (trackButton != null) {
            trackButton.active = hasSelection;
            if (hasSelection) {
                Mission current = missions.get(selectedMissionIndex);
                boolean isExploration = "EXPLORACION".equalsIgnoreCase(current.getObjectiveType());
                boolean isIncursion = "INCURSION".equalsIgnoreCase(current.getObjectiveType());
                boolean isTrackable = (isExploration || isIncursion) && current.getTargetPos() != null;
                boolean isCompleted = current.isCompleted();
                if (!isTrackable || isCompleted) {
                    trackButton.visible = false;
                    trackButton.active = false;
                } else {
                    trackButton.visible = true;
                    trackButton.active = true;
                    boolean isTracked = current.getTargetPos() != null && WaypointHudRenderer.hasWaypoint(current.getTargetPos());
                    trackButton.setMessage(isTracked ?
                            Component.translatable("gui.misionesmod.btn.untrack") :
                            Component.translatable("gui.misionesmod.btn.track"));
                }
            } else {
                trackButton.visible = false;
            }
        }

        if (editButton != null) {
            editButton.active = hasSelection;
            editButton.visible = hasSelection;
        }

        if (deleteButton != null) {
            deleteButton.active = hasSelection;
            deleteButton.visible = hasSelection;
        }
    }

    private void toggleTrackCurrentMission() {
        List<Mission> missions = getDisplayedMissions();
        if (selectedMissionIndex < missions.size()) {
            Mission current = missions.get(selectedMissionIndex);
            BlockPos targetPos = current.getTargetPos();
            if (targetPos != null) {
                if (WaypointHudRenderer.hasWaypoint(targetPos)) {
                    WaypointHudRenderer.removeWaypoint(targetPos);
                } else {
                    WaypointHudRenderer.setWaypoint(true, current.getTitle(), targetPos);
                }
            }
            updateActionButtons();
        }
    }

    private void editCurrentMission() {
        List<Mission> missions = getDisplayedMissions();
        if (selectedMissionIndex < missions.size()) {
            Mission current = missions.get(selectedMissionIndex);
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new CreateMissionScreen(this, current));
            }
        }
    }

    private void deleteCurrentMission() {
        List<Mission> missions = getDisplayedMissions();
        if (selectedMissionIndex < missions.size()) {
            Mission current = missions.get(selectedMissionIndex);
            ClientPlayNetworking.send(new ModPackets.DeleteMissionPayload(current.getId()));
            if (current.getTargetPos() != null) {
                WaypointHudRenderer.removeWaypoint(current.getTargetPos());
            }
            if (selectedMissionIndex >= missions.size() - 1) {
                selectedMissionIndex = Math.max(0, missions.size() - 2);
            }
            rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Fondo atenuado suave
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        Font font = this.font;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Ventana modal flotante Obsidian Slate
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2090D16);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF334155);

        // Barra de encabezado
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 21, 0xFF0F172A);
        graphics.fill(panelX, panelY + 21, panelX + PANEL_WIDTH, panelY + 22, 0xFF1E293B);
        graphics.text(font, Component.literal("§6§lPANEL DE MISIONES"), panelX + 10, panelY + 7, 0xFFFFFFFF);

        // Divisor vertical entre lista y detalle
        graphics.fill(panelX + 147, panelY + 22, panelX + 148, panelY + PANEL_HEIGHT - 1, 0xFF1E293B);

        List<Mission> missions = getDisplayedMissions();
        int rightX = panelX + 154;
        int rightWidth = PANEL_WIDTH - 160;
        int contentY = panelY + 26;

        if (missions.isEmpty()) {
            String emptyMsg = currentTab == 0 ? "§7No hay misiones pendientes." : "§7No hay misiones completadas.";
            graphics.centeredText(font, Component.literal(emptyMsg), rightX + rightWidth / 2, contentY + 40, 0xFF64748B);
        } else if (selectedMissionIndex < missions.size()) {
            Mission current = missions.get(selectedMissionIndex);
            boolean isCompleted = currentTab == 1 || current.isCompleted();

            // 1. Título
            String title = current.getTitle() != null ? current.getTitle() : "Misión";
            if (isCompleted) {
                graphics.text(font, Component.literal("§8✔ §7§l" + title), rightX, contentY, 0xFFAAAAAA);
            } else {
                graphics.text(font, Component.literal("§e§l" + title), rightX, contentY, 0xFFFFFFFF);
            }

            // 2. Estado
            if (isCompleted) {
                graphics.text(font, Component.literal("§8Estado: COMPLETADA"), rightX, contentY + 13, 0xFF64748B);
            } else {
                graphics.text(font, Component.literal("§6Estado: §aPENDIENTE"), rightX, contentY + 13, 0xFFFFFFFF);
            }

            // 3. Descripción (si existe para exploración o incursión)
            String objType = current.getObjectiveType();
            boolean hasDesc = ("EXPLORACION".equalsIgnoreCase(objType) || "INCURSION".equalsIgnoreCase(objType)) && current.getDescription() != null && !current.getDescription().isBlank();
            int descHeight = 0;
            if (hasDesc) {
                graphics.textWithWordWrap(font, Component.literal((isCompleted ? "§8" : "§7") + current.getDescription()), rightX, contentY + 27, rightWidth - 6, isCompleted ? 0xFF64748B : 0xFF94A3B8);
                int descLines = font.split(Component.literal(current.getDescription()), rightWidth - 6).size();
                descHeight = descLines * 9 + 4;
            }

            int objY = contentY + 27 + descHeight;
            Player player = Minecraft.getInstance().player;
            int nextSectionY = objY + 28;

            if ("CRAFTEO".equalsIgnoreCase(objType)) {
                int req = current.getRequiredCount();
                String itemName = current.getItemDisplayName();
                Component objComp;
                if (isCompleted) {
                    objComp = Component.literal("§7Objetivo: Crafteo de " + req + " de " + itemName);
                } else {
                    objComp = Component.literal("§bObjetivo: §fCrafteo de §e" + req + " de " + itemName);
                }
                graphics.textWithWordWrap(font, objComp, rightX, objY, rightWidth - 6, isCompleted ? 0xFF888888 : 0xFFFFFFFF);
                int objLines = font.split(objComp, rightWidth - 6).size();
                int afterObjY = objY + (objLines * 9) + 3;

                int craftY;
                if (!isCompleted) {
                    int found = 0;
                    if (player != null) {
                        String reqId = current.getRequiredItemId();
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            ItemStack s = player.getInventory().getItem(i);
                            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equalsIgnoreCase(reqId)) {
                                found += s.getCount();
                            }
                        }
                    }
                    String invColor = (found >= req ? "§a" : "§c");
                    graphics.text(font, Component.literal("§fProgreso: " + invColor + found + " / " + req), rightX, afterObjY, 0xFFFFFFFF);
                    craftY = afterObjY + 24;
                } else {
                    craftY = afterObjY + 10;
                }

                // Grid 3x3 de Crafteo (con espacio separado de Progreso)
                graphics.text(font, Component.literal(isCompleted ? "§8Receta de crafteo:" : "§7Receta sugerida:"), rightX, craftY - 10, isCompleted ? 0xFF64748B : 0xFF94A3B8);

                Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(current.getRequiredItemId()));
                List<String> serverGrid = current.getRecipeGridItemIds();
                ItemStack[] grid = new ItemStack[9];
                boolean hasServerGrid = false;
                for (int i = 0; i < 9; i++) {
                    if (serverGrid != null && i < serverGrid.size() && !serverGrid.get(i).isEmpty()) {
                        Item ingItem = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(serverGrid.get(i)));
                        if (ingItem != null && ingItem != Items.AIR) {
                            grid[i] = new ItemStack(ingItem);
                            hasServerGrid = true;
                        } else {
                            grid[i] = ItemStack.EMPTY;
                        }
                    } else {
                        grid[i] = ItemStack.EMPTY;
                    }
                }
                if (!hasServerGrid) {
                    grid = resolveRecipeGrid(targetItem, player);
                }

                int slotSize = 16;
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int sx = rightX + c * slotSize;
                        int sy = craftY + r * slotSize;
                        graphics.fill(sx, sy, sx + slotSize - 1, sy + slotSize - 1, isCompleted ? 0x8805070A : 0x880F172A);
                        graphics.outline(sx, sy, slotSize - 1, slotSize - 1, isCompleted ? 0xFF334155 : 0xFF475569);
                        ItemStack ing = grid[r * 3 + c];
                        if (ing != null && !ing.isEmpty()) {
                            graphics.item(ing, sx, sy);
                            if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                                graphics.setTooltipForNextFrame(font, ing, mouseX, mouseY);
                            }
                        }
                    }
                }

                // Flecha y resultado
                int arrowX = rightX + (3 * slotSize) + 6;
                int arrowY = craftY + slotSize - 1;
                graphics.text(font, Component.literal(isCompleted ? "§8➜" : "§6➜"), arrowX, arrowY, 0xFFFFFFFF);

                int resX = arrowX + 12;
                int resY = craftY + slotSize - 5;
                graphics.fill(resX, resY, resX + 18, resY + 18, isCompleted ? 0x8805070A : 0xAA0F172A);
                graphics.outline(resX, resY, 18, 18, isCompleted ? 0xFF334155 : 0xFFF59E0B);
                ItemStack resStack = (targetItem != null && targetItem != Items.AIR) ? new ItemStack(targetItem, req) : ItemStack.EMPTY;
                if (!resStack.isEmpty()) {
                    graphics.item(resStack, resX + 1, resY + 1);
                    graphics.itemDecorations(font, resStack, resX + 1, resY + 1);
                    if (mouseX >= resX && mouseX < resX + 18 && mouseY >= resY && mouseY < resY + 18) {
                        graphics.setTooltipForNextFrame(font, resStack, mouseX, mouseY);
                    }
                }

                nextSectionY = craftY + (3 * slotSize) + 5;
            } else if ("OBTENCION".equalsIgnoreCase(objType)) {
                int req = current.getRequiredCount();
                String itemName = current.getItemDisplayName();
                Component objComp;
                if (isCompleted) {
                    objComp = Component.literal("§7Objetivo: Obtener " + req + " de " + itemName);
                } else {
                    objComp = Component.literal("§bObjetivo: §fObtener §e" + req + " de " + itemName);
                }
                graphics.textWithWordWrap(font, objComp, rightX, objY, rightWidth - 6, isCompleted ? 0xFF888888 : 0xFFFFFFFF);
                int objLines = font.split(objComp, rightWidth - 6).size();
                int afterObjY = objY + (objLines * 9) + 3;

                int itemBoxY;
                if (!isCompleted) {
                    int found = 0;
                    if (player != null) {
                        String reqId = current.getRequiredItemId();
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            ItemStack s = player.getInventory().getItem(i);
                            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equalsIgnoreCase(reqId)) {
                                found += s.getCount();
                            }
                        }
                    }
                    String invColor = (found >= req ? "§a" : "§c");
                    graphics.text(font, Component.literal("§fProgreso en inventario: " + invColor + found + " / " + req), rightX, afterObjY, 0xFFFFFFFF);
                    itemBoxY = afterObjY + 16;
                } else {
                    itemBoxY = afterObjY + 8;
                }

                int itemBoxX = rightX;
                graphics.fill(itemBoxX, itemBoxY, itemBoxX + 18, itemBoxY + 18, isCompleted ? 0x8805070A : 0xAA0F172A);
                graphics.outline(itemBoxX, itemBoxY, 18, 18, isCompleted ? 0xFF334155 : 0xFF38BDF8);
                Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(current.getRequiredItemId()));
                ItemStack reqStack = (targetItem != null && targetItem != Items.AIR) ? new ItemStack(targetItem, req) : ItemStack.EMPTY;
                if (!reqStack.isEmpty()) {
                    graphics.item(reqStack, itemBoxX + 1, itemBoxY + 1);
                    graphics.itemDecorations(font, reqStack, itemBoxX + 1, itemBoxY + 1);
                    if (mouseX >= itemBoxX && mouseX < itemBoxX + 18 && mouseY >= itemBoxY && mouseY < itemBoxY + 18) {
                        graphics.setTooltipForNextFrame(font, reqStack, mouseX, mouseY);
                    }
                }
                graphics.text(font, Component.literal(isCompleted ? "§8(Obtenido)" : "§7(Consigue este ítem y mantenlo en tu inventario)"), itemBoxX + 24, itemBoxY + 5, 0xFF888888);
                nextSectionY = itemBoxY + 24;
            } else if ("COCINAR".equalsIgnoreCase(objType)) {
                int req = current.getRequiredCount();
                String itemName = current.getItemDisplayName();
                Component objComp;
                if (isCompleted) {
                    objComp = Component.literal("§7Objetivo: Cocinar " + req + " de " + itemName);
                } else {
                    objComp = Component.literal("§bObjetivo: §fCocinar §e" + req + " de " + itemName);
                }
                graphics.textWithWordWrap(font, objComp, rightX, objY, rightWidth - 6, isCompleted ? 0xFF888888 : 0xFFFFFFFF);
                int objLines = font.split(objComp, rightWidth - 6).size();
                int afterObjY = objY + (objLines * 9) + 3;

                int found = current.getCurrentProgress();
                String invColor = (found >= req ? "§a" : "§c");
                graphics.text(font, Component.literal("§fCocinado y retirado: " + invColor + found + " / " + req), rightX, afterObjY, 0xFFFFFFFF);

                Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(current.getRequiredItemId()));
                ItemStack[] furnaceRecipe = resolveFurnaceRecipe(targetItem, player);
                ItemStack rawIngredient = furnaceRecipe[0];
                ItemStack fuelItem = furnaceRecipe[1];
                ItemStack resultStack = (targetItem != null && targetItem != Items.AIR) ? new ItemStack(targetItem, req) : ItemStack.EMPTY;

                int guideY = afterObjY + 14;
                graphics.text(font, Component.literal("§6🔥 Cómo cocinarlo:"), rightX, guideY, 0xFFF59E0B);

                // Caja visual con aspecto de Horno
                int fBoxX = rightX;
                int fBoxY = guideY + 11;
                int fBoxW = rightWidth - 8;
                int fBoxH = 50;
                graphics.fill(fBoxX, fBoxY, fBoxX + fBoxW, fBoxY + fBoxH, isCompleted ? 0x8805070A : 0xAA0A0F1D);
                graphics.outline(fBoxX, fBoxY, fBoxW, fBoxH, isCompleted ? 0xFF334155 : 0xFF475569);

                // Ranura de entrada (Ingrediente Crudo)
                int inSlotX = fBoxX + 10;
                int inSlotY = fBoxY + 4;
                graphics.fill(inSlotX, inSlotY, inSlotX + 18, inSlotY + 18, 0xAA0F172A);
                graphics.outline(inSlotX, inSlotY, 18, 18, isCompleted ? 0xFF334155 : 0xFF38BDF8);
                if (!rawIngredient.isEmpty()) {
                    graphics.item(rawIngredient, inSlotX + 1, inSlotY + 1);
                    if (mouseX >= inSlotX && mouseX < inSlotX + 18 && mouseY >= inSlotY && mouseY < inSlotY + 18) {
                        graphics.setTooltipForNextFrame(font, rawIngredient, mouseX, mouseY);
                    }
                }

                // Fuego central
                int flameX = inSlotX + 5;
                int flameY = inSlotY + 18;
                graphics.text(font, Component.literal("§6🔥"), flameX, flameY, 0xFFF59E0B);

                // Ranura de combustible (Carbón)
                int fuelSlotX = inSlotX;
                int fuelSlotY = inSlotY + 28;
                graphics.fill(fuelSlotX, fuelSlotY, fuelSlotX + 18, fuelSlotY + 18, 0xAA0F172A);
                graphics.outline(fuelSlotX, fuelSlotY, 18, 18, isCompleted ? 0xFF334155 : 0xFFF59E0B);
                if (!fuelItem.isEmpty()) {
                    graphics.item(fuelItem, fuelSlotX + 1, fuelSlotY + 1);
                    if (mouseX >= fuelSlotX && mouseX < fuelSlotX + 18 && mouseY >= fuelSlotY && mouseY < fuelSlotY + 18) {
                        graphics.setTooltipForNextFrame(font, fuelItem, mouseX, mouseY);
                    }
                }

                // Flecha indicadora hacia el resultado
                int arrowX = inSlotX + 24;
                int arrowY = inSlotY + 14;
                graphics.text(font, Component.literal(isCompleted ? "§8➔" : "§6➔"), arrowX, arrowY, 0xFFFFFFFF);

                // Ranura de salida (Ítem cocinado)
                int outSlotX = arrowX + 14;
                int outSlotY = inSlotY + 8;
                graphics.fill(outSlotX, outSlotY, outSlotX + 22, outSlotY + 22, isCompleted ? 0x8805070A : 0xAA0F172A);
                graphics.outline(outSlotX, outSlotY, 22, 22, isCompleted ? 0xFF334155 : 0xFF22C55E);
                if (!resultStack.isEmpty()) {
                    graphics.item(resultStack, outSlotX + 3, outSlotY + 3);
                    graphics.itemDecorations(font, resultStack, outSlotX + 3, outSlotY + 3);
                    if (mouseX >= outSlotX && mouseX < outSlotX + 22 && mouseY >= outSlotY && mouseY < outSlotY + 22) {
                        graphics.setTooltipForNextFrame(font, resultStack, mouseX, mouseY);
                    }
                }

                // Iconos de estaciones válidas a la derecha de la caja
                int stationX = outSlotX + 30;
                graphics.text(font, Component.literal("§7Fundir en:"), stationX, inSlotY + 2, 0xFF94A3B8);
                graphics.item(new ItemStack(Items.FURNACE), stationX, inSlotY + 14);
                graphics.item(new ItemStack(Items.SMOKER), stationX + 18, inSlotY + 14);
                graphics.item(new ItemStack(Items.BLAST_FURNACE), stationX + 36, inSlotY + 14);
                if (mouseX >= stationX && mouseX < stationX + 18 && mouseY >= inSlotY + 14 && mouseY < inSlotY + 32) {
                    graphics.setTooltipForNextFrame(font, new ItemStack(Items.FURNACE), mouseX, mouseY);
                } else if (mouseX >= stationX + 18 && mouseX < stationX + 36 && mouseY >= inSlotY + 14 && mouseY < inSlotY + 32) {
                    graphics.setTooltipForNextFrame(font, new ItemStack(Items.SMOKER), mouseX, mouseY);
                } else if (mouseX >= stationX + 36 && mouseX < stationX + 54 && mouseY >= inSlotY + 14 && mouseY < inSlotY + 32) {
                    graphics.setTooltipForNextFrame(font, new ItemStack(Items.BLAST_FURNACE), mouseX, mouseY);
                }

                // Explicación paso a paso de cómo completar la misión
                int stepsY = fBoxY + fBoxH + 4;
                graphics.text(font, Component.literal("§e1. §7Coloca el ingrediente crudo arriba con carbón abajo."), rightX, stepsY, 0xFFCBD5E1);
                graphics.text(font, Component.literal("§e2. §f¡Importante! §aRetira el ítem cocinado §fpara que cuente."), rightX, stepsY + 10, 0xFFFFFFFF);
                graphics.text(font, Component.literal("§7(Válido en Horno convencional, Ahumadero o Alto Horno)"), rightX, stepsY + 20, 0xFF64748B);

                nextSectionY = stepsY + 32;
            } else if ("EXPLORACION".equalsIgnoreCase(objType)) {
                if (current.getTargetPos() != null) {
                    BlockPos pos = current.getTargetPos();
                    if (isCompleted) {
                        graphics.text(font, Component.literal("§7Objetivo: Zona X: " + pos.getX() + ", Z: " + pos.getZ()), rightX, objY, 0xFF888888);
                        nextSectionY = objY + 16;
                    } else {
                        graphics.text(font, Component.literal("§bObjetivo: §fCoordenadas §eX: " + pos.getX() + ", Z: " + pos.getZ()), rightX, objY, 0xFFFFFFFF);
                        if (player != null) {
                            double dist = Math.sqrt(pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()));
                            graphics.text(font, Component.literal("§fDistancia: §a" + (int) dist + "m"), rightX, objY + 12, 0xFFFFFFFF);
                            nextSectionY = objY + 28;
                        } else {
                            nextSectionY = objY + 16;
                        }
                    }
                } else {
                    graphics.text(font, Component.literal("§7Objetivo: Exploración"), rightX, objY, 0xFF888888);
                    nextSectionY = objY + 16;
                }
            } else if ("INCURSION".equalsIgnoreCase(objType)) {
                BlockPos pos = current.getTargetPos();
                if (pos != null) {
                    if (isCompleted) {
                        graphics.text(font, Component.literal("§7Objetivo: Incursión (Completada)"), rightX, objY, 0xFF888888);
                        graphics.text(font, Component.literal("§8Zona: X: " + pos.getX() + ", Z: " + pos.getZ() + " | Oleadas: " + current.getTotalWaves()), rightX, objY + 12, 0xFF64748B);
                        nextSectionY = objY + 26;
                    } else {
                        graphics.text(font, Component.literal("§c§lObjetivo: §fIncursión en Zona"), rightX, objY, 0xFFFFFFFF);
                        graphics.text(font, Component.literal("§7Entrada: §eX: " + pos.getX() + ", Z: " + pos.getZ()), rightX, objY + 12, 0xFFFFFFFF);
                        if (player != null) {
                            double dist = Math.sqrt(pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()));
                            graphics.text(font, Component.literal("§fDistancia: §a" + (int) dist + "m §7| §c" + current.getTotalWaves() + " Oleadas"), rightX, objY + 24, 0xFFFFFFFF);
                        } else {
                            graphics.text(font, Component.literal("§7Oleadas: §c" + current.getTotalWaves()), rightX, objY + 24, 0xFFFFFFFF);
                        }
                        if (isAdmin) {
                            int spawns = current.getSpawnPoints() != null ? current.getSpawnPoints().size() : 0;
                            int chests = current.getChestPoints() != null ? current.getChestPoints().size() : 0;
                            graphics.text(font, Component.literal("§8[Admin] Spawns: " + spawns + " | Cofres: " + chests), rightX, objY + 36, 0xFF94A3B8);
                        } else {
                            graphics.text(font, Component.literal("§7Misión: §fSuperar oleadas y escapar"), rightX, objY + 36, 0xFF94A3B8);
                        }
                        nextSectionY = objY + 50;
                    }
                } else {
                    graphics.text(font, Component.literal("§cObjetivo: Incursión"), rightX, objY, 0xFFFFFFFF);
                    nextSectionY = objY + 16;
                }
            }

            // 4. Tipo de Drop
            int lootY = nextSectionY + 2;
            String rewardTier = current.getRewardTier() != null ? current.getRewardTier().toUpperCase() : "COMÚN";
            if (isCompleted) {
                graphics.text(font, Component.literal("§8Drop reclamado: §7" + rewardTier), rightX, lootY, 0xFF64748B);
            } else {
                if ("personalizado".equalsIgnoreCase(current.getRewardTier())) {
                    graphics.text(font, Component.literal("§6Tipo de Drop: §ePersonalizado §7(" + current.getCustomItemIds().size() + ")"), rightX, lootY, 0xFFFFFFFF);
                } else {
                    graphics.text(font, Component.literal("§6Tipo de Drop: §e" + rewardTier), rightX, lootY, 0xFFFFFFFF);
                }
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
