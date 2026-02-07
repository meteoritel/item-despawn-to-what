package com.meteorite.expiringitemlib.handler;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meteorite.expiringitemlib.ExpiringItemLib;
import com.meteorite.expiringitemlib.config.BaseConversionConfig;
import com.meteorite.expiringitemlib.util.ResourceLocationAdapter;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseConfigHandler<T extends BaseConversionConfig> {
    protected static final Logger LOGGER = LogManager.getLogger();
    protected static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ResourceLocation.class,new ResourceLocationAdapter())
            .create();

    protected final String fileName;
    protected final Type listType;
    protected List<T> lastLoadedConfigs = null;
    protected boolean configsChanged = false;

    public BaseConfigHandler(String fileName) {
        this.fileName = fileName;
        this.listType = createListType();
    }

    // 配置文件生成路径
    public Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(ExpiringItemLib.MOD_ID)
                .resolve(fileName);
    }

    // 生成默认配置文件
    public void generateDefaultConfig () {
        Path configPath = getConfigPath();

        try{
            // 创建父目录
            Files.createDirectories(configPath.getParent());

            if (!Files.exists(configPath)) {
                List<T> defaultEntries = createDefaultEntries();
                saveConfig(defaultEntries);
                LOGGER.info("Generate default configuration file: {}", configPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to generate configuration file: {}", fileName, e);
        }

    }

    // 加载配置
    public List<T> loadConfig() {
        Path configPath = getConfigPath();
        List<T> entries = new ArrayList<>();

        if (!Files.exists(configPath)) {
            LOGGER.warn("Configuration file does not exist: {}", configPath);
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath)) {
            entries = GSON.fromJson(reader, listType);

            if (entries == null) {
                LOGGER.warn("Configuration file is empty: {}", configPath);
                entries = new ArrayList<>();
            }

            entries.removeIf(entry -> !isValidEntry(entry));

            // 检查配置是否变化
            configsChanged = !entries.equals(lastLoadedConfigs);
            // 缓存上次配置
            lastLoadedConfigs = new ArrayList<>(entries);

            LOGGER.debug("Loaded {} entries from {}", entries.size(), configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to read configuration file: {}", fileName, e);
        }
        return entries;
    }

    // 保存配置文件
    public void saveConfig(List<T> entries) throws IOException {
        Path configPath = getConfigPath();

        try(BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(entries, writer);
        }
    }

    protected boolean isValidEntry(T entry) {
        return entry != null && entry.shouldProcess();
    }

    // 子类中创建默认的json内容
    protected abstract List<T> createDefaultEntries();
    // 子类中明确指定具体类型，避免泛型擦除问题
    protected abstract Type createListType();

    // ========= 用于热重载，暂时无作用======== //
    // 配置是否改变了
    public boolean isConfigsChanged() {
        return configsChanged;
    }

    // 获取上次加载的配置
    public List<T> getLastLoadedConfigs() {
        return lastLoadedConfigs != null ?
                new ArrayList<>(lastLoadedConfigs) : new ArrayList<>();
    }

}