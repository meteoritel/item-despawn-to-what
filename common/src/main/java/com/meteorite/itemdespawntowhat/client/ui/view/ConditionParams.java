package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 新协议条件类型的参数规格：字段名与形态按 core/type/condition 的 Codec 对齐。
 * 未登记的类型返回 null（调用方退化为 JSON 编辑器），已登记但无参数的类型返回空列表。
 */
public final class ConditionParams {

    private static final String EDIT = "gui.itemdespawntowhat.edit.";
    private static final String COND = "gui.itemdespawntowhat.condition.";
    private static final String DIR = "gui.itemdespawntowhat.direction.";

    private static final Map<ResourceLocation, List<ParamSpec>> SPECS = Map.of(
            id("dimension"), List.of(
                    ParamSpec.list("dimensions", COND + "dimension", true)),
            id("outdoor"), List.of(),
            id("weather"), List.of(
                    ParamSpec.enumOf("weather", COND + "weather", "clear", List.of("clear", "rain", "thunder"))),
            id("surrounding_blocks"), List.of(
                    ParamSpec.text("up", DIR + "up", false),
                    ParamSpec.text("down", DIR + "down", false),
                    ParamSpec.text("north", DIR + "north", false),
                    ParamSpec.text("south", DIR + "south", false),
                    ParamSpec.text("east", DIR + "east", false),
                    ParamSpec.text("west", DIR + "west", false)),
            id("catalyst_present"), List.of(
                    ParamSpec.list("items", COND + "catalyst_items", true),
                    ParamSpec.integer("count", COND + "counts", "1")),
            id("fluid_present"), List.of(
                    ParamSpec.text("fluid", COND + "fluid", false),
                    ParamSpec.bool("require_source", EDIT + "inner_fluid.source_label", "true")),
            id("time_of_day"), List.of(
                    ParamSpec.integer("from", COND + "time_of_day.from", "0"),
                    ParamSpec.integer("to", COND + "time_of_day.to", "24000")),
            id("y_level"), List.of(
                    ParamSpec.integer("min", COND + "range.min", ""),
                    ParamSpec.integer("max", COND + "range.max", "")),
            id("light_level"), List.of(
                    ParamSpec.integer("min", COND + "range.min", ""),
                    ParamSpec.integer("max", COND + "range.max", ""))
    );

    // 参数为复杂嵌套结构、暂用 JSON 编辑器承载的类型
    static final List<String> JSON_PARAM_PATHS = List.of("biome");

    private ConditionParams() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 已登记类型返回规格列表（可为空）；未登记返回 null
    public static @Nullable List<ParamSpec> specsOf(@Nullable ResourceLocation conditionType) {
        if (conditionType == null) {
            return null;
        }
        return SPECS.get(conditionType);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
