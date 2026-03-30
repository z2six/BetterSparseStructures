package net.z2six.bettersparsestructures;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Bettersparsestructures.MODID)
public final class Bettersparsestructures {
    public static final String MODID = "bettersparsestructures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Bettersparsestructures(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ServerConfig::onConfigLoading);
        modEventBus.addListener(ServerConfig::onConfigReloading);
        modEventBus.addListener(ClientConfig::onConfigLoading);
        modEventBus.addListener(ClientConfig::onConfigReloading);
        modEventBus.addListener(DebugStructureNetworking::register);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MODID + "-client.toml");
        NeoForge.EVENT_BUS.addListener(DebugStructureMarkerService::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DebugStructureMarkerService::onPlayerChangedDimension);
    }
}
