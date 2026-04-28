package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.manage.ConfigCache;
import com.meteorite.itemdespawntowhat.manage.ConfigBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

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

    public static void reloadConfigsForType(Path configDir, ConfigType configType) {
        ConfigBootstrap.reloadConfigsForType(configDir, configType);
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
