package com.meteorite.itemdespawntowhat.config.io;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责一个配置类型对应 JSON 文件的原子读写。
 */
public final class JsonConfigRepository<T extends BaseConversionConfig> {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ConversionTypeDefinition<T> definition;
    private final Path configPath;

    public JsonConfigRepository(Path configDir, ConversionTypeDefinition<T> definition) {
        this.definition = definition;
        this.configPath = configDir.resolve(Constants.MOD_ID).resolve(definition.type().getFileName());
        this.legacyPath = BuiltinConversionTypes.isBuiltin(definition.type())
                ? configDir.resolve(Constants.MOD_ID).resolve(definition.type().id().getPath() + ".json")
                : null;
    }

    private final Path legacyPath;

    public Path getConfigPath() {
        return configPath;
    }

    public boolean exists() {
        return Files.exists(configPath);
    }

    public void generateDefaultIfMissing() throws IOException {
        migrateLegacyIfNeeded();
        if (exists()) {
            return;
        }
        save(definition.createDefaultEntries());
        LOGGER.info("Generated default configuration file: {}", configPath);
    }

    public List<T> loadLenient() {
        try {
            List<T> entries = readEntries();
            entries.removeIf(entry -> entry == null || !entry.shouldProcess());
            return entries;
        } catch (IOException e) {
            LOGGER.error("Failed to read configuration file: {}", configPath, e);
            return new ArrayList<>();
        }
    }

    public List<T> loadStrict() throws IOException {
        List<T> entries = readEntries();
        for (T entry : entries) {
            if (entry == null || !entry.validate()) {
                throw new IOException("Configuration contains an invalid entry: " + configPath);
            }
        }
        entries.removeIf(entry -> !entry.isEnabled());
        return entries;
    }

    public void save(List<? extends BaseConversionConfig> entries) throws IOException {
        if (entries == null || entries.stream().anyMatch(entry -> entry == null
                || entry.getConversionType() != definition.type()
                || !entry.validate())) {
            throw new IOException("Configuration contains an invalid entry: " + configPath);
        }
        writeBytesAtomically(definition.codec().serialize(entries).getBytes(StandardCharsets.UTF_8));
    }

    public byte[] readBytes() throws IOException {
        return Files.readAllBytes(configPath);
    }

    public void restoreBytes(byte[] content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("Config backup cannot be null");
        }
        writeBytesAtomically(content);
    }

    private List<T> readEntries() throws IOException {
        migrateLegacyIfNeeded();
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            ConfigJsonCodec.DecodeResult<T> result = definition.codec().deserializeWithMigration(json);
            if (result.migrated()) {
                backupBeforeSchemaMigration();
                writeBytesAtomically(result.migratedJson().getBytes(StandardCharsets.UTF_8));
                LOGGER.info("Migrated configuration schema to v{}: {}",
                        ConfigMigrator.CURRENT_SCHEMA_VERSION, configPath);
            }
            return result.entries();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse configuration file: " + configPath, e);
        }
    }

    private void backupBeforeSchemaMigration() throws IOException {
        Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".v1.bak");
        if (!Files.exists(backupPath)) {
            Files.copy(configPath, backupPath);
        }
    }

    private void migrateLegacyIfNeeded() throws IOException {
        if (legacyPath == null || Files.exists(configPath) || !Files.exists(legacyPath)) {
            return;
        }
        Files.createDirectories(configPath.getParent());
        Files.copy(legacyPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(legacyPath);
        LOGGER.info("Migrated legacy configuration file {} to {}", legacyPath, configPath);
    }

    private void writeBytesAtomically(byte[] content) throws IOException {
        Path parent = configPath.getParent();
        Files.createDirectories(parent);
        Path temporaryPath = Files.createTempFile(parent, configPath.getFileName() + ".", ".tmp");

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
}
