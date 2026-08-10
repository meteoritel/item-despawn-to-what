package com.meteorite.itemdespawntowhat.config.service;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.JsonConfigRepository;
import com.meteorite.itemdespawntowhat.config.runtime.CompiledConversionRule;
import com.meteorite.itemdespawntowhat.config.runtime.RuntimeConfigSnapshot;
import com.meteorite.itemdespawntowhat.config.runtime.RuntimeConfigSnapshotBuilder;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理配置文件加载、重载与运行时快照发布。
 */
public final class ConfigService {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Path configDir;
    private final AtomicReference<RuntimeConfigSnapshot> snapshot =
            new AtomicReference<>(RuntimeConfigSnapshot.empty());
    private volatile boolean initialized;

    public ConfigService(Path configDir) {
        this.configDir = configDir.toAbsolutePath().normalize();
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        RuntimeConfigSnapshotBuilder builder = new RuntimeConfigSnapshotBuilder();
        for (ConversionTypeDefinition<?> definition : ConversionTypeRegistry.all()) {
            try {
                JsonConfigRepository<?> repository = definition.repository(configDir);
                repository.generateDefaultIfMissing();
                builder.addAll(repository.loadLenient());
            } catch (Exception e) {
                LOGGER.error("Failed to load configs for type: {}", definition.type(), e);
            }
        }
        publish(builder.build());
    }

    public synchronized boolean reloadAll() {
        try {
            RuntimeConfigSnapshotBuilder builder = new RuntimeConfigSnapshotBuilder();
            for (ConversionTypeDefinition<?> definition : ConversionTypeRegistry.all()) {
                JsonConfigRepository<?> repository = definition.repository(configDir);
                repository.generateDefaultIfMissing();
                builder.addAll(repository.loadStrict());
            }
            publish(builder.build());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to reload configs; keeping previous runtime snapshot", e);
            return false;
        }
    }

    public synchronized boolean reloadType(ConfigType type) {
        try {
            ConversionTypeDefinition<?> definition = ConversionTypeRegistry.get(type);
            JsonConfigRepository<?> repository = definition.repository(configDir);
            repository.generateDefaultIfMissing();
            List<? extends BaseConversionConfig> replacements = repository.loadStrict();

            RuntimeConfigSnapshotBuilder builder = new RuntimeConfigSnapshotBuilder();
            for (ConfigType currentType : ConfigType.values()) {
                builder.addAll(currentType == type
                        ? replacements
                        : snapshot.get().getConfigsByType(currentType));
            }
            publish(builder.build());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to reload configs for type {}; keeping previous runtime snapshot", type, e);
            return false;
        }
    }

    public synchronized void removeByInternalId(String internalId) {
        if (internalId == null || internalId.isBlank()) {
            return;
        }
        RuntimeConfigSnapshot current = snapshot.get();
        if (current.getRuleByInternalId(internalId) == null) {
            return;
        }

        RuntimeConfigSnapshotBuilder builder = new RuntimeConfigSnapshotBuilder();
        for (ConfigType type : ConfigType.values()) {
            builder.addAll(current.<BaseConversionConfig>getConfigsByType(type).stream()
                    .filter(config -> !internalId.equals(config.getInternalId()))
                    .toList());
        }
        publish(builder.build());
    }

    public synchronized void clear() {
        snapshot.set(RuntimeConfigSnapshot.empty());
        initialized = false;
    }

    public List<BaseConversionConfig> getConfigsForItem(ResourceLocation itemId) {
        checkInitialized();
        return snapshot.get().getRulesForItem(itemId).stream()
                .map(CompiledConversionRule::definition)
                .toList();
    }

    @Nullable
    public BaseConversionConfig getConfigByInternalId(String internalId) {
        checkInitialized();
        CompiledConversionRule rule = snapshot.get().getRuleByInternalId(internalId);
        return rule == null ? null : rule.definition();
    }

    public List<CompiledConversionRule> getRulesForItem(ResourceLocation itemId) {
        checkInitialized();
        return snapshot.get().getRulesForItem(itemId);
    }

    @Nullable
    public CompiledConversionRule getRuleByInternalId(String internalId) {
        checkInitialized();
        return snapshot.get().getRuleByInternalId(internalId);
    }

    public boolean hasConfigsForItem(ResourceLocation itemId) {
        checkInitialized();
        return snapshot.get().hasConfigsForItem(itemId);
    }

    public int getMaxComplexity(ResourceLocation itemId) {
        checkInitialized();
        return snapshot.get().getMaxComplexity(itemId);
    }

    public <T extends BaseConversionConfig> List<T> getConfigsByType(ConfigType type) {
        checkInitialized();
        return snapshot.get().getConfigsByType(type);
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void publish(RuntimeConfigSnapshot nextSnapshot) {
        snapshot.set(nextSnapshot);
        initialized = true;
        LOGGER.info("Published config snapshot: {} items, {} configs",
                nextSnapshot.itemCount(), nextSnapshot.configCount());
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Config service is not initialized");
        }
    }
}
