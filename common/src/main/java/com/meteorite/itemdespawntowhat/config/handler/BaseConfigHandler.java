package com.meteorite.itemdespawntowhat.config.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.util.JsonOrderTypeAdapterFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置文件读写基类，负责类型化 JSON 解析和原子写盘。
 */
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
        try {
            List<T> entries = readConfigEntries();
            entries.removeIf(entry -> !isValidEntry(entry));
            LOGGER.debug("Loaded {} entries from {}", entries.size(), getConfigPath());
            return entries;
        } catch (IOException e) {
            LOGGER.error("Failed to read configuration file: {}", fileName, e);
            return new ArrayList<>();
        }
    }

    // 严格加载配置；损坏、空文件或类型错误均交由调用方处理
    public List<T> loadConfigStrict() throws IOException {
        List<T> entries = readConfigEntries();
        for (T entry : entries) {
            if (!isValidEntry(entry)) {
                throw new IOException("Configuration contains an invalid entry: " + getConfigPath());
            }
        }
        LOGGER.debug("Strictly loaded {} entries from {}", entries.size(), getConfigPath());
        return entries;
    }

    // 保存配置文件
    public void saveConfig(List<? extends BaseConversionConfig> entries) throws IOException {
        writeConfigBytesAtomically(GSON.toJson(entries).getBytes(StandardCharsets.UTF_8));
    }

    // 读取原始配置字节，用于保存失败后的磁盘回滚
    public byte[] readConfigBytes() throws IOException {
        return Files.readAllBytes(getConfigPath());
    }

    // 原子恢复保存前的原始配置内容
    public void restoreConfigBytes(byte[] content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("Config backup cannot be null");
        }
        writeConfigBytesAtomically(content);
    }

    private void writeConfigBytesAtomically(byte[] content) throws IOException {
        Path configPath = getConfigPath();
        Path parent = configPath.getParent();
        Files.createDirectories(parent);
        Path temporaryPath = Files.createTempFile(parent, fileName + ".", ".tmp");

        try {
            Files.write(temporaryPath, content);

            try {
                Files.move(temporaryPath, configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
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
            return deserializeFromJsonStrict(json);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize config from JSON", e);
            return new ArrayList<>();
        }
    }

    // 严格解析网络 JSON，确保合法空数组与解析失败可以被区分
    public List<T> deserializeFromJsonStrict(String json) {
        if (json == null) {
            throw new JsonParseException("Config JSON cannot be null");
        }

        List<T> entries = GSON.fromJson(json, listType);
        if (entries == null) {
            throw new JsonParseException("Config JSON must be an array");
        }
        return entries;
    }

    protected boolean isValidEntry(T entry) {
        return entry != null && entry.shouldProcess();
    }

    private List<T> readConfigEntries() throws IOException {
        Path configPath = getConfigPath();
        try (BufferedReader reader = Files.newBufferedReader(configPath)) {
            List<T> entries = GSON.fromJson(reader, listType);
            if (entries == null) {
                throw new IOException("Configuration file is empty: " + configPath);
            }
            return entries;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse configuration file: " + configPath, e);
        }
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
