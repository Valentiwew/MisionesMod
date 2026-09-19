package com.misionesmod.client.gui;

import com.misionesmod.client.hud.WaypointHudRenderer;
import com.misionesmod.mission.Mission;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;

public class IncursionSetupSession {

    public static boolean active = false;

    // Estado retenido de la misión en creación/edición
    public static CreateMissionScreen parentScreen = null;
    public static Mission existingMission = null;
    public static String savedTitle = "";
    public static String savedDesc = "";
    public static int savedObjectiveIndex = 0; // INCURSION
    public static int savedTierIndex = 0;
    public static List<ItemStack> savedCustomItems = new ArrayList<>();
    public static int savedWaves = 4;
    public static List<String> savedMobs = new ArrayList<>();
    public static List<ItemStack> savedChestLoot = new ArrayList<>();

    // Puntos y recorrido de la incursión
    public static BlockPos extractionPos = null; // Punto de Inicio / Reunión
    public static BlockPos roofPos = null;       // Punto de Escape / Extracción
    public static final List<BlockPos> routePoints = new ArrayList<>();
    public static final List<String> routePointNames = new ArrayList<>();
    public static final List<BlockPos> spawnPoints = new ArrayList<>();
    public static final List<BlockPos> savedChestPositions = new ArrayList<>();
    public static final Map<BlockPos, List<ItemStack>> customChestLootMap = new HashMap<>();
    public static final List<BlockPos> entryGateBlocks = new ArrayList<>();
    public static final List<BlockPos> finalGateBlocks = new ArrayList<>();

    public static void start(
            CreateMissionScreen screen,
            Mission mission,
            String title,
            String desc,
            int objectiveIndex,
            int tierIndex,
            List<ItemStack> customItems,
            int waves,
            List<String> mobs,
            List<ItemStack> chestLoot,
            BlockPos currentExtraction,
            BlockPos currentRoof,
            List<BlockPos> currentSpawns,
            List<BlockPos> currentChests,
            List<BlockPos> currentRoutes,
            List<String> currentRouteNames,
            List<BlockPos> currentCustomChestPositions,
            List<String> currentCustomChestPacks,
            List<BlockPos> currentEntryGates,
            List<BlockPos> currentFinalGates
    ) {
        parentScreen = screen;
        existingMission = mission;
        savedTitle = title != null ? title : "";
        savedDesc = desc != null ? desc : "";
        savedObjectiveIndex = objectiveIndex;
        savedTierIndex = tierIndex;
        savedCustomItems = new ArrayList<>(customItems);
        savedWaves = waves;
        savedMobs = new ArrayList<>(mobs);
        savedChestLoot = new ArrayList<>(chestLoot);

        extractionPos = currentExtraction;
        roofPos = currentRoof;

        spawnPoints.clear();
        if (currentSpawns != null) {
            spawnPoints.addAll(currentSpawns);
        }

        savedChestPositions.clear();
        if (currentChests != null) {
            savedChestPositions.addAll(currentChests);
        }

        routePoints.clear();
        if (currentRoutes != null) {
            routePoints.addAll(currentRoutes);
        }

        routePointNames.clear();
        if (currentRouteNames != null) {
            routePointNames.addAll(currentRouteNames);
        }

        customChestLootMap.clear();
        if (currentCustomChestPositions != null && currentCustomChestPacks != null) {
            for (int i = 0; i < Math.min(currentCustomChestPositions.size(), currentCustomChestPacks.size()); i++) {
                BlockPos cp = currentCustomChestPositions.get(i);
                List<ItemStack> items = Mission.parseLootPack(currentCustomChestPacks.get(i));
                customChestLootMap.put(cp, items);
                if (!savedChestPositions.contains(cp)) {
                    savedChestPositions.add(cp);
                }
            }
        }

        entryGateBlocks.clear();
        if (currentEntryGates != null) {
            entryGateBlocks.addAll(currentEntryGates);
        }

        finalGateBlocks.clear();
        if (currentFinalGates != null) {
            finalGateBlocks.addAll(currentFinalGates);
        }

        active = true;
        setStatus("Modo En el Mundo activado. Usa las teclas de arriba ([M] para terminar)", 0xFF22C55E);
    }

    public static String statusMessage = "Muévete por la zona para fijar los puntos ([M] para terminar)";
    public static int statusColor = 0xFFF59E0B;
    public static long statusExpiry = 0L;

