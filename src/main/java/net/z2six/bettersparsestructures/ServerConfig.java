package net.z2six.bettersparsestructures;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ServerConfig {
    public static final int MAX_SPACING_RADIUS_CHUNKS = 10_000;
    public static final double MAX_SIZE_SCALING_VALUE = 1_000_000_000_000D;
    public static final double MAX_REPETITION_BIAS_WEIGHT = 100.0D;
    public static final double MAX_REPETITION_BIAS_THRESHOLD = 1_000.0D;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue GLOBAL_SPACING_RADIUS_CHUNKS = BUILDER
            .comment(
                    "Minimum spacing value used when no override matches.",
                    "In horizontal mode this is a chunk radius between structure starts.",
                    "In 3D mode this is converted to blocks using 16 blocks per chunk."
            )
            .defineInRange("globalSpacingRadiusChunks", 50, 0, MAX_SPACING_RADIUS_CHUNKS);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELISTED_STRUCTURES = BUILDER
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

    private static final ModConfigSpec.BooleanValue COUNT_WHITELISTED_STRUCTURES_FOR_SPACING = BUILDER
            .comment("If true, whitelisted structures are still remembered as nearby blockers for other structures. They are still never blocked themselves.")
            .define("countWhitelistedStructuresForSpacing", true);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> SPACING_RADIUS_OVERRIDES = BUILDER
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

    private static final ModConfigSpec.BooleanValue ENABLE_SIZE_SCALED_SPACING = BUILDER
            .comment(
                    "If true, spacing is scaled by realized structure size.",
                    "The size score uses the hybrid formula footprint * sqrt(height).",
                    "Small structures shrink spacing, large structures expand it."
            )
            .define("enableSizeScaledSpacing", false);

    private static final ModConfigSpec.DoubleValue MINIMUM_SIZE = BUILDER
            .comment("The hybrid size score that counts as the smallest structure for size-scaled spacing.")
            .defineInRange("minimumSize", 10.0D, 0.0D, MAX_SIZE_SCALING_VALUE);

    private static final ModConfigSpec.DoubleValue MAXIMUM_SIZE = BUILDER
            .comment("The hybrid size score that counts as the largest structure for size-scaled spacing.")
            .defineInRange("maximumSize", 1_000.0D, 0.0D, MAX_SIZE_SCALING_VALUE);

    private static final ModConfigSpec.DoubleValue DISTANCE_MODIFIER = BUILDER
            .comment(
                    "How strongly size scaling changes spacing.",
                    "A value of 10 means the smallest structures use 0.1x spacing, the midpoint uses 1x, and the largest use 10x."
            )
            .defineInRange("distanceModifier", 10.0D, 1.0D, 1_000.0D);

    private static final ModConfigSpec.BooleanValue ENABLE_REPETITION_BIAS = BUILDER
            .comment(
                    "If true, repeated nearby structure ids and size classes build up pressure against generating the same kinds of structures again.",
                    "This helps large modpacks avoid the same small or repeated structures winning every time.",
                    "Whitelisted structures are still never blocked by this bias."
            )
            .define("enableRepetitionBias", false);

    private static final ModConfigSpec.IntValue REPETITION_BIAS_RADIUS_CHUNKS = BUILDER
            .comment(
                    "How far Better Sparse Structures looks for recently accepted nearby structures when calculating repetition bias.",
                    "Higher values make the anti-repetition effect more regional.",
                    "In 3D mode this is converted to blocks using 16 blocks per chunk."
            )
            .defineInRange("repetitionBiasRadiusChunks", 24, 1, MAX_SPACING_RADIUS_CHUNKS);

    private static final ModConfigSpec.DoubleValue STRUCTURE_ID_BIAS_WEIGHT = BUILDER
            .comment(
                    "How strongly nearby accepted structures with the exact same structure id add repetition bias.",
                    "Higher values make repeated exact structures much more likely to be rejected."
            )
            .defineInRange("structureIdBiasWeight", 1.0D, 0.0D, MAX_REPETITION_BIAS_WEIGHT);

    private static final ModConfigSpec.DoubleValue SIZE_CLASS_BIAS_WEIGHT = BUILDER
            .comment(
                    "How strongly nearby accepted structures in the same size class add repetition bias.",
                    "This is usually weaker than structureIdBiasWeight so exact repeats matter more than just being similarly sized."
            )
            .defineInRange("sizeClassBiasWeight", 0.35D, 0.0D, MAX_REPETITION_BIAS_WEIGHT);

    private static final ModConfigSpec.DoubleValue REPETITION_BIAS_THRESHOLD = BUILDER
            .comment(
                    "If a candidate's local repetition bias reaches this value, the structure is rejected.",
                    "Lower values are stricter. Higher values allow more repeats before rejection."
            )
            .defineInRange("repetitionBiasThreshold", 2.5D, 0.0D, MAX_REPETITION_BIAS_THRESHOLD);

    private static final ModConfigSpec.BooleanValue USE_3D_BLOCK_SPACING = BUILDER
            .comment("If true, spacing is checked in 3D using full structure bounding boxes. Each spacing chunk is treated as 16 blocks.")
            .define("use3dBlockSpacing", false);

    private static final ModConfigSpec.BooleanValue ALLOW_STRUCTURE_OVERLAP = BUILDER
            .comment("If false, a new structure is rejected whenever its realized 3D bounding box overlaps any already-allowed structure, regardless of whitelisting.")
            .define("allowStructureOverlap", false);

    private static final ModConfigSpec.BooleanValue SEND_DEBUG_STRUCTURE_MARKERS = BUILDER
            .comment("Sends server-side structure attempt debug markers to modded clients that support them.")
            .define("sendDebugStructureMarkers", false);

    private static final ModConfigSpec.BooleanValue LOG_STRUCTURE_ATTEMPTS = BUILDER
            .comment("Logs accepted, rejected, and whitelisted structure attempts at INFO level.")
            .define("logStructureAttempts", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile StructureRuleSet structureRules = StructureRuleSet.empty(50);
    private static volatile boolean sendDebugStructureMarkers;
    private static volatile boolean logStructureAttempts = true;
    private static volatile boolean use3dBlockSpacing;
    private static volatile boolean allowStructureOverlap;
    private static volatile boolean enableSizeScaledSpacing;
    private static volatile double minimumSize = 10.0D;
    private static volatile double maximumSize = 1_000.0D;
    private static volatile double distanceModifier = 10.0D;
    private static volatile boolean enableRepetitionBias;
    private static volatile int repetitionBiasRadiusChunks = 24;
    private static volatile double structureIdBiasWeight = 1.0D;
    private static volatile double sizeClassBiasWeight = 0.35D;
    private static volatile double repetitionBiasThreshold = 2.5D;

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

    public static boolean enableRepetitionBias() {
        return enableRepetitionBias;
    }

    public static int repetitionBiasRadiusChunks() {
        return repetitionBiasRadiusChunks;
    }

    public static double structureIdBiasWeight() {
        return structureIdBiasWeight;
    }

    public static double sizeClassBiasWeight() {
        return sizeClassBiasWeight;
    }

    public static double repetitionBiasThreshold() {
        return repetitionBiasThreshold;
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
        enableRepetitionBias = ENABLE_REPETITION_BIAS.get();
        repetitionBiasRadiusChunks = REPETITION_BIAS_RADIUS_CHUNKS.get();
        structureIdBiasWeight = STRUCTURE_ID_BIAS_WEIGHT.get();
        sizeClassBiasWeight = SIZE_CLASS_BIAS_WEIGHT.get();
        repetitionBiasThreshold = REPETITION_BIAS_THRESHOLD.get();
        use3dBlockSpacing = USE_3D_BLOCK_SPACING.get();
        allowStructureOverlap = ALLOW_STRUCTURE_OVERLAP.get();

        Bettersparsestructures.LOGGER.info(
                "Loaded Better Sparse Structures server config: globalSpacingRadiusChunks={}, whitelistedStructures={}, countWhitelistedStructuresForSpacing={}, spacingRadiusOverrides={}, enableSizeScaledSpacing={}, minimumSize={}, maximumSize={}, distanceModifier={}, enableRepetitionBias={}, repetitionBiasRadiusChunks={}, structureIdBiasWeight={}, sizeClassBiasWeight={}, repetitionBiasThreshold={}, use3dBlockSpacing={}, allowStructureOverlap={}, sendDebugStructureMarkers={}, logStructureAttempts={}",
                structureRules.globalSpacingRadiusChunks(),
                structureRules.whitelistCount(),
                countWhitelistedStructuresForSpacing(),
                structureRules.overrideCount(),
                enableSizeScaledSpacing,
                minimumSize,
                maximumSize,
                distanceModifier,
                enableRepetitionBias,
                repetitionBiasRadiusChunks,
                structureIdBiasWeight,
                sizeClassBiasWeight,
                repetitionBiasThreshold,
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
