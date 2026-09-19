package com.misionesmod.drop;

import com.misionesmod.MisionesMod;
import com.misionesmod.loot.LootConfig;
import com.misionesmod.network.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class DropManager {
    private static final List<ItemStack> customDropItems = new ArrayList<>();

    public static List<ItemStack> getCustomDropItems() {
        return new ArrayList<>(customDropItems);
    }

    public static synchronized void setCustomDropItems(List<ItemStack> items) {
        customDropItems.clear();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                customDropItems.add(s.copy());
            }
        }
    }

    public static void openDropLootEditor(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(27) {
            @Override
            public void setChanged() {
                super.setChanged();
                List<ItemStack> newItems = new ArrayList<>();
                for (int i = 0; i < getContainerSize(); i++) {
                    ItemStack stack = getItem(i);
                    if (!stack.isEmpty()) {
                        newItems.add(stack.copy());
                    }
                }
                setCustomDropItems(newItems);
            }
        };

        for (int i = 0; i < Math.min(27, customDropItems.size()); i++) {
            container.setItem(i, customDropItems.get(i).copy());
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) -> ChestMenu.threeRows(containerId, playerInventory, container),
                Component.literal("§6Editar Botín del Drop")
        ));
    }

    private static int dropCounter = 0;

    public static class PendingDrop {
        public final ServerLevel level;
        public final BlockPos landPos;
        public final String tier;
        public final String dropTitle;
        public double currentY;
        public final double targetY;
        public int ticksRemaining;
        public final int totalTicks;

        public PendingDrop(ServerLevel level, BlockPos landPos, String tier, String dropTitle, int descentTicks) {
            this.level = level;
            this.landPos = landPos;
            this.tier = tier;
            this.dropTitle = dropTitle;
            this.targetY = landPos.getY();
            this.currentY = landPos.getY() + 38.0;
            this.ticksRemaining = descentTicks;
            this.totalTicks = descentTicks;
        }
    }

    private static final List<PendingDrop> pendingDrops = new ArrayList<>();

    public static class ScheduledDrop {
        public final ServerLevel level;
        public final BlockPos targetPos;
        public final String tier;
        public final int intervalTicks;
        public int ticksRemaining;
        public final boolean recurring;

        public ScheduledDrop(ServerLevel level, BlockPos targetPos, String tier, int intervalTicks, boolean recurring) {
            this.level = level;
            this.targetPos = targetPos;
            this.tier = tier;
            this.intervalTicks = intervalTicks;
            this.ticksRemaining = intervalTicks;
            this.recurring = recurring;
        }
    }

    private static final List<ScheduledDrop> scheduledDrops = new ArrayList<>();

    public static synchronized void scheduleDrop(ServerLevel level, BlockPos pos, String tier, int delayMinutes, boolean recurring) {
        if (level == null) return;
        if (delayMinutes <= 0) {
            spawnDrop(level, pos, tier);
            return;
        }
        scheduledDrops.add(new ScheduledDrop(level, pos, tier, delayMinutes * 60 * 20, recurring));
        MisionesMod.LOGGER.info("Drop programado en {} minutos (recurrente: {})", delayMinutes, recurring);
    }

    public static synchronized void tickDrops(MinecraftServer server) {
        // 1. Temporizador de drops programados
        if (!scheduledDrops.isEmpty()) {
            java.util.Iterator<ScheduledDrop> schIt = scheduledDrops.iterator();
            while (schIt.hasNext()) {
                ScheduledDrop sd = schIt.next();
                sd.ticksRemaining--;
                if (sd.ticksRemaining <= 0) {
                    BlockPos dropPos = sd.targetPos;
                    if (dropPos == null || (dropPos.getX() == 0 && dropPos.getZ() == 0)) {
                        List<ServerPlayer> players = server.getPlayerList().getPlayers();
                        if (!players.isEmpty()) {
                            ServerPlayer randP = players.get(sd.level.getRandom().nextInt(players.size()));
                            double angle = sd.level.getRandom().nextDouble() * Math.PI * 2;
                            double dist = 25 + sd.level.getRandom().nextDouble() * 35;
                            int rx = (int) (randP.getX() + Math.cos(angle) * dist);
                            int rz = (int) (randP.getZ() + Math.sin(angle) * dist);
                            dropPos = new BlockPos(rx, 64, rz);
                        } else {
                            dropPos = new BlockPos(0, 64, 0);
                        }
                    }
                    spawnDrop(sd.level, dropPos, sd.tier);
                    if (sd.recurring) {
                        sd.ticksRemaining = sd.intervalTicks;
                    } else {
                        schIt.remove();
                    }
                }
            }
        }

        // 2. Descenso paulatino de drops en el aire
        if (!pendingDrops.isEmpty()) {
            java.util.Iterator<PendingDrop> it = pendingDrops.iterator();
            while (it.hasNext()) {
                PendingDrop drop = it.next();
                drop.ticksRemaining--;

                // Descenso paulatino
                double step = 38.0 / drop.totalTicks;
                drop.currentY = Math.max(drop.targetY, drop.currentY - step);

                // Partículas de estela de humo y llamarada descendiendo
                drop.level.sendParticles(
                        ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        drop.landPos.getX() + 0.5, drop.currentY, drop.landPos.getZ() + 0.5,
                        2,
                        0.15, 0.15, 0.15,
                        0.01
                );
                drop.level.sendParticles(
                        ParticleTypes.FLAME,
                        drop.landPos.getX() + 0.5, drop.currentY, drop.landPos.getZ() + 0.5,
                        1,
                        0.1, 0.1, 0.1,
                        0.02
                );

                // Sonido intermitente de bengala/cohete durante la caída
                if (drop.ticksRemaining % 25 == 0 && drop.ticksRemaining > 0) {
                    drop.level.playSound(
                            null,
                            drop.landPos.getX(), (int) drop.currentY, drop.landPos.getZ(),
                            SoundEvents.FIREWORK_ROCKET_LAUNCH,
                            SoundSource.BLOCKS,
                            1.5f, 0.7f
                    );
                }

                // Aterrizaje al llegar al suelo
                if (drop.ticksRemaining <= 0 || drop.currentY <= drop.targetY) {
                    it.remove();
                    landDrop(drop);
                }
            }
        }

        // 3. Chequear cofres activos para ver si fueron completamente vaciados o destruidos
        if (!activeDrops.isEmpty()) {
            java.util.Iterator<ActiveDrop> actIt = activeDrops.iterator();
            while (actIt.hasNext()) {
                ActiveDrop ad = actIt.next();
                if (ad.level == null || !ad.level.isLoaded(ad.pos)) continue;
                BlockEntity be = ad.level.getBlockEntity(ad.pos);
                boolean emptied = false;
                boolean destroyed = false;

                if (be instanceof ChestBlockEntity chest) {
                    if (chest.isEmpty()) {
                        emptied = true;
                    }
                } else {
                    // Ya no es un cofre (fue picado o destruido manualmente)
                    destroyed = true;
                }

                if (emptied || destroyed) {
                    actIt.remove();

                    if (emptied) {
                        // 1. Eliminar bloque de cofre sin soltar ítem
                        ad.level.removeBlock(ad.pos, false);
                    }

                    // 2. Animación de explosión estética (partículas y sonido) al vaciarse O al picarse
                    ad.level.sendParticles(
                            ParticleTypes.EXPLOSION_EMITTER,
                            ad.pos.getX() + 0.5, ad.pos.getY() + 0.5, ad.pos.getZ() + 0.5,
                            1, 0, 0, 0, 0
                    );
                    ad.level.sendParticles(
                            ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            ad.pos.getX() + 0.5, ad.pos.getY() + 0.5, ad.pos.getZ() + 0.5,
                            25, 0.4, 0.4, 0.4, 0.05
                    );
                    ad.level.playSound(
                            null,
                            ad.pos.getX(), ad.pos.getY(), ad.pos.getZ(),
                            SoundEvents.GENERIC_EXPLODE,
                            SoundSource.BLOCKS,
                            1.5f, 1.1f
                    );

                    // Encontrar jugador más cercano que looteó (radio de 16 bloques)
                    ServerPlayer looter = null;
                    double minDst = 256.0;
                    for (ServerPlayer p : ad.level.players()) {
                        double d2 = p.distanceToSqr(ad.pos.getX() + 0.5, ad.pos.getY() + 0.5, ad.pos.getZ() + 0.5);
                        if (d2 < minDst) {
                            minDst = d2;
                            looter = p;
                        }
                    }
                    String looterName = (looter != null) ? looter.getName().getString() : "un jugador";

                    // Anuncio sobre la hotbar sin chat
                    ModPackets.NotificationPayload lootedNotif = new ModPackets.NotificationPayload(
                            "§6El " + ad.dropTitle + " fue tomado por §e" + looterName,
                            0xFFF59E0B
                    );

                    // 3. Remover el marcador para todos los jugadores y anunciar en hotbar
                    ModPackets.SetWaypointPayload removePayload = new ModPackets.SetWaypointPayload(
                            false,
                            ad.dropTitle,
                            ad.pos
                    );
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(p, removePayload);
                        ServerPlayNetworking.send(p, lootedNotif);
                    }
                }
            }
        }
    }

    public static class ActiveDrop {
        public final ServerLevel level;
        public final BlockPos pos;
        public final String dropTitle;

        public ActiveDrop(ServerLevel level, BlockPos pos, String dropTitle) {
            this.level = level;
            this.pos = pos;
            this.dropTitle = dropTitle;
        }
    }

    private static final List<ActiveDrop> activeDrops = new ArrayList<>();

    private static void landDrop(PendingDrop drop) {
        ServerLevel level = drop.level;
        BlockPos landPos = drop.landPos;
        String resolvedTier = drop.tier;

        // 1. Sonido de explosión al aterrizar
        level.playSound(
                null,
                landPos.getX(), landPos.getY(), landPos.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS,
                5.0f,
                0.9f
        );

        // 2. Colocar cofre
        level.setBlockAndUpdate(landPos, Blocks.CHEST.defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(landPos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            if ("personalizado".equalsIgnoreCase(resolvedTier) && !customDropItems.isEmpty()) {
                chest.clearContent();
                for (int i = 0; i < Math.min(chest.getContainerSize(), customDropItems.size()); i++) {
                    chest.setItem(i, customDropItems.get(i).copy());
                }
                chest.setChanged();
            } else {
                LootConfig.populateChest(chest, resolvedTier, level.getRandom());
            }
        }

        activeDrops.add(new ActiveDrop(level, landPos, drop.dropTitle));

        // 3. Partículas de impacto en tierra y confeti inicial
        level.sendParticles(
                ParticleTypes.EXPLOSION,
                landPos.getX() + 0.5, landPos.getY() + 1.0, landPos.getZ() + 0.5,
                3,
                0.3, 0.3, 0.3,
                0.0
        );
        level.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                landPos.getX() + 0.5, landPos.getY() + 0.8, landPos.getZ() + 0.5,
                30,
                0.3, 0.8, 0.3,
                0.03
        );
        level.sendParticles(
                ParticleTypes.SMOKE,
                landPos.getX() + 0.5, landPos.getY() + 0.8, landPos.getZ() + 0.5,
                20,
                0.3, 0.5, 0.3,
                0.04
        );
        level.sendParticles(
                ParticleTypes.FIREWORK,
                landPos.getX() + 0.5, landPos.getY() + 1.2, landPos.getZ() + 0.5,
                20,
                0.4, 0.4, 0.4,
                0.08
        );

        MisionesMod.LOGGER.info("Suministro aéreo '{}' aterrizó en [{}, {}, {}]", drop.dropTitle, landPos.getX(), landPos.getY(), landPos.getZ());
    }

    public static synchronized void spawnDrop(ServerLevel level, BlockPos targetPos, String tier) {
        if (level == null || targetPos == null) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;

        // Determinar bloque de superficie real (sin quedar flotando sobre nieve o pasto)
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, targetPos.getX(), targetPos.getZ());
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(targetPos.getX(), surfaceY, targetPos.getZ());
        while (checkPos.getY() > level.getMinY()) {
            net.minecraft.world.level.block.state.BlockState current = level.getBlockState(checkPos);
            net.minecraft.world.level.block.state.BlockState below = level.getBlockState(checkPos.below());
            if (current.isAir() || current.canBeReplaced() || current.is(Blocks.SNOW)) {
                if (!below.isAir() && !below.canBeReplaced() && !below.is(Blocks.SNOW)) {
                    // El bloque inferior es sólido real. Colocar cofre en checkPos
                    break;
                }
                checkPos.move(net.minecraft.core.Direction.DOWN);
            } else {
                break;
            }
        }
        BlockPos landPos = checkPos.immutable();

        String resolvedTier = (tier == null || tier.isBlank()) ? "raro" : tier.toLowerCase();
        int dropNum = ++dropCounter;
        String dropTitle = "Drop #" + dropNum; // Total misterio, sin revelar tier

        // 1. Enviar notificación sobre la hotbar con coordenadas (sin chat ni palabra AirDrop)
        ModPackets.NotificationPayload dropNotif = new ModPackets.NotificationPayload(
                "§6Un Drop caerá en §eX: " + landPos.getX() + ", Z: " + landPos.getZ(),
                0xFFF59E0B
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, dropNotif);
        }

        // 2. Transmitir waypoint a todos los jugadores
        ModPackets.SetWaypointPayload waypointPayload = new ModPackets.SetWaypointPayload(
                true,
                dropTitle,
                landPos
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, waypointPayload);
        }

        // 3. Iniciar descenso paulatino (160 ticks = 8 segundos)
        pendingDrops.add(new PendingDrop(level, landPos, resolvedTier, dropTitle, 160));
        MisionesMod.LOGGER.info("Suministro aéreo '{}' iniciando descenso hacia [{}, {}, {}]", dropTitle, landPos.getX(), landPos.getY(), landPos.getZ());
    }
}
