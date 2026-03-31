package net.z2six.bettersparsestructures;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Map;
import java.util.WeakHashMap;

public final class GlobalStructureIndexSavedData extends SavedData {
    private static final int CELL_SIZE_CHUNKS = 32;
    private static final int CELL_SIZE_BLOCKS = CELL_SIZE_CHUNKS * 16;

    private static final String DATA_NAME = Bettersparsestructures.MODID + "_global_structure_index";
    private static final String LEGACY_POSITIONS_TAG = "accepted_structure_chunks";
    private static final String STRUCTURES_TAG = "accepted_structures";
    private static final String CHUNK_TAG = "chunk";
    private static final String STRUCTURE_ID_TAG = "structure_id";
    private static final String MIN_X_TAG = "min_x";
    private static final String MIN_Y_TAG = "min_y";
    private static final String MIN_Z_TAG = "min_z";
    private static final String MAX_X_TAG = "max_x";
    private static final String MAX_Y_TAG = "max_y";
    private static final String MAX_Z_TAG = "max_z";
    private static final String LEGACY_UNKNOWN_STRUCTURE_ID = Bettersparsestructures.MODID + ":legacy_unknown";

    private static final Map<ServerLevel, GlobalStructureIndexSavedData> CACHE = new WeakHashMap<>();
    private static final Factory<GlobalStructureIndexSavedData> FACTORY = new Factory<>(
            GlobalStructureIndexSavedData::new,
            GlobalStructureIndexSavedData::load
    );

    private final Long2ObjectOpenHashMap<StoredStructure> rememberedStructures = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongArrayList> rememberedStructuresByCell = new Long2ObjectOpenHashMap<>();

    private GlobalStructureIndexSavedData() {
    }

