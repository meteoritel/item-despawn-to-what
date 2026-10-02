package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.command.ConversionConfigCommand;
import com.meteorite.itemdespawntowhat.network.EditSessionLockManager;
import com.meteorite.itemdespawntowhat.network.EditSessionTimeoutHandler;
import com.meteorite.itemdespawntowhat.network.handler.SaveConfigChunkAccumulator;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.network.registrar.ConfigEditPayloadRegistrar;
import com.meteorite.itemdespawntowhat.network.registrar.RuleEditPayloadRegistrar;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeEvents;
import com.meteorite.itemdespawntowhat.server.event.ItemConversionEvent;
import com.meteorite.itemdespawntowhat.server.conversion.ConversionTracker;
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

            // 加载可配置内容
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
            ConversionTracker.clearAll();
            // 新链路：清空未完成的变更集分片与会话缓存
            RuleEditServerHandler.reset();
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

        // 注册事件监听
        ItemConversionEvent.register();
        // 新链路（core/runtime）事件入口：与旧链路并列运行，便于对照测试
        RuleRuntimeEvents.register();
        ConfigEditPayloadRegistrar.register();
        EditSessionTimeoutHandler.register();
        // 新链路（core/network/transport）网络入口：与旧链路并列注册
        RuleEditPayloadRegistrar.register();
    }
}
