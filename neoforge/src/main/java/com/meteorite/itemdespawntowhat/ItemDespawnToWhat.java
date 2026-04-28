package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.command.ConversionConfigCommand;
import com.meteorite.itemdespawntowhat.network.EditSessionLockManager;
import com.meteorite.itemdespawntowhat.network.handler.SaveConfigChunkAccumulator;
import com.meteorite.itemdespawntowhat.platform.Services;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

@Mod(ItemDespawnToWhat.MOD_ID)
public class ItemDespawnToWhat {

    public static final String MOD_ID = "itemdespawntowhat";
    public static final Logger LOGGER = LogManager.getLogger();

    public ItemDespawnToWhat(ModContainer modContainer) {
        // Lower root logger level so DEBUG messages reach the Console appender
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().setLevel(Level.DEBUG);
        ctx.updateLoggers();

        modContainer.registerConfig(ModConfig.Type.SERVER, ModConfigValues.SPEC);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("{} mod initialized", MOD_ID);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting - initializing caches");
        // Inject NeoForge config values into common constants (config is loaded by now)
        Constants.blockPlaceIntervalTicks = ModConfigValues.BLOCK_PLACE_INTERVAL_TICKS.get();
        Constants.lightningIntervalTicks = ModConfigValues.LIGHTNING_INTERVAL_TICKS.get();
        Constants.explosionIntervalTicks = ModConfigValues.EXPLOSION_INTERVAL_TICKS.get();
        Constants.arrowIntervalTicks = ModConfigValues.ARROW_INTERVAL_TICKS.get();
        Constants.entityScaleOverrides = List.copyOf(ModConfigValues.ENTITY_SCALE_OVERRIDES.get());
        if (!ConfigExtractorManager.isInitialized()) {
            ConfigExtractorManager.initialize(Services.PLATFORM.getConfigDir());
            LOGGER.info("Caches initialized via ConfigExtractorManager");
        } else {
            LOGGER.info("Caches already initialized, skipping...");
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            EditSessionLockManager.release(serverPlayer);
            SaveConfigChunkAccumulator.clear(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        EditSessionLockManager.clear();
        SaveConfigChunkAccumulator.clearAll();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server stopped - clearing caches");
        SaveConfigChunkAccumulator.clearAll();
        ConfigExtractorManager.clearAllCaches();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ConversionConfigCommand.register(
                event.getDispatcher()
        );
    }

}
