package com.misionesmod;

import com.misionesmod.command.DropCommand;
import com.misionesmod.drop.DropManager;
import com.misionesmod.loot.LootConfig;
import com.misionesmod.mission.Mission;
import com.misionesmod.mission.MissionManager;
import com.misionesmod.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MisionesMod implements ModInitializer {
    public static final String MOD_ID = "misionesmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Iniciando MisionesMod para Minecraft 26.2 / 26.3...");

        // 1. Inicializar Loot & Misiones
        LootConfig.initialize();
        MissionManager.initialize();

        // 2. Registrar paquetes de red
        ModPackets.registerCommonPayloads();

        // 3. Registrar receptores del servidor para paquetes de red
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.CreateMissionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                context.server().execute(() -> {
                    String id = payload.id();
                    Mission existing = (id != null && !id.isBlank()) ? MissionManager.getMission(id) : null;
                    if (existing != null) {
                        existing.setTitle(payload.title());
                        existing.setDescription(payload.description());
                        if (payload.pos() != null) existing.setTargetPos(payload.pos());
                        existing.setRewardTier(payload.tier());
                        existing.setRewardDescription(payload.rewardDesc());
                        existing.setObjectiveType(payload.objectiveType());
                        existing.setRequiredItemId(payload.requiredItemId());
                        existing.setRequiredCount(payload.requiredCount());
                        existing.setCustomItemIds(payload.itemIds());
                        existing.setCustomItemCounts(payload.counts());
                        existing.setExtractionPos(payload.extractionPos());
                        existing.setRoofPos(payload.roofPos());
                        existing.setIncursionRadius(payload.incursionRadius());
                        existing.setSpawnPoints(payload.spawnPoints());
                        existing.setMobTypes(payload.mobTypes());
                        existing.setTotalWaves(payload.totalWaves());
                        existing.setBuildingChestLootIds(payload.chestLootIds());
                        existing.setBuildingChestLootCounts(payload.chestLootCounts());
                        existing.setChestPoints(payload.chestPoints());
                        MissionManager.save();
                        MissionManager.syncToAll(context.server());
                        player.sendSystemMessage(Component.literal("§aMisión Modificada."));
                    } else {
                        String newId = "mision_" + System.currentTimeMillis();
                        Mission mission = new Mission(
                                newId,
                                payload.title(),
                                payload.description(),
                                payload.pos(),
                                player.level().dimension().identifier().toString(),
                                payload.tier(),
                                payload.rewardDesc()
                        );
                        mission.setObjectiveType(payload.objectiveType());
                        mission.setRequiredItemId(payload.requiredItemId());
                        mission.setRequiredCount(payload.requiredCount());
                        mission.setCustomItemIds(payload.itemIds());
                        mission.setCustomItemCounts(payload.counts());
                        mission.setExtractionPos(payload.extractionPos());
                        mission.setRoofPos(payload.roofPos());
                        mission.setIncursionRadius(payload.incursionRadius());
                        mission.setSpawnPoints(payload.spawnPoints());
                        mission.setMobTypes(payload.mobTypes());
                        mission.setTotalWaves(payload.totalWaves());
                        mission.setBuildingChestLootIds(payload.chestLootIds());
                        mission.setBuildingChestLootCounts(payload.chestLootCounts());
                        mission.setChestPoints(payload.chestPoints());
                        MissionManager.addMission(mission, context.server());
                    }
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.ClaimMissionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> MissionManager.completeMission(payload.missionId(), player));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.TriggerDropPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                BlockPos pos = payload.pos() != null ? payload.pos() : player.blockPosition();
                context.server().execute(() -> {
                    if (payload.delayMinutes() > 0) {
                        DropManager.scheduleDrop(player.level(), pos, payload.tier(), payload.delayMinutes(), payload.recurring());
                        String msg = "§a[DROP] Suministro programado en " + payload.delayMinutes() + " min" + (payload.recurring() ? " (Repetitivo cada " + payload.delayMinutes() + "m)" : "") + ".";
                        player.sendSystemMessage(Component.literal(msg));
                    } else {
                        DropManager.spawnDrop(player.level(), pos, payload.tier());
                    }
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.DeleteMissionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                context.server().execute(() -> MissionManager.removeMission(payload.missionId(), context.server()));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.OpenLootEditorPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                context.server().execute(() -> {
                    if ("drop".equalsIgnoreCase(payload.targetId())) {
                        DropManager.openDropLootEditor(player);
                    }
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SaveCustomLootPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                context.server().execute(() -> {
                    java.util.List<ItemStack> items = new java.util.ArrayList<>();
                    for (int i = 0; i < payload.itemIds().size(); i++) {
                        try {
                            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(payload.itemIds().get(i));
                            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
                            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                int count = Math.max(1, payload.counts().get(i));
                                items.add(new ItemStack(item, count));
                            }
                        } catch (Exception ignored) {}
                    }

                    if ("drop".equalsIgnoreCase(payload.targetId())) {
                        DropManager.setCustomDropItems(items);
                    }
                });
            }
        });

        // 4. Sincronizar misiones al conectarse un jugador
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MissionManager.syncToPlayer(handler.getPlayer());
        });

        // 5. Cargar y guardar misiones por mundo
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MissionManager.loadForServer(server);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            MissionManager.save();
            MissionManager.clear();
            com.misionesmod.incursion.IncursionManager.clear();
        });

        // 6. Registrar comando /drop
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DropCommand.register(dispatcher);
        });

        // 7. Tick de caída paulatina de suministros aéreos, auto-cumplimiento de misiones e incursiones
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            DropManager.tickDrops(server);
            MissionManager.tickMissions(server);
            com.misionesmod.incursion.IncursionManager.tick(server);
        });

        LOGGER.info("MisionesMod inicializado con éxito.");
    }
}
