package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.command.ConversionConfigCommand;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(ItemDespawnToWhat.MOD_ID)
public class ItemDespawnToWhat {

    public static final String MOD_ID = "itemdespawntowhat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemDespawnToWhat(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);

        // 注册事件管理器
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("{} mod initialized", MOD_ID);
    }

    // 游戏启动时，生成默认配置文件
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Starting common setup for {}", MOD_ID);
        event.enqueueWork(() -> {
            ConfigHandlerManager.initialize();
            LOGGER.info("Configurations loaded during common setup");

        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting - initializing caches");

        if (!ConfigExtractorManager.isInitialized()) {
            ConfigExtractorManager.initialize();
            LOGGER.info("Caches initialized via ConfigExtractorManager");
        } else {
            LOGGER.info("Caches already initialized, skipping...");
        }
        ConfigExtractorManager.expandTagConfigs();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server stopped - clearing caches");
        ConfigExtractorManager.clearAllCaches();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ConversionConfigCommand.register(
                event.getDispatcher()
        );
    }
}
