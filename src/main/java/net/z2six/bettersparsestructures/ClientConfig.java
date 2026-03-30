package net.z2six.bettersparsestructures;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue SHOW_DEBUG_STRUCTURE_MARKERS = BUILDER
            .comment("Shows client-side debug markers for structure attempts received from a modded server.")
            .define("showDebugStructureMarkers", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile boolean showDebugStructureMarkers;

    private ClientConfig() {
    }

    public static boolean showDebugStructureMarkers() {
        return showDebugStructureMarkers;
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

        showDebugStructureMarkers = SHOW_DEBUG_STRUCTURE_MARKERS.get();
        Bettersparsestructures.LOGGER.info(
                "Loaded Better Sparse Structures client config: showDebugStructureMarkers={}",
                showDebugStructureMarkers
        );
    }
}
