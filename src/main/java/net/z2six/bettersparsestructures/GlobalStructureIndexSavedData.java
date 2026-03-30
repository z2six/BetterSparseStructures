package net.z2six.bettersparsestructures;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Map;
import java.util.WeakHashMap;

public final class GlobalStructureIndexSavedData extends SavedData {
    private static final int CELL_SIZE_CHUNKS = 32;
    private static final String DATA_NAME = Bettersparsestructures.MODID + "_global_structure_index";
    private static final String LEGACY_POSITIONS_TAG = "accepted_structure_chunks";
    private static final String STRUCTURES_TAG = "accepted_structures";
    private static final String CHUNK_TAG = "chunk";
    private static final String STRUCTURE_ID_TAG = "structure_id";
    private static final String LEGACY_UNKNOWN_STRUCTURE_ID = Bettersparsestructures.MODID + ":legacy_unknown";
    private static final Map<ServerLevel, GlobalStructureIndexSavedData> CACHE = new WeakHashMap<>();
    private static final Factory<GlobalStructureIndexSavedData> FACTORY = new Factory<>(
            GlobalStructureIndexSavedData::new,
            GlobalStructureIndexSavedData::load
    );

    private final Long2ObjectOpenHashMap<String> acceptedStructures = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongArrayList> acceptedStructuresByCell = new Long2ObjectOpenHashMap<>();

    private GlobalStructureIndexSavedData() {
    }

    private static GlobalStructureIndexSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GlobalStructureIndexSavedData data = new GlobalStructureIndexSavedData();
        if (tag.contains(STRUCTURES_TAG, Tag.TAG_LIST)) {
            ListTag entries = tag.getList(STRUCTURES_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                long chunk = entry.getLong(CHUNK_TAG);
                String structureId = entry.getString(STRUCTURE_ID_TAG);
                data.addAcceptedStructure(chunk, structureId.isBlank() ? LEGACY_UNKNOWN_STRUCTURE_ID : structureId);
            }
        } else {
            for (long chunk : tag.getLongArray(LEGACY_POSITIONS_TAG)) {
                data.addAcceptedStructure(chunk, LEGACY_UNKNOWN_STRUCTURE_ID);
            }
        }
        return data;
    }

    public static GlobalStructureIndexSavedData get(ServerLevel level) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(level, GlobalStructureIndexSavedData::loadFromStorage);
        }
    }

    private static GlobalStructureIndexSavedData loadFromStorage(ServerLevel level) {
        DimensionDataStorage dataStorage = level.getDataStorage();
        return dataStorage.computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized boolean tryAccept(ChunkPos candidateChunk, String candidateStructureId, StructureRuleSet rules) {
        int candidateRadius = rules.spacingRadiusChunks(candidateStructureId);
        int candidateCellX = cellCoordinate(candidateChunk.x);
        int candidateCellZ = cellCoordinate(candidateChunk.z);
        int cellRange = Math.max(0, Math.floorDiv(rules.maxSpacingRadiusChunks() + CELL_SIZE_CHUNKS - 1, CELL_SIZE_CHUNKS));

        for (int cellX = candidateCellX - cellRange; cellX <= candidateCellX + cellRange; cellX++) {
            for (int cellZ = candidateCellZ - cellRange; cellZ <= candidateCellZ + cellRange; cellZ++) {
                LongArrayList indexedChunks = this.acceptedStructuresByCell.get(cellKey(cellX, cellZ));
                if (indexedChunks == null) {
                    continue;
                }

                for (int index = 0; index < indexedChunks.size(); index++) {
                    long existingChunk = indexedChunks.getLong(index);
                    String existingStructureId = this.acceptedStructures.get(existingChunk);
                    if (existingStructureId == null) {
                        continue;
                    }

                    if (rules.isWhitelisted(existingStructureId) && !ServerConfig.countWhitelistedStructuresForSpacing()) {
                        continue;
                    }

                    int existingRadius = rules.spacingRadiusChunks(existingStructureId);
                    int requiredRadius = Math.max(candidateRadius, existingRadius);
                    long radiusSquared = (long) requiredRadius * requiredRadius;
                    long deltaX = (long) ChunkPos.getX(existingChunk) - candidateChunk.x;
                    long deltaZ = (long) ChunkPos.getZ(existingChunk) - candidateChunk.z;
                    long distanceSquared = deltaX * deltaX + deltaZ * deltaZ;

                    if (distanceSquared <= radiusSquared) {
                        return false;
                    }
                }
            }
        }

        long candidateChunkKey = candidateChunk.toLong();
        String previousStructureId = this.acceptedStructures.get(candidateChunkKey);
        if (candidateStructureId.equals(previousStructureId)) {
            return true;
        }

        addAcceptedStructure(candidateChunkKey, candidateStructureId);
        if (!candidateStructureId.equals(previousStructureId)) {
            this.setDirty();
        }

        return true;
    }

    public synchronized void rememberWhitelistedStructure(ChunkPos chunkPos, String structureId) {
        long chunkKey = chunkPos.toLong();
        String previousStructureId = this.acceptedStructures.get(chunkKey);
        if (structureId.equals(previousStructureId)) {
            return;
        }

        addAcceptedStructure(chunkKey, structureId);
        this.setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Long2ObjectMap.Entry<String> entry : this.acceptedStructures.long2ObjectEntrySet()) {
            CompoundTag structureTag = new CompoundTag();
            structureTag.putLong(CHUNK_TAG, entry.getLongKey());
            structureTag.putString(STRUCTURE_ID_TAG, entry.getValue());
            entries.add(structureTag);
        }
        tag.put(STRUCTURES_TAG, entries);
        return tag;
    }

    private void addAcceptedStructure(long chunkKey, String structureId) {
        String previousStructureId = this.acceptedStructures.put(chunkKey, structureId);
        if (previousStructureId != null) {
            return;
        }

        long cellKey = cellKey(cellCoordinate(ChunkPos.getX(chunkKey)), cellCoordinate(ChunkPos.getZ(chunkKey)));
        this.acceptedStructuresByCell.computeIfAbsent(cellKey, ignored -> new LongArrayList()).add(chunkKey);
    }

    private static int cellCoordinate(int chunkCoordinate) {
        return Math.floorDiv(chunkCoordinate, CELL_SIZE_CHUNKS);
    }

    private static long cellKey(int cellX, int cellZ) {
        return ChunkPos.asLong(cellX, cellZ);
    }
}
