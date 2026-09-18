package com.misionesmod.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.misionesmod.MisionesMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

public class LootConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LootData data = new LootData();

    public static class LootItem {
        public String itemId;
        public int minCount;
        public int maxCount;
        public int weight;

        public LootItem() {}

        public LootItem(String itemId, int minCount, int maxCount, int weight) {
            this.itemId = itemId;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.weight = weight;
        }
    }

    public static class LootData {
        public Map<String, List<LootItem>> tiers = new HashMap<>();

        public LootData() {
            // Default tiers
            List<LootItem> common = new ArrayList<>();
            common.add(new LootItem("minecraft:iron_ingot", 4, 16, 50));
            common.add(new LootItem("minecraft:gold_ingot", 2, 8, 40));
            common.add(new LootItem("minecraft:bread", 4, 12, 60));
            common.add(new LootItem("minecraft:arrow", 8, 32, 50));
            common.add(new LootItem("minecraft:firework_rocket", 4, 16, 40));

            List<LootItem> rare = new ArrayList<>();
            rare.add(new LootItem("minecraft:diamond", 2, 6, 30));
            rare.add(new LootItem("minecraft:emerald", 4, 12, 35));
            rare.add(new LootItem("minecraft:golden_apple", 1, 3, 25));
            rare.add(new LootItem("minecraft:ender_pearl", 2, 6, 30));
            rare.add(new LootItem("minecraft:experience_bottle", 4, 12, 30));

            List<LootItem> epic = new ArrayList<>();
            epic.add(new LootItem("minecraft:diamond_block", 1, 2, 15));
            epic.add(new LootItem("minecraft:netherite_scrap", 1, 3, 10));
            epic.add(new LootItem("minecraft:enchanted_golden_apple", 1, 2, 8));
            epic.add(new LootItem("minecraft:totem_of_undying", 1, 1, 10));

            List<LootItem> legendary = new ArrayList<>();
            legendary.add(new LootItem("minecraft:netherite_ingot", 1, 2, 8));
            legendary.add(new LootItem("minecraft:totem_of_undying", 1, 2, 10));
            legendary.add(new LootItem("minecraft:enchanted_golden_apple", 2, 4, 12));
            legendary.add(new LootItem("minecraft:elytra", 1, 1, 5));

            tiers.put("comun", common);
            tiers.put("raro", rare);
            tiers.put("epico", epic);
            tiers.put("legendario", legendary);
        }
    }

    public static void initialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("misionesmod");
        File dir = configDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, "loot.json");
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
                MisionesMod.LOGGER.info("Configuración de loot por defecto generada en: {}", file.getAbsolutePath());
            } catch (Exception e) {
                MisionesMod.LOGGER.error("Error al guardar loot.json por defecto", e);
            }
        } else {
            try (FileReader reader = new FileReader(file)) {
                data = GSON.fromJson(reader, LootData.class);
                if (data == null || data.tiers == null) {
                    data = new LootData();
                }
                MisionesMod.LOGGER.info("Configuración de loot cargada exitosamente desde loot.json");
            } catch (Exception e) {
                MisionesMod.LOGGER.error("Error al cargar loot.json", e);
            }
        }
    }

    public static List<ItemStack> getRandomLoot(String tierName, int rolls, RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        if (data == null || data.tiers == null) return result;

        List<LootItem> items = data.tiers.get(tierName.toLowerCase(Locale.ROOT));
        if (items == null || items.isEmpty()) {
            items = data.tiers.get("comun");
        }
        if (items == null || items.isEmpty()) return result;

        int totalWeight = 0;
        for (LootItem item : items) {
            totalWeight += Math.max(1, item.weight);
        }

        for (int i = 0; i < rolls; i++) {
            int roll = random.nextInt(totalWeight);
            int current = 0;
            for (LootItem lootItem : items) {
                current += Math.max(1, lootItem.weight);
                if (roll < current) {
                    try {
                        Identifier id = Identifier.parse(lootItem.itemId);
                        Item item = BuiltInRegistries.ITEM.getValue(id);
                        if (item != null && item != Items.AIR) {
                            int count = lootItem.minCount;
                            if (lootItem.maxCount > lootItem.minCount) {
                                count += random.nextInt(lootItem.maxCount - lootItem.minCount + 1);
                            }
                            result.add(new ItemStack(item, Math.max(1, count)));
                        }
                    } catch (Exception ex) {
                        MisionesMod.LOGGER.warn("Item no reconocido en loot.json: {}", lootItem.itemId);
                    }
                    break;
                }
            }
        }

        return result;
    }

    public static void populateChest(ChestBlockEntity chest, String tierName, RandomSource random) {
        if (chest == null) return;
        chest.clearContent();
        int rolls = 5 + random.nextInt(6); // Entre 5 y 10 objetos
        List<ItemStack> drops = getRandomLoot(tierName, rolls, random);

        int size = chest.getContainerSize();
        for (ItemStack stack : drops) {
            int slot = random.nextInt(size);
            if (chest.getItem(slot).isEmpty()) {
                chest.setItem(slot, stack);
            } else {
                for (int s = 0; s < size; s++) {
                    if (chest.getItem(s).isEmpty()) {
                        chest.setItem(s, stack);
                        break;
                    }
                }
            }
        }
        chest.setChanged();
    }
}
