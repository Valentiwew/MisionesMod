package com.misionesmod.client;

import com.misionesmod.client.gui.MisionesScreen;
import com.misionesmod.client.hud.WaypointHudRenderer;
import com.misionesmod.mission.Mission;
import com.misionesmod.network.ModPackets;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MisionesModClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("misionesmod-client");

    public static final KeyMapping.Category MOD_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("misionesmod", "category"));

    public static final KeyMapping OPEN_MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.open_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_M,
            MOD_CATEGORY
    ));

    public static final KeyMapping SET_EXTRACTION_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.extraction",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            MOD_CATEGORY
    ));

    public static final KeyMapping ADD_ROUTE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.route",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            MOD_CATEGORY
    ));

    public static final KeyMapping SET_ESCAPE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.escape",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_E,
            MOD_CATEGORY
    ));

    public static final KeyMapping ADD_SPAWN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.spawn",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            MOD_CATEGORY
    ));

    public static final KeyMapping CLEAR_SPAWNS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.clear_spawns",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            MOD_CATEGORY
    ));

    public static final KeyMapping REGISTER_CHEST_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.register_chest",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            MOD_CATEGORY
    ));

    public static final KeyMapping EDIT_CHEST_LOOT_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.edit_chest_loot",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_L,
            MOD_CATEGORY
    ));

    public static final KeyMapping HELP_SETUP_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.help",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_I,
            MOD_CATEGORY
    ));

    public static final KeyMapping SELECT_MOBS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.misionesmod.incursion.select_mobs",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            MOD_CATEGORY
    ));

    public static final List<Mission> clientMissions = new ArrayList<>();
    public static final java.util.Set<String> notifiedItemMissions = new java.util.HashSet<>();
    public static final java.util.Set<String> craftableItemIds = new java.util.HashSet<>();
    public static final java.util.Set<String> smeltableItemIds = new java.util.HashSet<>();

    private static net.minecraft.network.chat.Component currentOverlayMessage = null;
    private static int overlayMessageTicks = 0;

    public static void showOverlayMessage(Minecraft mc, net.minecraft.network.chat.Component msg, int ticks) {
        WaypointHudRenderer.showHotbarNotification(msg.getString(), 0xFF38BDF8);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Inicializando cliente de MisionesMod...");
        com.misionesmod.client.hud.ClientHudConfig.load();

        // 1. Registrar elemento HUD para brújula y marcadores
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("misionesmod", "waypoint_hud"),
                new WaypointHudRenderer()
        );

        // 2. Receptores de red del cliente
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SyncMissionsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                clientMissions.clear();
                clientMissions.addAll(payload.missions());
                LOGGER.info("Sincronizadas {} misiones en el cliente.", clientMissions.size());

                // Auto-marcar misiones de incursión/exploración pendientes
                for (Mission m : clientMissions) {
                    if (!m.isCompleted() && ("INCURSION".equalsIgnoreCase(m.getObjectiveType()) || "EXPLORACION".equalsIgnoreCase(m.getObjectiveType())) && m.getTargetPos() != null) {
                        if (!WaypointHudRenderer.hasWaypoint(m.getTargetPos())) {
                            WaypointHudRenderer.setWaypoint(true, m.getTitle(), m.getTargetPos(), false);
                        }
                    } else if (m.isCompleted() && m.getTargetPos() != null) {
                        WaypointHudRenderer.removeWaypoint(m.getTargetPos());
                    }
                }

                // Si la pantalla de misiones está abierta, refrescarla
                if (context.client().gui.screen() instanceof MisionesScreen screen) {
                    screen.refreshMissions();
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SetWaypointPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                WaypointHudRenderer.setWaypoint(payload.active(), payload.title(), payload.pos());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SyncCraftableItemsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                craftableItemIds.clear();
                craftableItemIds.addAll(payload.craftableItemIds());
                smeltableItemIds.clear();
                smeltableItemIds.addAll(payload.smeltableItemIds());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.NotificationPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                WaypointHudRenderer.showHotbarNotification(payload.text(), payload.color());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SyncIncursionStatusPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                WaypointHudRenderer.setIncursionStatus(
                        payload.active(),
                        payload.missionId(),
                        payload.missionTitle(),
                        payload.currentWave(),
                        payload.totalWaves(),
                        payload.remainingEnemies(),
                        payload.alivePlayers(),
                        payload.totalPlayers(),
                        payload.isEscapePhase(),
                        payload.extractionPos(),
                        payload.isLootingPhase(),
                        payload.lootingSeconds(),
                        payload.chestsCount(),
                        payload.currentObjectivePos(),
                        payload.currentObjectiveTitle()
                );
            });
        });

        // Limpiar estado al desconectarse de un mundo o servidor para no arrastrar misiones
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clientMissions.clear();
            notifiedItemMissions.clear();
            craftableItemIds.clear();
            smeltableItemIds.clear();
            overlayMessageTicks = 0;
            currentOverlayMessage = null;
            WaypointHudRenderer.clear();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clientMissions.clear();
            notifiedItemMissions.clear();
            craftableItemIds.clear();
            smeltableItemIds.clear();
            overlayMessageTicks = 0;
            currentOverlayMessage = null;
            WaypointHudRenderer.clear();
        });

        // 3. Listener para tecla de acceso rápido y comprobación de objetivos de ítem
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (com.misionesmod.client.gui.IncursionSetupSession.active) {
                while (SET_EXTRACTION_KEY.consumeClick()) {
                    if (client.player != null) {
                        com.misionesmod.client.gui.IncursionSetupSession.setExtraction(client.player.blockPosition(), client);
                    }
                }
                while (ADD_ROUTE_KEY.consumeClick()) {
                    if (client.player != null) {
                        com.misionesmod.client.gui.IncursionSetupSession.addRoutePoint(client.player.blockPosition(), client);
                    }
                }
                while (SET_ESCAPE_KEY.consumeClick()) {
                    if (client.player != null) {
                        com.misionesmod.client.gui.IncursionSetupSession.setRoof(client.player.blockPosition(), client);
                    }
                }
                while (ADD_SPAWN_KEY.consumeClick()) {
                    if (client.player != null) {
                        com.misionesmod.client.gui.IncursionSetupSession.addSpawn(client.player.blockPosition(), client);
                    }
                }
                while (CLEAR_SPAWNS_KEY.consumeClick()) {
                    com.misionesmod.client.gui.IncursionSetupSession.clearSpawns(client);
                }
                while (REGISTER_CHEST_KEY.consumeClick()) {
                    com.misionesmod.client.gui.IncursionSetupSession.registerTargetedChest(client);
                }
                while (EDIT_CHEST_LOOT_KEY.consumeClick()) {
                    com.misionesmod.client.gui.IncursionSetupSession.editTargetedChestLoot(client);
                }
                while (HELP_SETUP_KEY.consumeClick()) {
                    com.misionesmod.client.gui.IncursionSetupSession.printHelpGuide(client);
                }
                while (SELECT_MOBS_KEY.consumeClick()) {
                    com.misionesmod.client.gui.IncursionSetupSession.openMobSelector(client);
                }
                while (OPEN_MENU_KEY.consumeClick()) {
                    com.misionesmod.client.gui.IncursionSetupSession.finishAndReopen(client);
                }
            } else {
                while (OPEN_MENU_KEY.consumeClick()) {
                    if (client.gui.screen() == null && client.player != null) {
                        client.gui.setScreen(new MisionesScreen());
                    } else if (client.gui.screen() instanceof MisionesScreen) {
                        client.gui.setScreen(null);
                    }
                }
            }

            // Mantener mensaje en el actionbar durante el tiempo configurado
            if (overlayMessageTicks > 0 && currentOverlayMessage != null) {
                overlayMessageTicks--;
                if (client.gui != null && client.gui.hud != null && (overlayMessageTicks % 10 == 0)) {
                    client.gui.hud.setOverlayMessage(currentOverlayMessage, false);
                }
            }

        });

        LOGGER.info("Cliente de MisionesMod inicializado.");
    }
}