    public static void setStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusExpiry = System.currentTimeMillis() + 6000L;
    }

    public static void setExtraction(BlockPos pos, Minecraft mc) {
        if (pos == null) return;
        extractionPos = pos;
        if (mc.player != null && mc.level != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.4f);
            for (int i = 0; i < 15; i++) {
                mc.level.addParticle(
                        ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5 + (Math.random() - 0.5) * 0.8,
                        pos.getY() + 0.5 + Math.random() * 0.8,
                        pos.getZ() + 0.5 + (Math.random() - 0.5) * 0.8,
                        0, 0.05, 0
                );
            }
        }
        setStatus("✔ Punto de Inicio fijado en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(), 0xFF22C55E);
    }

    public static void setRoof(BlockPos pos, Minecraft mc) {
        if (pos == null) return;
        roofPos = pos;
        if (mc.player != null && mc.level != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.4f);
            for (int i = 0; i < 15; i++) {
                mc.level.addParticle(
                        ParticleTypes.FLAME,
                        pos.getX() + 0.5 + (Math.random() - 0.5) * 0.8,
                        pos.getY() + 0.5 + Math.random() * 0.8,
                        pos.getZ() + 0.5 + (Math.random() - 0.5) * 0.8,
                        0, 0.03, 0
                );
            }
        }
        setStatus("Punto de Escape establecido en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(), 0xFFF59E0B);
    }

    public static void addRoutePoint(BlockPos pos, Minecraft mc) {
        if (pos == null) return;
        routePoints.add(pos);
        String name = "Checkpoint #" + routePoints.size();
        routePointNames.add(name);
        if (mc.player != null && mc.level != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.6f);
            for (int i = 0; i < 15; i++) {
                mc.level.addParticle(
                        ParticleTypes.ENCHANT,
                        pos.getX() + 0.5 + (Math.random() - 0.5) * 0.8,
                        pos.getY() + 0.5 + Math.random() * 0.8,
                        pos.getZ() + 0.5 + (Math.random() - 0.5) * 0.8,
                        0, 0.05, 0
                );
            }
        }
        setStatus("Checkpoint #" + routePoints.size() + " añadido en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(), 0xFF38BDF8);
    }

    public static void addSpawn(BlockPos pos, Minecraft mc) {
        if (pos == null) return;
        spawnPoints.add(pos);
        if (mc.player != null && mc.level != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            for (int i = 0; i < 15; i++) {
                mc.level.addParticle(
                        ParticleTypes.PORTAL,
                        pos.getX() + 0.5 + (Math.random() - 0.5) * 0.6,
                        pos.getY() + 0.5 + Math.random() * 0.8,
                        pos.getZ() + 0.5 + (Math.random() - 0.5) * 0.6,
                        (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2
                );
            }
        }
        setStatus("Mob Spawn #" + spawnPoints.size() + " añadido en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(), 0xFFEF4444);
    }

    public static void registerTargetedChest(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos target = bhr.getBlockPos();
            if (mc.level != null && mc.level.getBlockState(target).getBlock() instanceof ChestBlock) {
                if (savedChestPositions.contains(target)) {
                    savedChestPositions.remove(target);
                    customChestLootMap.remove(target);
                    if (mc.player != null) {
                        mc.player.playSound(SoundEvents.CHEST_CLOSE, 1.0f, 0.9f);
                    }
                    setStatus("Cofre desregistrado", 0xFFEF4444);
                } else {
                    savedChestPositions.add(target);
                    if (mc.player != null && mc.level != null) {
                        mc.player.playSound(SoundEvents.CHEST_OPEN, 1.0f, 1.2f);
                        for (int i = 0; i < 15; i++) {
                            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 1.2, target.getZ() + 0.5, 0, 0.05, 0);
                        }
                    }
                    String lootKey = com.misionesmod.client.MisionesModClient.EDIT_CHEST_LOOT_KEY != null ?
                            com.misionesmod.client.MisionesModClient.EDIT_CHEST_LOOT_KEY.getTranslatedKeyMessage().getString() : "L";
                    setStatus("Cofre #" + savedChestPositions.size() + " registrado ([" + lootKey + "] para Loot)", 0xFF22C55E);
                }
                return;
            }
        }
        setStatus("Apunta la mira directamente a un cofre para registrarlo", 0xFF94A3B8);
    }

    public static void editTargetedChestLoot(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos target = bhr.getBlockPos();
            if (mc.level != null && mc.level.getBlockState(target).getBlock() instanceof ChestBlock) {
                if (!savedChestPositions.contains(target)) {
                    savedChestPositions.add(target);
                }
                List<ItemStack> currentLoot = customChestLootMap.getOrDefault(target, new ArrayList<>());
                if (mc.gui != null) {
                    mc.gui.setScreen(new LootEditorScreen(null, "chest_" + target.getX() + "_" + target.getZ(), currentLoot, savedItems -> {
                        customChestLootMap.put(target, savedItems);
                        setStatus("✔ Loot guardado para el cofre en " + target.toShortString(), 0xFF22C55E);
                    }));
                }
                return;
            }
        }
        setStatus("Apunta la mira a un cofre y presiona [L] para personalizar su Loot", 0xFF94A3B8);
    }

    public static void openMobSelector(Minecraft mc) {
        if (mc.gui != null) {
            mc.gui.setScreen(new MobSelectorScreen(null, savedMobs, mobs -> {
                savedMobs.clear();
                savedMobs.addAll(mobs);
                setStatus("Mobs seleccionados: " + savedMobs.size() + " tipo(s)", 0xFF8B5CF6);
            }));
        }
    }

    public static void clearSpawns(Minecraft mc) {
        spawnPoints.clear();
        routePoints.clear();
        routePointNames.clear();
        extractionPos = null;
        roofPos = null;
        savedChestPositions.clear();
        customChestLootMap.clear();
        entryGateBlocks.clear();
        finalGateBlocks.clear();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.ANVIL_BREAK, 0.8f, 1.2f);
        }
        setStatus("Toda la configuración de la incursión ha sido borrada", 0xFFEF4444);
    }

    public static void toggleTargetedEntryGate(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos target = bhr.getBlockPos();
            if (entryGateBlocks.contains(target)) {
                entryGateBlocks.remove(target);
                if (mc.player != null) mc.player.playSound(SoundEvents.STONE_BREAK, 0.8f, 1.0f);
                setStatus("Bloque de puerta de inicio desregistrado", 0xFFEF4444);
            } else {
                entryGateBlocks.add(target);
                if (mc.player != null && mc.level != null) {
                    mc.player.playSound(SoundEvents.CHEST_OPEN, 0.8f, 1.4f);
                    for (int i = 0; i < 10; i++) {
                        mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 1.1, target.getZ() + 0.5, 0, 0.05, 0);
                    }
                }
                setStatus("Puerta Inicio #" + entryGateBlocks.size() + " registrada (se abrirá al iniciar)", 0xFF22C55E);
            }
            return;
        }
        setStatus("Apunta con la mira a un bloque para marcarlo como puerta de inicio", 0xFF94A3B8);
    }

    public static void toggleTargetedFinalGate(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos target = bhr.getBlockPos();
            if (finalGateBlocks.contains(target)) {
                finalGateBlocks.remove(target);
                if (mc.player != null) mc.player.playSound(SoundEvents.STONE_BREAK, 0.8f, 1.0f);
                setStatus("Bloque de puerta final desregistrado", 0xFFEF4444);
            } else {
                finalGateBlocks.add(target);
                if (mc.player != null && mc.level != null) {
                    mc.player.playSound(SoundEvents.IRON_DOOR_OPEN, 0.8f, 1.2f);
                    for (int i = 0; i < 10; i++) {
                        mc.level.addParticle(ParticleTypes.FLAME, target.getX() + 0.5, target.getY() + 1.1, target.getZ() + 0.5, 0, 0.05, 0);
                    }
                }
                setStatus("Puerta Final #" + finalGateBlocks.size() + " registrada (se abrirá en oleada final)", 0xFFF59E0B);
            }
            return;
        }
        setStatus("Apunta con la mira a un bloque para marcarlo como puerta final", 0xFF94A3B8);
    }

    public static void finishAndReopen(Minecraft mc) {
        active = false;
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
        }
        CreateMissionScreen screen = new CreateMissionScreen(
                parentScreen != null ? parentScreen.getParentScreen() : null,
                existingMission
        );

        List<BlockPos> customChestPositions = new ArrayList<>();
        List<String> customChestLootPack = new ArrayList<>();
        for (Map.Entry<BlockPos, List<ItemStack>> entry : customChestLootMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                customChestPositions.add(entry.getKey());
                customChestLootPack.add(Mission.serializeLootPack(entry.getValue()));
            }
        }

        screen.restoreFromSetupSession(
                savedTitle,
                savedDesc,
                savedObjectiveIndex,
                savedTierIndex,
                savedCustomItems,
                savedWaves,
                savedMobs,
                savedChestLoot,
                extractionPos,
                roofPos,
                spawnPoints,
                savedChestPositions,
                routePoints,
                routePointNames,
                customChestPositions,
                customChestLootPack,
                entryGateBlocks,
                finalGateBlocks
        );
        if (mc.gui != null) {
            mc.gui.setScreen(screen);
        }
    }

    public static void printHelpGuide(Minecraft mc) {
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.2f);
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6§l╔═══════════════════════════════════════╗"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6§l║      §e§lGUÍA DE CONTROLES (ADMIN)      §6§l║"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6§l╠═══════════════════════════════════════╝"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §a[G] §fInicio: §7Fija la entrada donde se esperará al equipo."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §b[H] §fCheckpoint: §7Añade checkpoints a lo largo del recorrido."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §e[E] §fEscape: §7Marca la salida final donde termina la misión."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §c[J] §fMob Spawn: §7Añade puntos donde aparecerán los enemigos."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §6[C] §fCofre: §7Apunta a un cofre para incluirlo en la misión."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §d[L] §fLoot: §7Apunta a un cofre para abrir su editor de botín."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §2[B] §fP. Inicio: §7Apunta a bloques para destruirlos al comenzar."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §6[N] §fP. Final: §7Apunta a bloques para destruirlos en oleada final."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §5[V] §fMobs: §7Abre el selector de criaturas (vainilla y mods)."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §4[K] §fLimpiar: §7Borra absolutamente toda la configuración."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6║ §a[M] §fListo: §7Guarda la configuración y vuelve al menú."));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6§l╚═══════════════════════════════════════╝"));
    }
}
