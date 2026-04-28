package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.command.ConversionConfigCommand;
import com.meteorite.itemdespawntowhat.network.EditSessionLockManager;
import com.meteorite.itemdespawntowhat.network.EditSessionTimeoutHandler;
import com.meteorite.itemdespawntowhat.network.handler.SaveConfigChunkAccumulator;
import com.meteorite.itemdespawntowhat.network.registrar.ConfigEditPayloadRegistrar;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.meteorite.itemdespawntowhat.server.event.ItemConversionEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ItemDespawnToWhat implements ModInitializer {

    public static final String MOD_ID = "itemdespawntowhat";
    public static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("{} mod initialized on Fabric", MOD_ID);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Server starting - loading config and initializing caches");
            FabricModConfig.load();
            Constants.lightningIntervalTicks = FabricModConfig.getLightningIntervalTicks();
            Constants.explosionIntervalTicks = FabricModConfig.getExplosionIntervalTicks();
            Constants.arrowIntervalTicks = FabricModConfig.getArrowIntervalTicks();
            Constants.blockPlaceIntervalTicks = FabricModConfig.getBlockPlaceIntervalTicks();
            Constants.entityScaleOverrides = java.util.List.copyOf(FabricModConfig.getEntityScaleOverrides());
            if (!ConfigExtractorManager.isInitialized()) {
                ConfigExtractorManager.initialize(Services.PLATFORM.getConfigDir());
                LOGGER.info("Caches initialized via ConfigExtractorManager");
            } else {
                LOGGER.info("Caches already initialized, skipping...");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EditSessionLockManager.clear();
            SaveConfigChunkAccumulator.clearAll();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOGGER.info("Server stopped - clearing caches");
            SaveConfigChunkAccumulator.clearAll();
            ConfigExtractorManager.clearAllCaches();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer serverPlayer) {
                EditSessionLockManager.release(serverPlayer);
                SaveConfigChunkAccumulator.clear(serverPlayer);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ConversionConfigCommand.register(dispatcher));

        ItemConversionEvent.register();
        ConfigEditPayloadRegistrar.register();
        EditSessionTimeoutHandler.register();
    }
}
