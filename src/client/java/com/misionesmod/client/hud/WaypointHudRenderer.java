package com.misionesmod.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import com.misionesmod.client.MisionesModClient;
import com.misionesmod.mission.Mission;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class WaypointHudRenderer implements HudElement {
    public static class Waypoint {
        public String title;
        public BlockPos pos;
        public boolean isDrop;
        public long createdAt;
        public boolean removing = false;
        public long removeStartTime = 0;

        public Waypoint(String title, BlockPos pos, boolean isDrop) {
            this.title = title != null ? title : "";
            this.pos = pos;
            this.isDrop = isDrop;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static class QueuedNotification {
        public final String text;
        public final int borderColor;
        public final long duration;

        public QueuedNotification(String text, int borderColor, long duration) {
            this.text = text;
            this.borderColor = borderColor;
            this.duration = duration;
        }
    }

    public static class ActiveNotification {
        public String text;
        public int borderColor;
        public long duration;
        public long startTime;

        public ActiveNotification(String text, int borderColor, long duration) {
            this.text = text;
            this.borderColor = borderColor;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
        }
    }

    public static class IncursionStatus {
        public boolean active = false;
        public String missionId = "";
        public String missionTitle = "";
        public int currentWave = 1;
        public int totalWaves = 4;
        public int remainingEnemies = 0;
        public int alivePlayers = 1;
        public int totalPlayers = 1;
        public boolean isEscapePhase = false;
        public BlockPos extractionPos = null;
        public boolean isLootingPhase = false;
        public int lootingSeconds = 0;
        public int chestsCount = 0;
        public BlockPos currentObjectivePos = null;
        public String currentObjectiveTitle = "";
    }

    private static final IncursionStatus incursionStatus = new IncursionStatus();

    public static synchronized void setIncursionStatus(
            boolean active, String missionId, String missionTitle,
            int currentWave, int totalWaves, int remainingEnemies,
            int alivePlayers, int totalPlayers, boolean isEscapePhase,
            BlockPos extractionPos, boolean isLootingPhase,
            int lootingSeconds, int chestsCount,
            BlockPos currentObjectivePos, String currentObjectiveTitle
    ) {
        incursionStatus.active = active;
        incursionStatus.missionId = missionId;
        incursionStatus.missionTitle = missionTitle;
        incursionStatus.currentWave = currentWave;
        incursionStatus.totalWaves = totalWaves;
        incursionStatus.remainingEnemies = remainingEnemies;
        incursionStatus.alivePlayers = alivePlayers;
        incursionStatus.totalPlayers = totalPlayers;
        incursionStatus.isEscapePhase = isEscapePhase;
        incursionStatus.extractionPos = extractionPos;
        incursionStatus.isLootingPhase = isLootingPhase;
        incursionStatus.lootingSeconds = lootingSeconds;
        incursionStatus.chestsCount = chestsCount;
        incursionStatus.currentObjectivePos = currentObjectivePos;
        incursionStatus.currentObjectiveTitle = currentObjectiveTitle != null ? currentObjectiveTitle : "";
    }

    public static class MissionAnimState {
        public long firstSeen = System.currentTimeMillis();
        public boolean completed = false;
        public long completedTime = 0;
    }

    private static final java.util.Map<String, MissionAnimState> missionAnimStates = new java.util.HashMap<>();

    private static final List<Waypoint> waypoints = new ArrayList<>();
    private static final List<ActiveNotification> activeNotifications = new ArrayList<>();

    public static synchronized void showHotbarNotification(String text, int borderColor) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();

        boolean isImmortality = text.contains("Inmortalidad temporal");
        if (isImmortality) {
            // Si hay cuenta regresiva de inmortalidad, mantener solo ese mensaje limpio en pantalla
            activeNotifications.removeIf(an -> !an.text.contains("Inmortalidad temporal"));
        }

        boolean isCountdown = text.contains("misión inicia en") || text.contains("reunidos, la misión inicia");
        if (text.contains("Despejen a los enemigos")) {
            activeNotifications.removeIf(an -> an.text.contains("misión inicia en") || an.text.contains("reunidos, la misión inicia"));
        }
        if (text.contains("Sigan avanzando") || text.contains("Oleada Final") || text.contains("Toma el loot")) {
            activeNotifications.removeIf(an -> an.text.contains("Siguiente oleada en") || an.text.contains("Has despejado el camino"));
        }
        boolean isWaveCooldown = text.contains("Siguiente oleada en") || text.contains("Has despejado el camino, siguiente oleada en");
        boolean isChestLoot = (text.contains("tomó cosas de un cofre") || text.contains("tomó cosas del")) && (text.contains("cofre") || text.contains("botín"));
        boolean isChest = text.contains("Cofre ") && (text.contains("registrado") || text.contains("desregistrado"));
        boolean isSpawn = text.contains("Spawn ") && text.contains("añadido");
        boolean isEscape = text.contains("Punto de Escape establecido") || text.contains("Esperando al equipo en el punto de escape");
        boolean isRoute = text.contains("de ruta añadido");
        boolean isWaiting = text.contains("Esperando a los jugadores");
        boolean isLooting = text.contains("Fase de Botín") || text.contains("saqueo restante");

        for (ActiveNotification an : activeNotifications) {
            boolean match = false;
            if (isImmortality && an.text.contains("Inmortalidad temporal")) match = true;
            else if (isCountdown && (an.text.contains("misión inicia en") || an.text.contains("reunidos, la misión inicia"))) match = true;
            else if (isWaveCooldown && (an.text.contains("Siguiente oleada en") || an.text.contains("Has despejado el camino, siguiente oleada en"))) match = true;
            else if (isChestLoot && (an.text.contains("tomó cosas de un cofre") || an.text.contains("tomó cosas del"))) match = true;
            else if (isChest && an.text.contains("Cofre ") && (an.text.contains("registrado") || an.text.contains("desregistrado"))) match = true;
            else if (isSpawn && an.text.contains("Spawn ") && an.text.contains("añadido")) match = true;
            else if (isEscape && (an.text.contains("Punto de Escape") || an.text.contains("Esperando al equipo en el punto de escape"))) match = true;
            else if (isRoute && an.text.contains("de ruta añadido")) match = true;
            else if (isWaiting && an.text.contains("Esperando a los jugadores")) match = true;
            else if (isLooting && (an.text.contains("Fase de Botín") || an.text.contains("saqueo restante"))) match = true;
            else if (an.text.equals(text)) match = true;

            if (match) {
                an.text = text;
                an.borderColor = borderColor;
                an.startTime = now;
                an.duration = isImmortality ? 1600L : 4200L;
                return;
            }
        }

        // Si ya hay 3 apiladas, remover la más antigua
        if (activeNotifications.size() >= 3) {
            activeNotifications.remove(0);
        }

        // Duración cómoda de 4.2 segundos (o 1.6s si es temporizador de inmortalidad)
        activeNotifications.add(new ActiveNotification(text, borderColor, isImmortality ? 1600L : 4200L));
    }

    public static synchronized void clear() {
        waypoints.clear();
        activeNotifications.clear();
        incursionStatus.active = false;
    }

    public static synchronized void setWaypoint(boolean isActive, String title, BlockPos pos) {
        boolean isDrop = title != null && (title.toLowerCase().contains("suministro") || title.toLowerCase().contains("drop"));
        setWaypoint(isActive, title, pos, isDrop);
    }

    public static synchronized void setWaypoint(boolean isActive, String title, BlockPos pos, boolean isDrop) {
        if (pos == null && (title == null || title.isBlank())) return;
        if (!isActive) {
            removeWaypoint(pos, title);
            return;
        }
        // Si ya existe y se está removiendo, reactivarlo
        for (Waypoint w : waypoints) {
            boolean matchPos = (pos != null && w.pos.equals(pos));
            boolean matchTitle = (title != null && !title.isBlank() && w.title.equalsIgnoreCase(title));
            if (matchPos || matchTitle) {
                w.title = title != null ? title : "";
                if (pos != null) w.pos = pos;
                w.isDrop = isDrop;
                w.removing = false;
                return;
            }
        }
        if (pos != null) {
            waypoints.add(new Waypoint(title, pos, isDrop));
        }
    }

    public static synchronized boolean hasWaypoint(BlockPos pos) {
        if (pos == null) return false;
        for (Waypoint w : waypoints) {
            if (w.pos.equals(pos) && !w.removing) return true;
        }
        return false;
    }

    public static synchronized void removeWaypoint(BlockPos pos) {
        removeWaypoint(pos, null);
    }

    public static synchronized void removeWaypoint(BlockPos pos, String title) {
        long now = System.currentTimeMillis();
        for (Waypoint w : waypoints) {
            boolean matchPos = (pos != null && w.pos.equals(pos));
            boolean matchTitle = (title != null && !title.isBlank() && w.title.equalsIgnoreCase(title));
            if ((matchPos || matchTitle) && !w.removing) {
                w.removing = true;
                w.removeStartTime = now;
            }
        }
    }

    public static synchronized boolean isActive() {
        return !waypoints.isEmpty();
    }

    private static int applyAlpha(int color, float alpha) {
        int originalAlpha = (color >> 24) & 0xFF;
        if (originalAlpha == 0) originalAlpha = 255;
        int a = Math.clamp((int) (originalAlpha * alpha), 0, 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        // 1. Limpieza y animación de salida al llegar al destino
        synchronized (WaypointHudRenderer.class) {
            Iterator<Waypoint> it = waypoints.iterator();
            while (it.hasNext()) {
                Waypoint w = it.next();

                // Si terminó la animación de salida (350ms), removerlo definitivamente
                if (w.removing) {
                    if (now - w.removeStartTime > 350) {
                        it.remove();
                    }
                    continue;
                }

                if (now - w.createdAt < 1500) {
                    continue;
                }

                double dx = w.pos.getX() + 0.5 - player.getX();
                double dy = w.pos.getY() + 0.5 - player.getY();
                double dz = w.pos.getZ() + 0.5 - player.getZ();
                double horizDist = Math.sqrt(dx * dx + dz * dz);

                if (w.isDrop) {
                    // Si el jugador está cerca del cofre (hasta 3 bloques) y el cofre fue vaciado
                    if (horizDist <= 3.0 && Math.abs(dy) <= 2.5 && mc.level != null && mc.level.isLoaded(w.pos)) {
                        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(w.pos);
                        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                            net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(w.pos);
                            if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest && chest.isEmpty()) {
                                w.removing = true;
                                w.removeStartTime = now;
                            }
                        }
                    }
                } else {
                    if (horizDist <= 1.5 && Math.abs(dy) <= 2.2) {
                        w.removing = true;
                        w.removeStartTime = now;
                        // Auto-reclamar de inmediato la misión al llegar (excepto incursiones)
                        for (com.misionesmod.mission.Mission m : com.misionesmod.client.MisionesModClient.clientMissions) {
                            if ("INCURSION".equalsIgnoreCase(m.getObjectiveType())) continue;
                            if (m.getTargetPos() != null && m.getTargetPos().equals(w.pos) && !m.isCompleted()) {
                                m.setTargetReached(true);
                                m.setCompleted(true);
                                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.misionesmod.network.ModPackets.ClaimMissionPayload(m.getId()));
                            }
                        }
                    }
                }
            }
        }

        // 2A. Barra de Incursión en Pantalla (si hay una activa)
        int incursionOffset = 0;
        if (incursionStatus.active) {
            String incursionText;
            int bannerColor;
            if (incursionStatus.isEscapePhase || incursionStatus.currentWave >= incursionStatus.totalWaves) {
                int dist = 0;
                String arrow = "▲";
                if (incursionStatus.extractionPos != null) {
                    double dx = incursionStatus.extractionPos.getX() + 0.5 - player.getX();
                    double dz = incursionStatus.extractionPos.getZ() + 0.5 - player.getZ();
                    dist = (int) Math.sqrt(dx * dx + dz * dz);
                    float playerYaw = player.getYRot();
                    double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
                    double diff = Mth.wrapDegrees(angleToTarget - playerYaw);
                    if (Math.abs(diff) < 20) arrow = "▲";
                    else if (diff < -20 && diff > -160) arrow = "◀";
                    else if (diff > 20 && diff < 160) arrow = "▶";
                    else arrow = "▼";
                }
                String lootTimerStr = (incursionStatus.lootingSeconds > 0 && incursionStatus.chestsCount > 0) ?
                        " §7| §6Loot: " + incursionStatus.lootingSeconds + "s" : "";
                incursionText = "§e" + arrow + " §fSalida §a(" + dist + "m)" + lootTimerStr +
                        " §7| §c§lOleada Final §7| §fVivos: §a" + incursionStatus.alivePlayers + "/" + incursionStatus.totalPlayers;
                bannerColor = 0xFFF59E0B;
            } else if (incursionStatus.isLootingPhase) {
                incursionText = "§a§l¡Fase de Botín! §fSaqueen los cofres §e(" + incursionStatus.lootingSeconds + "s) §7| §fVivos: §a" + incursionStatus.alivePlayers + "/" + incursionStatus.totalPlayers;
                bannerColor = 0xFF22C55E;
            } else if (incursionStatus.remainingEnemies == 0) {
                incursionText = "§a§lCamino despejado §7| §fOleada " + incursionStatus.currentWave + "/" + incursionStatus.totalWaves + " §7| §fVivos: §a" + incursionStatus.alivePlayers + "/" + incursionStatus.totalPlayers;
                bannerColor = 0xFF22C55E;
            } else {
                String ckSuffix = (incursionStatus.currentObjectiveTitle != null && incursionStatus.currentObjectiveTitle.startsWith("Checkpoint")) ?
                        " §7| §b" + incursionStatus.currentObjectiveTitle : "";
                incursionText = "§c§lOleada " + incursionStatus.currentWave + "/" + incursionStatus.totalWaves +
                        " §7| §cMobs: " + incursionStatus.remainingEnemies + ckSuffix +
                        " §7| §fVivos: §a" + incursionStatus.alivePlayers + "/" + incursionStatus.totalPlayers;
                bannerColor = 0xFFEF4444;
            }

            int w = font.width(incursionText) + 20;
            int h = 18;
            int ix = (screenWidth - w) / 2;
            int iy = 6;

            graphics.fill(ix, iy, ix + w, iy + h, 0xEE090D16);
            graphics.outline(ix, iy, w, h, bannerColor);
            graphics.centeredText(font, Component.literal(incursionText), screenWidth / 2, iy + 5, 0xFFFFFFFF);
            incursionOffset = 24;
        } else if (com.misionesmod.client.gui.IncursionSetupSession.active) {
            int spawnsCount = com.misionesmod.client.gui.IncursionSetupSession.spawnPoints.size();
            int chestsCount = com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions.size();
            int routesCount = com.misionesmod.client.gui.IncursionSetupSession.routePoints.size();
            int entryGatesCount = com.misionesmod.client.gui.IncursionSetupSession.entryGateBlocks.size();
            int finalGatesCount = com.misionesmod.client.gui.IncursionSetupSession.finalGateBlocks.size();
            boolean hasExt = com.misionesmod.client.gui.IncursionSetupSession.extractionPos != null;
            boolean hasRoof = com.misionesmod.client.gui.IncursionSetupSession.roofPos != null;

            String keyExt = MisionesModClient.SET_EXTRACTION_KEY != null ? MisionesModClient.SET_EXTRACTION_KEY.getTranslatedKeyMessage().getString() : "G";
            String keyRoute = MisionesModClient.ADD_ROUTE_KEY != null ? MisionesModClient.ADD_ROUTE_KEY.getTranslatedKeyMessage().getString() : "H";
            String keyRoof = MisionesModClient.SET_ESCAPE_KEY != null ? MisionesModClient.SET_ESCAPE_KEY.getTranslatedKeyMessage().getString() : "E";
            String keySpawn = MisionesModClient.ADD_SPAWN_KEY != null ? MisionesModClient.ADD_SPAWN_KEY.getTranslatedKeyMessage().getString() : "J";
            String keyChest = MisionesModClient.REGISTER_CHEST_KEY != null ? MisionesModClient.REGISTER_CHEST_KEY.getTranslatedKeyMessage().getString() : "C";
            String keyEntryGate = MisionesModClient.SET_ENTRY_GATE_KEY != null ? MisionesModClient.SET_ENTRY_GATE_KEY.getTranslatedKeyMessage().getString() : "B";
            String keyFinalGate = MisionesModClient.SET_FINAL_GATE_KEY != null ? MisionesModClient.SET_FINAL_GATE_KEY.getTranslatedKeyMessage().getString() : "N";
            String keyLoot = MisionesModClient.EDIT_CHEST_LOOT_KEY != null ? MisionesModClient.EDIT_CHEST_LOOT_KEY.getTranslatedKeyMessage().getString() : "L";
            String keyMob = MisionesModClient.SELECT_MOBS_KEY != null ? MisionesModClient.SELECT_MOBS_KEY.getTranslatedKeyMessage().getString() : "V";
            String keyClear = MisionesModClient.CLEAR_SPAWNS_KEY != null ? MisionesModClient.CLEAR_SPAWNS_KEY.getTranslatedKeyMessage().getString() : "K";
            String keyHelp = MisionesModClient.HELP_SETUP_KEY != null ? MisionesModClient.HELP_SETUP_KEY.getTranslatedKeyMessage().getString() : "I";
            String keyDone = MisionesModClient.OPEN_MENU_KEY != null ? MisionesModClient.OPEN_MENU_KEY.getTranslatedKeyMessage().getString() : "M";

            String setupText = "§a[" + keyExt + "] §fInicio " + (hasExt ? "§a✔" : "§7[--]") +
                    " §7| §b[" + keyRoute + "] §fCheckp. (§e" + routesCount + "§f)" +
                    " §7| §e[" + keyRoof + "] §fEscape " + (hasRoof ? "§e✔" : "§7[--]") +
                    " §7| §c[" + keySpawn + "] §fMobs (§e" + spawnsCount + "§f)" +
                    " §7| §6[" + keyChest + "] §fCofres (§e" + chestsCount + "§f)" +
                    " §7| §2[" + keyEntryGate + "] §fP.Inicio (§e" + entryGatesCount + "§f)" +
                    " §7| §6[" + keyFinalGate + "] §fP.Final (§e" + finalGatesCount + "§f)" +
                    " §7| §d[" + keyLoot + "] §fLoot §7| §5[" + keyMob + "] §fMobs" +
                    " §7| §4[" + keyClear + "] §fLimpiar §7| §e[" + keyHelp + "] §fAyuda §7| §6[" + keyDone + "] §fListo";

            int w = font.width(setupText) + 20;
            int h = 18;
            int ix = (screenWidth - w) / 2;
            int iy = 6;

            graphics.fill(ix, iy, ix + w, iy + h, 0xEE090D16);
            graphics.outline(ix, iy, w, h, 0xFFF59E0B);
            graphics.centeredText(font, Component.literal(setupText), screenWidth / 2, iy + 5, 0xFFFFFFFF);
            incursionOffset = 26;

            // Renderizar aviso de estado superior con distancia
            if (now < com.misionesmod.client.gui.IncursionSetupSession.statusExpiry && com.misionesmod.client.gui.IncursionSetupSession.statusMessage != null) {
                String sMsg = com.misionesmod.client.gui.IncursionSetupSession.statusMessage;
                int sw = font.width(sMsg) + 16;
                int sx = (screenWidth - sw) / 2;
                int sy = 28;
                graphics.fill(sx, sy, sx + sw, sy + 16, 0xEE090D16);
                graphics.outline(sx, sy, sw, 16, com.misionesmod.client.gui.IncursionSetupSession.statusColor);
                graphics.centeredText(font, Component.literal(sMsg), screenWidth / 2, sy + 4, 0xFFFFFFFF);
                incursionOffset = 48;
            }

            // Spawns de partículas periódicas en el mundo en los puntos configurados
            if (now % 300 < 50 && player.level() != null) {
                if (hasExt) {
                    BlockPos p = com.misionesmod.client.gui.IncursionSetupSession.extractionPos;
                    player.level().addParticle(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.05, 0);
                }
                if (hasRoof) {
                    BlockPos p = com.misionesmod.client.gui.IncursionSetupSession.roofPos;
                    player.level().addParticle(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.03, 0);
                }
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.routePoints) {
                    player.level().addParticle(ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.05, 0);
                }
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.spawnPoints) {
                    player.level().addParticle(ParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.05, 0);
                }
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions) {
                    player.level().addParticle(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.05, 0);
                }
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.entryGateBlocks) {
                    player.level().addParticle(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 0, 0.05, 0);
                }
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.finalGateBlocks) {
                    player.level().addParticle(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 0, 0.03, 0);
                }
            }
        }

        // 2B. Renderizar Waypoints en la parte superior (Solo Setup en el Mundo)
        List<Waypoint> activeList = new ArrayList<>();

        if (com.misionesmod.client.gui.IncursionSetupSession.active) {
            if (com.misionesmod.client.gui.IncursionSetupSession.extractionPos != null) {
                activeList.add(new Waypoint("📍 Inicio", com.misionesmod.client.gui.IncursionSetupSession.extractionPos, false));
            }
            if (com.misionesmod.client.gui.IncursionSetupSession.roofPos != null) {
                activeList.add(new Waypoint("📍 Escape", com.misionesmod.client.gui.IncursionSetupSession.roofPos, false));
            }
            for (int rIdx = 0; rIdx < com.misionesmod.client.gui.IncursionSetupSession.routePoints.size(); rIdx++) {
                activeList.add(new Waypoint("🚩 Checkpoint #" + (rIdx + 1), com.misionesmod.client.gui.IncursionSetupSession.routePoints.get(rIdx), false));
            }
            for (int sIdx = 0; sIdx < com.misionesmod.client.gui.IncursionSetupSession.spawnPoints.size(); sIdx++) {
                activeList.add(new Waypoint("🧟 Mob Spawn #" + (sIdx + 1), com.misionesmod.client.gui.IncursionSetupSession.spawnPoints.get(sIdx), false));
            }
            for (int cIdx = 0; cIdx < com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions.size(); cIdx++) {
                BlockPos cp = com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions.get(cIdx);
                boolean hasCustom = com.misionesmod.client.gui.IncursionSetupSession.customChestLootMap.containsKey(cp);
                activeList.add(new Waypoint("📦 Cofre #" + (cIdx + 1) + (hasCustom ? " §d[Loot]" : ""), cp, false));
            }
        }

        if (!activeList.isEmpty()) {
            activeList.sort((a, b) -> {
                double distA = a.pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
                double distB = b.pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
                return Double.compare(distA, distB);
            });

            int maxToShow = Math.min(3, activeList.size());
            for (int i = 0; i < maxToShow; i++) {
                Waypoint wp = activeList.get(i);
                double dx = wp.pos.getX() + 0.5 - player.getX();
                double dz = wp.pos.getZ() + 0.5 - player.getZ();
                int distance = (int) Math.sqrt(dx * dx + dz * dz);

                float playerYaw = player.getYRot();
                double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
                double diff = Mth.wrapDegrees(angleToTarget - playerYaw);

                String indicator;
                if (Math.abs(diff) < 20) {
                    indicator = "▲";
                } else if (diff < -20 && diff > -160) {
                    indicator = "◀";
                } else if (diff > 20 && diff < 160) {
                    indicator = "▶";
                } else {
                    indicator = "▼";
                }

                String displayText;
                if (wp.title.equalsIgnoreCase("Salida")) {
                    displayText = "§a" + indicator + " Salida (" + distance + "m)";
                } else if (wp.isDrop) {
                    displayText = "§6" + indicator + " " + wp.title + " §e(" + distance + "m)";
                } else {
                    displayText = "§f" + indicator + " " + wp.title + " §e(" + distance + "m)";
                }

                // Cálculo de animación de Entrada y Salida
                float alpha = 1.0f;
                float animYOffset = 0.0f;

                // Entrada (300ms: fade-in y descenso suave)
                long age = now - wp.createdAt;
                if (age < 300) {
                    float p = (float) age / 300.0f;
                    alpha = p;
                    animYOffset = (1.0f - p) * -8.0f;
                }

                // Salida (350ms: fade-out y ascenso suave)
                if (wp.removing) {
                    float p = Math.min(1.0f, (float) (now - wp.removeStartTime) / 350.0f);
                    alpha *= (1.0f - p);
                    animYOffset -= p * 6.0f;
                }

                if (alpha <= 0.01f) continue;

                int textWidth = font.width(displayText);
                int boxWidth = textWidth + 16;
                int boxHeight = 16;
                int x = (screenWidth - boxWidth) / 2;
                int y = 8 + incursionOffset + (i * 19) + (int) animYOffset;

                int borderColor = wp.isDrop ? 0xFFF59E0B : (wp.title.equalsIgnoreCase("Salida") ? 0xFF22C55E : 0xFF38BDF8);
                graphics.fill(x, y, x + boxWidth, y + boxHeight, applyAlpha(0xCC090D16, alpha));
                graphics.outline(x, y, boxWidth, boxHeight, applyAlpha(borderColor, alpha));
                graphics.centeredText(font, Component.literal(displayText), screenWidth / 2, y + 4, applyAlpha(0xFFFFFFFF, alpha));
            }
        }

        // 3. Renderizar Notificaciones Animadas apiladas ARRIBA DE LA HOTBAR
        List<ActiveNotification> notifList;
        synchronized (WaypointHudRenderer.class) {
            activeNotifications.removeIf(n -> (now - n.startTime) >= n.duration);
            notifList = new ArrayList<>(activeNotifications);
        }

        int totalNotifs = notifList.size();
        for (int idx = 0; idx < totalNotifs; idx++) {
            ActiveNotification notif = notifList.get(idx);
            long elapsed = now - notif.startTime;
            float alpha = 1.0f;
            float offsetY = 0.0f;

            // Entrada (250ms: desliza suavemente hacia arriba)
            if (elapsed < 250) {
                float p = (float) elapsed / 250.0f;
                alpha = p;
                offsetY = (1.0f - p) * 8.0f;
            }
            // Salida (últimos 350ms: fade out)
            else if (elapsed > notif.duration - 350) {
                float p = (float) (elapsed - (notif.duration - 350)) / 350.0f;
                alpha = Math.max(0.0f, 1.0f - p);
                offsetY = (1.0f - p) * -6.0f;
            }

            if (alpha <= 0.01f) continue;

            int stackFromBottom = (totalNotifs - 1 - idx) * 20;
            int notifY = screenHeight - 68 - stackFromBottom + (int) offsetY;

            int textW = font.width(notif.text);
            int boxW = textW + 20;
            int boxH = 17;
            int notifX = (screenWidth - boxW) / 2;

            graphics.fill(notifX, notifY, notifX + boxW, notifY + boxH, applyAlpha(0xEE090D16, alpha));
            graphics.outline(notifX, notifY, boxW, boxH, applyAlpha(notif.borderColor, alpha));
            graphics.centeredText(font, Component.literal(notif.text), screenWidth / 2, notifY + 4, applyAlpha(0xFFFFFFFF, alpha));
        }

        // 4. Renderizar Widget Flotante en la esquina (Drops y Misiones en progreso)
        renderFloatingTrackerWidget(graphics, font, screenWidth, screenHeight, player);
    }

    private static float cornerAnimProgress = 0.0f;
    private static long lastCornerFrameTime = 0L;
    private static List<String> cachedLines = new ArrayList<>();
    private static List<Integer> cachedColors = new ArrayList<>();
    private static Map<Integer, ItemStack> cachedIcons = new HashMap<>();

    private void renderFloatingTrackerWidget(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight, Player player) {
        if (!ClientHudConfig.widgetEnabled) return;
        if (com.misionesmod.client.gui.IncursionSetupSession.active) return;
        long now = System.currentTimeMillis();

        List<String> lines = new ArrayList<>();
        List<Integer> lineColors = new ArrayList<>();
        Map<Integer, ItemStack> lineItemIcons = new HashMap<>();

        // 1. Drops activos
        synchronized (WaypointHudRenderer.class) {
            for (Waypoint w : waypoints) {
                if (w.isDrop && !w.removing) {
                    double dx = w.pos.getX() + 0.5 - player.getX();
                    double dz = w.pos.getZ() + 0.5 - player.getZ();
                    int dist = (int) Math.sqrt(dx * dx + dz * dz);

                    float playerYaw = player.getYRot();
                    double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
                    double diff = Mth.wrapDegrees(angleToTarget - playerYaw);
                    String dropArrow = "▲";
                    if (Math.abs(diff) >= 20) {
                        if (diff < -20 && diff > -160) dropArrow = "◀";
                        else if (diff > 20 && diff < 160) dropArrow = "▶";
                        else dropArrow = "▼";
                    }

                    lines.add("§6📦 " + dropArrow + " " + w.title + " §e(" + dist + "m)");
                    lineColors.add(0xFFF59E0B);
                    lines.add("  §7X: §f" + w.pos.getX() + " §7Z: §f" + w.pos.getZ());
                    lineColors.add(0xFFCBD5E1);
                }
            }
        }

        // 2. Misiones y animación de estado
        for (Mission m : MisionesModClient.clientMissions) {
            MissionAnimState anim = missionAnimStates.computeIfAbsent(m.getId(), k -> {
                MissionAnimState s = new MissionAnimState();
                if (m.isCompleted()) {
                    s.completed = true;
                    s.completedTime = 0; // Si ya estaba completada al entrar, no reproducir animación
                }
                return s;
            });

            if (m.isCompleted()) {
                if (!anim.completed) {
                    anim.completed = true;
                    anim.completedTime = now;
                    if (player != null) {
                        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
                // Si ya pasaron 3.5 segundos tras completarla, se remueve
                if (now - anim.completedTime > 3500) {
                    continue;
                }
                lines.add("§a✔ " + m.getTitle());
                lineColors.add(0xFF22C55E);
                lines.add("  §fha sido completada");
                lineColors.add(0xFF86EFAC);
                continue;
            }

            // Para misiones de incursión, no mostrar en esta tarjeta de esquina mientras está activa
            // (para no repetir con la barra superior de oleadas), solo mostrar al completarse
            if ("INCURSION".equalsIgnoreCase(m.getObjectiveType())) {
                continue;
            }

            // Misión activa
            BlockPos targetPos = m.getTargetPos();
            String obj = m.getObjectiveType();

            String arrow = "▲";
            int dist = 0;
            if (targetPos != null) {
                double dx = targetPos.getX() + 0.5 - player.getX();
                double dz = targetPos.getZ() + 0.5 - player.getZ();
                dist = (int) Math.sqrt(dx * dx + dz * dz);
                float playerYaw = player.getYRot();
                double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
                double diff = Mth.wrapDegrees(angleToTarget - playerYaw);
                if (Math.abs(diff) < 20) arrow = "▲";
                else if (diff < -20 && diff > -160) arrow = "◀";
                else if (diff > 20 && diff < 160) arrow = "▶";
                else arrow = "▼";
            }

            lines.add("§b⚔ " + m.getTitle());
            lineColors.add(0xFF38BDF8);

            if ("OBTENCION".equalsIgnoreCase(obj) || "CRAFTEO".equalsIgnoreCase(obj) || "COCINAR".equalsIgnoreCase(obj)) {
                int found = 0;
                String reqId = m.getRequiredItemId();
                int req = m.getRequiredCount();
                if ("OBTENCION".equalsIgnoreCase(obj)) {
                    for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
                        ItemStack st = player.getInventory().getItem(s);
                        if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equalsIgnoreCase(reqId)) {
                            found += st.getCount();
                        }
                    }
                } else {
                    found = m.getCurrentProgress();
                }
                String color = found >= req ? "§a" : "§e";
                String verb = "Obtener: ";
                if ("CRAFTEO".equalsIgnoreCase(obj)) verb = "Craftear: ";
                else if ("COCINAR".equalsIgnoreCase(obj)) verb = "Cocinar: ";
                lines.add("  §7" + verb + color + found + "/" + req + " " + m.getItemDisplayName());
                lineColors.add(0xFFE2E8F0);

                if (reqId != null && !reqId.isBlank()) {
                    try {
                        Identifier itemId = Identifier.parse(reqId);
                        Item item = BuiltInRegistries.ITEM.getValue(itemId);
                        if (item != null && item != net.minecraft.world.item.Items.AIR) {
                            lineItemIcons.put(lines.size() - 1, new ItemStack(item));
                        }
                    } catch (Exception ignored) {}
                }
            } else if ("EXPLORACION".equalsIgnoreCase(obj) && m.getTargetPos() != null) {
                lines.add("  §f" + arrow + " §7Destino: §fX:" + m.getTargetPos().getX() + " Z:" + m.getTargetPos().getZ() + " §a(" + dist + "m)");
                lineColors.add(0xFF94A3B8);
            }
        }

        // Animación suave de entrada y salida de la tarjeta (smoothstep)
        long dt = (lastCornerFrameTime == 0L) ? 16L : Math.min(100L, now - lastCornerFrameTime);
        lastCornerFrameTime = now;
        float animSpeed = (float) dt / 320.0f;

        if (!lines.isEmpty()) {
            cornerAnimProgress = Math.min(1.0f, cornerAnimProgress + animSpeed);
            cachedLines = new ArrayList<>(lines);
            cachedColors = new ArrayList<>(lineColors);
            cachedIcons = new HashMap<>(lineItemIcons);
        } else {
            cornerAnimProgress = Math.max(0.0f, cornerAnimProgress - animSpeed);
        }

        if (cornerAnimProgress <= 0.005f) return;

        float t = cornerAnimProgress;
        float smoothT = t * t * (3.0f - 2.0f * t);

        List<String> activeLines = !lines.isEmpty() ? lines : cachedLines;
        List<Integer> activeColors = !lines.isEmpty() ? lineColors : cachedColors;
        Map<Integer, ItemStack> activeIcons = !lines.isEmpty() ? lineItemIcons : cachedIcons;

        if (activeLines.isEmpty()) return;

        int maxTextW = 120;
        for (String l : activeLines) {
            maxTextW = Math.max(maxTextW, font.width(l));
        }
        boolean hasAnyIcons = !activeIcons.isEmpty();
        int cardW = maxTextW + 14 + (hasAnyIcons ? 22 : 0);
        int cardH = activeLines.size() * 11 + 10;
        if (hasAnyIcons) {
            cardH = Math.max(cardH, 28);
        }

        int posX;
        int posY;
        switch (ClientHudConfig.cornerPosition) {
            case TOP_LEFT -> {
                posX = 8;
                posY = 28;
            }
            case BOTTOM_LEFT -> {
                posX = 8;
                posY = screenHeight - cardH - 24;
            }
            case BOTTOM_RIGHT -> {
                posX = screenWidth - cardW - 8;
                posY = screenHeight - cardH - 24;
            }
            case TOP_RIGHT -> {
                posX = screenWidth - cardW - 8;
                posY = 28;
            }
            default -> {
                posX = screenWidth - cardW - 8;
                posY = 28;
            }
        }

        float alpha = smoothT;
        boolean isRightSide = (ClientHudConfig.cornerPosition == ClientHudConfig.CornerPosition.TOP_RIGHT || ClientHudConfig.cornerPosition == ClientHudConfig.CornerPosition.BOTTOM_RIGHT);
        float slideOffset = (1.0f - smoothT) * (isRightSide ? 30.0f : -30.0f);
        int drawX = posX + (int) slideOffset;

        graphics.fill(drawX, posY, drawX + cardW, posY + cardH, applyAlpha(0xCC090D16, alpha));
        graphics.outline(drawX, posY, cardW, cardH, applyAlpha(0xFF334155, alpha));
        graphics.fill(drawX + 1, posY + 1, drawX + cardW - 1, posY + 3, applyAlpha(0xFF1E293B, alpha));

        int lineY = posY + 5;
        for (int i = 0; i < activeLines.size(); i++) {
            graphics.text(font, Component.literal(activeLines.get(i)), drawX + 6, lineY, applyAlpha(activeColors.get(i), alpha));
            ItemStack iconStack = activeIcons.get(i);
            if (iconStack != null && !iconStack.isEmpty()) {
                int itemX = drawX + cardW - 19;
                int itemY = lineY - 4;
                graphics.item(iconStack, itemX, itemY);
            }
            lineY += 11;
        }
    }
}
