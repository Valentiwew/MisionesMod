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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class IncursionManager {

    public enum IncursionState {
        WAITING_FOR_PLAYERS,
        WAVE_ACTIVE,
        WAVE_COOLDOWN,
        ESCAPE_PHASE,
        COMPLETED
    }

    public static class IncursionSession {
        public final Mission mission;
        public IncursionState state = IncursionState.WAITING_FOR_PLAYERS;
        public int currentWave = 1;
        public int waveCooldownTicks = 0;
        public int cooldownTicks = 0;
        public boolean chestsPopulated = false;

        public final Set<UUID> registeredParticipants = new HashSet<>();
        public final Set<UUID> aliveParticipants = new HashSet<>();
        public final Set<UUID> fallenParticipants = new HashSet<>();
        public final List<Entity> activeWaveMobs = new ArrayList<>();

        public IncursionSession(Mission mission) {
            this.mission = mission;
        }

        public void reset() {
            state = IncursionState.WAITING_FOR_PLAYERS;
            currentWave = 1;
            waveCooldownTicks = 0;
            cooldownTicks = 0;
            chestsPopulated = false;
            registeredParticipants.clear();
            aliveParticipants.clear();
            fallenParticipants.clear();
            cleanMobs();
        }

        public void cleanMobs() {
            for (Entity e : activeWaveMobs) {
                if (e != null && e.isAlive()) {
                    e.discard();
                }
            }
            activeWaveMobs.clear();
        }
    }

    private static final Map<String, IncursionSession> sessions = new HashMap<>();

    public static synchronized void tick(MinecraftServer server) {
        if (server == null) return;

        List<Mission> missions = MissionManager.getMissions();
        for (Mission m : missions) {
            if (!"INCURSION".equalsIgnoreCase(m.getObjectiveType())) continue;

            IncursionSession session = sessions.computeIfAbsent(m.getId(), k -> new IncursionSession(m));
            ServerLevel level = server.getLevel(server.overworld().dimension());
            if (level == null) continue;

            BlockPos center = m.getTargetPos();
            if (center == null) continue;
            int radius = Math.max(24, m.getIncursionRadius());

            // Delimitar caja envolvente dinámica de todo el edificio
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

            // 1. Detectar jugadores en la zona envolvente del edificio
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

                    // Solo iniciar si hay jugadores en la zona que aún NO hayan completado esta misión
                    List<ServerPlayer> eligiblePlayers = new ArrayList<>();
                    for (ServerPlayer p : playersInZone) {
                        if (!m.isCompletedBy(p.getUUID())) {
                            eligiblePlayers.add(p);
                        }
                    }

                    if (!eligiblePlayers.isEmpty()) {
                        // Iniciar incursión
                        session.state = IncursionState.WAVE_ACTIVE;
                        session.currentWave = 1;
                        session.registeredParticipants.clear();
                        session.aliveParticipants.clear();
                        session.fallenParticipants.clear();

                        for (ServerPlayer p : eligiblePlayers) {
                            session.registeredParticipants.add(p.getUUID());
                            session.aliveParticipants.add(p.getUUID());
                        }

                        // Poblar cofres del edificio si hay botín personalizado
                        populateChests(level, minX, maxX, minY, maxY, minZ, maxZ, m);

                        // Spawn de la Oleada 1
                        spawnWaveMobs(level, session);

                        // Alerta y sonido de cuerno de asalto
                        level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 0.9f);
                        ModPackets.NotificationPayload notif = new ModPackets.NotificationPayload(
                                "§c§l[INCURSIÓN] §f¡Incursión iniciada! ¡Despeja la zona y sube hacia la azotea!",
                                0xFFEF4444
                        );
                        for (ServerPlayer p : eligiblePlayers) {
                            ServerPlayNetworking.send(p, notif);
                        }
                    }
                }

                case WAVE_ACTIVE -> {
                    // Actualizar estado de jugadores (vivos vs caídos)
                    updateParticipants(server, session, playersInZone);

                    // Si todos los participantes murieron (Team wipe)
                    if (session.aliveParticipants.isEmpty()) {
                        session.cleanMobs();
                        broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                "§c§l[INCURSIÓN FALLIDA] §fTodo el equipo ha caído. La incursión ha finalizado.",
                                0xFFEF4444
                        ));
                        clearHud(server, session);
                        session.state = IncursionState.COMPLETED;
                        session.cooldownTicks = 400; // 20 segundos
                        continue;
                    }

                    // Limpiar entidades muertas de la lista
                    session.activeWaveMobs.removeIf(e -> e == null || !e.isAlive());

                    // Si todos los mobs de la oleada fueron eliminados
                    if (session.activeWaveMobs.isEmpty()) {
                        if (session.currentWave < m.getTotalWaves()) {
                            session.state = IncursionState.WAVE_COOLDOWN;
                            session.waveCooldownTicks = 300; // 15 segundos de descanso
                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§a§l¡Piso Despejado! §fSube de nivel hacia la cima... Siguiente oleada en 15s",
                                    0xFF22C55E
                            ));
                        } else {
                            // Todas las oleadas completadas -> ¡Fase de Escape!
                            session.state = IncursionState.ESCAPE_PHASE;
                            level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1.0f);
                            broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                    "§e§l[¡ESCAPE!] §f¡Azotea despejada! ¡Baja deprisa a la Entrada para escapar con el botín!",
                                    0xFFF59E0B
                            ));
                        }
                    }
                }

                case WAVE_COOLDOWN -> {
                    updateParticipants(server, session, playersInZone);
                    session.waveCooldownTicks--;
                    if (session.waveCooldownTicks <= 0) {
                        session.currentWave++;
                        session.state = IncursionState.WAVE_ACTIVE;
                        spawnWaveMobs(level, session);

                        level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.4f);
                        boolean isFinalWave = session.currentWave == m.getTotalWaves();
                        String waveMsg = isFinalWave ?
                                "§4§l[CLÍMAX EN AZOTEA] §f¡Oleada Final! ¡Resiste y despeja la azotea!" :
                                "§c§l[INCURSIÓN] §f¡Oleada " + session.currentWave + "/" + m.getTotalWaves() + "! ¡Sigue subiendo!";
                        int waveColor = isFinalWave ? 0xFFDC2626 : 0xFFEF4444;

                        broadcastToRegistered(server, session, new ModPackets.NotificationPayload(waveMsg, waveColor));
                    }
                }

                case ESCAPE_PHASE -> {
                    updateParticipants(server, session, playersInZone);

                    if (session.aliveParticipants.isEmpty()) {
                        session.cleanMobs();
                        broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                "§c§l[INCURSIÓN FALLIDA] §fEl equipo cayó durante el escape.",
                                0xFFEF4444
                        ));
                        clearHud(server, session);
                        session.state = IncursionState.COMPLETED;
                        session.cooldownTicks = 400; // 20s
                        continue;
                    }

                    // Comprobar si algún participante vivo llegó a la zona de extracción
                    BlockPos extPos = m.getExtractionPos() != null ? m.getExtractionPos() : center;

                    // Partículas de señalización en el punto de extracción
                    if (server.getTickCount() % 15 == 0) {
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                extPos.getX() + 0.5, extPos.getY() + 1.2, extPos.getZ() + 0.5,
                                12, 0.5, 0.8, 0.5, 0.05);
                        level.sendParticles(ParticleTypes.END_ROD,
                                extPos.getX() + 0.5, extPos.getY() + 0.5, extPos.getZ() + 0.5,
                                6, 0.2, 1.2, 0.2, 0.02);
                    }

                    boolean escaped = false;
                    double extX = extPos.getX() + 0.5;
                    double extY = extPos.getY() + 0.5;
                    double extZ = extPos.getZ() + 0.5;

                    for (UUID uuid : session.aliveParticipants) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null) {
                            double dx = player.getX() - extX;
                            double dy = player.getY() - extY;
                            double dz = player.getZ() - extZ;
                            double hDistSq = dx * dx + dz * dz;
                            if (hDistSq <= 49.0 && Math.abs(dy) <= 6.0) { // Radio cómodo de 7 bloques
                                escaped = true;
                                break;
                            }
                        }
                    }

                    if (escaped) {
                        // ¡VICTORIA GRUPAL! Recompensar a todos los registrados (vivos y caídos)
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

                        level.playSound(null, extPos.getX(), extPos.getY(), extPos.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
                        broadcastToRegistered(server, session, new ModPackets.NotificationPayload(
                                "§6§l¡Incursión Completada! §a¡El equipo ha escapado con el botín!",
                                0xFF22C55E
                        ));

                        clearHud(server, session);
                        session.cleanMobs();
                        session.state = IncursionState.COMPLETED;
                        session.cooldownTicks = 1200; // 60 segundos antes de permitir cualquier reset
                    }
                }

                case COMPLETED -> {
                    if (session.cooldownTicks > 0) {
                        session.cooldownTicks--;
                    } else {
                        // Solo resetear a WAITING_FOR_PLAYERS cuando ya no quede ningún jugador en el edificio
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
                        entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                        if (entity instanceof Mob mob) {
                            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
                            mob.setPersistenceRequired();
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

    private static void populateChests(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Mission m) {
        List<String> lootIds = m.getBuildingChestLootIds();
        List<Integer> lootCounts = m.getBuildingChestLootCounts();
        if (lootIds.isEmpty()) return;

        List<ItemStack> pool = new ArrayList<>();
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
        if (pool.isEmpty()) return;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        RandomSource rnd = level.getRandom();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    mpos.set(x, y, z);
                    BlockState state = level.getBlockState(mpos);
                    if (state.getBlock() instanceof ChestBlock) {
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
                BlockPos.ZERO
        );
        for (UUID uuid : session.registeredParticipants) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                ServerPlayNetworking.send(sp, clearPayload);
            }
        }
    }

    private static void syncHud(MinecraftServer server, IncursionSession session) {
        BlockPos extPos = session.mission.getExtractionPos() != null ? session.mission.getExtractionPos() : session.mission.getTargetPos();
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
                extPos
        );

        for (UUID uuid : session.registeredParticipants) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                ServerPlayNetworking.send(sp, payload);
            }
        }
    }

    public static void clear() {
        for (IncursionSession s : sessions.values()) {
            s.cleanMobs();
        }
        sessions.clear();
    }
}
