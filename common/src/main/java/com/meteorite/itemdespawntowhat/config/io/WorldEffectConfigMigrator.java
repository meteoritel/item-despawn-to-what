package com.meteorite.itemdespawntowhat.config.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeRegistry;
import com.meteorite.itemdespawntowhat.util.JsonOrderTypeAdapterFactory;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将旧的世界效果联合配置文件安全拆分为独立转化类型文件。
 */
public final class WorldEffectConfigMigrator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(new JsonOrderTypeAdapterFactory())
            .create();
    private static final Map<String, TargetDefinition> TARGETS = createTargets();
    private static final Set<String> COMMON_FIELDS = Set.of(
            "schema_version", "item", "source_multiple", "result_multiple", "conversion_time",
            "conditions", "consumption", "priority", "enabled", "notes");

    private WorldEffectConfigMigrator() {
    }

    public static void migrate(Path configDir) throws IOException {
        Path modDirectory = configDir.resolve(Constants.MOD_ID);
        List<Path> sourcePaths = List.of(
                modDirectory.resolve(Constants.MOD_ID).resolve("item_to_world_effect.json"),
                modDirectory.resolve("item_to_world_effect.json"));
        List<Path> existingSources = sourcePaths.stream().filter(Files::isRegularFile).toList();
        if (existingSources.isEmpty()) {
            return;
        }

        Map<String, JsonArray> splitEntries = emptyTargetArrays();
        for (Path source : existingSources) {
            JsonArray migrated = readMigratedArray(source);
            for (JsonElement element : migrated) {
                splitEntry(element.getAsJsonObject(), splitEntries);
            }
        }

        Map<Path, byte[]> writes = new LinkedHashMap<>();
        for (TargetDefinition target : TARGETS.values()) {
            Path targetPath = modDirectory.resolve(Constants.MOD_ID).resolve(target.path() + ".json");
            JsonArray merged = Files.isRegularFile(targetPath)
                    ? readMigratedArray(targetPath)
                    : new JsonArray();
            appendDistinct(merged, splitEntries.get(target.path()));
            validate(target.path(), merged);
            writes.put(targetPath, GSON.toJson(merged).getBytes(StandardCharsets.UTF_8));
        }

        for (Map.Entry<Path, byte[]> write : writes.entrySet()) {
            writeAtomically(write.getKey(), write.getValue());
        }
        for (Path source : existingSources) {
            Path backup = source.resolveSibling(source.getFileName() + ".pre-split.bak");
            if (!Files.exists(backup)) {
                Files.copy(source, backup);
            }
            Files.delete(source);
        }
        LOGGER.info("Split {} legacy world effect configuration file(s) into {} conversion types",
                existingSources.size(), TARGETS.size());
    }

    private static JsonArray readMigratedArray(Path path) throws IOException {
        try {
            JsonElement root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonElement.class);
            return ConfigMigrator.migrate(root).json().getAsJsonArray();
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse world effect migration input: " + path, e);
        }
    }

    private static void splitEntry(JsonObject source, Map<String, JsonArray> splitEntries) {
        JsonElement sideEffectElement = source.get("side_effect");
        if (sideEffectElement == null || !sideEffectElement.isJsonPrimitive()) {
            throw new JsonParseException("Legacy world effect entry is missing side_effect");
        }
        String sideEffect = sideEffectElement.getAsString().toUpperCase(Locale.ROOT);
        TargetDefinition target = TARGETS.get(sideEffect);
        if (target == null) {
            throw new JsonParseException("Unknown legacy side_effect: " + sideEffect);
        }

        JsonObject converted = new JsonObject();
        for (String field : COMMON_FIELDS) {
            if (source.has(field)) {
                converted.add(field, source.get(field).deepCopy());
            }
        }
        for (String field : target.specificFields()) {
            if (source.has(field)) {
                converted.add(field, source.get(field).deepCopy());
            }
        }
        if ("RAIN".equals(sideEffect) || "CLEAR".equals(sideEffect)) {
            converted.addProperty("weather_mode", sideEffect);
        }
        splitEntries.get(target.path()).add(converted);
    }

    private static void validate(String path, JsonArray entries) {
        ConversionType type = ConversionTypeRegistry.require(
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
        ConversionTypeDefinition<?> definition = ConversionTypeRegistry.get(type);
        definition.codec().deserializeStrict(GSON.toJson(entries));
    }

    private static void appendDistinct(JsonArray target, JsonArray additions) {
        List<JsonElement> existing = new ArrayList<>();
        target.forEach(existing::add);
        for (JsonElement addition : additions) {
            if (existing.stream().noneMatch(addition::equals)) {
                JsonElement copy = addition.deepCopy();
                target.add(copy);
                existing.add(copy);
            }
        }
    }

    private static Map<String, JsonArray> emptyTargetArrays() {
        Map<String, JsonArray> result = new LinkedHashMap<>();
        TARGETS.values().forEach(target -> result.putIfAbsent(target.path(), new JsonArray()));
        return result;
    }

    private static Map<String, TargetDefinition> createTargets() {
        Map<String, TargetDefinition> targets = new LinkedHashMap<>();
        targets.put("LIGHTNING", new TargetDefinition("item_to_lightning", Set.of("visual_only")));
        targets.put("EXPLOSION", new TargetDefinition("item_to_explosion",
                Set.of("explosion_power", "explosion_fire", "explosion_direction_type")));
        targets.put("ARROW_RAIN", new TargetDefinition("item_to_arrow_rain",
                Set.of("arrow_pickup_status", "arrow_potion_effects")));
        TargetDefinition weather = new TargetDefinition("item_to_weather",
                Set.of("weather_duration_ticks", "is_thundering"));
        targets.put("RAIN", weather);
        targets.put("CLEAR", weather);
        return Map.copyOf(targets);
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record TargetDefinition(String path, Set<String> specificFields) {
    }
}
