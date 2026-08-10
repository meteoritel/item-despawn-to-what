package com.meteorite.itemdespawntowhat.config.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.meteorite.itemdespawntowhat.Constants;

/**
 * 将旧配置条目按版本顺序迁移到当前 schema。
 */
public final class ConfigMigrator {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private ConfigMigrator() {
    }

    public static MigrationResult migrate(JsonElement root) {
        if (root == null || !root.isJsonArray()) {
            throw new JsonParseException("Config JSON must be an array");
        }

        JsonArray migratedRoot = root.deepCopy().getAsJsonArray();
        boolean migrated = false;
        for (JsonElement element : migratedRoot) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Every config entry must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            int version = schemaVersion(entry);
            if (version > CURRENT_SCHEMA_VERSION) {
                throw new JsonParseException("Unsupported config schema_version: " + version);
            }
            if (version < 1) {
                throw new JsonParseException("Invalid config schema_version: " + version);
            }
            if (version == 1) {
                migrateV1ToV2(entry);
                migrated = true;
            }
        }
        return new MigrationResult(migratedRoot, migrated);
    }

    private static int schemaVersion(JsonObject entry) {
        JsonElement version = entry.get("schema_version");
        if (version == null) {
            return 1;
        }
        try {
            if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("schema_version must be an integer");
            }
            double value = version.getAsDouble();
            if (!Double.isFinite(value) || value != Math.rint(value)
                    || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new JsonParseException("schema_version must be an integer");
            }
            return (int) value;
        } catch (RuntimeException e) {
            if (e instanceof JsonParseException parseException) {
                throw parseException;
            }
            throw new JsonParseException("schema_version must be an integer", e);
        }
    }

    private static void migrateV1ToV2(JsonObject entry) {
        JsonArray leaves = new JsonArray();
        migrateDimension(entry, leaves);
        migrateOutdoor(entry, leaves);
        migrateSurroundingBlocks(entry, leaves);

        JsonObject consumption = new JsonObject();
        migrateCatalysts(entry, leaves, consumption);
        migrateFluid(entry, leaves, consumption);

        JsonObject expression = new JsonObject();
        JsonArray groups = new JsonArray();
        if (!leaves.isEmpty()) {
            JsonObject group = new JsonObject();
            group.add("conditions", leaves);
            groups.add(group);
        }
        expression.add("groups", groups);
        entry.add("conditions", expression);
        if (!consumption.isEmpty()) {
            entry.add("consumption", consumption);
        }
        if (!entry.has("priority")) {
            entry.addProperty("priority", 0);
        }
        entry.addProperty("schema_version", CURRENT_SCHEMA_VERSION);
    }

    private static void migrateDimension(JsonObject entry, JsonArray leaves) {
        JsonElement dimension = entry.remove("dimension");
        if (isNonBlankString(dimension)) {
            JsonObject params = new JsonObject();
            params.add("dimension", dimension);
            leaves.add(leaf("dimension", params));
        }
    }

    private static void migrateOutdoor(JsonObject entry, JsonArray leaves) {
        JsonElement outdoor = entry.remove("need_outdoor");
        if (outdoor != null && outdoor.isJsonPrimitive() && outdoor.getAsBoolean()) {
            leaves.add(leaf("outdoor", new JsonObject()));
        }
    }

    private static void migrateSurroundingBlocks(JsonObject entry, JsonArray leaves) {
        JsonElement surrounding = entry.remove("surrounding_blocks");
        if (surrounding != null && surrounding.isJsonObject() && !surrounding.getAsJsonObject().isEmpty()) {
            JsonObject params = new JsonObject();
            params.add("blocks", surrounding);
            leaves.add(leaf("surrounding_blocks", params));
        }
    }

    private static void migrateCatalysts(JsonObject entry, JsonArray leaves, JsonObject consumption) {
        JsonElement catalystElement = entry.remove("catalyst_items");
        if (catalystElement == null || !catalystElement.isJsonObject()) {
            return;
        }
        JsonObject catalyst = catalystElement.getAsJsonObject();
        JsonArray items = arrayOrEmpty(catalyst.get("catalyst_items"));
        if (items.isEmpty()) {
            return;
        }

        JsonObject params = new JsonObject();
        params.add("items", items.deepCopy());
        leaves.add(leaf("catalyst_present", params));
        boolean consume = !catalyst.has("consume_catalyst") || catalyst.get("consume_catalyst").getAsBoolean();
        if (consume) {
            consumption.add("catalyst_items", items.deepCopy());
        }
    }

    private static void migrateFluid(JsonObject entry, JsonArray leaves, JsonObject consumption) {
        JsonElement fluidElement = entry.remove("inner_fluid");
        if (fluidElement == null || !fluidElement.isJsonObject()) {
            return;
        }
        JsonObject fluid = fluidElement.getAsJsonObject();
        JsonElement fluidId = fluid.get("inner_fluid");
        if (!isNonBlankString(fluidId)) {
            return;
        }

        boolean requireSource = !fluid.has("require_source") || fluid.get("require_source").getAsBoolean();
        JsonObject params = new JsonObject();
        params.add("fluid", fluidId.deepCopy());
        params.addProperty("require_source", requireSource);
        leaves.add(leaf("fluid_present", params));

        if (fluid.has("consume_fluid") && fluid.get("consume_fluid").getAsBoolean()) {
            JsonObject fluidConsumption = new JsonObject();
            fluidConsumption.add("fluid", fluidId.deepCopy());
            fluidConsumption.addProperty("require_source", requireSource);
            consumption.add("inner_fluid", fluidConsumption);
        }
    }

    private static JsonObject leaf(String path, JsonObject params) {
        JsonObject leaf = new JsonObject();
        leaf.addProperty("type", Constants.MOD_ID + ":" + path);
        leaf.add("params", params);
        leaf.addProperty("negated", false);
        return leaf;
    }

    private static JsonArray arrayOrEmpty(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static boolean isNonBlankString(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()
                && !element.getAsString().isBlank();
    }

    /** 保存迁移后的 JSON 树及是否发生版本变化。 */
    public record MigrationResult(JsonElement json, boolean migrated) {
    }
}
