package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.command.ConversionConfigCommand;
import com.meteorite.itemdespawntowhat.network.EditSessionLockManager;
import com.meteorite.itemdespawntowhat.network.handler.SaveConfigChunkAccumulator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ItemDespawnToWhat.MOD_ID)
public class ItemDespawnToWhat {

    public static final String MOD_ID = "itemdespawntowhat";
    public static final Logger LOGGER = LogManager.getLogger();

    public ItemDespawnToWhat(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, ModConfigValues.SPEC);

        // 注册事件管理器
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("{} mod initialized", MOD_ID);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting - initializing caches");
        // 服务端启动时，载入规则缓存
        if (!ConfigExtractorManager.isInitialized()) {
            ConfigExtractorManager.initialize();
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
