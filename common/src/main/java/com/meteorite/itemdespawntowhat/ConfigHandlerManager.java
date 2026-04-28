package com.meteorite.itemdespawntowhat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.handler.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// 服务端启动才会第一次生辰json文件
public class ConfigHandlerManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile ConfigHandlerManager INSTANCE;

    // 注册表，配置类型 -> 配置处理器实例
    private final ConcurrentMap<ConfigType, BaseConfigHandler<?>> handlerRegistry = new ConcurrentHashMap<>();
    private volatile boolean defaultsGenerated = false;

    private ConfigHandlerManager(Path configDir) {
        registerDefaultHandlers(configDir);
    }

    public static ConfigHandlerManager getInstance(Path configDir) {
        if (INSTANCE == null) {
            synchronized (ConfigHandlerManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ConfigHandlerManager(configDir);
                }
            }
        }
        return INSTANCE;
    }

    // 注册所有配置处理器
    private void registerDefaultHandlers(Path configDir) {
        registerHandler(new ItemToItemConfigHandler(configDir));
        registerHandler(new ItemToMobConfigHandler(configDir));
        registerHandler(new ItemToBlockConfigHandler(configDir));
        registerHandler(new ItemToXpConfigHandler(configDir));
        registerHandler(new ItemToWorldEffectConfigHandler(configDir));
        LOGGER.debug("Default configuration handlers registered");
    }

    private void registerHandler(BaseConfigHandler<?> handler) {
        ConfigType configType = handler.getConfigType();

        if (handlerRegistry.putIfAbsent(configType, handler) != null) {
            LOGGER.warn("Handler already registered for type: {}", configType.name());
            return;
        }

        LOGGER.debug("Registered configuration handler: {}", configType.name());
    }

    // ========== 生成所有的默认配置文件 ========== //
    public synchronized void generateDefaultConfig() {
        if (defaultsGenerated) {
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

        defaultsGenerated = true;
    }

    public boolean isLoaded() {
        return defaultsGenerated;
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
