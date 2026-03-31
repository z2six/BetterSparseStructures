package net.z2six.bettersparsestructures;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue SHOW_DEBUG_STRUCTURE_MARKERS = BUILDER
            .comment("Shows client-side debug markers for structure attempts received from a modded server.")
            .define("showDebugStructureMarkers", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

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
