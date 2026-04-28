package com.meteorite.itemdespawntowhat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.platform.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FabricModConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String FILE_NAME = "itemdespawntowhat.json";

    // 默认配置值
    private static int lightningIntervalTicks = 8;
    private static int explosionIntervalTicks = 8;
    private static int arrowIntervalTicks = 2;
    private static int blockPlaceIntervalTicks = 1;
    private static List<String> entityScaleOverrides = List.of(
            "minecraft:ghast=2.0",
            "minecraft:ender_dragon=2.0",
            "minecraft:iron_golem=3.0",
            "minecraft:wither=3.0"
    );

    private FabricModConfig() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void load() {
        Path configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);

        if (!Files.exists(configPath)) {
            createDefault(configPath);
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> config = GSON.fromJson(reader, mapType);

            if (config == null) {
                LOGGER.warn("Config file is empty, using defaults");
                return;
            }

            lightningIntervalTicks = getInt(config, "lightning_interval_ticks", 8, 1, 100);
            explosionIntervalTicks = getInt(config, "explosion_interval_ticks", 8, 1, 100);
            arrowIntervalTicks = getInt(config, "arrow_interval_ticks", 2, 1, 100);
            blockPlaceIntervalTicks = getInt(config, "block_place_interval_ticks", 1, 1, 100);

            Object overridesObj = config.get("entity_scale_overrides");
            if (overridesObj instanceof List<?> list) {
                entityScaleOverrides = list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(s -> s.contains("="))
                        .toList();
            }

            LOGGER.info("Loaded config from {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to load config, using defaults", e);
        }
    }

    private static void createDefault(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                Map<String, Object> defaults = Map.of(
                        "lightning_interval_ticks", lightningIntervalTicks,
                        "explosion_interval_ticks", explosionIntervalTicks,
                        "arrow_interval_ticks", arrowIntervalTicks,
                        "block_place_interval_ticks", blockPlaceIntervalTicks,
                        "entity_scale_overrides", entityScaleOverrides
                );
                GSON.toJson(defaults, writer);
            }
            LOGGER.info("Created default config at {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static int getInt(Map<String, Object> config, String key, int defaultValue, int min, int max) {
        Object value = config.get(key);
        if (value instanceof Number num) {
            int intValue = num.intValue();
            if (intValue < min || intValue > max) {
                LOGGER.warn("Config '{}' value {} out of range [{}, {}], using default {}",
                        key, intValue, min, max, defaultValue);
                return defaultValue;
            }
            return intValue;
        }
        return defaultValue;
    }

    // --- accessors ---

    public static int getLightningIntervalTicks() {
        return lightningIntervalTicks;
    }

    public static int getExplosionIntervalTicks() {
        return explosionIntervalTicks;
    }

    public static int getArrowIntervalTicks() {
        return arrowIntervalTicks;
    }

    public static int getBlockPlaceIntervalTicks() {
        return blockPlaceIntervalTicks;
    }

    public static List<String> getEntityScaleOverrides() {
        return entityScaleOverrides;
    }

}
