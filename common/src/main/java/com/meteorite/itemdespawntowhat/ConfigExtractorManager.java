package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.manage.ConfigCache;
import com.meteorite.itemdespawntowhat.manage.ConfigBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * 对外提供配置生命周期与缓存查询能力的统一入口。
 */
public class ConfigExtractorManager {

    private ConfigExtractorManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 生命周期 ========== //

    public static synchronized void initialize(Path configDir) {
        ConfigBootstrap.initialize(configDir);
    }

    public static boolean reloadAllConfigs(Path configDir) {
        return ConfigBootstrap.reloadAllConfigs(configDir);
    }

    public static boolean reloadConfigsForType(Path configDir, ConfigType configType) {
        return ConfigBootstrap.reloadConfigsForType(configDir, configType);
    }

    public static void clearAllCaches() {
        ConfigBootstrap.clearAllCaches();
    }

    // ========== 查询 ========== //

    public static List<BaseConversionConfig> getAllConfigsForItem(ResourceLocation itemId) {
        return ConfigCache.getAllConfigsForItem(itemId);
    }

    @Nullable
    public static BaseConversionConfig getConfigByInternalId(String internalId) {
        return ConfigCache.getConfigByInternalId(internalId);
    }

    public static boolean hasAnyConfigs(ResourceLocation itemId) {
        return ConfigCache.hasAnyConfigs(itemId);
    }

    // 获取某个物品所有配置的最高复杂度
    public static int getMaxComplexityForItem(ResourceLocation itemId) {
        return ConfigCache.getMaxComplexityForItem(itemId);
    }

    public static <T extends BaseConversionConfig> List<T> getConfigByType(ConfigType configType) {
        return ConfigCache.getConfigByType(configType);
    }

    // ========== 缓存维护 ========== //
    public static void removeConfigByInternalId(String internalId) {
        ConfigBootstrap.removeConfigByInternalId(internalId);
    }

    public static boolean isInitialized() {
        return ConfigCache.isInitialized();
    }
}
