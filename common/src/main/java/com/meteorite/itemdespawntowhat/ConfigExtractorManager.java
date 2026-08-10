package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.config.service.ConfigService;
import com.meteorite.itemdespawntowhat.config.runtime.CompiledConversionRule;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * 对外提供配置生命周期与缓存查询能力的统一入口。
 */
public class ConfigExtractorManager {
    private static volatile ConfigService service;

    private ConfigExtractorManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 生命周期 ========== //

    public static synchronized void initialize(Path configDir) {
        if (service == null) {
            service = new ConfigService(configDir);
        }
        service.initialize();
    }

    public static synchronized boolean reloadAllConfigs(Path configDir) {
        return getOrCreateService(configDir).reloadAll();
    }

    public static synchronized boolean reloadConfigsForType(Path configDir, ConversionType configType) {
        return getOrCreateService(configDir).reloadType(configType);
    }

    public static synchronized void clearAllCaches() {
        if (service != null) {
            service.clear();
            service = null;
        }
    }

    // ========== 查询 ========== //

    public static List<CompiledConversionRule> getRulesForItem(ResourceLocation itemId) {
        return getService().getRulesForItem(itemId);
    }

    @Nullable
    public static CompiledConversionRule getRuleByInternalId(String internalId) {
        return getService().getRuleByInternalId(internalId);
    }

    public static boolean hasAnyConfigs(ResourceLocation itemId) {
        return getService().hasConfigsForItem(itemId);
    }

    // 获取某个物品所有配置的最高显式优先级
    public static int getMaxPriorityForItem(ResourceLocation itemId) {
        return getService().getMaxPriority(itemId);
    }

    public static <T extends BaseConversionConfig> List<T> getConfigByType(ConversionType configType) {
        return getService().getConfigsByType(configType);
    }

    // ========== 缓存维护 ========== //
    public static void removeConfigByInternalId(String internalId) {
        getService().removeByInternalId(internalId);
    }

    public static boolean isInitialized() {
        ConfigService current = service;
        return current != null && current.isInitialized();
    }

    private static ConfigService getOrCreateService(Path configDir) {
        if (service == null) {
            service = new ConfigService(configDir);
            service.initialize();
        }
        return service;
    }

    private static ConfigService getService() {
        ConfigService current = service;
        if (current == null) {
            throw new IllegalStateException("Config service is not initialized");
        }
        return current;
    }
}
