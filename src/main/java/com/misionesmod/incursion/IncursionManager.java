package com.misionesmod.incursion;

import com.misionesmod.mission.Mission;
import com.misionesmod.mission.MissionManager;
import com.misionesmod.network.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import java.util.*;

public class IncursionManager {

    public enum IncursionState {
        WAITING_FOR_PLAYERS,
        WAVE_ACTIVE,
        WAVE_COOLDOWN,
        LOOTING_PHASE,
        ESCAPE_PHASE,
        COMPLETED
    }

    public static class IncursionSession {
        public final Mission mission;
        public IncursionState state = IncursionState.WAITING_FOR_PLAYERS;
        public int currentWave = 1;
        public int currentRouteIndex = 0;
        public int assemblyWaitTicks = 0;
        public int waitingNotifyTicks = 0;
        public int waveActiveTicks = 0;
        public int waveCooldownTicks = 0;
        public int lootingTicks = 0;
        public int cooldownTicks = 0;
        public int escapeGraceTicks = 0;
        public boolean chestsPopulated = false;

        public final Set<UUID> registeredParticipants = new HashSet<>();
        public final Set<UUID> aliveParticipants = new HashSet<>();
        public final Set<UUID> fallenParticipants = new HashSet<>();
        public final List<Entity> activeWaveMobs = new ArrayList<>();
        public final Set<BlockPos> activeChests = new HashSet<>();
        public final Map<BlockPos, Set<String>> chestLooters = new HashMap<>();
        public final Map<BlockPos, Integer> chestNumbers = new HashMap<>();
        public final Set<Integer> completedCheckpoints = new HashSet<>();
        public final Set<BlockPos> playerPlacedBlocks = new HashSet<>();
        public final Map<UUID, Integer> immortalityTicks = new HashMap<>();
        public ServerLevel currentLevel = null;

        public IncursionSession(Mission mission) {
            this.mission = mission;
        }

        public void reset() {
            state = IncursionState.WAITING_FOR_PLAYERS;
            currentWave = 1;
            currentRouteIndex = 0;
            assemblyWaitTicks = 0;
            waitingNotifyTicks = 0;
            waveActiveTicks = 0;
            waveCooldownTicks = 0;
            lootingTicks = 0;
            cooldownTicks = 0;
            escapeGraceTicks = 0;
            chestsPopulated = false;
            registeredParticipants.clear();
            aliveParticipants.clear();
            fallenParticipants.clear();
            activeChests.clear();
            chestLooters.clear();
            chestNumbers.clear();
            completedCheckpoints.clear();
            playerPlacedBlocks.clear();
            immortalityTicks.clear();
            cleanMobs();
        }

