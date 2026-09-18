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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import com.misionesmod.mission.Mission;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class CreateMissionScreen extends Screen {
    private final Screen parentScreen;

    private static final int PANEL_WIDTH = 270;
    private static final int PANEL_HEIGHT = 292;

    private EditBox titleBox;

    private static final String[] OBJECTIVE_TYPES = {"INCURSION", "OBTENCION", "CRAFTEO"};
    private static final String[] OBJECTIVE_NAMES = {"Incursión (Edificio)", "Obtener Ítem", "Crafteo de Ítem"};
    private int selectedObjectiveIndex = 0; // "INCURSION" por defecto
    private Button objectiveButton;

    // Campos de crafteo / obtención
    private String selectedItemId = "minecraft:iron_ingot";
    private Button selectItemButton;
    private EditBox reqCountBox;

    // Campos de Incursión
    private BlockPos incursionExtractionPos = null;
    private BlockPos incursionRoofPos = null;
    private final List<BlockPos> incursionSpawns = new ArrayList<>();
    private final List<BlockPos> incursionChestPositions = new ArrayList<>();
    private List<String> incursionMobs = new ArrayList<>();
    private int incursionWaves = 4;
    private List<ItemStack> incursionChestItems = new ArrayList<>();

    private Button incursionExtractionBtn;
    private Button incursionRoofBtn;
    private Button incursionSpawnBtn;
    private Button incursionWavesBtn;
    private Button incursionMobsBtn;
    private Button incursionChestsBtn;
    private Button incursionWorldSetupBtn;

    private static final String[] TIERS = {"comun", "raro", "epico", "legendario", "personalizado"};
    private static final String[] TIER_NAMES = {"Común", "Raro", "Épico", "Legendario", "Personalizado"};
    private int selectedTierIndex = 0; // "comun" por defecto
    private Button tierButton;
    private Button designLootButton;

    private final Mission existingMission;
    private List<ItemStack> customItems = new ArrayList<>();

    private String savedTitle = "";
    private String savedDesc = "";
    private String savedReqCount = "1";
    private boolean valuesInitialized = false;

    public CreateMissionScreen(Screen parentScreen) {
        this(parentScreen, null);
    }

    public CreateMissionScreen(Screen parentScreen, Mission existingMission) {
        super(Component.literal(existingMission != null ? "Modificar Misión (Admin)" : "Crear Nueva Misión (Admin)"));
        this.parentScreen = parentScreen;
        this.existingMission = existingMission;

        if (existingMission != null) {
            this.savedTitle = existingMission.getTitle() != null ? existingMission.getTitle() : "";
            this.savedDesc = existingMission.getDescription() != null ? existingMission.getDescription() : "";
            this.savedReqCount = String.valueOf(existingMission.getRequiredCount());
            this.valuesInitialized = true;

            for (int i = 0; i < TIERS.length; i++) {
                if (TIERS[i].equalsIgnoreCase(existingMission.getRewardTier())) {
                    this.selectedTierIndex = i;
                    break;
                }
            }

            for (int i = 0; i < OBJECTIVE_TYPES.length; i++) {
                if (OBJECTIVE_TYPES[i].equalsIgnoreCase(existingMission.getObjectiveType())) {
                    this.selectedObjectiveIndex = i;
                    break;
                }
            }

            if (existingMission.getRequiredItemId() != null && !existingMission.getRequiredItemId().isEmpty()) {
                this.selectedItemId = existingMission.getRequiredItemId();
            }

            // Incursión
            this.incursionExtractionPos = existingMission.getExtractionPos();
            this.incursionRoofPos = existingMission.getRoofPos();
            if (existingMission.getSpawnPoints() != null) {
                this.incursionSpawns.addAll(existingMission.getSpawnPoints());
            }
            if (existingMission.getMobTypes() != null) {
                this.incursionMobs.addAll(existingMission.getMobTypes());
            }
            if (existingMission.getChestPoints() != null) {
                this.incursionChestPositions.addAll(existingMission.getChestPoints());
            }

            List<String> chestIds = existingMission.getBuildingChestLootIds();
            List<Integer> chestCounts = existingMission.getBuildingChestLootCounts();
            for (int i = 0; i < chestIds.size(); i++) {
                try {
                    Identifier id = Identifier.parse(chestIds.get(i));
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    if (item != null && item != Items.AIR) {
                        int count = Math.max(1, chestCounts.get(i));
                        this.incursionChestItems.add(new ItemStack(item, count));
                    }
                } catch (Exception ignored) {}
            }

            List<String> ids = existingMission.getCustomItemIds();
            List<Integer> counts = existingMission.getCustomItemCounts();
            for (int i = 0; i < ids.size(); i++) {
                try {
                    Identifier id = Identifier.parse(ids.get(i));
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    if (item != null && item != Items.AIR) {
                        int count = Math.max(1, counts.get(i));
                        this.customItems.add(new ItemStack(item, count));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public Screen getParentScreen() {
        return this.parentScreen;
    }

    public void restoreFromSetupSession(
            String title,
            String desc,
            int objectiveIndex,
            int tierIndex,
            List<ItemStack> customItems,
            int waves,
            List<String> mobs,
            List<ItemStack> chestLoot,
            BlockPos extractionPos,
            BlockPos roofPos,
            List<BlockPos> spawns,
            List<BlockPos> chestPositions
    ) {
        this.savedTitle = title;
        this.savedDesc = desc;
        this.selectedObjectiveIndex = objectiveIndex;
        this.selectedTierIndex = tierIndex;
        this.customItems = new ArrayList<>(customItems);
        this.incursionWaves = waves;
        this.incursionMobs = new ArrayList<>(mobs);
        this.incursionChestItems = new ArrayList<>(chestLoot);
        this.incursionExtractionPos = extractionPos;
        this.incursionRoofPos = roofPos;
        this.incursionSpawns.clear();
        if (spawns != null) {
            this.incursionSpawns.addAll(spawns);
        }
        this.incursionChestPositions.clear();
        if (chestPositions != null) {
            this.incursionChestPositions.addAll(chestPositions);
        }
        this.valuesInitialized = true;
    }

    private void saveInputValues() {
        if (titleBox != null) savedTitle = titleBox.getValue();
        if (reqCountBox != null) savedReqCount = reqCountBox.getValue();
    }

    private String getItemDisplayName(String itemId) {
        if (itemId == null || itemId.isBlank()) return "Ítem";
        try {
            Identifier id = Identifier.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null && item != Items.AIR) {
                return item.getName(item.getDefaultInstance()).getString();
            }
        } catch (Exception ignored) {}
        return itemId.replace("minecraft:", "").replace("_", " ");
    }

    @Override
    protected void init() {
        super.init();

        saveInputValues();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldWidth = 230;
        int fieldX = panelX + (PANEL_WIDTH - fieldWidth) / 2;

        // 1. Título
        titleBox = new EditBox(this.font, fieldX, panelY + 39, fieldWidth, 18, Component.literal("Título"));
        titleBox.setMaxLength(64);
        titleBox.setHint(Component.literal("Nombre de la misión..."));
        titleBox.setValue(savedTitle);
        this.addRenderableWidget(titleBox);

        // 2. Selector de Tipo de Objetivo
        objectiveButton = Button.builder(
                Component.literal("Objetivo: §b" + OBJECTIVE_NAMES[selectedObjectiveIndex]),
                b -> {
                    selectedObjectiveIndex = (selectedObjectiveIndex + 1) % OBJECTIVE_TYPES.length;
                    objectiveButton.setMessage(Component.literal("Objetivo: §b" + OBJECTIVE_NAMES[selectedObjectiveIndex]));
                    updateObjectiveWidgets();
                }
        ).bounds(fieldX, panelY + 74, fieldWidth, 19).build();
        this.addRenderableWidget(objectiveButton);

        // 4B. Selector visual de Ítem y Cantidad (para Obtención y Crafteo)
        selectItemButton = Button.builder(
                Component.literal("§e📦 " + getItemDisplayName(selectedItemId)),
                b -> {
                    saveInputValues();
                    if (this.minecraft != null) {
                        boolean isCraft = "CRAFTEO".equalsIgnoreCase(OBJECTIVE_TYPES[selectedObjectiveIndex]);
                        this.minecraft.gui.setScreen(new ItemSelectorScreen(this, isCraft, item -> {
                            this.selectedItemId = BuiltInRegistries.ITEM.getKey(item).toString();
                            if (selectItemButton != null) {
                                selectItemButton.setMessage(Component.literal("§e📦 " + getItemDisplayName(selectedItemId)));
                            }
                        }));
                    }
                }
        ).bounds(fieldX, panelY + 114, 166, 19).build();
        this.addRenderableWidget(selectItemButton);

        reqCountBox = new EditBox(this.font, fieldX + 172, panelY + 114, 58, 18, Component.literal("Cant"));
        reqCountBox.setMaxLength(4);
        reqCountBox.setValue(savedReqCount.isEmpty() ? "1" : savedReqCount);
        this.addRenderableWidget(reqCountBox);

        // 4C. Controles específicos de INCURSIÓN (con espaciado vertical de 24px)
        int halfW = 112;
        int col2X = fieldX + 118;

        incursionExtractionBtn = Button.builder(
                Component.literal("📍 Entrada/Escape " + (incursionExtractionPos != null ? "§a✔" : "§7[Aquí]")),
                b -> {
                    Player p = Minecraft.getInstance().player;
                    if (p != null) {
                        incursionExtractionPos = p.blockPosition();
                        incursionExtractionBtn.setMessage(Component.literal("📍 Entrada/Escape §a✔"));
                    }
                }
        ).bounds(fieldX, panelY + 104, halfW, 18).build();
        this.addRenderableWidget(incursionExtractionBtn);

        incursionRoofBtn = Button.builder(
                Component.literal("📍 Azotea/Cima " + (incursionRoofPos != null ? "§a✔" : "§7[Aquí]")),
                b -> {
                    Player p = Minecraft.getInstance().player;
                    if (p != null) {
                        incursionRoofPos = p.blockPosition();
                        incursionRoofBtn.setMessage(Component.literal("📍 Azotea/Cima §a✔"));
                    }
                }
        ).bounds(col2X, panelY + 104, halfW, 18).build();
        this.addRenderableWidget(incursionRoofBtn);

        incursionSpawnBtn = Button.builder(
                Component.literal("➕ Spawn (" + incursionSpawns.size() + ")"),
                b -> {
                    Player p = Minecraft.getInstance().player;
                    if (p != null) {
                        incursionSpawns.add(p.blockPosition());
                        incursionSpawnBtn.setMessage(Component.literal("➕ Spawn (" + incursionSpawns.size() + ")"));
                    }
                }
        ).bounds(fieldX, panelY + 128, halfW, 18).build();
        this.addRenderableWidget(incursionSpawnBtn);

        incursionWavesBtn = Button.builder(
                Component.literal("Oleadas: §e" + incursionWaves),
                b -> {
                    incursionWaves = (incursionWaves % 6) + 1;
                    if (incursionWaves < 2) incursionWaves = 2;
                    incursionWavesBtn.setMessage(Component.literal("Oleadas: §e" + incursionWaves));
                }
        ).bounds(col2X, panelY + 128, halfW, 18).build();
        this.addRenderableWidget(incursionWavesBtn);

        incursionMobsBtn = Button.builder(
                Component.literal("🧟 Mobs (" + (incursionMobs.isEmpty() ? "Zombies" : incursionMobs.size()) + ")"),
                b -> {
                    saveInputValues();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new MobSelectorScreen(this, incursionMobs, mobs -> {
                            this.incursionMobs = mobs;
                            if (incursionMobsBtn != null) {
                                incursionMobsBtn.setMessage(Component.literal("🧟 Mobs (" + (incursionMobs.isEmpty() ? "Zombies" : incursionMobs.size()) + ")"));
                            }
                        }));
                    }
                }
        ).bounds(fieldX, panelY + 152, halfW, 18).build();
        this.addRenderableWidget(incursionMobsBtn);

        incursionChestsBtn = Button.builder(
                Component.literal("📦 Botín Cofres (" + incursionChestItems.size() + ")"),
                b -> {
                    saveInputValues();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new LootEditorScreen(this, "incursion_chest", incursionChestItems, items -> {
                            this.incursionChestItems = items;
                            if (incursionChestsBtn != null) {
                                incursionChestsBtn.setMessage(Component.literal("📦 Botín Cofres (" + incursionChestItems.size() + ")"));
                            }
                        }));
                    }
                }
        ).bounds(col2X, panelY + 152, halfW, 18).build();
        this.addRenderableWidget(incursionChestsBtn);

        incursionWorldSetupBtn = Button.builder(
                Component.literal("§6🚶 Marcar Puntos en el Mundo"),
                b -> {
                    saveInputValues();
                    IncursionSetupSession.start(
                            this,
                            this.existingMission,
                            this.savedTitle,
                            "",
                            this.selectedObjectiveIndex,
                            this.selectedTierIndex,
                            this.customItems,
                            this.incursionWaves,
                            this.incursionMobs,
                            this.incursionChestItems,
                            this.incursionExtractionPos,
                            this.incursionRoofPos,
                            this.incursionSpawns,
                            this.incursionChestPositions
                    );
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(null);
                    }
                }
        ).bounds(fieldX, panelY + 176, fieldWidth, 19).build();
        this.addRenderableWidget(incursionWorldSetupBtn);

        // 5. Selector de Tier de Recompensa
        tierButton = Button.builder(
                Component.literal("Tipo de Drop: §e" + TIER_NAMES[selectedTierIndex]),
                b -> {
                    selectedTierIndex = (selectedTierIndex + 1) % TIERS.length;
                    tierButton.setMessage(Component.literal("Tipo de Drop: §e" + TIER_NAMES[selectedTierIndex]));
                    updateLootDesignButton();
                }
        ).bounds(fieldX, panelY + 200, fieldWidth, 19).build();
        this.addRenderableWidget(tierButton);

        // 6. Botón opcional para diseñar botín creativo de recompensa final
        designLootButton = Button.builder(
                Component.literal("§e📦 Editar Loot en Creativo"),
                b -> {
                    saveInputValues();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new LootEditorScreen(this, "mission", this.customItems, items -> this.customItems = items));
                    }
                }
        ).bounds(fieldX, panelY + 224, fieldWidth, 19).build();
        this.addRenderableWidget(designLootButton);

        // 7. Botones de acción inferiores
        int actionY = panelY + PANEL_HEIGHT - 25;
        Button saveButton = Button.builder(
                Component.literal(existingMission != null ? "§aGuardar" : "§aPublicar"),
                b -> saveAndSend()
        ).bounds(panelX + (PANEL_WIDTH / 2) - 110, actionY, 105, 19).build();
        this.addRenderableWidget(saveButton);

        Button cancelButton = Button.builder(
                Component.literal("Cancelar"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parentScreen);
                    }
                }
        ).bounds(panelX + (PANEL_WIDTH / 2) + 5, actionY, 100, 19).build();
        this.addRenderableWidget(cancelButton);

        updateObjectiveWidgets();
        updateLootDesignButton();
    }

    private void updateObjectiveWidgets() {
        String obj = OBJECTIVE_TYPES[selectedObjectiveIndex];
        boolean isItemRelated = "OBTENCION".equalsIgnoreCase(obj) || "CRAFTEO".equalsIgnoreCase(obj);
        boolean isIncursion = "INCURSION".equalsIgnoreCase(obj);

        int panelY = (this.height - PANEL_HEIGHT) / 2;

        if (selectItemButton != null) { selectItemButton.visible = isItemRelated; selectItemButton.active = isItemRelated; }
        if (reqCountBox != null) { reqCountBox.visible = isItemRelated; reqCountBox.active = isItemRelated; }

        if (incursionExtractionBtn != null) { incursionExtractionBtn.visible = isIncursion; incursionExtractionBtn.active = isIncursion; }
        if (incursionRoofBtn != null) { incursionRoofBtn.visible = isIncursion; incursionRoofBtn.active = isIncursion; }
        if (incursionSpawnBtn != null) { incursionSpawnBtn.visible = isIncursion; incursionSpawnBtn.active = isIncursion; }
        if (incursionWavesBtn != null) { incursionWavesBtn.visible = isIncursion; incursionWavesBtn.active = isIncursion; }
        if (incursionMobsBtn != null) { incursionMobsBtn.visible = isIncursion; incursionMobsBtn.active = isIncursion; }
        if (incursionChestsBtn != null) { incursionChestsBtn.visible = isIncursion; incursionChestsBtn.active = isIncursion; }
        if (incursionWorldSetupBtn != null) { incursionWorldSetupBtn.visible = isIncursion; incursionWorldSetupBtn.active = isIncursion; }

        int tierY = isIncursion ? panelY + 200 : panelY + 148;
        int lootDesignY = isIncursion ? panelY + 224 : panelY + 172;

        if (tierButton != null) {
            tierButton.setY(tierY);
        }
        if (designLootButton != null) {
            designLootButton.setY(lootDesignY);
        }
    }

    private void updateLootDesignButton() {
        boolean isCustom = "personalizado".equalsIgnoreCase(TIERS[selectedTierIndex]);
        if (designLootButton != null) {
            designLootButton.visible = isCustom;
            designLootButton.active = isCustom;
        }
    }

    private void saveAndSend() {
        String title = titleBox.getValue().trim();
        if (title.isEmpty()) {
            title = "Misión Táctica";
        }

        String objType = OBJECTIVE_TYPES[selectedObjectiveIndex];
        BlockPos targetPos = null;
        Player player = Minecraft.getInstance().player;
        BlockPos playerPos = player != null ? player.blockPosition() : BlockPos.ZERO;

        if ("INCURSION".equalsIgnoreCase(objType)) {
            targetPos = incursionExtractionPos != null ? incursionExtractionPos : playerPos;
            if (incursionExtractionPos == null) incursionExtractionPos = targetPos;
            if (incursionRoofPos == null) incursionRoofPos = targetPos.above(15);
            if (incursionSpawns.isEmpty()) incursionSpawns.add(targetPos);
        } else {
            targetPos = playerPos;
        }

        int reqCount = 1;
        if (reqCountBox != null) {
            try { reqCount = Math.max(1, Integer.parseInt(reqCountBox.getValue().trim())); } catch (Exception ignored) {}
        }

        String desc = "";
        if ("INCURSION".equalsIgnoreCase(objType)) {
            desc = "Incursión en edificio: superar " + incursionWaves + " oleadas y escapar a la entrada.";
        }

        String tier = TIERS[selectedTierIndex];
        String reward = "Botín " + TIER_NAMES[selectedTierIndex];

        List<String> itemIds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        if ("personalizado".equalsIgnoreCase(tier)) {
            for (ItemStack stack : customItems) {
                if (!stack.isEmpty()) {
                    itemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    counts.add(stack.getCount());
                }
            }
        }

        List<String> chestLootIds = new ArrayList<>();
        List<Integer> chestLootCounts = new ArrayList<>();
        for (ItemStack stack : incursionChestItems) {
            if (!stack.isEmpty()) {
                chestLootIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                chestLootCounts.add(stack.getCount());
            }
        }

        String missionId = existingMission != null ? existingMission.getId() : "";
        ClientPlayNetworking.send(new ModPackets.CreateMissionPayload(
                missionId,
                title,
                desc,
                targetPos,
                tier,
                reward,
                objType,
                selectedItemId,
                reqCount,
                itemIds,
                counts,
                incursionExtractionPos,
                incursionRoofPos,
                36,
                incursionSpawns,
                incursionMobs,
                incursionWaves,
                chestLootIds,
                chestLootCounts,
                incursionChestPositions
        ));

        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parentScreen);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        Font font = this.font;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;
        int fieldX = panelX + (PANEL_WIDTH - 230) / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2090D16);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF334155);

        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 22, 0x551E293B);
        graphics.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, 0xFF334155);
        graphics.centeredText(font, this.title, centerX, panelY + 7, 0xFFFFFFFF);

        String obj = OBJECTIVE_TYPES[selectedObjectiveIndex];
        if ("OBTENCION".equalsIgnoreCase(obj)) {
            graphics.text(font, Component.literal("§7Ítem Requerido:"), fieldX, panelY + 101, 0xFFCCCCCC);
            graphics.text(font, Component.literal("§7Cantidad:"), fieldX + 172, panelY + 101, 0xFFCCCCCC);
        } else if ("CRAFTEO".equalsIgnoreCase(obj)) {
            graphics.text(font, Component.literal("§7Ítem a Craftear:"), fieldX, panelY + 101, 0xFFCCCCCC);
            graphics.text(font, Component.literal("§7Cantidad:"), fieldX + 172, panelY + 101, 0xFFCCCCCC);
        } else if ("INCURSION".equalsIgnoreCase(obj)) {
            graphics.centeredText(font, Component.literal("§7Configuración de Incursión en Edificio:"), centerX, panelY + 93, 0xFFCCCCCC);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
