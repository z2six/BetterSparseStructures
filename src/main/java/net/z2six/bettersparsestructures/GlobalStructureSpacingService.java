package net.z2six.bettersparsestructures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.z2six.bettersparsestructures.mixin.StructureManagerAccessor;
import net.z2six.bettersparsestructures.mixin.WorldGenRegionAccessor;

public final class GlobalStructureSpacingService {
    private GlobalStructureSpacingService() {
    }

    public static boolean tryAcceptStructure(StructureManager structureManager, StructureStart structureStart, String structureId) {
        LevelAccessor levelAccessor = ((StructureManagerAccessor) structureManager).bettersparsestructures$getLevel();
        ServerLevel serverLevel = resolveServerLevel(levelAccessor);
        if (serverLevel == null) {
            return true;
        }

        StructureRuleSet rules = ServerConfig.structureRules();
        BoundingBox boundingBox = structureStart.getBoundingBox();
        boolean accepted;
        boolean whitelisted = rules.isWhitelisted(structureId);

        accepted = GlobalStructureIndexSavedData.get(serverLevel)
                .tryAllowStructure(structureStart.getChunkPos(), structureId, boundingBox, rules, whitelisted);

        if (ServerConfig.logStructureAttempts()) {
            String decision = accepted ? (whitelisted ? "Whitelisted" : "Accepted") : "Rejected";
            double sizeScore = GlobalStructureIndexSavedData.hybridSizeScore(boundingBox);
            double sizeMultiplier = GlobalStructureIndexSavedData.sizeSpacingMultiplier(boundingBox);
            double baseSpacing = rules.spacingRadiusChunks(structureId);
            String effectiveSpacing = ServerConfig.use3dBlockSpacing()
                    ? String.format(java.util.Locale.ROOT, "%.2f blocks", baseSpacing * 16.0D * sizeMultiplier)
                    : String.format(java.util.Locale.ROOT, "%.2f chunks", baseSpacing * sizeMultiplier);
            Bettersparsestructures.LOGGER.info(
                    "{} structure attempt {} at chunk [{}, {}], y=[{}, {}], bbox=[({}, {}, {}) -> ({}, {}, {})], spacingMode={}, sizeScore={}, sizeMultiplier={}, effectiveSpacing={} in {}",
                    decision,
                    structureId,
                    structureStart.getChunkPos().x,
                    structureStart.getChunkPos().z,
                    boundingBox.minY(),
                    boundingBox.maxY(),
                    boundingBox.minX(),
                    boundingBox.minY(),
                    boundingBox.minZ(),
                    boundingBox.maxX(),
                    boundingBox.maxY(),
                    boundingBox.maxZ(),
                    ServerConfig.use3dBlockSpacing() ? "3d_blocks" : "horizontal_chunks",
                    String.format(java.util.Locale.ROOT, "%.2f", sizeScore),
                    String.format(java.util.Locale.ROOT, "%.3f", sizeMultiplier),
                    effectiveSpacing,
                    serverLevel.dimension().location()
            );
        }

        if (ServerConfig.sendDebugStructureMarkers()) {
            DebugStructureMarkerService.recordMarker(serverLevel, structureStart.getChunkPos(), structureId, accepted);
        }

        return accepted;
    }

    private static ServerLevel resolveServerLevel(LevelAccessor levelAccessor) {
        if (levelAccessor instanceof ServerLevel serverLevel) {
            return serverLevel;
        }

        if (levelAccessor instanceof WorldGenRegion worldGenRegion) {
            return ((WorldGenRegionAccessor) worldGenRegion).bettersparsestructures$getLevel();
        }

        return null;
    }
}
