package net.z2six.bettersparsestructures;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Bettersparsestructures.MODID)
public final class Bettersparsestructures {
    public static final String MODID = "bettersparsestructures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Bettersparsestructures() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ServerConfig::onConfigLoading);
        modEventBus.addListener(ServerConfig::onConfigReloading);
        modEventBus.addListener(ClientConfig::onConfigLoading);
        modEventBus.addListener(ClientConfig::onConfigReloading);
        DebugStructureNetworking.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MODID + "-client.toml");
        MinecraftForge.EVENT_BUS.addListener(DebugStructureMarkerService::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(DebugStructureMarkerService::onPlayerChangedDimension);
    }
}
