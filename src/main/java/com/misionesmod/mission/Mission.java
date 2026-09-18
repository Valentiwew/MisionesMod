package com.misionesmod.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public class Mission {
    private String id;
    private String title;
    private String description;
    private BlockPos targetPos;
    private String dimension;
    private String rewardTier;
    private String rewardDescription;
    private boolean completed;
    private java.util.Set<java.util.UUID> completedPlayers = new java.util.HashSet<>();
    private String objectiveType = "EXPLORACION"; // EXPLORACION, CRAFTEO, LIBRE
    private String requiredItemId = "minecraft:iron_ingot";
    private int requiredCount = 1;
    private transient boolean targetReached = false;

    public Mission() {}

    public Mission(String id, String title, String description, BlockPos targetPos, String dimension, String rewardTier, String rewardDescription) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.targetPos = targetPos;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.rewardTier = rewardTier != null ? rewardTier : "comun";
        this.rewardDescription = rewardDescription != null ? rewardDescription : "Botín sorpresa";
        this.completed = false;
        this.objectiveType = targetPos != null ? "EXPLORACION" : "LIBRE";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BlockPos getTargetPos() { return targetPos; }
    public void setTargetPos(BlockPos targetPos) { this.targetPos = targetPos; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getRewardTier() { return rewardTier; }
    public void setRewardTier(String rewardTier) { this.rewardTier = rewardTier; }

    public String getRewardDescription() { return rewardDescription; }
    public void setRewardDescription(String rewardDescription) { this.rewardDescription = rewardDescription; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean isCompletedBy(java.util.UUID playerUuid) {
        return completedPlayers != null && playerUuid != null && completedPlayers.contains(playerUuid);
    }

    public void setCompletedBy(java.util.UUID playerUuid, boolean completed) {
        if (completedPlayers == null) completedPlayers = new java.util.HashSet<>();
        if (playerUuid != null) {
            if (completed) {
                completedPlayers.add(playerUuid);
            } else {
                completedPlayers.remove(playerUuid);
            }
        }
    }

    public java.util.Set<java.util.UUID> getCompletedPlayers() {
        if (completedPlayers == null) completedPlayers = new java.util.HashSet<>();
        return completedPlayers;
    }

    public void setCompletedPlayers(java.util.Set<java.util.UUID> completedPlayers) {
        this.completedPlayers = completedPlayers != null ? completedPlayers : new java.util.HashSet<>();
    }

    public Mission copyForPlayer(java.util.UUID playerUuid) {
        Mission copy = new Mission(id, title, description, targetPos, dimension, rewardTier, rewardDescription);
        copy.setCompleted(isCompletedBy(playerUuid));
        copy.setObjectiveType(getObjectiveType());
        copy.setRequiredItemId(getRequiredItemId());
        copy.setRequiredCount(getRequiredCount());
        copy.setCustomItemIds(new java.util.ArrayList<>(customItemIds));
        copy.setCustomItemCounts(new java.util.ArrayList<>(customItemCounts));
        copy.setRecipeGridItemIds(new java.util.ArrayList<>(recipeGridItemIds));
        copy.setCompletedPlayers(new java.util.HashSet<>(getCompletedPlayers()));
        copy.setExtractionPos(extractionPos);
        copy.setRoofPos(roofPos);
        copy.setIncursionRadius(incursionRadius);
        copy.setSpawnPoints(new java.util.ArrayList<>(spawnPoints));
        copy.setMobTypes(new java.util.ArrayList<>(mobTypes));
        copy.setTotalWaves(totalWaves);
        copy.setBuildingChestLootIds(new java.util.ArrayList<>(buildingChestLootIds));
        copy.setBuildingChestLootCounts(new java.util.ArrayList<>(buildingChestLootCounts));
        copy.setChestPoints(new java.util.ArrayList<>(chestPoints));
        return copy;
    }

    public String getObjectiveType() { return objectiveType != null ? objectiveType : "EXPLORACION"; }
    public void setObjectiveType(String objectiveType) { this.objectiveType = objectiveType != null ? objectiveType : "EXPLORACION"; }

    public String getRequiredItemId() { return requiredItemId != null ? requiredItemId : ""; }
    public void setRequiredItemId(String requiredItemId) { this.requiredItemId = requiredItemId != null ? requiredItemId : ""; }

    public int getRequiredCount() { return Math.max(1, requiredCount); }
    public void setRequiredCount(int requiredCount) { this.requiredCount = Math.max(1, requiredCount); }

    public boolean isTargetReached() { return targetReached; }
    public void setTargetReached(boolean targetReached) { this.targetReached = targetReached; }

    // Campos y métodos de Incursión
    private BlockPos extractionPos = null;
    private BlockPos roofPos = null;
    private int incursionRadius = 24;
    private java.util.List<BlockPos> spawnPoints = new java.util.ArrayList<>();
    private java.util.List<String> mobTypes = new java.util.ArrayList<>();
    private int totalWaves = 4;
    private java.util.List<String> buildingChestLootIds = new java.util.ArrayList<>();
    private java.util.List<Integer> buildingChestLootCounts = new java.util.ArrayList<>();

    public BlockPos getExtractionPos() { return extractionPos; }
    public void setExtractionPos(BlockPos extractionPos) { this.extractionPos = extractionPos; }

    public BlockPos getRoofPos() { return roofPos; }
    public void setRoofPos(BlockPos roofPos) { this.roofPos = roofPos; }

    public int getIncursionRadius() { return incursionRadius > 0 ? incursionRadius : 24; }
    public void setIncursionRadius(int incursionRadius) { this.incursionRadius = incursionRadius; }

    public java.util.List<BlockPos> getSpawnPoints() { return spawnPoints; }
    public void setSpawnPoints(java.util.List<BlockPos> spawnPoints) { this.spawnPoints = spawnPoints != null ? spawnPoints : new java.util.ArrayList<>(); }

    public java.util.List<String> getMobTypes() { return mobTypes; }
    public void setMobTypes(java.util.List<String> mobTypes) { this.mobTypes = mobTypes != null ? mobTypes : new java.util.ArrayList<>(); }

    public int getTotalWaves() { return totalWaves > 0 ? totalWaves : 4; }
    public void setTotalWaves(int totalWaves) { this.totalWaves = totalWaves; }

    private java.util.List<BlockPos> chestPoints = new java.util.ArrayList<>();

    public java.util.List<BlockPos> getChestPoints() { return chestPoints; }
    public void setChestPoints(java.util.List<BlockPos> chestPoints) { this.chestPoints = chestPoints != null ? chestPoints : new java.util.ArrayList<>(); }

    public java.util.List<String> getBuildingChestLootIds() { return buildingChestLootIds; }
    public void setBuildingChestLootIds(java.util.List<String> list) { this.buildingChestLootIds = list != null ? list : new java.util.ArrayList<>(); }

    public java.util.List<Integer> getBuildingChestLootCounts() { return buildingChestLootCounts; }
    public void setBuildingChestLootCounts(java.util.List<Integer> list) { this.buildingChestLootCounts = list != null ? list : new java.util.ArrayList<>(); }

    public String getItemDisplayName() {
        if (requiredItemId == null || requiredItemId.isBlank()) return "Ítem";
        try {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(requiredItemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return item.getName(new net.minecraft.world.item.ItemStack(item)).getString();
            }
        } catch (Exception ignored) {}
        return requiredItemId.replace("minecraft:", "").replace("_", " ");
    }

    private java.util.List<String> customItemIds = new java.util.ArrayList<>();
    private java.util.List<Integer> customItemCounts = new java.util.ArrayList<>();

    public java.util.List<String> getCustomItemIds() { return customItemIds; }
    public void setCustomItemIds(java.util.List<String> list) { this.customItemIds = list != null ? list : new java.util.ArrayList<>(); }

    public java.util.List<Integer> getCustomItemCounts() { return customItemCounts; }
    public void setCustomItemCounts(java.util.List<Integer> list) { this.customItemCounts = list != null ? list : new java.util.ArrayList<>(); }

    private java.util.List<String> recipeGridItemIds = new java.util.ArrayList<>();

    public java.util.List<String> getRecipeGridItemIds() { return recipeGridItemIds; }
    public void setRecipeGridItemIds(java.util.List<String> list) { this.recipeGridItemIds = list != null ? list : new java.util.ArrayList<>(); }

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(title);
        buf.writeUtf(description);
        boolean hasPos = targetPos != null;
        buf.writeBoolean(hasPos);
        if (hasPos) {
            buf.writeBlockPos(targetPos);
        }
        buf.writeUtf(dimension);
        buf.writeUtf(rewardTier);
        buf.writeUtf(rewardDescription);
        buf.writeBoolean(completed);
        buf.writeUtf(getObjectiveType());
        buf.writeUtf(getRequiredItemId());
        buf.writeVarInt(getRequiredCount());

        buf.writeVarInt(customItemIds.size());
        for (int i = 0; i < customItemIds.size(); i++) {
            buf.writeUtf(customItemIds.get(i));
            buf.writeVarInt(customItemCounts.get(i));
        }

        buf.writeVarInt(recipeGridItemIds.size());
        for (String s : recipeGridItemIds) {
            buf.writeUtf(s != null ? s : "");
        }

        // Incursión
        boolean hasExt = extractionPos != null;
        buf.writeBoolean(hasExt);
        if (hasExt) buf.writeBlockPos(extractionPos);

        boolean hasRoof = roofPos != null;
        buf.writeBoolean(hasRoof);
        if (hasRoof) buf.writeBlockPos(roofPos);

        buf.writeVarInt(incursionRadius);

        buf.writeVarInt(spawnPoints.size());
        for (BlockPos p : spawnPoints) {
            buf.writeBlockPos(p);
        }

        buf.writeVarInt(mobTypes.size());
        for (String m : mobTypes) {
            buf.writeUtf(m != null ? m : "");
        }

        buf.writeVarInt(totalWaves);

        buf.writeVarInt(buildingChestLootIds.size());
        for (int i = 0; i < buildingChestLootIds.size(); i++) {
            buf.writeUtf(buildingChestLootIds.get(i));
            buf.writeVarInt(buildingChestLootCounts.get(i));
        }

        buf.writeVarInt(chestPoints.size());
        for (BlockPos p : chestPoints) {
            buf.writeBlockPos(p);
        }
    }

    public static Mission readFromBuf(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String title = buf.readUtf();
        String desc = buf.readUtf();
        boolean hasPos = buf.readBoolean();
        BlockPos pos = hasPos ? buf.readBlockPos() : null;
        String dim = buf.readUtf();
        String tier = buf.readUtf();
        String rewardDesc = buf.readUtf();
        boolean comp = buf.readBoolean();
        String objType = buf.readUtf();
        String reqItem = buf.readUtf();
        int reqCount = buf.readVarInt();

        Mission mission = new Mission(id, title, desc, pos, dim, tier, rewardDesc);
        mission.setCompleted(comp);
        mission.setObjectiveType(objType);
        mission.setRequiredItemId(reqItem);
        mission.setRequiredCount(reqCount);

        int customCount = buf.readVarInt();
        java.util.List<String> ids = new java.util.ArrayList<>(customCount);
        java.util.List<Integer> counts = new java.util.ArrayList<>(customCount);
        for (int i = 0; i < customCount; i++) {
            ids.add(buf.readUtf());
            counts.add(buf.readVarInt());
        }
        mission.setCustomItemIds(ids);
        mission.setCustomItemCounts(counts);

        int gridCount = buf.readVarInt();
        java.util.List<String> grid = new java.util.ArrayList<>(gridCount);
        for (int i = 0; i < gridCount; i++) {
            grid.add(buf.readUtf());
        }
        mission.setRecipeGridItemIds(grid);

        // Incursión
        boolean hasExt = buf.readBoolean();
        BlockPos extPos = hasExt ? buf.readBlockPos() : null;
        mission.setExtractionPos(extPos);

        boolean hasRoof = buf.readBoolean();
        BlockPos roofPos = hasRoof ? buf.readBlockPos() : null;
        mission.setRoofPos(roofPos);

        mission.setIncursionRadius(buf.readVarInt());

        int spawnCount = buf.readVarInt();
        java.util.List<BlockPos> spawns = new java.util.ArrayList<>(spawnCount);
        for (int i = 0; i < spawnCount; i++) {
            spawns.add(buf.readBlockPos());
        }
        mission.setSpawnPoints(spawns);

        int mobCount = buf.readVarInt();
        java.util.List<String> mobs = new java.util.ArrayList<>(mobCount);
        for (int i = 0; i < mobCount; i++) {
            mobs.add(buf.readUtf());
        }
        mission.setMobTypes(mobs);

        mission.setTotalWaves(buf.readVarInt());

        int lootCount = buf.readVarInt();
        java.util.List<String> lootIds = new java.util.ArrayList<>(lootCount);
        java.util.List<Integer> lootCounts = new java.util.ArrayList<>(lootCount);
        for (int i = 0; i < lootCount; i++) {
            lootIds.add(buf.readUtf());
            lootCounts.add(buf.readVarInt());
        }
        mission.setBuildingChestLootIds(lootIds);
        mission.setBuildingChestLootCounts(lootCounts);

        int chestCount = buf.readVarInt();
        java.util.List<BlockPos> chests = new java.util.ArrayList<>(chestCount);
        for (int i = 0; i < chestCount; i++) {
            chests.add(buf.readBlockPos());
        }
        mission.setChestPoints(chests);

        return mission;
    }
}
