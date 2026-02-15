package com.meteorite.itemdespawntowhat;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.config.handler.ItemToBlockConfigHandler;
import com.meteorite.itemdespawntowhat.config.handler.ItemToEntityConfigHandler;
import com.meteorite.itemdespawntowhat.config.handler.ItemToItemConfigHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConfigManager INSTANCE = new ConfigManager();

    // 注册表，配置类型 -> 配置处理器实例
    private final ConcurrentMap<ConfigType, BaseConfigHandler<?>> handlerRegistry = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    private ConfigManager() {
        registerDefaultHandlers();
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        // 生成默认配置文件
        INSTANCE.generateDefaultConfig();
        LOGGER.info("ConfigManager initialized and default configs generated");
    }

    // 注册所有配置处理器
    private void registerDefaultHandlers() {
        registerHandler(new ItemToItemConfigHandler());
        registerHandler(new ItemToEntityConfigHandler());
        registerHandler(new ItemToBlockConfigHandler());
        LOGGER.debug("Default configuration handlers registered");
    }

    private void registerHandler(BaseConfigHandler<?> handler) {
        if (loaded) {
            LOGGER.info("Default configs already generation: {}", handler.getConfigType().name());
            return;
        }

        ConfigType configType = handler.getConfigType();

        if (handlerRegistry.putIfAbsent(configType, handler) != null) {
            LOGGER.warn("Handler already registered for type: {}", configType.name());
            return;
        }

        LOGGER.debug("Registered configuration handler: {}", configType.name());
    }

    // ---------- 生成所有的默认配置文件 ----------//
    public synchronized void generateDefaultConfig() {
        if (loaded) {
            LOGGER.debug("Default configs already generated");
            return;
        }

        for (Map.Entry<ConfigType, BaseConfigHandler<?>> entry : handlerRegistry.entrySet()) {
            ConfigType configType = entry.getKey();
            BaseConfigHandler<?> handler = entry.getValue();

            try{
                handler.generateDefaultConfig();
                LOGGER.debug("Generated default config for: {}", configType);
            } catch (Exception e) {
                LOGGER.error("Failed to generate default config for: {}", configType, e);
            }
        }

        loaded = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    // 获取指定类型的配置处理器
    @SuppressWarnings("unchecked")
    public <T extends BaseConversionConfig> BaseConfigHandler<T> getHandler(ConfigType configType) {
        if (!handlerRegistry.containsKey(configType)) {
            LOGGER.warn("No handler registered for config type: {}", configType);
            return null;
        }
        return (BaseConfigHandler<T>) handlerRegistry.get(configType);
    }

    public Set<ConfigType> getRegisteredHandlerTypes() {
        return Collections.unmodifiableSet(handlerRegistry.keySet());
    }

}
