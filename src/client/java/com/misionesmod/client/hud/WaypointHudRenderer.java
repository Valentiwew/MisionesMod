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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        public final String text;
        public final int borderColor;
        public final long duration;
        public final long startTime;

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
    }

    private static final IncursionStatus incursionStatus = new IncursionStatus();

    public static synchronized void setIncursionStatus(boolean active, String missionId, String missionTitle, int currentWave, int totalWaves, int remainingEnemies, int alivePlayers, int totalPlayers, boolean isEscapePhase, BlockPos extractionPos) {
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

        if (active && isEscapePhase && extractionPos != null) {
            setWaypoint(true, "Entrada/Escape: " + missionTitle, extractionPos, false);
        } else if (!active || !isEscapePhase) {
            if (extractionPos != null) {
                removeWaypoint(extractionPos, "Entrada/Escape: " + missionTitle);
            }
        }
    }

    private static final List<Waypoint> waypoints = new ArrayList<>();
    private static final List<ActiveNotification> activeNotifications = new ArrayList<>();

    public static synchronized void showHotbarNotification(String text, int borderColor) {
        if (text == null || text.isBlank()) return;

        // Evitar duplicados exactos si ya está activo
        for (ActiveNotification an : activeNotifications) {
            if (an.text.equals(text)) return;
        }

        // Si ya hay 3 apiladas, remover la más antigua
        if (activeNotifications.size() >= 3) {
            activeNotifications.remove(0);
        }

        // Duración cómoda de 4.2 segundos
        activeNotifications.add(new ActiveNotification(text, borderColor, 4200L));
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
                    // Si ya pasó el tiempo de descenso (~9 segundos) y en la posición ya no hay un cofre (fue saqueado o destruido)
                    if (now - w.createdAt > 9000 && mc.level != null && mc.level.isLoaded(w.pos)) {
                        if (!(mc.level.getBlockState(w.pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
                            w.removing = true;
                            w.removeStartTime = now;
                            continue;
                        }
                    }

                    // Si el jugador está cerca del cofre (hasta 4 bloques) y el cofre fue vaciado o destruido
                    if (horizDist <= 4.0 && Math.abs(dy) <= 3.0 && mc.level != null && mc.level.isLoaded(w.pos)) {
                        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(w.pos);
                        if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
                            w.removing = true;
                            w.removeStartTime = now;
                        } else {
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
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2f));
                        // Auto-reclamar de inmediato la misión al llegar
                        for (com.misionesmod.mission.Mission m : com.misionesmod.client.MisionesModClient.clientMissions) {
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
            if (incursionStatus.isEscapePhase) {
                int dist = 0;
                if (incursionStatus.extractionPos != null) {
                    double dx = incursionStatus.extractionPos.getX() + 0.5 - player.getX();
                    double dz = incursionStatus.extractionPos.getZ() + 0.5 - player.getZ();
                    dist = (int) Math.sqrt(dx * dx + dz * dz);
                }
                incursionText = "§e§l[¡ESCAPE!] §f¡Baja a la Entrada! §a(" + dist + "m) §7| §fVivos: §a" + incursionStatus.alivePlayers + "/" + incursionStatus.totalPlayers;
                bannerColor = 0xFFF59E0B;
            } else if (incursionStatus.currentWave == incursionStatus.totalWaves) {
                incursionText = "§4§l[CLÍMAX EN AZOTEA] §fOleada Final §7| §cZombies: " + incursionStatus.remainingEnemies +
                        " §7| §aVivos: " + incursionStatus.alivePlayers + "/" + incursionStatus.totalPlayers;
                bannerColor = 0xFFEF4444;
            } else {
                incursionText = "§c§l[INCURSIÓN] §fOleada " + incursionStatus.currentWave + "/" + incursionStatus.totalWaves +
                        " §7| §cZombies: " + incursionStatus.remainingEnemies +
                        " §7| §eSube a la azotea ⬆";
                bannerColor = 0xFFEF4444;
            }

            int w = font.width(incursionText) + 20;
            int h = 18;
            int ix = (screenWidth - w) / 2;
            int iy = 6;

            graphics.fill(ix, iy, ix + w, iy + h, 0xEE090D16);
            graphics.outline(ix, iy, w, h, bannerColor);
            graphics.centeredText(font, Component.literal(incursionText), screenWidth / 2, iy + 5, 0xFFFFFFFF);
            incursionOffset = 22;
        } else if (com.misionesmod.client.gui.IncursionSetupSession.active) {
            int spawnsCount = com.misionesmod.client.gui.IncursionSetupSession.spawnPoints.size();
            int chestsCount = com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions.size();
            boolean hasExt = com.misionesmod.client.gui.IncursionSetupSession.extractionPos != null;
            boolean hasRoof = com.misionesmod.client.gui.IncursionSetupSession.roofPos != null;

            String setupText = "§6§l[MODO EDIFICIO] §a[G] §fEntrada " + (hasExt ? "§a✔" : "§7[--]") +
                    " §7| §e[H] §fAzotea " + (hasRoof ? "§e✔" : "§7[--]") +
                    " §7| §c[J] §f+Spawn (§e" + spawnsCount + "§f) §7| §6[C] §fCofre (§e" + chestsCount + "§f) §7| §4[K] §fLimpiar §7| §6[M] §fTerminar";

            int w = font.width(setupText) + 20;
            int h = 18;
            int ix = (screenWidth - w) / 2;
            int iy = 6;

            graphics.fill(ix, iy, ix + w, iy + h, 0xEE090D16);
            graphics.outline(ix, iy, w, h, 0xFFF59E0B);
            graphics.centeredText(font, Component.literal(setupText), screenWidth / 2, iy + 5, 0xFFFFFFFF);
            incursionOffset = 22;

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
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.spawnPoints) {
                    player.level().addParticle(ParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.05, 0);
                }
                for (BlockPos p : com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions) {
                    player.level().addParticle(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.05, 0);
                }
            }
        }

        // 2B. Renderizar Waypoints en la parte superior con animación de entrada y salida
        List<Waypoint> activeList;
        synchronized (WaypointHudRenderer.class) {
            activeList = new ArrayList<>(waypoints);
        }

        if (com.misionesmod.client.gui.IncursionSetupSession.active) {
            if (com.misionesmod.client.gui.IncursionSetupSession.extractionPos != null) {
                activeList.add(new Waypoint("📍 Entrada/Escape", com.misionesmod.client.gui.IncursionSetupSession.extractionPos, false));
            }
            if (com.misionesmod.client.gui.IncursionSetupSession.roofPos != null) {
                activeList.add(new Waypoint("📍 Azotea", com.misionesmod.client.gui.IncursionSetupSession.roofPos, false));
            }
            for (int sIdx = 0; sIdx < com.misionesmod.client.gui.IncursionSetupSession.spawnPoints.size(); sIdx++) {
                activeList.add(new Waypoint("🧟 Spawn #" + (sIdx + 1), com.misionesmod.client.gui.IncursionSetupSession.spawnPoints.get(sIdx), false));
            }
            for (int cIdx = 0; cIdx < com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions.size(); cIdx++) {
                activeList.add(new Waypoint("📦 Cofre #" + (cIdx + 1), com.misionesmod.client.gui.IncursionSetupSession.savedChestPositions.get(cIdx), false));
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

                String badge;
                if (wp.title.startsWith("📍") || wp.title.startsWith("🧟") || wp.title.startsWith("📦")) {
                    badge = "";
                } else if (wp.isDrop) {
                    badge = "§6[DROP] ";
                } else {
                    badge = "§b[MISIÓN] ";
                }
                String displayText = badge + "§f" + indicator + " " + wp.title + " §e(" + distance + "m)";

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

                int borderColor = wp.isDrop ? 0xFFF59E0B : 0xFF38BDF8;
                graphics.fill(x, y, x + boxWidth, y + boxHeight, applyAlpha(0xCC090D16, alpha));
                graphics.outline(x, y, boxWidth, boxHeight, applyAlpha(borderColor, alpha));
                graphics.centeredText(font, Component.literal(displayText), screenWidth / 2, y + 4, applyAlpha(0xFFFFFFFF, alpha));
            }
        }

        // 3. Renderizar Notificaciones Animadas apiladas arriba de la Hotbar (Sin retrasos, una arriba de la otra)
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
                offsetY = -p * 6.0f;
            }

            if (alpha <= 0.01f) continue;

            // Apilado: la notificación más nueva (última en la lista) va abajo (screenHeight - 65).
            // Las anteriores suben 22px por cada nivel.
            int stackLevel = (totalNotifs - 1) - idx;
            int baseY = screenHeight - 65 - (stackLevel * 22);

            int textW = font.width(notif.text);
            int boxW = textW + 20;
            int boxH = 18;
            int notifX = (screenWidth - boxW) / 2;
            int notifY = baseY + (int) offsetY;

            graphics.fill(notifX, notifY, notifX + boxW, notifY + boxH, applyAlpha(0xEE090D16, alpha));
            graphics.outline(notifX, notifY, boxW, boxH, applyAlpha(notif.borderColor, alpha));
            graphics.centeredText(font, Component.literal(notif.text), screenWidth / 2, notifY + 5, applyAlpha(0xFFFFFFFF, alpha));
        }
    }
}