        public void cleanMobs() {
            for (Entity e : activeWaveMobs) {
                if (e != null && e.isAlive()) {
                    e.discard();
                }
            }
            activeWaveMobs.clear();
            if (currentLevel != null && mission.getTargetPos() != null) {
                BlockPos center = mission.getTargetPos();
                int r = Math.max(64, mission.getIncursionRadius() + 32);
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center).inflate(r, 64, r);
                for (Mob mob : currentLevel.getEntitiesOfClass(Mob.class, box)) {
                    if (mob.entityTags().contains("incursion_mob")) {
                        mob.discard();
                    }
                }
            }
        }
    }

    private static final Map<String, IncursionSession> sessions = new HashMap<>();

    public static boolean isSessionActive(String missionId) {
        IncursionSession session = sessions.get(missionId);
        return session != null && session.state != IncursionState.WAITING_FOR_PLAYERS && session.state != IncursionState.COMPLETED;
    }

    public static void onBlockPlaced(Level level, ServerPlayer player, BlockPos pos) {
        for (IncursionSession s : sessions.values()) {
            if (s.state != IncursionState.WAITING_FOR_PLAYERS && s.state != IncursionState.COMPLETED) {
                if (s.registeredParticipants.contains(player.getUUID()) && isInsideIncursion(level, pos, s)) {
                    s.playerPlacedBlocks.add(pos.immutable());
                }
            }
        }
    }

    public static boolean isChestLocked(net.minecraft.world.level.Level level, BlockPos pos, ServerPlayer player) {
        for (IncursionSession s : sessions.values()) {
            if (s.activeChests.contains(pos)) {
                if (s.state == IncursionState.WAITING_FOR_PLAYERS || s.state == IncursionState.WAVE_ACTIVE || s.state == IncursionState.WAVE_COOLDOWN) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getChestLockMessage(BlockPos pos) {
        for (IncursionSession s : sessions.values()) {
            if (s.activeChests.contains(pos)) {
                if (s.state == IncursionState.WAITING_FOR_PLAYERS) {
                    return "§c✕ Este cofre está sellado hasta que inicie la misión.";
                } else if (s.state == IncursionState.WAVE_ACTIVE || s.state == IncursionState.WAVE_COOLDOWN) {
                    return "§c¡Los cofres están bloqueados! Despeja las oleadas primero.";
                }
            }
        }
        return null;
    }

    public static void onChestOpened(net.minecraft.world.level.Level level, BlockPos pos, ServerPlayer player) {
        for (IncursionSession s : sessions.values()) {
            if (s.state == IncursionState.LOOTING_PHASE || s.state == IncursionState.ESCAPE_PHASE) {
                if (s.activeChests.contains(pos)) {
                    s.chestLooters.computeIfAbsent(pos, k -> new LinkedHashSet<>()).add(player.getName().getString());
                }
            }
        }
    }

    public static boolean isInsideIncursion(Level level, BlockPos pos, IncursionSession session) {
        Mission m = session.mission;
        BlockPos center = m.getTargetPos();
        if (center == null) return false;
        int radius = Math.max(24, m.getIncursionRadius());
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - 10;
        int maxY = center.getY() + 50;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        if (m.getExtractionPos() != null) {
            minX = Math.min(minX, m.getExtractionPos().getX() - 12);
            maxX = Math.max(maxX, m.getExtractionPos().getX() + 12);
            minY = Math.min(minY, m.getExtractionPos().getY() - 10);
            maxY = Math.max(maxY, m.getExtractionPos().getY() + 20);
            minZ = Math.min(minZ, m.getExtractionPos().getZ() - 12);
            maxZ = Math.max(maxZ, m.getExtractionPos().getZ() + 12);
        }
        if (m.getRoofPos() != null) {
            minX = Math.min(minX, m.getRoofPos().getX() - 12);
            maxX = Math.max(maxX, m.getRoofPos().getX() + 12);
            minY = Math.min(minY, m.getRoofPos().getY() - 10);
            maxY = Math.max(maxY, m.getRoofPos().getY() + 25);
            minZ = Math.min(minZ, m.getRoofPos().getZ() - 12);
            maxZ = Math.max(maxZ, m.getRoofPos().getZ() + 12);
        }
        for (BlockPos sp : m.getSpawnPoints()) {
            minX = Math.min(minX, sp.getX() - 10);
            maxX = Math.max(maxX, sp.getX() + 10);
            minY = Math.min(minY, sp.getY() - 10);
            maxY = Math.max(maxY, sp.getY() + 15);
            minZ = Math.min(minZ, sp.getZ() - 10);
            maxZ = Math.max(maxZ, sp.getZ() + 10);
        }
        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public static boolean handleBlockBreak(Level level, ServerPlayer player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level.isClientSide()) return true;

        // 1. Revisar sesiones de incursión activas
        for (IncursionSession session : sessions.values()) {
            if (session.state == IncursionState.WAITING_FOR_PLAYERS || session.state == IncursionState.COMPLETED) {
                continue;
            }
            if (!session.registeredParticipants.contains(player.getUUID())) {
                continue; // Solo afecta a los participantes registrados de la misión
            }
            if (isInsideIncursion(level, pos, session)) {
                // Si el bloque fue colocado por un jugador durante la incursión, permitir romperlo
                if (session.playerPlacedBlocks.remove(pos)) {
                    return true;
                }

                // Si es un cofre de la incursión
                if (state.getBlock() instanceof ChestBlock) {
                    if (session.state == IncursionState.WAVE_ACTIVE || session.state == IncursionState.WAVE_COOLDOWN) {
                        ServerPlayNetworking.send(player, new ModPackets.NotificationPayload(
                                "§c¡Los cofres están bloqueados! Despeja las oleadas primero.",
                                0xFFEF4444
                        ));
                        return false;
                    }
                    if (session.activeChests.contains(pos)) {
                        if (blockEntity instanceof ChestBlockEntity chest) {
                            Containers.dropContents(level, pos, chest);
                            chest.clearContent();
                        }
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 1.2f);
                        if (level instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 25, 0.3, 0.6, 0.3, 0.03);
                            sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.02);
                        }
                        session.activeChests.remove(pos);
                        session.chestLooters.computeIfAbsent(pos, k -> new LinkedHashSet<>()).add(player.getName().getString());
                        broadcastToRegistered(level.getServer(), session, new ModPackets.NotificationPayload(
                                "§6" + player.getName().getString() + " §ftomó cosas de un cofre",
                                0xFFF59E0B
                        ));
                        syncHud(level.getServer(), session);
                        return false; // Cancela la rotura normal para NO soltar el bloque del cofre
                    }
                }

                // La estructura no se puede romper durante la incursión (solo colocar bloques)
                if (!player.isCreative()) {
                    ServerPlayNetworking.send(player, new ModPackets.NotificationPayload(
                            "§c✕ No puedes romper bloques en la estructura durante la incursión.",
                            0xFFEF4444
                    ));
                    return false;
                }
            }
        }

        return true;
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;
        for (IncursionSession session : sessions.values()) {
            if (session.state != IncursionState.WAITING_FOR_PLAYERS && session.state != IncursionState.COMPLETED) {
                if (session.registeredParticipants.contains(player.getUUID())) {
                    BlockPos respawnPos = null;
                    if (!session.completedCheckpoints.isEmpty()) {
                        int maxCk = Collections.max(session.completedCheckpoints);
                        if (session.mission.getRoutePoints() != null && maxCk < session.mission.getRoutePoints().size()) {
                            respawnPos = session.mission.getRoutePoints().get(maxCk);
                        }
                    }
                    if (respawnPos == null) {
                        respawnPos = session.mission.getExtractionPos() != null ? session.mission.getExtractionPos() : session.mission.getTargetPos();
                    }
                    if (respawnPos != null) {
                        player.teleportTo(respawnPos.getX() + 0.5, respawnPos.getY() + 0.1, respawnPos.getZ() + 0.5);
                        session.aliveParticipants.add(player.getUUID());
                        session.fallenParticipants.remove(player.getUUID());
                        session.immortalityTicks.put(player.getUUID(), 300);

                        // Otorgar 15 segundos de inmortalidad (Resistencia V = 100% de reducción de daño), regeneración, resistencia al fuego e invisibilidad
                        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 300, 4, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 2, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, false, false, true));

                        ServerPlayNetworking.send(player, new ModPackets.NotificationPayload(
                                "§6Inmortalidad temporal (15s) ¡Recupera tus cosas!",
                                0xFFF59E0B
                        ));
                    }
                    break;
                }
            }
        }
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null) return;

        List<Mission> missions = MissionManager.getMissions();
        for (Mission m : missions) {
            if (!"INCURSION".equalsIgnoreCase(m.getObjectiveType())) continue;
            if (m.isCompleted()) continue; // No ejecutar si la misión ya fue completada

            IncursionSession session = sessions.computeIfAbsent(m.getId(), k -> new IncursionSession(m));
            ServerLevel level = server.getLevel(server.overworld().dimension());
            if (level == null) continue;
            session.currentLevel = level;

            // Manejar cuenta regresiva de inmortalidad y desviar mobs
            if (!session.immortalityTicks.isEmpty()) {
                for (Iterator<Map.Entry<UUID, Integer>> it = session.immortalityTicks.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<UUID, Integer> entry = it.next();
                    int rem = entry.getValue() - 1;
                    ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
                    if (sp == null || !sp.isAlive() || rem <= 0) {
                        it.remove();
                        if (sp != null) {
                            sp.removeEffect(MobEffects.INVISIBILITY);
                        }
                        continue;
                    }
                    entry.setValue(rem);
                    for (Entity mobEnt : session.activeWaveMobs) {
                        if (mobEnt instanceof Mob mob && mob.getTarget() == sp) {
                            mob.setTarget(null);
                        }
                    }
                    if (rem % 20 == 0) {
                        int secs = rem / 20;
                        ServerPlayNetworking.send(sp, new ModPackets.NotificationPayload(
                                "§6Inmortalidad temporal (" + secs + "s) ¡Recupera tus cosas!",
                                0xFFF59E0B
                        ));
                    }
                }
            }

            BlockPos center = m.getTargetPos();
            if (center == null) continue;
            int radius = Math.max(24, m.getIncursionRadius());

            // Delimitar caja envolvente dinámica de todo el edificio / área de misión
            int minX = center.getX() - radius;
            int maxX = center.getX() + radius;
            int minY = center.getY() - 10;
            int maxY = center.getY() + 50;
            int minZ = center.getZ() - radius;
            int maxZ = center.getZ() + radius;

            if (m.getExtractionPos() != null) {
                minX = Math.min(minX, m.getExtractionPos().getX() - 12);
                maxX = Math.max(maxX, m.getExtractionPos().getX() + 12);
                minY = Math.min(minY, m.getExtractionPos().getY() - 10);
                maxY = Math.max(maxY, m.getExtractionPos().getY() + 20);
                minZ = Math.min(minZ, m.getExtractionPos().getZ() - 12);
                maxZ = Math.max(maxZ, m.getExtractionPos().getZ() + 12);
            }
            if (m.getRoofPos() != null) {
                minX = Math.min(minX, m.getRoofPos().getX() - 12);
                maxX = Math.max(maxX, m.getRoofPos().getX() + 12);
                minY = Math.min(minY, m.getRoofPos().getY() - 10);
                maxY = Math.max(maxY, m.getRoofPos().getY() + 25);
                minZ = Math.min(minZ, m.getRoofPos().getZ() - 12);
                maxZ = Math.max(maxZ, m.getRoofPos().getZ() + 12);
            }
            for (BlockPos sp : m.getSpawnPoints()) {
                minX = Math.min(minX, sp.getX() - 10);
                maxX = Math.max(maxX, sp.getX() + 10);
                minY = Math.min(minY, sp.getY() - 10);
                maxY = Math.max(maxY, sp.getY() + 15);
                minZ = Math.min(minZ, sp.getZ() - 10);
                maxZ = Math.max(maxZ, sp.getZ() + 10);
            }

            // 1. Detectar jugadores en la zona envolvente
            List<ServerPlayer> playersInZone = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) continue;
                if (p.getX() >= minX && p.getX() <= maxX &&
                    p.getY() >= minY && p.getY() <= maxY &&
                    p.getZ() >= minZ && p.getZ() <= maxZ) {
                    playersInZone.add(p);
                }
            }

            switch (session.state) {
                case WAITING_FOR_PLAYERS -> {
                    if (session.cooldownTicks > 0) {
                        session.cooldownTicks--;
                        continue;
                    }

                    // El inicio solo se activa si los jugadores se reúnen en la Entrada / Inicio
                    BlockPos startPos = m.getExtractionPos() != null ? m.getExtractionPos() : center;
                    double startX = startPos.getX() + 0.5;
                    double startY = startPos.getY() + 0.5;
                    double startZ = startPos.getZ() + 0.5;

                    List<ServerPlayer> eligiblePlayers = new ArrayList<>();
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (p.isSpectator() || m.isCompletedBy(p.getUUID())) continue;
                        if (p.level() == level) {
                            eligiblePlayers.add(p);
                        }
                    }

                    if (eligiblePlayers.isEmpty()) {
                        session.assemblyWaitTicks = 0;
                        session.waitingNotifyTicks = 0;
                        continue;
                    }

                    List<ServerPlayer> playersAtStart = new ArrayList<>();
                    for (ServerPlayer p : eligiblePlayers) {
                        double dx = p.getX() - startX;
                        double dy = p.getY() - startY;
                        double dz = p.getZ() - startZ;
                        if (Math.abs(dx) <= 2.0 && Math.abs(dz) <= 2.0 && Math.abs(dy) <= 2.5) { // Área 4x4 bloques
                            playersAtStart.add(p);
                        }
                    }

                    if (playersAtStart.isEmpty()) {
                        session.assemblyWaitTicks = 0;
                        session.waitingNotifyTicks = 0;
                        continue;
                    }

                    int atStartCount = playersAtStart.size();
                    int totalEligible = eligiblePlayers.size();

                    // Si aún faltan jugadores disponibles en el servidor
                    if (atStartCount < totalEligible) {
                        session.assemblyWaitTicks = 0;
                        if (session.waitingNotifyTicks <= 0) {
                            session.waitingNotifyTicks = 60; // Notificar cada 3s
                            ModPackets.NotificationPayload waitNotif = new ModPackets.NotificationPayload(
                                    "§6§l[INCURSIÓN] §eEsperando a los jugadores (" + atStartCount + "/" + totalEligible + ")...",
                                    0xFFF59E0B
                            );
                            for (ServerPlayer p : playersAtStart) {
                                ServerPlayNetworking.send(p, waitNotif);
                            }
                        } else {
                            session.waitingNotifyTicks--;
                        }
                        continue;
                    }

                    // Todos los jugadores disponibles están reunidos: cuenta regresiva
                    session.waitingNotifyTicks = 0;
                    if (session.assemblyWaitTicks <= 0) {
                        session.assemblyWaitTicks = 100; // 5 segundos
                        ModPackets.NotificationPayload readyNotif = new ModPackets.NotificationPayload(
                                "§aTodos reunidos, la misión inicia en 5s",
                                0xFF22C55E
                        );
                        for (ServerPlayer p : playersAtStart) {
                            ServerPlayNetworking.send(p, readyNotif);
                        }
                    } else {
                        session.assemblyWaitTicks--;
                        if (session.assemblyWaitTicks % 20 == 0 && session.assemblyWaitTicks > 0) {
                            int secs = session.assemblyWaitTicks / 20;
                            ModPackets.NotificationPayload countNotif = new ModPackets.NotificationPayload(
                                    "§6La misión inicia en " + secs + "s",
                                    0xFFF59E0B
                            );
                            for (ServerPlayer p : playersAtStart) {
                                ServerPlayNetworking.send(p, countNotif);
                            }
                        }

                        if (session.assemblyWaitTicks <= 0) {
                            // Iniciar incursión
                            session.state = IncursionState.WAVE_ACTIVE;
                            session.currentWave = 1;
                            session.currentRouteIndex = 0;
                            session.waveActiveTicks = 0;
                            session.escapeGraceTicks = 0;
                            session.lootingTicks = 0;
                            session.registeredParticipants.clear();
                            session.aliveParticipants.clear();
                            session.fallenParticipants.clear();

                            for (ServerPlayer p : playersAtStart) {
                                session.registeredParticipants.add(p.getUUID());
                                session.aliveParticipants.add(p.getUUID());
                            }

                            // Poblar cofres con botín individual o general
                            populateChests(level, minX, maxX, minY, maxY, minZ, maxZ, session);

                            // Spawn de la Oleada 1
                            spawnWaveMobs(level, session);

                            // Alerta y sonido de cuerno de asalto
                            level.playSound(null, startPos.getX(), startPos.getY(), startPos.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 0.9f);
                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§c§l¡Despejen a los enemigos y avancen!",
                                    0xFFEF4444
                            ));

                            // Si hay punto de ruta inicial, marcarlo
                            if (m.getRoutePoints() != null && !m.getRoutePoints().isEmpty()) {
                                BlockPos rPos = m.getRoutePoints().get(0);
                                String rName = (m.getRoutePointNames() != null && !m.getRoutePointNames().isEmpty()) ?
                                        m.getRoutePointNames().get(0) : "Ruta #1";
                                for (UUID uuid : session.registeredParticipants) {
                                    ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                                    if (sp != null) {
                                        ServerPlayNetworking.send(sp, new ModPackets.SetWaypointPayload(true, "🚩 " + rName, rPos));
                                    }
                                }
                            }
                        }
                    }
                }

                case WAVE_ACTIVE -> {
                    session.waveActiveTicks++;
                    // Actualizar estado de jugadores (vivos vs caídos)
                    updateParticipants(server, session, playersInZone);

                    // Monitorear llegada a checkpoints durante la oleada
                    if (m.getRoutePoints() != null && session.currentRouteIndex < m.getRoutePoints().size()) {
                        BlockPos ckPos = m.getRoutePoints().get(session.currentRouteIndex);
                        double ckX = ckPos.getX() + 0.5;
                        double ckY = ckPos.getY() + 0.5;
                        double ckZ = ckPos.getZ() + 0.5;

                        if (server.getTickCount() % 10 == 0) {
                            level.sendParticles(ParticleTypes.END_ROD, ckX, ckY + 0.1, ckZ, 6, 0.1, 1.2, 0.1, 0.02);
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ckX, ckY + 0.5, ckZ, 4, 0.2, 0.2, 0.2, 0.02);
                        }

                        boolean reached = false;
                        for (UUID u : session.aliveParticipants) {
                            ServerPlayer p = server.getPlayerList().getPlayer(u);
                            if (p != null) {
                                double dx = p.getX() - ckX;
                                double dy = p.getY() - ckY;
                                double dz = p.getZ() - ckZ;
                                if ((dx * dx + dz * dz) <= 2.25 && Math.abs(dy) <= 1.8) {
                                    reached = true;
                                    break;
                                }
                            }
                        }
                        if (reached && !session.completedCheckpoints.contains(session.currentRouteIndex)) {
                            session.completedCheckpoints.add(session.currentRouteIndex);
                            session.currentRouteIndex++;
                            level.playSound(null, ckX, ckY, ckZ, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ckX, ckY, ckZ, 20, 0.5, 0.5, 0.5, 0.1);
                            level.sendParticles(ParticleTypes.END_ROD, ckX, ckY + 1.0, ckZ, 10, 0.2, 0.5, 0.2, 0.05);

                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§a§lCheckpoint Alcanzado!",
                                    0xFF22C55E
                            ));
                            syncHud(server, session);
                        }
                    }

                    boolean anyConnected = false;
                    for (UUID uuid : session.registeredParticipants) {
                        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                        if (sp != null && !sp.isSpectator()) {
                            anyConnected = true;
                            break;
                        }
                    }
                    if (!anyConnected) {
                        session.cleanMobs();
                        clearHud(server, session);
                        session.state = IncursionState.COMPLETED;
                        session.cooldownTicks = 400;
                        continue;
                    }

                    // Limpiar entidades muertas de la lista
                    session.activeWaveMobs.removeIf(e -> e == null || !e.isAlive() || e.isRemoved());

                    // Si todos los mobs de la oleada fueron eliminados
                    if (session.activeWaveMobs.isEmpty() && session.waveActiveTicks >= 60) {
                        if (session.currentWave < m.getTotalWaves()) {
                            session.state = IncursionState.WAVE_COOLDOWN;
                            session.waveCooldownTicks = 300; // 15 segundos de descanso
                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);

                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§aSiguiente oleada en 15s",
                                    0xFF22C55E
                            ));
                        } else {
                            // En la oleada final: activación de escape y botín
                            session.state = IncursionState.ESCAPE_PHASE;
                            session.escapeGraceTicks = 40;
                            session.cooldownTicks = 240;
                            if (session.lootingTicks <= 0) session.lootingTicks = 1200;
                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1.0f);

                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§6§l¡Toma el loot de los cofres y corre!",
                                    0xFFF59E0B
                            ));
                        }
                    }
                }

                case WAVE_COOLDOWN -> {
                    updateParticipants(server, session, playersInZone);

                    // Monitorear llegada a checkpoints durante el descanso
                    if (m.getRoutePoints() != null && session.currentRouteIndex < m.getRoutePoints().size()) {
                        BlockPos ckPos = m.getRoutePoints().get(session.currentRouteIndex);
                        double ckX = ckPos.getX() + 0.5;
                        double ckY = ckPos.getY() + 0.5;
                        double ckZ = ckPos.getZ() + 0.5;

                        if (server.getTickCount() % 10 == 0) {
                            level.sendParticles(ParticleTypes.END_ROD, ckX, ckY + 0.1, ckZ, 6, 0.1, 1.2, 0.1, 0.02);
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ckX, ckY + 0.5, ckZ, 4, 0.2, 0.2, 0.2, 0.02);
                        }

                        boolean reached = false;
                        for (UUID u : session.aliveParticipants) {
                            ServerPlayer p = server.getPlayerList().getPlayer(u);
                            if (p != null) {
                                double dx = p.getX() - ckX;
                                double dy = p.getY() - ckY;
                                double dz = p.getZ() - ckZ;
                                if ((dx * dx + dz * dz) <= 2.25 && Math.abs(dy) <= 1.8) {
                                    reached = true;
                                    break;
                                }
                            }
                        }
                        if (reached && !session.completedCheckpoints.contains(session.currentRouteIndex)) {
                            session.completedCheckpoints.add(session.currentRouteIndex);
                            session.currentRouteIndex++;
                            level.playSound(null, ckX, ckY, ckZ, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ckX, ckY, ckZ, 20, 0.5, 0.5, 0.5, 0.1);
                            level.sendParticles(ParticleTypes.END_ROD, ckX, ckY + 1.0, ckZ, 10, 0.2, 0.5, 0.2, 0.05);

                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§a§lCheckpoint Alcanzado!",
                                    0xFF22C55E
                            ));
                            syncHud(server, session);
                        }
                    }

                    session.waveCooldownTicks--;
                    if (session.waveCooldownTicks % 20 == 0 && session.waveCooldownTicks > 0) {
                        int secs = session.waveCooldownTicks / 20;
                        broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                "§aSiguiente oleada en " + secs + "s",
                                0xFF22C55E
                        ));
                    }

                    if (session.waveCooldownTicks <= 0) {
                        session.currentWave++;
                        session.waveActiveTicks = 0;
                        if (session.currentWave >= m.getTotalWaves()) {
                            // Oleada Final: Habilitar botín, evacuación inmediata y refuerzos continuos
                            session.state = IncursionState.ESCAPE_PHASE;
                            session.escapeGraceTicks = 40;
                            session.cooldownTicks = 240; // 12s entre refuerzos
                            if (session.lootingTicks <= 0) session.lootingTicks = 1200;
                            spawnWaveMobs(level, session);

                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.9f);
                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§6§l¡Toma el loot de los cofres y corre!",
                                    0xFFF59E0B
                            ));
                        } else {
                            session.state = IncursionState.WAVE_ACTIVE;
                            spawnWaveMobs(level, session);

                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.4f);
                        }
                    }
                }

                case LOOTING_PHASE -> {
                    updateParticipants(server, session, playersInZone);
                    session.lootingTicks--;

                    // Monitorear cofres saqueados que se vacían SOLO si un jugador los abrió
                    if (!session.activeChests.isEmpty()) {
                        for (BlockPos cp : new ArrayList<>(session.activeChests)) {
                            Set<String> looters = session.chestLooters.get(cp);
                            boolean wasOpenedByPlayer = looters != null && !looters.isEmpty();
                            if (wasOpenedByPlayer && level.getBlockEntity(cp) instanceof ChestBlockEntity chest && chest.isEmpty()) {
                                session.activeChests.remove(cp);
                                level.setBlockAndUpdate(cp, Blocks.AIR.defaultBlockState());
                                level.playSound(null, cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 1.2f);
                                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cp.getX() + 0.5, cp.getY() + 0.8, cp.getZ() + 0.5, 25, 0.3, 0.6, 0.3, 0.03);
                                level.sendParticles(ParticleTypes.SMOKE, cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.02);

                                List<String> looterList = new ArrayList<>(looters);
                                String looterName = looterList.isEmpty() ? "Un superviviente" : looterList.get(looterList.size() - 1);
                                String announcement = "§6" + looterName + " §ftomó cosas de un cofre";

                                broadcastToRegistered(server, session, new ModPackets.NotificationPayload(announcement, 0xFFF59E0B));
                                syncHud(server, session);
                            }
                        }
                    }

                    if (session.lootingTicks <= 0) {
                        session.state = IncursionState.ESCAPE_PHASE;
                        session.escapeGraceTicks = 40; // 2s de gracia
                        level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.9f);

                        // Spawn de refuerzos hostiles
                        spawnWaveMobs(level, session);

                        broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                "§c§l¡Evacuación inminente! §fEl área se llenará de enemigos. ¡Corran a la salida!",
                                0xFFEF4444
                        ));
                    }
                }

                case ESCAPE_PHASE -> {
                    updateParticipants(server, session, playersInZone);

                    // Temporizador de botín en la oleada final
                    if (session.lootingTicks > 0) {
                        session.lootingTicks--;
                        if (session.lootingTicks == 0 && !session.activeChests.isEmpty()) {
                            for (BlockPos cp : new ArrayList<>(session.activeChests)) {
                                level.setBlockAndUpdate(cp, Blocks.AIR.defaultBlockState());
                                level.playSound(null, cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 1.2f);
                                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cp.getX() + 0.5, cp.getY() + 0.8, cp.getZ() + 0.5, 25, 0.3, 0.6, 0.3, 0.03);
                                level.sendParticles(ParticleTypes.SMOKE, cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.02);
                            }
                            session.activeChests.clear();
                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 1.0f);
                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§c§l¡Se acabó el tiempo del botín! Los cofres han desaparecido.",
                                    0xFFEF4444
                            ));
                            syncHud(server, session);
                        }
                    }

                    // Monitorear cofres que se sigan vaciando en el escape SOLO si un jugador los abrió
                    if (!session.activeChests.isEmpty()) {
                        for (BlockPos cp : new ArrayList<>(session.activeChests)) {
                            Set<String> looters = session.chestLooters.get(cp);
                            boolean wasOpenedByPlayer = looters != null && !looters.isEmpty();
                            if (wasOpenedByPlayer && level.getBlockEntity(cp) instanceof ChestBlockEntity chest && chest.isEmpty()) {
                                session.activeChests.remove(cp);
                                level.setBlockAndUpdate(cp, Blocks.AIR.defaultBlockState());
                                level.playSound(null, cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 1.2f);
                                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cp.getX() + 0.5, cp.getY() + 0.8, cp.getZ() + 0.5, 25, 0.3, 0.6, 0.3, 0.03);
                                level.sendParticles(ParticleTypes.SMOKE, cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.02);

                                List<String> looterList = new ArrayList<>(looters);
                                String looterName = looterList.isEmpty() ? "Un superviviente" : looterList.get(looterList.size() - 1);
                                String announcement = "§6" + looterName + " §ftomó cosas de un cofre";

                                broadcastToRegistered(server, session, new ModPackets.NotificationPayload(announcement, 0xFFF59E0B));
                                syncHud(server, session);
                            }
                        }
                    }

                    // Refuerzos continuos en la oleada final con cooldown para evitar lag
                    session.activeWaveMobs.removeIf(e -> e == null || !e.isAlive() || e.isRemoved());
                    if (session.cooldownTicks > 0) {
                        session.cooldownTicks--;
                    } else if (session.activeWaveMobs.size() <= 4) {
                        spawnWaveMobs(level, session);
                        session.cooldownTicks = 240; // 12s de cooldown
                    }

                    boolean anyConnected = false;
                    for (UUID uuid : session.registeredParticipants) {
                        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                        if (sp != null && !sp.isSpectator()) {
                            anyConnected = true;
                            break;
                        }
                    }
                    if (!anyConnected) {
                        session.cleanMobs();
                        clearHud(server, session);
                        session.state = IncursionState.COMPLETED;
                        session.cooldownTicks = 400; // 20s
                        continue;
                    }

                    if (session.escapeGraceTicks > 0) {
                        session.escapeGraceTicks--;
                        continue;
                    }

                    // Comprobar si TODOS los participantes vivos llegaron al punto de escape
                    BlockPos escapePos = m.getRoofPos() != null ? m.getRoofPos() : (m.getExtractionPos() != null ? m.getExtractionPos() : center);

                    // Partículas de señalización en el punto de escape
                    if (server.getTickCount() % 15 == 0) {
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                escapePos.getX() + 0.5, escapePos.getY() + 1.2, escapePos.getZ() + 0.5,
                                12, 0.4, 0.6, 0.4, 0.05);
                        level.sendParticles(ParticleTypes.END_ROD,
                                escapePos.getX() + 0.5, escapePos.getY() + 0.5, escapePos.getZ() + 0.5,
                                6, 0.2, 1.2, 0.2, 0.02);
                    }

                    double extX = escapePos.getX() + 0.5;
                    double extY = escapePos.getY() + 0.5;
                    double extZ = escapePos.getZ() + 0.5;

                    int atEscape = 0;
                    int totalAlive = session.aliveParticipants.size();
                    boolean escaped = totalAlive > 0;

                    for (UUID uuid : session.aliveParticipants) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null) {
                            double dx = player.getX() - extX;
                            double dy = player.getY() - extY;
                            double dz = player.getZ() - extZ;
                            if (Math.abs(dx) <= 2.0 && Math.abs(dz) <= 2.0 && Math.abs(dy) <= 2.5) {
                                atEscape++;
                            } else {
                                escaped = false;
                            }
                        }
                    }

                    if (!escaped && atEscape > 0) {
                        if (server.getTickCount() % 40 == 0) {
                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§e§l[ESCAPE] §fEsperando al equipo en el punto de escape (" + atEscape + "/" + totalAlive + ")...",
                                    0xFFF59E0B
                            ));
                        }
                    }

                    if (escaped) {
                        // ¡VICTORIA! Marcar como completada y otorgar recompensa
                        for (UUID uuid : session.registeredParticipants) {
                            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                            if (player != null) {
                                MissionManager.completeMission(m.getId(), player);
                            } else {
                                m.setCompletedBy(uuid, true);
                            }
                        }
                        MissionManager.save();
                        MissionManager.syncToAll(server);

                        level.playSound(null, escapePos.getX(), escapePos.getY(), escapePos.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
                        // Mensaje de completado removido a petición del usuario para evitar redundancia con la UI superior

                        clearHud(server, session);
                        session.cleanMobs();
                        session.state = IncursionState.COMPLETED;
                        session.cooldownTicks = 6000;
                    }
                }

                case COMPLETED -> {
                    // Si la misión ya fue completada, no reiniciar jamás
                    if (m.isCompleted()) {
                        continue;
                    }
                    if (session.cooldownTicks > 0) {
                        session.cooldownTicks--;
                    } else {
                        if (playersInZone.isEmpty()) {
                            session.reset();
                        }
                    }
                }
            }

            // Sincronizar HUD a participantes cada 5 ticks si está activo
            if (server.getTickCount() % 5 == 0 && session.state != IncursionState.WAITING_FOR_PLAYERS && session.state != IncursionState.COMPLETED) {
                syncHud(server, session);
            }
        }
    }

    private static void updateParticipants(MinecraftServer server, IncursionSession session, List<ServerPlayer> inZone) {
        for (ServerPlayer p : inZone) {
            // Solo añadir como nuevo participante si no ha completado la misión
            if (!session.mission.isCompletedBy(p.getUUID())) {
                session.registeredParticipants.add(p.getUUID());
            }
            if (session.registeredParticipants.contains(p.getUUID())) {
                if (p.isAlive() && !p.isSpectator()) {
                    session.aliveParticipants.add(p.getUUID());
                    session.fallenParticipants.remove(p.getUUID());
                } else {
                    session.aliveParticipants.remove(p.getUUID());
                    session.fallenParticipants.add(p.getUUID());
                }
            }
        }

        // Chequear desconectados o muertos
        Iterator<UUID> it = session.aliveParticipants.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null || sp.isDeadOrDying() || sp.isSpectator()) {
                it.remove();
                session.fallenParticipants.add(id);
            }
        }
    }

    private static void spawnWaveMobs(ServerLevel level, IncursionSession session) {
        Mission m = session.mission;
        List<BlockPos> spawns = m.getSpawnPoints();
        List<String> mobTypes = m.getMobTypes();
        if (mobTypes.isEmpty()) {
            mobTypes = List.of("minecraft:zombie");
        }

        RandomSource rnd = level.getRandom();
        int baseCount = Math.max(4, session.currentWave * 4);

        for (int i = 0; i < baseCount; i++) {
            BlockPos spawnPos;
            if (!spawns.isEmpty()) {
                spawnPos = spawns.get(rnd.nextInt(spawns.size()));
            } else {
                BlockPos center = m.getTargetPos() != null ? m.getTargetPos() : BlockPos.ZERO;
                spawnPos = center.offset(rnd.nextInt(12) - 6, 0, rnd.nextInt(12) - 6);
            }

            String typeId = mobTypes.get(rnd.nextInt(mobTypes.size()));
            try {
                Identifier id = Identifier.parse(typeId);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                if (type != null) {
                    Entity entity = type.create(level, EntitySpawnReason.EVENT);
                    if (entity != null) {
                        double spawnY = level.getBlockState(spawnPos).isSolid() ? (spawnPos.getY() + 1.0) : spawnPos.getY();
                        entity.setPos(spawnPos.getX() + 0.5, spawnY, spawnPos.getZ() + 0.5);
                        entity.addTag("incursion_mob");
                        if (entity instanceof Mob mob) {
                            mob.addTag("incursion_mob");
                            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
                            mob.setPersistenceRequired();

                            // Dificultad progresiva por oleada
                            if (session.currentWave >= 2) {
                                Item helmet = session.currentWave >= 3 ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET;
                                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
                                mob.setDropChance(EquipmentSlot.HEAD, 0.0f);

                                if (mob.getMainHandItem().isEmpty() || mob.getMainHandItem().is(Items.AIR)) {
                                    Item weapon = session.currentWave >= 3 ? Items.IRON_SWORD : Items.STONE_SWORD;
                                    mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
                                    mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
                                }
                            }
                            if (session.currentWave >= 3) {
                                mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 12000, 0, false, false));
                                if (session.currentWave >= 4) {
                                    mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 12000, 0, false, false));
                                    mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                                    mob.setDropChance(EquipmentSlot.CHEST, 0.0f);
                                }
                            }
                        }
                        level.addFreshEntity(entity);
                        session.activeWaveMobs.add(entity);

                        // Partículas de aparición
                        level.sendParticles(
                                ParticleTypes.PORTAL,
                                spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5,
                                10, 0.3, 0.5, 0.3, 0.05
                        );
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void populateChests(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, IncursionSession session) {
        Mission m = session.mission;
        session.activeChests.clear();
        session.chestNumbers.clear();
        session.chestLooters.clear();

        Set<BlockPos> targetChests = new LinkedHashSet<>();
        if (m.getChestPoints() != null) targetChests.addAll(m.getChestPoints());
        if (m.getCustomChestPositions() != null) targetChests.addAll(m.getCustomChestPositions());

        List<ItemStack> pool = new ArrayList<>();
        List<String> lootIds = m.getBuildingChestLootIds();
        List<Integer> lootCounts = m.getBuildingChestLootCounts();
        if (lootIds != null) {
            for (int i = 0; i < lootIds.size(); i++) {
                try {
                    Identifier id = Identifier.parse(lootIds.get(i));
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        int count = Math.max(1, lootCounts.get(i));
                        pool.add(new ItemStack(item, count));
                    }
                } catch (Exception ignored) {}
            }
        }

        // Si no se configuró botín en la misión, dotar a los cofres con suministros de supervivencia
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Items.GOLDEN_APPLE, 2));
            pool.add(new ItemStack(Items.IRON_INGOT, 8));
            pool.add(new ItemStack(Items.GOLD_INGOT, 6));
            pool.add(new ItemStack(Items.DIAMOND, 2));
            pool.add(new ItemStack(Items.COOKED_BEEF, 12));
            pool.add(new ItemStack(Items.ARROW, 16));
        }

        RandomSource rnd = level.getRandom();
        int chestIndex = 1;

        if (!targetChests.isEmpty()) {
            for (BlockPos cp : targetChests) {
                if (level.getBlockState(cp).getBlock() instanceof ChestBlock) {
                    session.activeChests.add(cp);
                    session.chestNumbers.put(cp, chestIndex++);
                    session.chestLooters.put(cp, new LinkedHashSet<>());

                    BlockEntity be = level.getBlockEntity(cp);
                    if (be instanceof ChestBlockEntity chest && chest.isEmpty()) {
                        List<ItemStack> customLoot = m.getLootForChest(cp);
                        if (!customLoot.isEmpty()) {
                            for (int slot = 0; slot < Math.min(chest.getContainerSize(), customLoot.size()); slot++) {
                                ItemStack st = customLoot.get(slot);
                                if (st != null && !st.isEmpty()) {
                                    chest.setItem(slot, st.copy());
                                }
                            }
                        } else if (!pool.isEmpty()) {
                            int count = 2 + rnd.nextInt(4);
                            for (int c = 0; c < count; c++) {
                                ItemStack pick = pool.get(rnd.nextInt(pool.size())).copy();
                                int slot = rnd.nextInt(chest.getContainerSize());
                                chest.setItem(slot, pick);
                            }
                        }
                        chest.setChanged();
                    }
                }
            }
            return;
        }

        // Fallback: si no se registraron cofres manualmente con [C], escanear el área
        if (pool.isEmpty()) return;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    mpos.set(x, y, z);
                    BlockState state = level.getBlockState(mpos);
                    if (state.getBlock() instanceof ChestBlock) {
                        BlockPos immutablePos = mpos.immutable();
                        session.activeChests.add(immutablePos);
                        session.chestNumbers.put(immutablePos, chestIndex++);
                        session.chestLooters.put(immutablePos, new LinkedHashSet<>());

                        BlockEntity be = level.getBlockEntity(mpos);
                        if (be instanceof ChestBlockEntity chest && chest.isEmpty()) {
                            int count = 2 + rnd.nextInt(4);
                            for (int c = 0; c < count; c++) {
                                ItemStack pick = pool.get(rnd.nextInt(pool.size())).copy();
                                int slot = rnd.nextInt(chest.getContainerSize());
                                chest.setItem(slot, pick);
                            }
                            chest.setChanged();
                        }
                    }
                }
            }
        }
    }

    private static void broadcastToRegistered(MinecraftServer server, IncursionSession session, ModPackets.NotificationPayload payload) {
        for (UUID uuid : session.registeredParticipants) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                ServerPlayNetworking.send(sp, payload);
            }
        }
    }

    private static void clearHud(MinecraftServer server, IncursionSession session) {
        ModPackets.SyncIncursionStatusPayload clearPayload = new ModPackets.SyncIncursionStatusPayload(
                false,
                session.mission.getId(),
                session.mission.getTitle(),
                0,
                0,
                0,
                0,
                0,
                false,
                BlockPos.ZERO,
                false,
                0,
                0,
                null,
                ""
        );
        for (UUID uuid : session.registeredParticipants) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                ServerPlayNetworking.send(sp, clearPayload);
                ServerPlayNetworking.send(sp, new ModPackets.SetWaypointPayload(false, "", BlockPos.ZERO));
            }
        }
    }

    private static void syncHud(MinecraftServer server, IncursionSession session) {
        Mission m = session.mission;
        BlockPos extPos = m.getRoofPos() != null ? m.getRoofPos() :
                (m.getExtractionPos() != null ? m.getExtractionPos() : m.getTargetPos());
        int chestsCount = session.activeChests.size();
        int lootingSecs = Math.max(0, session.lootingTicks / 20);

        for (UUID uuid : session.registeredParticipants) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) continue;

            BlockPos objPos;
            String objTitle;

            if (session.state == IncursionState.WAITING_FOR_PLAYERS) {
                objPos = m.getExtractionPos() != null ? m.getExtractionPos() : m.getTargetPos();
                objTitle = "Punto de Inicio";
            } else if (session.state == IncursionState.ESCAPE_PHASE) {
                objPos = extPos;
                objTitle = "Salida";
            } else if (session.state == IncursionState.LOOTING_PHASE) {
                if (!session.activeChests.isEmpty()) {
                    objPos = session.activeChests.iterator().next();
                    objTitle = "Cofres de Botín";
                } else {
                    objPos = extPos;
                    objTitle = "Salida";
                }
            } else {
                // WAVE_ACTIVE o WAVE_COOLDOWN
                if (m.getRoutePoints() != null && session.currentRouteIndex < m.getRoutePoints().size()) {
                    objPos = m.getRoutePoints().get(session.currentRouteIndex);
                    objTitle = (m.getRoutePointNames() != null && session.currentRouteIndex < m.getRoutePointNames().size()) ?
                            m.getRoutePointNames().get(session.currentRouteIndex) : ("Checkpoint #" + (session.currentRouteIndex + 1));
                } else {
                    objPos = m.getTargetPos();
                    objTitle = "";
                }
            }

            ModPackets.SyncIncursionStatusPayload payload = new ModPackets.SyncIncursionStatusPayload(
                    true,
                    session.mission.getId(),
                    session.mission.getTitle(),
                    session.currentWave,
                    session.mission.getTotalWaves(),
                    session.activeWaveMobs.size(),
                    session.aliveParticipants.size(),
                    session.registeredParticipants.size(),
                    session.state == IncursionState.ESCAPE_PHASE,
                    extPos,
                    session.state == IncursionState.LOOTING_PHASE || session.state == IncursionState.ESCAPE_PHASE,
                    lootingSecs,
                    chestsCount,
                    objPos,
                    objTitle
            );

            ServerPlayNetworking.send(sp, payload);
        }
    }

    public static void clear() {
        for (IncursionSession s : sessions.values()) {
            s.cleanMobs();
        }
        sessions.clear();
    }
}
