package com.misionesmod.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.misionesmod.MisionesMod;
import com.misionesmod.loot.LootConfig;
import com.misionesmod.network.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MissionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Mission> missions = new ArrayList<>();
    private static File storageFile;

    public static void initialize() {
        // Inicialización base sin cargar archivo global
    }

    public static synchronized void loadForServer(MinecraftServer server) {
        if (server == null) return;
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        File dir = worldDir.resolve("misionesmod").toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        storageFile = new File(dir, "missions.json");
        load();
        for (Mission m : missions) {
            if ("CRAFTEO".equalsIgnoreCase(m.getObjectiveType()) && (m.getRecipeGridItemIds() == null || m.getRecipeGridItemIds().isEmpty())) {
                updateRecipeGridForMission(m, server);
            }
        }
    }

    public static synchronized void clear() {
        missions.clear();
        storageFile = null;
    }

    public static synchronized void load() {
        missions.clear();
        if (storageFile != null && storageFile.exists()) {
            try (FileReader reader = new FileReader(storageFile)) {
                Type listType = new TypeToken<ArrayList<Mission>>() {}.getType();
                List<Mission> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) {
                    missions.clear();
                    missions.addAll(loaded);
                    MisionesMod.LOGGER.info("Se cargaron {} misiones desde missions.json", missions.size());
                }
            } catch (Exception e) {
                MisionesMod.LOGGER.error("Error al cargar misiones desde {}", storageFile.getAbsolutePath(), e);
            }
        }
    }

    public static synchronized void save() {
        if (storageFile != null) {
            try (FileWriter writer = new FileWriter(storageFile)) {
                GSON.toJson(missions, writer);
            } catch (Exception e) {
                MisionesMod.LOGGER.error("Error al guardar misiones en {}", storageFile.getAbsolutePath(), e);
            }
        }
    }

    public static synchronized List<Mission> getMissions() {
        return new ArrayList<>(missions);
    }

    public static synchronized Mission getMission(String id) {
        for (Mission m : missions) {
            if (m.getId().equalsIgnoreCase(id)) {
                return m;
            }
        }
        return null;
    }

    public static synchronized void addMission(Mission mission, MinecraftServer server) {
        if (server != null) {
            updateRecipeGridForMission(mission, server);
        }
        missions.removeIf(m -> m.getId().equalsIgnoreCase(mission.getId()));
        missions.add(mission);
        save();
        if (server != null) {
            syncToAll(server);
            ModPackets.NotificationPayload missionNotif = new ModPackets.NotificationPayload(
                    "§aNueva misión disponible: §e" + mission.getTitle(),
                    0xFF22C55E
            );
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, missionNotif);
            }
        }
    }

    public static synchronized boolean removeMission(String id, MinecraftServer server) {
        boolean removed = missions.removeIf(m -> m.getId().equalsIgnoreCase(id));
        if (removed) {
            save();
            if (server != null) {
                syncToAll(server);
            }
        }
        return removed;
    }

    private static final Map<String, Integer> playerBaselineCraftStats = new HashMap<>();
    private static final Map<String, Integer> playerSmeltedCounts = new HashMap<>();

    public static int getCraftedCount(Mission mission, ServerPlayer player) {
        if (!"CRAFTEO".equalsIgnoreCase(mission.getObjectiveType())) return 0;
        try {
            Identifier id = Identifier.parse(mission.getRequiredItemId());
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR) return 0;
            int totalCrafted = player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(item));
            String key = mission.getId() + "_" + player.getUUID();
            if (!playerBaselineCraftStats.containsKey(key)) {
                playerBaselineCraftStats.put(key, totalCrafted);
                return 0;
            }
            return Math.max(0, totalCrafted - playerBaselineCraftStats.get(key));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getSmeltedCount(Mission mission, ServerPlayer player) {
        if (!"COCINAR".equalsIgnoreCase(mission.getObjectiveType())) return 0;
        String key = mission.getId() + "_" + player.getUUID();
        return playerSmeltedCounts.getOrDefault(key, 0);
    }

    public static synchronized void onItemSmelted(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || count <= 0) return;
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        List<Mission> missions = getMissions();
        boolean changed = false;
        for (Mission m : missions) {
            if ("COCINAR".equalsIgnoreCase(m.getObjectiveType()) && !m.isCompletedBy(player.getUUID())) {
                if (itemId.equalsIgnoreCase(m.getRequiredItemId())) {
                    String key = m.getId() + "_" + player.getUUID();
                    int current = playerSmeltedCounts.getOrDefault(key, 0) + count;
                    playerSmeltedCounts.put(key, current);
                    changed = true;
                    if (current >= m.getRequiredCount()) {
                        completeMission(m.getId(), player);
                    }
                }
            }
        }
        if (changed) {
            syncToPlayer(player);
        }
    }

    public static synchronized boolean completeMission(String id, ServerPlayer player) {
        Mission mission = getMission(id);
        if (mission != null && !mission.isCompletedBy(player.getUUID())) {
            // 1. Validar objetivo según su tipo
            String objType = mission.getObjectiveType();
            if ("CRAFTEO".equalsIgnoreCase(objType)) {
                int crafted = getCraftedCount(mission, player);
                if (crafted < mission.getRequiredCount()) {
                    return false;
                }
            } else if ("COCINAR".equalsIgnoreCase(objType)) {
                int smelted = getSmeltedCount(mission, player);
                if (smelted < mission.getRequiredCount()) {
                    return false;
                }
            } else if ("OBTENCION".equalsIgnoreCase(objType)) {
                String reqId = mission.getRequiredItemId();
                int reqCount = mission.getRequiredCount();
                int found = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equalsIgnoreCase(reqId)) {
                        found += s.getCount();
                    }
                }
                if (found < reqCount) {
                    return false;
                }

                // El jugador conserva todos los objetos conseguidos tanto en CRAFTEO como en OBTENCION.
            } else if ("EXPLORACION".equalsIgnoreCase(objType)) {
                if (mission.getTargetPos() != null) {
                    double dist = Math.sqrt(mission.getTargetPos().distToCenterSqr(player.getX(), player.getY(), player.getZ()));
                    if (dist > 8.0) {
                        return false;
                    }
                }
            }

            mission.setCompletedBy(player.getUUID(), true);
            save();

            // Give loot based on tier (or custom loot if personalizado)
            List<ItemStack> rewards = new ArrayList<>();
            if ("personalizado".equalsIgnoreCase(mission.getRewardTier()) && !mission.getCustomItemIds().isEmpty()) {
                for (int i = 0; i < mission.getCustomItemIds().size(); i++) {
                    try {
                        Identifier itemId = Identifier.parse(mission.getCustomItemIds().get(i));
                        Item item = BuiltInRegistries.ITEM.getValue(itemId);
                        if (item != null && item != Items.AIR) {
                            int count = Math.max(1, mission.getCustomItemCounts().get(i));
                            rewards.add(new ItemStack(item, count));
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (rewards.isEmpty()) {
                rewards = LootConfig.getRandomLoot(mission.getRewardTier(), 3, player.level().getRandom());
            }

            for (ItemStack reward : rewards) {
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
            }

            // Notification sound & actionbar overlay (sin spam en el chat)
            player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    1.0f, 1.0f
            );



            if (player.level().getServer() != null) {
                syncToAll(player.level().getServer());
            }
            return true;
        }
        return false;
    }

    private static int tickCounter = 0;

    public static synchronized void tickMissions(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return; // Chequear cada 1 segundo

        List<Mission> currentMissions = getMissions();
        if (currentMissions.isEmpty()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            for (Mission m : currentMissions) {
                if (m.isCompletedBy(player.getUUID())) continue;
                String objType = m.getObjectiveType();
                if ("CRAFTEO".equalsIgnoreCase(objType)) {
                    int crafted = getCraftedCount(m, player);
                    if (crafted >= m.getRequiredCount()) {
                        completeMission(m.getId(), player);
                        break;
                    }
                } else if ("COCINAR".equalsIgnoreCase(objType)) {
                    int smelted = getSmeltedCount(m, player);
                    if (smelted >= m.getRequiredCount()) {
                        completeMission(m.getId(), player);
                        break;
                    }
                } else if ("OBTENCION".equalsIgnoreCase(objType)) {
                    String reqId = m.getRequiredItemId();
                    int reqCount = m.getRequiredCount();
                    int found = 0;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack s = player.getInventory().getItem(i);
                        if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equalsIgnoreCase(reqId)) {
                            found += s.getCount();
                        }
                    }
                    if (found >= reqCount) {
                        completeMission(m.getId(), player);
                        break;
                    }
                }
            }
        }
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(player);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) return;
        List<Mission> currentMissions = getMissions();
        List<Mission> personalized = new ArrayList<>();
        for (Mission m : currentMissions) {
            Mission copy = m.copyForPlayer(player.getUUID());
            if ("CRAFTEO".equalsIgnoreCase(m.getObjectiveType())) {
                copy.setCurrentProgress(getCraftedCount(m, player));
            } else if ("COCINAR".equalsIgnoreCase(m.getObjectiveType())) {
                copy.setCurrentProgress(getSmeltedCount(m, player));
            }
            personalized.add(copy);
        }
        ServerPlayNetworking.send(player, new ModPackets.SyncMissionsPayload(personalized));
        if (player.level().getServer() != null) {
            ServerPlayNetworking.send(player, new ModPackets.SyncCraftableItemsPayload(
                    getCraftableItemIds(player.level().getServer()),
                    getSmeltableItemIds(player.level().getServer())
            ));
        }
    }

    public static void updateRecipeGridForMission(Mission mission, MinecraftServer server) {
        if (server == null || mission == null) return;
        if (!"CRAFTEO".equalsIgnoreCase(mission.getObjectiveType())) {
            mission.getRecipeGridItemIds().clear();
            return;
        }
        String reqId = mission.getRequiredItemId();
        if (reqId == null || reqId.isBlank()) return;

        Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(reqId));
        if (targetItem == null || targetItem == Items.AIR) return;

        List<String> grid = new ArrayList<>(Collections.nCopies(9, ""));
        try {
            ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (holder.value().getType() == RecipeType.CRAFTING) {
                    for (RecipeDisplay display : holder.value().display()) {
                        ItemStack res = display.result().resolveForFirstStack(context);
                        if (!res.isEmpty() && res.is(targetItem)) {
                            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                                int w = shaped.width();
                                int h = shaped.height();
                                List<SlotDisplay> ing = shaped.ingredients();
                                for (int r = 0; r < h && r < 3; r++) {
                                    for (int c = 0; c < w && c < 3; c++) {
                                        int idx = r * w + c;
                                        if (idx < ing.size()) {
                                            ItemStack is = ing.get(idx).resolveForFirstStack(context);
                                            if (!is.isEmpty() && is.getItem() != Items.AIR) {
                                                grid.set(r * 3 + c, BuiltInRegistries.ITEM.getKey(is.getItem()).toString());
                                            }
                                        }
                                    }
                                }
                                mission.setRecipeGridItemIds(grid);
                                return;
                            } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                                List<SlotDisplay> ing = shapeless.ingredients();
                                for (int i = 0; i < ing.size() && i < 9; i++) {
                                    ItemStack is = ing.get(i).resolveForFirstStack(context);
                                    if (!is.isEmpty() && is.getItem() != Items.AIR) {
                                        grid.set(i, BuiltInRegistries.ITEM.getKey(is.getItem()).toString());
                                    }
                                }
                                mission.setRecipeGridItemIds(grid);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        mission.setRecipeGridItemIds(grid);
    }

    public static List<String> getCraftableItemIds(MinecraftServer server) {
        List<String> list = new ArrayList<>();
        if (server == null) return list;
        try {
            ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
            Set<String> set = new HashSet<>();
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (holder.value().getType() == RecipeType.CRAFTING) {
                    for (RecipeDisplay display : holder.value().display()) {
                        ItemStack res = display.result().resolveForFirstStack(context);
                        if (!res.isEmpty() && res.getItem() != Items.AIR) {
                            set.add(BuiltInRegistries.ITEM.getKey(res.getItem()).toString());
                        }
                    }
                }
            }
            list.addAll(set);
        } catch (Exception ignored) {}
        return list;
    }

    public static List<String> getSmeltableItemIds(MinecraftServer server) {
        List<String> list = new ArrayList<>();
        if (server == null) return list;
        try {
            ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
            Set<String> set = new HashSet<>();
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                RecipeType<?> type = holder.value().getType();
                if (type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING) {
                    for (RecipeDisplay display : holder.value().display()) {
                        ItemStack res = display.result().resolveForFirstStack(context);
                        if (!res.isEmpty() && res.getItem() != Items.AIR) {
                            set.add(BuiltInRegistries.ITEM.getKey(res.getItem()).toString());
                        }
                    }
                }
            }
            list.addAll(set);
        } catch (Exception ignored) {}
        return list;
    }
}
