package com.misionesmod.network;

import com.misionesmod.mission.Mission;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ModPackets {

    // 1. SyncMissionsPayload (S2C)
    public record SyncMissionsPayload(List<Mission> missions) implements CustomPacketPayload {
        public static final Type<SyncMissionsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "sync_missions"));
        public static final StreamCodec<FriendlyByteBuf, SyncMissionsPayload> CODEC = CustomPacketPayload.codec(
                SyncMissionsPayload::write,
                SyncMissionsPayload::new
        );

        public SyncMissionsPayload(FriendlyByteBuf buf) {
            this(readMissions(buf));
        }

        private static List<Mission> readMissions(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<Mission> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(Mission.readFromBuf(buf));
            }
            return list;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(missions.size());
            for (Mission m : missions) {
                m.writeToBuf(buf);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 2. SetWaypointPayload (S2C)
    public record SetWaypointPayload(boolean active, String title, BlockPos pos) implements CustomPacketPayload {
        public static final Type<SetWaypointPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "set_waypoint"));
        public static final StreamCodec<FriendlyByteBuf, SetWaypointPayload> CODEC = CustomPacketPayload.codec(
                SetWaypointPayload::write,
                SetWaypointPayload::new
        );

        public SetWaypointPayload(FriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readUtf(), buf.readBoolean() ? buf.readBlockPos() : null);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeUtf(title != null ? title : "");
            boolean hasPos = pos != null;
            buf.writeBoolean(hasPos);
            if (hasPos) {
                buf.writeBlockPos(pos);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 3. CreateMissionPayload (C2S)
    public record CreateMissionPayload(
            String id, String title, String description, BlockPos pos, String tier, String rewardDesc,
            String objectiveType, String requiredItemId, int requiredCount, List<String> itemIds, List<Integer> counts,
            BlockPos extractionPos, BlockPos roofPos, int incursionRadius, List<BlockPos> spawnPoints,
            List<String> mobTypes, int totalWaves, List<String> chestLootIds, List<Integer> chestLootCounts,
            List<BlockPos> chestPoints,
            List<BlockPos> routePoints,
            List<String> routePointNames,
            List<BlockPos> customChestPositions,
            List<String> customChestLootPack
    ) implements CustomPacketPayload {
        public static final Type<CreateMissionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "create_mission"));
        public static final StreamCodec<FriendlyByteBuf, CreateMissionPayload> CODEC = CustomPacketPayload.codec(
                CreateMissionPayload::write,
                CreateMissionPayload::new
        );

        public CreateMissionPayload(FriendlyByteBuf buf) {
            this(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean() ? buf.readBlockPos() : null,
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    readStringList(buf),
                    readIntList(buf),
                    buf.readBoolean() ? buf.readBlockPos() : null,
                    buf.readBoolean() ? buf.readBlockPos() : null,
                    buf.readVarInt(),
                    readBlockPosList(buf),
                    readStringList(buf),
                    buf.readVarInt(),
                    readStringList(buf),
                    readIntList(buf),
                    readBlockPosList(buf),
                    readBlockPosList(buf),
                    readStringList(buf),
                    readBlockPosList(buf),
                    readStringList(buf)
            );
        }

        private static List<String> readStringList(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) list.add(buf.readUtf());
            return list;
        }

        private static List<Integer> readIntList(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Integer> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) list.add(buf.readVarInt());
            return list;
        }

        private static List<BlockPos> readBlockPosList(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<BlockPos> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) list.add(buf.readBlockPos());
            return list;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id != null ? id : "");
            buf.writeUtf(title != null ? title : "");
            buf.writeUtf(description != null ? description : "");
            boolean hasPos = pos != null;
            buf.writeBoolean(hasPos);
            if (hasPos) {
                buf.writeBlockPos(pos);
            }
            buf.writeUtf(tier != null ? tier : "comun");
            buf.writeUtf(rewardDesc != null ? rewardDesc : "");
            buf.writeUtf(objectiveType != null ? objectiveType : "EXPLORACION");
            buf.writeUtf(requiredItemId != null ? requiredItemId : "");
            buf.writeVarInt(requiredCount);

            buf.writeVarInt(itemIds != null ? itemIds.size() : 0);
            if (itemIds != null) {
                for (String itemId : itemIds) buf.writeUtf(itemId);
            }

            buf.writeVarInt(counts != null ? counts.size() : 0);
            if (counts != null) {
                for (int count : counts) buf.writeVarInt(count);
            }

            boolean hasExt = extractionPos != null;
            buf.writeBoolean(hasExt);
            if (hasExt) buf.writeBlockPos(extractionPos);

            boolean hasRoof = roofPos != null;
            buf.writeBoolean(hasRoof);
            if (hasRoof) buf.writeBlockPos(roofPos);

            buf.writeVarInt(incursionRadius);

            buf.writeVarInt(spawnPoints != null ? spawnPoints.size() : 0);
            if (spawnPoints != null) {
                for (BlockPos p : spawnPoints) buf.writeBlockPos(p);
            }

            buf.writeVarInt(mobTypes != null ? mobTypes.size() : 0);
            if (mobTypes != null) {
                for (String m : mobTypes) buf.writeUtf(m != null ? m : "");
            }

            buf.writeVarInt(totalWaves);

            buf.writeVarInt(chestLootIds != null ? chestLootIds.size() : 0);
            if (chestLootIds != null) {
                for (String s : chestLootIds) buf.writeUtf(s != null ? s : "");
            }

            buf.writeVarInt(chestLootCounts != null ? chestLootCounts.size() : 0);
            if (chestLootCounts != null) {
                for (int c : chestLootCounts) buf.writeVarInt(c);
            }

            buf.writeVarInt(chestPoints != null ? chestPoints.size() : 0);
            if (chestPoints != null) {
                for (BlockPos p : chestPoints) buf.writeBlockPos(p);
            }

            buf.writeVarInt(routePoints != null ? routePoints.size() : 0);
            if (routePoints != null) {
                for (BlockPos p : routePoints) buf.writeBlockPos(p);
            }

            buf.writeVarInt(routePointNames != null ? routePointNames.size() : 0);
            if (routePointNames != null) {
                for (String s : routePointNames) buf.writeUtf(s != null ? s : "");
            }

            buf.writeVarInt(customChestPositions != null ? customChestPositions.size() : 0);
            if (customChestPositions != null) {
                for (BlockPos p : customChestPositions) buf.writeBlockPos(p);
            }

            buf.writeVarInt(customChestLootPack != null ? customChestLootPack.size() : 0);
            if (customChestLootPack != null) {
                for (String s : customChestLootPack) buf.writeUtf(s != null ? s : "");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 4. ClaimMissionPayload (C2S)
    public record ClaimMissionPayload(String missionId) implements CustomPacketPayload {
        public static final Type<ClaimMissionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "claim_mission"));
        public static final StreamCodec<FriendlyByteBuf, ClaimMissionPayload> CODEC = CustomPacketPayload.codec(
                ClaimMissionPayload::write,
                ClaimMissionPayload::new
        );

        public ClaimMissionPayload(FriendlyByteBuf buf) {
            this(buf.readUtf());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(missionId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 5. TriggerDropPayload (C2S)
    public record TriggerDropPayload(BlockPos pos, String tier, int delayMinutes, boolean recurring) implements CustomPacketPayload {
        public static final Type<TriggerDropPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "trigger_drop"));
        public static final StreamCodec<FriendlyByteBuf, TriggerDropPayload> CODEC = CustomPacketPayload.codec(
                TriggerDropPayload::write,
                TriggerDropPayload::new
        );

        public TriggerDropPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBoolean() ? buf.readBlockPos() : null,
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean()
            );
        }

        public void write(FriendlyByteBuf buf) {
            boolean hasPos = pos != null;
            buf.writeBoolean(hasPos);
            if (hasPos) {
                buf.writeBlockPos(pos);
            }
            buf.writeUtf(tier != null ? tier : "raro");
            buf.writeVarInt(delayMinutes);
            buf.writeBoolean(recurring);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 6. DeleteMissionPayload (C2S)
    public record DeleteMissionPayload(String missionId) implements CustomPacketPayload {
        public static final Type<DeleteMissionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "delete_mission"));
        public static final StreamCodec<FriendlyByteBuf, DeleteMissionPayload> CODEC = CustomPacketPayload.codec(
                DeleteMissionPayload::write,
                DeleteMissionPayload::new
        );

        public DeleteMissionPayload(FriendlyByteBuf buf) {
            this(buf.readUtf());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(missionId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 7. OpenLootEditorPayload (C2S)
    public record OpenLootEditorPayload(String targetId) implements CustomPacketPayload {
        public static final Type<OpenLootEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "open_loot_editor"));
        public static final StreamCodec<FriendlyByteBuf, OpenLootEditorPayload> CODEC = CustomPacketPayload.codec(
                OpenLootEditorPayload::write,
                OpenLootEditorPayload::new
        );

        public OpenLootEditorPayload(FriendlyByteBuf buf) {
            this(buf.readUtf());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(targetId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 8. SaveCustomLootPayload (C2S)
    public record SaveCustomLootPayload(String targetId, List<String> itemIds, List<Integer> counts) implements CustomPacketPayload {
        public static final Type<SaveCustomLootPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "save_custom_loot"));
        public static final StreamCodec<FriendlyByteBuf, SaveCustomLootPayload> CODEC = CustomPacketPayload.codec(
                SaveCustomLootPayload::write,
                SaveCustomLootPayload::new
        );

        public SaveCustomLootPayload(FriendlyByteBuf buf) {
            this(buf.readUtf(), readStringList(buf), readIntList(buf));
        }

        private static List<String> readStringList(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(buf.readUtf());
            }
            return list;
        }

        private static List<Integer> readIntList(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Integer> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(buf.readVarInt());
            }
            return list;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(targetId);
            buf.writeVarInt(itemIds.size());
            for (String s : itemIds) {
                buf.writeUtf(s);
            }
            buf.writeVarInt(counts.size());
            for (int c : counts) {
                buf.writeVarInt(c);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 9. SyncCraftableItemsPayload (S2C)
    public record SyncCraftableItemsPayload(List<String> craftableItemIds, List<String> smeltableItemIds) implements CustomPacketPayload {
        public static final Type<SyncCraftableItemsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "sync_craftable_items"));
        public static final StreamCodec<FriendlyByteBuf, SyncCraftableItemsPayload> CODEC = CustomPacketPayload.codec(
                SyncCraftableItemsPayload::write,
                SyncCraftableItemsPayload::new
        );

        public SyncCraftableItemsPayload(FriendlyByteBuf buf) {
            this(readList(buf), readList(buf));
        }

        private static List<String> readList(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(buf.readUtf());
            }
            return list;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(craftableItemIds != null ? craftableItemIds.size() : 0);
            if (craftableItemIds != null) {
                for (String s : craftableItemIds) {
                    buf.writeUtf(s);
                }
            }
            buf.writeVarInt(smeltableItemIds != null ? smeltableItemIds.size() : 0);
            if (smeltableItemIds != null) {
                for (String s : smeltableItemIds) {
                    buf.writeUtf(s);
                }
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 10. NotificationPayload (S2C)
    public record NotificationPayload(String text, int color) implements CustomPacketPayload {
        public static final Type<NotificationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "notification"));
        public static final StreamCodec<FriendlyByteBuf, NotificationPayload> CODEC = CustomPacketPayload.codec(
                NotificationPayload::write,
                NotificationPayload::new
        );

        public NotificationPayload(FriendlyByteBuf buf) {
            this(buf.readUtf(), buf.readInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(text != null ? text : "");
            buf.writeInt(color);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 11. SyncIncursionStatusPayload (S2C)
    public record SyncIncursionStatusPayload(
            boolean active,
            String missionId,
            String missionTitle,
            int currentWave,
            int totalWaves,
            int remainingEnemies,
            int alivePlayers,
            int totalPlayers,
            boolean isEscapePhase,
            BlockPos extractionPos,
            boolean isLootingPhase,
            int lootingSeconds,
            int chestsCount,
            BlockPos currentObjectivePos,
            String currentObjectiveTitle
    ) implements CustomPacketPayload {
        public static final Type<SyncIncursionStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("misionesmod", "sync_incursion_status"));
        public static final StreamCodec<FriendlyByteBuf, SyncIncursionStatusPayload> CODEC = CustomPacketPayload.codec(
                SyncIncursionStatusPayload::write,
                SyncIncursionStatusPayload::new
        );

        public SyncIncursionStatusPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean() ? buf.readBlockPos() : null,
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean() ? buf.readBlockPos() : null,
                    buf.readUtf()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeUtf(missionId != null ? missionId : "");
            buf.writeUtf(missionTitle != null ? missionTitle : "");
            buf.writeVarInt(currentWave);
            buf.writeVarInt(totalWaves);
            buf.writeVarInt(remainingEnemies);
            buf.writeVarInt(alivePlayers);
            buf.writeVarInt(totalPlayers);
            buf.writeBoolean(isEscapePhase);
            boolean hasExt = extractionPos != null;
            buf.writeBoolean(hasExt);
            if (hasExt) {
                buf.writeBlockPos(extractionPos);
            }
            buf.writeBoolean(isLootingPhase);
            buf.writeVarInt(lootingSeconds);
            buf.writeVarInt(chestsCount);
            boolean hasObj = currentObjectivePos != null;
            buf.writeBoolean(hasObj);
            if (hasObj) {
                buf.writeBlockPos(currentObjectivePos);
            }
            buf.writeUtf(currentObjectiveTitle != null ? currentObjectiveTitle : "");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerCommonPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(SyncMissionsPayload.TYPE, SyncMissionsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SetWaypointPayload.TYPE, SetWaypointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncCraftableItemsPayload.TYPE, SyncCraftableItemsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NotificationPayload.TYPE, NotificationPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncIncursionStatusPayload.TYPE, SyncIncursionStatusPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(CreateMissionPayload.TYPE, CreateMissionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClaimMissionPayload.TYPE, ClaimMissionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TriggerDropPayload.TYPE, TriggerDropPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DeleteMissionPayload.TYPE, DeleteMissionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenLootEditorPayload.TYPE, OpenLootEditorPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveCustomLootPayload.TYPE, SaveCustomLootPayload.CODEC);
    }
}
