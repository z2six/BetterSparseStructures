package net.z2six.bettersparsestructures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
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
        boolean accepted;
        boolean whitelisted = rules.isWhitelisted(structureId);

        if (whitelisted) {
            accepted = true;
        } else {
            accepted = GlobalStructureIndexSavedData.get(serverLevel)
                    .tryAccept(structureStart.getChunkPos(), structureId, rules);
        }

        if (ServerConfig.sendDebugStructureMarkers()) {
            Bettersparsestructures.LOGGER.info(
                    "{} structure attempt {} at chunk [{}, {}] in {}",
                    whitelisted ? "Whitelisted" : accepted ? "Accepted" : "Rejected",
                    structureId,
                    structureStart.getChunkPos().x,
                    structureStart.getChunkPos().z,
                    serverLevel.dimension().location()
            );
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
