package net.z2six.bettersparsestructures;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ServerConfig {
    public static final int MAX_SPACING_RADIUS_CHUNKS = 10_000;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue GLOBAL_SPACING_RADIUS_CHUNKS = BUILDER
            .comment("Minimum distance in chunks between any two accepted structure starts when no override matches.")
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
                    "Examples:",
                    "  \"minecraft:* = 24\"",
                    "  \"minecraft:pillager_outpost = 80\"",
                    "  \"mystructures:ancient_* = 128\""
            )
            .defineListAllowEmpty("spacingRadiusOverrides", List.of(), ServerConfig::isStringEntry);

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

        Bettersparsestructures.LOGGER.info(
                "Loaded Better Sparse Structures server config: globalSpacingRadiusChunks={}, whitelistedStructures={}, countWhitelistedStructuresForSpacing={}, spacingRadiusOverrides={}, sendDebugStructureMarkers={}, logStructureAttempts={}",
                structureRules.globalSpacingRadiusChunks(),
                structureRules.whitelistCount(),
                countWhitelistedStructuresForSpacing(),
                structureRules.overrideCount(),
                sendDebugStructureMarkers,
                logStructureAttempts
        );

        DebugStructureMarkerService.onServerConfigChanged(previousDebugMarkers, sendDebugStructureMarkers);
    }

    private static boolean isStringEntry(Object value) {
        return value instanceof String;
    }
}
