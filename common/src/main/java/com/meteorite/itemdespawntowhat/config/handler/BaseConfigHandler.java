package com.meteorite.itemdespawntowhat.config.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.util.JsonOrderTypeAdapterFactory;
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
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(new JsonOrderTypeAdapterFactory())
            .create();

    protected final String fileName;
    protected final Type listType;
    protected final ConfigType configType;
    protected final Path configDir;

    public BaseConfigHandler(ConfigType configType, Path configDir) {
        this.configType = configType;
        this.fileName = configType.getFileName();
        this.listType = createListType();
        this.configDir = configDir;
    }

    // 配置文件生成路径
    public Path getConfigPath() {
        return configDir.resolve(Constants.MOD_ID).resolve(fileName);
    }

    // 生成默认配置文件
    public void generateDefaultConfig () {
        Path configPath = getConfigPath();

        try{
            // 创建父目录
            Files.createDirectories(configPath.getParent());

            if (!isConfigFileExists()) {
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

            LOGGER.debug("Loaded {} entries from {}", entries.size(), configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to read configuration file: {}", fileName, e);
        }
        return entries;
    }

    // 保存配置文件
    public void saveConfig(List<? extends BaseConversionConfig> entries) throws IOException {
        Path configPath = getConfigPath();

        try(BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(entries, writer);
        }
    }

    // 配置文件是否存在
    public boolean isConfigFileExists() {
        return Files.exists(getConfigPath());
    }

    // 序列化配置列表为JSON字符串，用于数据传输
    public String serializeToJson(List<? extends BaseConversionConfig> configs) {
        return GSON.toJson(configs);
    }

    // 从JSON字符串反序列化为配置列表，用于数据传输
    public List<T> deserializeFromJson(String json) {
        try {
            return GSON.fromJson(json, listType);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize config from JSON", e);
            return new ArrayList<>();
        }
    }

    protected boolean isValidEntry(T entry) {
        return entry != null && entry.shouldProcess();
    }

    // 子类重写以创建默认的json内容
    protected List<T> createDefaultEntries() {
        return new ArrayList<>();
    }
    // 子类指定类型
    protected abstract Type createListType();

    public Gson getGson() {
        return GSON;
    }

    public ConfigType getConfigType() {
        return configType;
    }
}
