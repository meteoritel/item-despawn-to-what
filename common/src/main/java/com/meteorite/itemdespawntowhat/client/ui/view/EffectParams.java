package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 效果类型参数的声明式规格：字段名、i18n key、控件种类与默认值。
 * 表单层据此自动生成控件；新增效果类型只需在此登记规格，不必改表单代码。
 */
public final class EffectParams {

    private static final String EDIT = "gui.itemdespawntowhat.edit.";

    // 内置效果类型的参数字段规格（顺序即表单顺序）
    private static final Map<ResourceLocation, List<ParamSpec>> SPECS = Map.of(
            id("spawn_item"), List.of(
                    ParamSpec.text("item", EDIT + "result_id", true),
                    ParamSpec.integer("count", EDIT + "effect.count", "1"),
                    ParamSpec.integer("limit", EDIT + "result_limit", ""),
                    ParamSpec.integer("radius", EDIT + "search_radius", "")),
            id("spawn_entity"), List.of(
                    ParamSpec.text("entity", EDIT + "effect.entity", true),
                    ParamSpec.integer("count", EDIT + "effect.count", "1"),
                    ParamSpec.integer("age", EDIT + "effect.age", "0"),
                    ParamSpec.integer("limit", EDIT + "result_limit", ""),
                    ParamSpec.integer("radius", EDIT + "search_radius", "")),
            id("place_block"), List.of(
                    ParamSpec.text("block", EDIT + "effect.block", false),
                    ParamSpec.bool("use_source_block", EDIT + "effect.use_source_block", "false"),
                    ParamSpec.enumOf("shape", EDIT + "block_place_shape", "square", List.of("square", "circle", "cross")),
                    ParamSpec.integer("count", EDIT + "effect.count", "1"),
                    ParamSpec.integer("radius", EDIT + "search_radius", "6"),
                    ParamSpec.integer("limit", EDIT + "result_limit", "")),
            id("spawn_xp"), List.of(
                    ParamSpec.integer("amount", EDIT + "xp_per_item", "1"),
                    ParamSpec.bool("per_source_item", EDIT + "effect.per_source_item", "false")),
            id("loot_table"), List.of(
                    ParamSpec.text("loot_table", EDIT + "effect.loot_table", true),
                    ParamSpec.decimal("luck", EDIT + "luck", "0.0")),
            id("lightning"), List.of(
                    ParamSpec.integer("count", EDIT + "effect.count", "1")),
            id("explosion"), List.of(
                    ParamSpec.decimal("power", EDIT + "explosion_power", "3.0"),
                    ParamSpec.bool("fire", EDIT + "explosion_fire", "false"),
                    ParamSpec.bool("visual_only", EDIT + "visual_only", "false")),
            id("arrow_rain"), List.of(
                    ParamSpec.integer("count", EDIT + "effect.count", "16"),
                    ParamSpec.enumOf("pickup", EDIT + "arrow_pickup_status", "disallowed",
                            List.of("disallowed", "allowed", "creative_only"))),
            id("weather"), List.of(
                    ParamSpec.enumOf("mode", EDIT + "weather_mode", "rain", List.of("rain", "clear")),
                    ParamSpec.integer("duration_ticks", EDIT + "weather_duration_ticks", "6000"),
                    ParamSpec.bool("thundering", EDIT + "is_thundering", "false"))
    );

    private EffectParams() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 取某效果类型的参数规格；未登记（第三方类型）返回空列表，表单退化为仅通用字段
    public static List<ParamSpec> specsOf(@Nullable ResourceLocation effectType) {
        if (effectType == null) {
            return List.of();
        }
        return SPECS.getOrDefault(effectType, List.of());
    }

    // 模板新建时写入的类型参数默认值（空默认值不写入，避免产出服务端必填失败的空串）
    public static JsonObject defaults(@Nullable ResourceLocation effectType) {
        JsonObject json = new JsonObject();
        for (ParamSpec spec : specsOf(effectType)) {
            if (spec.defaultValue() == null || spec.defaultValue().isBlank()) {
                continue;
            }
            switch (spec.kind()) {
                case INTEGER -> json.addProperty(spec.key(), parseInt(spec.defaultValue(), 0));
                case DECIMAL -> json.addProperty(spec.key(), parseDouble(spec.defaultValue(), 0.0D));
                case BOOLEAN -> json.addProperty(spec.key(), Boolean.parseBoolean(spec.defaultValue()));
                default -> json.addProperty(spec.key(), spec.defaultValue());
            }
        }
        return json;
    }

    // 模板新建时的完整效果 JSON：type + 默认参数
    public static JsonObject defaultEffectJson(ResourceLocation effectType) {
        JsonObject json = defaults(effectType);
        json.addProperty("type", effectType.toString());
        return json;
    }

    // 文本转整数，失败回退
    public static int parseInt(@Nullable String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // 文本转小数，失败回退
    public static double parseDouble(@Nullable String text, double fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}