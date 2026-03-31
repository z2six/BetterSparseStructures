package net.z2six.bettersparsestructures;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

public final class ServerConfig {
    public static final int MAX_SPACING_RADIUS_CHUNKS = 10_000;
    public static final double MAX_SIZE_SCALING_VALUE = 1_000_000_000_000D;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue GLOBAL_SPACING_RADIUS_CHUNKS = BUILDER
            .comment(
                    "Minimum spacing value used when no override matches.",
                    "In horizontal mode this is a chunk radius between structure starts.",
                    "In 3D mode this is converted to blocks using 16 blocks per chunk."
            )
            .defineInRange("globalSpacingRadiusChunks", 50, 0, MAX_SPACING_RADIUS_CHUNKS);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELISTED_STRUCTURES = BUILDER
            .comment(
                    "Structure ids or wildcard patterns that should bypass Better Sparse Structures entirely.",
                    "Whitelisted structures are never blocked by this mod.",
                    "Whether they also count as blockers for other structures is controlled by countWhitelistedStructuresForSpacing.",
                    "Examples:",
                    "  \"minecraft:*\"",
                    "  \"minecraft:pillager_outpost\"",
                    "  \"mystructures:ancient_*\""
            )
            .defineListAllowEmpty("whitelistedStructures", List.of(), ServerConfig::isStringEntry);

