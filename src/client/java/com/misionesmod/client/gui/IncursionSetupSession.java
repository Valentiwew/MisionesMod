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

import java.util.ArrayList;
import java.util.List;

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

    // Puntos del edificio
    public static BlockPos extractionPos = null;
    public static BlockPos roofPos = null;
    public static final List<BlockPos> spawnPoints = new ArrayList<>();
    public static final List<BlockPos> savedChestPositions = new ArrayList<>();

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
            List<BlockPos> currentChests
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

        active = true;

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }

        WaypointHudRenderer.showHotbarNotification(
                "§6§l[MODO EDIFICIO] §fMuévete por el edificio para fijar puntos §7([M] para terminar)",
                0xFFF59E0B
        );
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
        WaypointHudRenderer.showHotbarNotification(
                "§a✔ Entrada / Escape fijada en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(),
                0xFF22C55E
        );
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
        WaypointHudRenderer.showHotbarNotification(
                "§e✔ Azotea/Cima fijada en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(),
                0xFFF59E0B
        );
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
        WaypointHudRenderer.showHotbarNotification(
                "§c✔ Spawn #" + spawnPoints.size() + " añadido en X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ(),
                0xFFEF4444
        );
    }

    public static void registerTargetedChest(Minecraft mc) {
        if (mc == null) return;
        if (mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos target = bhr.getBlockPos();
            if (mc.level != null && mc.level.getBlockState(target).getBlock() instanceof ChestBlock) {
                if (savedChestPositions.contains(target)) {
                    savedChestPositions.remove(target);
                    if (mc.player != null) {
                        mc.player.playSound(SoundEvents.CHEST_CLOSE, 1.0f, 0.9f);
                    }
                    WaypointHudRenderer.showHotbarNotification("§c✕ Cofre desregistrado", 0xFFEF4444);
                } else {
                    savedChestPositions.add(target);
                    if (mc.player != null && mc.level != null) {
                        mc.player.playSound(SoundEvents.CHEST_OPEN, 1.0f, 1.2f);
                        for (int i = 0; i < 15; i++) {
                            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 1.2, target.getZ() + 0.5, 0, 0.05, 0);
                        }
                    }
                    WaypointHudRenderer.showHotbarNotification("§a✔ Cofre #" + savedChestPositions.size() + " registrado con éxito", 0xFF22C55E);
                }
                return;
            }
        }
        WaypointHudRenderer.showHotbarNotification("§7Apunta la mira directamente a un cofre para registrarlo", 0xFF94A3B8);
    }

    public static void clearSpawns(Minecraft mc) {
        spawnPoints.clear();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.ANVIL_BREAK, 0.8f, 1.2f);
        }
        WaypointHudRenderer.showHotbarNotification(
                "§7Spawns de la incursión vaciados (0)",
                0xFF94A3B8
        );
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
                savedChestPositions
        );
        if (mc.gui != null) {
            mc.gui.setScreen(screen);
        }
    }
}