    private static GlobalStructureIndexSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GlobalStructureIndexSavedData data = new GlobalStructureIndexSavedData();
        if (tag.contains(STRUCTURES_TAG, Tag.TAG_LIST)) {
            ListTag entries = tag.getList(STRUCTURES_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                long chunkKey = entry.getLong(CHUNK_TAG);
                String structureId = entry.getString(STRUCTURE_ID_TAG);
                StoredBoundingBox boundingBox = readBoundingBox(entry);
                data.rememberStructure(chunkKey, new StoredStructure(structureId.isBlank() ? LEGACY_UNKNOWN_STRUCTURE_ID : structureId, boundingBox));
            }
        } else {
            for (long chunkKey : tag.getLongArray(LEGACY_POSITIONS_TAG)) {
                data.rememberStructure(chunkKey, new StoredStructure(LEGACY_UNKNOWN_STRUCTURE_ID, null));
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

    public synchronized boolean tryAllowStructure(
            ChunkPos candidateChunk,
            String candidateStructureId,
            BoundingBox candidateBoundingBox,
            StructureRuleSet rules,
            boolean whitelisted
    ) {
        return decideStructure(candidateChunk, candidateStructureId, candidateBoundingBox, rules, whitelisted).accepted();
    }

    public synchronized DecisionResult decideStructure(
            ChunkPos candidateChunk,
            String candidateStructureId,
            BoundingBox candidateBoundingBox,
            StructureRuleSet rules,
            boolean whitelisted
    ) {
        StoredStructure candidate = new StoredStructure(candidateStructureId, StoredBoundingBox.from(candidateBoundingBox));

        if (!ServerConfig.allowStructureOverlap() && overlapsExistingStructure(candidateChunk, candidate)) {
            return DecisionResult.rejected(RejectionReason.OVERLAP, RepetitionBiasResult.none());
        }

        RepetitionBiasResult repetitionBias = RepetitionBiasResult.none();
        if (!whitelisted) {
            if (ServerConfig.use3dBlockSpacing()) {
                if (violates3dSpacing(candidateChunk, candidate, rules)) {
                    return DecisionResult.rejected(RejectionReason.SPACING, RepetitionBiasResult.none());
                }
            } else if (violates2dSpacing(candidateChunk, candidate, rules)) {
                return DecisionResult.rejected(RejectionReason.SPACING, RepetitionBiasResult.none());
            }

            repetitionBias = repetitionBias(candidateChunk, candidate, rules);
            if (repetitionBias.rejected()) {
                return DecisionResult.rejected(RejectionReason.REPETITION, repetitionBias);
            }
        }

        if (shouldRememberStructure(whitelisted)) {
            long candidateChunkKey = candidateChunk.toLong();
            StoredStructure previous = this.rememberedStructures.get(candidateChunkKey);
            if (!candidate.equals(previous)) {
                rememberStructure(candidateChunkKey, candidate);
                this.setDirty();
            }
        }

        return DecisionResult.accepted(repetitionBias);
    }

    public static double hybridSizeScore(BoundingBox boundingBox) {
        return StoredBoundingBox.from(boundingBox).hybridSizeScore();
    }

    public static double sizeSpacingMultiplier(BoundingBox boundingBox) {
        return spacingMultiplier(new StoredStructure("candidate", StoredBoundingBox.from(boundingBox)));
    }

    public static String sizeClassName(BoundingBox boundingBox) {
        return sizeClass(new StoredStructure("candidate", StoredBoundingBox.from(boundingBox))).serializedName();
    }

    private boolean violates2dSpacing(ChunkPos candidateChunk, StoredStructure candidate, StructureRuleSet rules) {
        double candidateRadiusChunks = scaledSpacingChunks(rules.spacingRadiusChunks(candidate.structureId()), candidate);
        int maxRadiusBlocks = maxEffectiveSpacingBlocks(rules);
        int candidateBlockX = candidateChunk.getMinBlockX() + 8;
        int candidateBlockZ = candidateChunk.getMinBlockZ() + 8;
        LongOpenHashSet candidateKeys = collectCandidateKeys(
                candidateBlockX - maxRadiusBlocks,
                candidateBlockZ - maxRadiusBlocks,
                candidateBlockX + maxRadiusBlocks,
                candidateBlockZ + maxRadiusBlocks
        );

        for (long existingChunkKey : candidateKeys) {
            StoredStructure existing = this.rememberedStructures.get(existingChunkKey);
            if (existing == null) {
                continue;
            }

            if (isIgnoredForSpacing(existing.structureId(), rules)) {
                continue;
            }

            double existingRadiusChunks = scaledSpacingChunks(rules.spacingRadiusChunks(existing.structureId()), existing);
            double requiredRadiusChunks = Math.max(candidateRadiusChunks, existingRadiusChunks);
            long deltaX = (long) ChunkPos.getX(existingChunkKey) - candidateChunk.x;
            long deltaZ = (long) ChunkPos.getZ(existingChunkKey) - candidateChunk.z;
            long distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            double requiredRadiusSquared = requiredRadiusChunks * requiredRadiusChunks;

            if (distanceSquared <= requiredRadiusSquared) {
                return true;
            }
        }

        return false;
    }

    private boolean violates3dSpacing(ChunkPos candidateChunk, StoredStructure candidate, StructureRuleSet rules) {
        double candidateRadiusBlocks = scaledSpacingBlocks(rules.spacingRadiusChunks(candidate.structureId()), candidate);
        int maxRadiusBlocks = maxEffectiveSpacingBlocks(rules);
        StoredBoundingBox candidateBox = candidate.boundingBox();
        LongOpenHashSet candidateKeys = collectCandidateKeys(
                candidateBox.minX() - maxRadiusBlocks,
                candidateBox.minZ() - maxRadiusBlocks,
                candidateBox.maxX() + maxRadiusBlocks,
                candidateBox.maxZ() + maxRadiusBlocks
        );

        for (long existingChunkKey : candidateKeys) {
            StoredStructure existing = this.rememberedStructures.get(existingChunkKey);
            if (existing == null) {
                continue;
            }

            if (isIgnoredForSpacing(existing.structureId(), rules)) {
                continue;
            }

            double existingRadiusBlocks = scaledSpacingBlocks(rules.spacingRadiusChunks(existing.structureId()), existing);
            double requiredRadiusBlocks = Math.max(candidateRadiusBlocks, existingRadiusBlocks);
            double requiredRadiusSquared = requiredRadiusBlocks * requiredRadiusBlocks;

            if (existing.boundingBox() != null) {
                if (candidateBox.squaredDistanceTo(existing.boundingBox()) <= requiredRadiusSquared) {
                    return true;
                }
            } else {
                long distanceSquared = squaredHorizontalBlockDistance(candidateChunk, existingChunkKey);
                if (distanceSquared <= requiredRadiusSquared) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int maxEffectiveSpacingBlocks(StructureRuleSet rules) {
        double maxSpacingBlocks = rules.maxSpacingRadiusChunks() * 16.0D;
        if (ServerConfig.enableSizeScaledSpacing()) {
            maxSpacingBlocks *= ServerConfig.distanceModifier();
        }
        return (int) Math.ceil(maxSpacingBlocks);
    }

    private static double scaledSpacingChunks(int baseSpacingChunks, StoredStructure structure) {
        return baseSpacingChunks * spacingMultiplier(structure);
    }

    private static double scaledSpacingBlocks(int baseSpacingChunks, StoredStructure structure) {
        return baseSpacingChunks * 16.0D * spacingMultiplier(structure);
    }

    private static double spacingMultiplier(StoredStructure structure) {
        if (!ServerConfig.enableSizeScaledSpacing() || structure == null || structure.boundingBox() == null) {
            return 1.0D;
        }

        double minimumSize = ServerConfig.minimumSize();
        double maximumSize = ServerConfig.maximumSize();
        if (maximumSize <= minimumSize) {
            return 1.0D;
        }

        double sizeScore = structure.boundingBox().hybridSizeScore();
        double normalized = (sizeScore - minimumSize) / (maximumSize - minimumSize);
        normalized = Math.max(0.0D, Math.min(1.0D, normalized));

        double modifier = ServerConfig.distanceModifier();
        return Math.pow(modifier, normalized * 2.0D - 1.0D);
    }

    private RepetitionBiasResult repetitionBias(ChunkPos candidateChunk, StoredStructure candidate, StructureRuleSet rules) {
        if (!ServerConfig.enableRepetitionBias()) {
            return RepetitionBiasResult.none();
        }

        int radiusChunks = ServerConfig.repetitionBiasRadiusChunks();
        if (radiusChunks <= 0) {
            return RepetitionBiasResult.none();
        }

        SizeClass candidateSizeClass = sizeClass(candidate);
        double structureIdPressure = 0.0D;
        double sizeClassPressure = 0.0D;

        if (ServerConfig.use3dBlockSpacing()) {
            int radiusBlocks = radiusChunks * 16;
            StoredBoundingBox candidateBox = candidate.boundingBox();
            LongOpenHashSet candidateKeys = collectCandidateKeys(
                    candidateBox.minX() - radiusBlocks,
                    candidateBox.minZ() - radiusBlocks,
                    candidateBox.maxX() + radiusBlocks,
                    candidateBox.maxZ() + radiusBlocks
            );

            for (long existingChunkKey : candidateKeys) {
                StoredStructure existing = this.rememberedStructures.get(existingChunkKey);
                if (existing == null || isIgnoredForSpacing(existing.structureId(), rules)) {
                    continue;
                }

                double closeness = 0.0D;
                if (existing.boundingBox() != null) {
                    closeness = closeness(candidateBox.squaredDistanceTo(existing.boundingBox()), radiusBlocks);
                } else {
                    closeness = closeness(squaredHorizontalBlockDistance(candidateChunk, existingChunkKey), radiusBlocks);
                }

                if (closeness <= 0.0D) {
                    continue;
                }

                if (candidate.structureId().equals(existing.structureId())) {
                    structureIdPressure += closeness * ServerConfig.structureIdBiasWeight();
                }

                if (candidateSizeClass == sizeClass(existing)) {
                    sizeClassPressure += closeness * ServerConfig.sizeClassBiasWeight();
                }
            }
        } else {
            LongOpenHashSet candidateKeys = collectCandidateKeys(
                    candidateChunk.getMinBlockX() + 8 - radiusChunks * 16,
                    candidateChunk.getMinBlockZ() + 8 - radiusChunks * 16,
                    candidateChunk.getMinBlockX() + 8 + radiusChunks * 16,
                    candidateChunk.getMinBlockZ() + 8 + radiusChunks * 16
            );

            for (long existingChunkKey : candidateKeys) {
                StoredStructure existing = this.rememberedStructures.get(existingChunkKey);
                if (existing == null || isIgnoredForSpacing(existing.structureId(), rules)) {
                    continue;
                }

                long deltaX = (long) ChunkPos.getX(existingChunkKey) - candidateChunk.x;
                long deltaZ = (long) ChunkPos.getZ(existingChunkKey) - candidateChunk.z;
                double distanceChunks = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                double closeness = 1.0D - distanceChunks / radiusChunks;
                if (closeness <= 0.0D) {
                    continue;
                }

                if (candidate.structureId().equals(existing.structureId())) {
                    structureIdPressure += closeness * ServerConfig.structureIdBiasWeight();
                }

                if (candidateSizeClass == sizeClass(existing)) {
                    sizeClassPressure += closeness * ServerConfig.sizeClassBiasWeight();
                }
            }
        }

        double totalPressure = structureIdPressure + sizeClassPressure;
        return new RepetitionBiasResult(
                structureIdPressure,
                sizeClassPressure,
                totalPressure,
                ServerConfig.repetitionBiasThreshold(),
                candidateSizeClass,
                totalPressure >= ServerConfig.repetitionBiasThreshold()
        );
    }

    private static double closeness(long squaredDistance, int radiusBlocks) {
        double distance = Math.sqrt(squaredDistance);
        return 1.0D - distance / radiusBlocks;
    }

    private static SizeClass sizeClass(StoredStructure structure) {
        if (structure == null || structure.boundingBox() == null) {
            return SizeClass.UNKNOWN;
        }

        double minimumSize = ServerConfig.minimumSize();
        double maximumSize = ServerConfig.maximumSize();
        if (maximumSize <= minimumSize) {
            return SizeClass.MEDIUM;
        }

        double normalized = (structure.boundingBox().hybridSizeScore() - minimumSize) / (maximumSize - minimumSize);
        normalized = Math.max(0.0D, Math.min(1.0D, normalized));

        if (normalized <= 0.20D) {
            return SizeClass.TINY;
        }
        if (normalized <= 0.40D) {
            return SizeClass.SMALL;
        }
        if (normalized <= 0.60D) {
            return SizeClass.MEDIUM;
        }
        if (normalized <= 0.80D) {
            return SizeClass.LARGE;
        }
        return SizeClass.HUGE;
    }

    private boolean overlapsExistingStructure(ChunkPos candidateChunk, StoredStructure candidate) {
        StoredBoundingBox candidateBox = candidate.boundingBox();
        LongOpenHashSet candidateKeys = collectCandidateKeys(candidateBox.minX(), candidateBox.minZ(), candidateBox.maxX(), candidateBox.maxZ());

        for (long existingChunkKey : candidateKeys) {
            StoredStructure existing = this.rememberedStructures.get(existingChunkKey);
            if (existing == null || existing.boundingBox() == null) {
                continue;
            }

            if (candidateBox.overlaps(existing.boundingBox())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Long2ObjectMap.Entry<StoredStructure> entry : this.rememberedStructures.long2ObjectEntrySet()) {
            CompoundTag structureTag = new CompoundTag();
            structureTag.putLong(CHUNK_TAG, entry.getLongKey());
            structureTag.putString(STRUCTURE_ID_TAG, entry.getValue().structureId());
            if (entry.getValue().boundingBox() != null) {
                writeBoundingBox(structureTag, entry.getValue().boundingBox());
            }
            entries.add(structureTag);
        }
        tag.put(STRUCTURES_TAG, entries);
        return tag;
    }

    private boolean shouldRememberStructure(boolean whitelisted) {
        return !whitelisted || ServerConfig.countWhitelistedStructuresForSpacing() || !ServerConfig.allowStructureOverlap();
    }

    private boolean isIgnoredForSpacing(String structureId, StructureRuleSet rules) {
        return rules.isWhitelisted(structureId) && !ServerConfig.countWhitelistedStructuresForSpacing();
    }

    private void rememberStructure(long chunkKey, StoredStructure structure) {
        this.rememberedStructures.put(chunkKey, structure);
        LongOpenHashSet cellKeys = new LongOpenHashSet();
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        cellKeys.add(cellKeyForChunk(chunkX, chunkZ));

        if (structure.boundingBox() != null) {
            int minCellX = blockToCell(structure.boundingBox().minX());
            int maxCellX = blockToCell(structure.boundingBox().maxX());
            int minCellZ = blockToCell(structure.boundingBox().minZ());
            int maxCellZ = blockToCell(structure.boundingBox().maxZ());
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    cellKeys.add(cellKey(cellX, cellZ));
                }
            }
        }

        for (long cellKey : cellKeys) {
            LongArrayList indexedChunks = this.rememberedStructuresByCell.computeIfAbsent(cellKey, ignored -> new LongArrayList());
            if (!indexedChunks.contains(chunkKey)) {
                indexedChunks.add(chunkKey);
            }
        }
    }

    private LongOpenHashSet collectCandidateKeys(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int minCellX = blockToCell(minBlockX);
        int maxCellX = blockToCell(maxBlockX);
        int minCellZ = blockToCell(minBlockZ);
        int maxCellZ = blockToCell(maxBlockZ);
        LongOpenHashSet candidateKeys = new LongOpenHashSet();

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                LongArrayList indexedChunks = this.rememberedStructuresByCell.get(cellKey(cellX, cellZ));
                if (indexedChunks == null) {
                    continue;
                }

                for (int index = 0; index < indexedChunks.size(); index++) {
                    candidateKeys.add(indexedChunks.getLong(index));
                }
            }
        }

        return candidateKeys;
    }

    private static StoredBoundingBox readBoundingBox(CompoundTag tag) {
        if (!tag.contains(MIN_X_TAG, Tag.TAG_INT)) {
            return null;
        }

        return new StoredBoundingBox(
                tag.getInt(MIN_X_TAG),
                tag.getInt(MIN_Y_TAG),
                tag.getInt(MIN_Z_TAG),
                tag.getInt(MAX_X_TAG),
                tag.getInt(MAX_Y_TAG),
                tag.getInt(MAX_Z_TAG)
        );
    }

    private static void writeBoundingBox(CompoundTag tag, StoredBoundingBox boundingBox) {
        tag.putInt(MIN_X_TAG, boundingBox.minX());
        tag.putInt(MIN_Y_TAG, boundingBox.minY());
        tag.putInt(MIN_Z_TAG, boundingBox.minZ());
        tag.putInt(MAX_X_TAG, boundingBox.maxX());
        tag.putInt(MAX_Y_TAG, boundingBox.maxY());
        tag.putInt(MAX_Z_TAG, boundingBox.maxZ());
    }

    private static long squaredHorizontalBlockDistance(ChunkPos candidateChunk, long existingChunkKey) {
        long candidateBlockX = candidateChunk.getMinBlockX() + 8L;
        long candidateBlockZ = candidateChunk.getMinBlockZ() + 8L;
        long existingBlockX = ChunkPos.getX(existingChunkKey) * 16L + 8L;
        long existingBlockZ = ChunkPos.getZ(existingChunkKey) * 16L + 8L;
        long deltaX = existingBlockX - candidateBlockX;
        long deltaZ = existingBlockZ - candidateBlockZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static int blockToCell(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_SIZE_BLOCKS);
    }

    private static long cellKeyForChunk(int chunkX, int chunkZ) {
        return cellKey(Math.floorDiv(chunkX, CELL_SIZE_CHUNKS), Math.floorDiv(chunkZ, CELL_SIZE_CHUNKS));
    }

    private static long cellKey(int cellX, int cellZ) {
        return ChunkPos.asLong(cellX, cellZ);
    }

    private record StoredStructure(String structureId, StoredBoundingBox boundingBox) {
    }

    public record DecisionResult(boolean accepted, RejectionReason rejectionReason, RepetitionBiasResult repetitionBias) {
        public static DecisionResult accepted(RepetitionBiasResult repetitionBias) {
            return new DecisionResult(true, RejectionReason.NONE, repetitionBias);
        }

        public static DecisionResult rejected(RejectionReason rejectionReason, RepetitionBiasResult repetitionBias) {
            return new DecisionResult(false, rejectionReason, repetitionBias);
        }
    }

    public enum RejectionReason {
        NONE,
        OVERLAP,
        SPACING,
        REPETITION
    }

    public record RepetitionBiasResult(
            double structureIdPressure,
            double sizeClassPressure,
            double totalPressure,
            double threshold,
            SizeClass candidateSizeClass,
            boolean rejected
    ) {
        public static RepetitionBiasResult none() {
            return new RepetitionBiasResult(0.0D, 0.0D, 0.0D, ServerConfig.repetitionBiasThreshold(), SizeClass.UNKNOWN, false);
        }
    }

    public enum SizeClass {
        UNKNOWN("unknown"),
        TINY("tiny"),
        SMALL("small"),
        MEDIUM("medium"),
        LARGE("large"),
        HUGE("huge");

        private final String serializedName;

        SizeClass(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    private record StoredBoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static StoredBoundingBox from(BoundingBox boundingBox) {
            return new StoredBoundingBox(
                    boundingBox.minX(),
                    boundingBox.minY(),
                    boundingBox.minZ(),
                    boundingBox.maxX(),
                    boundingBox.maxY(),
                    boundingBox.maxZ()
            );
        }

        public boolean overlaps(StoredBoundingBox other) {
            return this.maxX >= other.minX
                    && this.minX <= other.maxX
                    && this.maxY >= other.minY
                    && this.minY <= other.maxY
                    && this.maxZ >= other.minZ
                    && this.minZ <= other.maxZ;
        }

        public long squaredDistanceTo(StoredBoundingBox other) {
            long deltaX = axisDistance(this.minX, this.maxX, other.minX, other.maxX);
            long deltaY = axisDistance(this.minY, this.maxY, other.minY, other.maxY);
            long deltaZ = axisDistance(this.minZ, this.maxZ, other.minZ, other.maxZ);
            return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        }

        public double hybridSizeScore() {
            double width = (double) this.maxX - this.minX + 1.0D;
            double height = (double) this.maxY - this.minY + 1.0D;
            double length = (double) this.maxZ - this.minZ + 1.0D;
            double footprint = width * length;
            return footprint * Math.sqrt(height);
        }

        private static long axisDistance(int minA, int maxA, int minB, int maxB) {
            if (maxA < minB) {
                return (long) minB - maxA;
            }

            if (maxB < minA) {
                return (long) minA - maxB;
            }

            return 0L;
        }
    }
}