    private static final ForgeConfigSpec.BooleanValue COUNT_WHITELISTED_STRUCTURES_FOR_SPACING = BUILDER
            .comment("If true, whitelisted structures are still remembered as nearby blockers for other structures. They are still never blocked themselves.")
            .define("countWhitelistedStructuresForSpacing", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPACING_RADIUS_OVERRIDES = BUILDER
            .comment(
                    "Per-structure or per-namespace spacing overrides in the form \"pattern=radius\".",
                    "More specific matches win over broader ones. Later entries win ties.",
                    "Override values are in chunks, and in 3D mode each chunk is treated as 16 blocks.",
                    "Examples:",
                    "  \"minecraft:* = 24\"",
                    "  \"minecraft:pillager_outpost = 80\"",
                    "  \"mystructures:ancient_* = 128\""
            )
            .defineListAllowEmpty("spacingRadiusOverrides", List.of(), ServerConfig::isStringEntry);

    private static final ForgeConfigSpec.BooleanValue ENABLE_SIZE_SCALED_SPACING = BUILDER
            .comment(
                    "If true, spacing is scaled by realized structure size.",
                    "The size score uses the hybrid formula footprint * sqrt(height).",
                    "Small structures shrink spacing, large structures expand it."
            )
            .define("enableSizeScaledSpacing", false);

    private static final ForgeConfigSpec.DoubleValue MINIMUM_SIZE = BUILDER
            .comment("The hybrid size score that counts as the smallest structure for size-scaled spacing.")
            .defineInRange("minimumSize", 10.0D, 0.0D, MAX_SIZE_SCALING_VALUE);

    private static final ForgeConfigSpec.DoubleValue MAXIMUM_SIZE = BUILDER
            .comment("The hybrid size score that counts as the largest structure for size-scaled spacing.")
            .defineInRange("maximumSize", 1_000.0D, 0.0D, MAX_SIZE_SCALING_VALUE);

    private static final ForgeConfigSpec.DoubleValue DISTANCE_MODIFIER = BUILDER
            .comment(
                    "How strongly size scaling changes spacing.",
                    "A value of 10 means the smallest structures use 0.1x spacing, the midpoint uses 1x, and the largest use 10x."
            )
            .defineInRange("distanceModifier", 10.0D, 1.0D, 1_000.0D);

    private static final ForgeConfigSpec.BooleanValue USE_3D_BLOCK_SPACING = BUILDER
            .comment("If true, spacing is checked in 3D using full structure bounding boxes. Each spacing chunk is treated as 16 blocks.")
            .define("use3dBlockSpacing", false);

    private static final ForgeConfigSpec.BooleanValue ALLOW_STRUCTURE_OVERLAP = BUILDER
            .comment("If false, a new structure is rejected whenever its realized 3D bounding box overlaps any already-allowed structure, regardless of whitelisting.")
            .define("allowStructureOverlap", false);

    private static final ForgeConfigSpec.BooleanValue SEND_DEBUG_STRUCTURE_MARKERS = BUILDER
            .comment("Sends server-side structure attempt debug markers to modded clients that support them.")
            .define("sendDebugStructureMarkers", false);

    private static final ForgeConfigSpec.BooleanValue LOG_STRUCTURE_ATTEMPTS = BUILDER
            .comment("Logs accepted, rejected, and whitelisted structure attempts at INFO level.")
            .define("logStructureAttempts", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile StructureRuleSet structureRules = StructureRuleSet.empty(50);
    private static volatile boolean sendDebugStructureMarkers;
    private static volatile boolean logStructureAttempts = true;
    private static volatile boolean use3dBlockSpacing;
    private static volatile boolean allowStructureOverlap;
    private static volatile boolean enableSizeScaledSpacing;
    private static volatile double minimumSize = 10.0D;
    private static volatile double maximumSize = 1_000.0D;
    private static volatile double distanceModifier = 10.0D;

    private ServerConfig() {
    }

    public static StructureRuleSet structureRules() {
        return structureRules;
    }

    public static boolean countWhitelistedStructuresForSpacing() {
        return COUNT_WHITELISTED_STRUCTURES_FOR_SPACING.get();
    }

    public static boolean sendDebugStructureMarkers() {
        return sendDebugStructureMarkers;
    }

    public static boolean logStructureAttempts() {
        return logStructureAttempts;
    }

    public static boolean use3dBlockSpacing() {
        return use3dBlockSpacing;
    }

    public static boolean allowStructureOverlap() {
        return allowStructureOverlap;
    }

    public static boolean enableSizeScaledSpacing() {
        return enableSizeScaledSpacing;
    }

    public static double minimumSize() {
        return minimumSize;
    }

    public static double maximumSize() {
        return maximumSize;
    }

    public static double distanceModifier() {
        return distanceModifier;
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        apply(event);
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        apply(event);
    }

    private static void apply(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        boolean previousDebugMarkers = sendDebugStructureMarkers;
        structureRules = StructureRuleSet.create(
                GLOBAL_SPACING_RADIUS_CHUNKS.get(),
                WHITELISTED_STRUCTURES.get(),
                SPACING_RADIUS_OVERRIDES.get()
        );
        sendDebugStructureMarkers = SEND_DEBUG_STRUCTURE_MARKERS.get();
        logStructureAttempts = LOG_STRUCTURE_ATTEMPTS.get();
        enableSizeScaledSpacing = ENABLE_SIZE_SCALED_SPACING.get();
        minimumSize = MINIMUM_SIZE.get();
        maximumSize = Math.max(minimumSize, MAXIMUM_SIZE.get());
        distanceModifier = DISTANCE_MODIFIER.get();
        use3dBlockSpacing = USE_3D_BLOCK_SPACING.get();
        allowStructureOverlap = ALLOW_STRUCTURE_OVERLAP.get();

        Bettersparsestructures.LOGGER.info(
                "Loaded Better Sparse Structures server config: globalSpacingRadiusChunks={}, whitelistedStructures={}, countWhitelistedStructuresForSpacing={}, spacingRadiusOverrides={}, enableSizeScaledSpacing={}, minimumSize={}, maximumSize={}, distanceModifier={}, use3dBlockSpacing={}, allowStructureOverlap={}, sendDebugStructureMarkers={}, logStructureAttempts={}",
                structureRules.globalSpacingRadiusChunks(),
                structureRules.whitelistCount(),
                countWhitelistedStructuresForSpacing(),
                structureRules.overrideCount(),
                enableSizeScaledSpacing,
                minimumSize,
                maximumSize,
                distanceModifier,
                use3dBlockSpacing,
                allowStructureOverlap,
                sendDebugStructureMarkers,
                logStructureAttempts
        );

        DebugStructureMarkerService.onServerConfigChanged(previousDebugMarkers, sendDebugStructureMarkers);
    }

    private static boolean isStringEntry(Object value) {
        return value instanceof String;
    }
}
