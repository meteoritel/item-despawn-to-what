package com.meteorite.itemdespawntowhat.config.condition.type;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;

import java.util.List;

/**
 * 汇总内置条件类型使用的轻量参数 DTO。
 */
public final class BuiltinConditionParameters {
    private BuiltinConditionParameters() {
    }

    /** 维度条件参数。 */
    public record Dimension(@SerializedName("dimension") String dimension) {
    }

    /** 露天条件不需要额外参数。 */
    public record Outdoor() {
    }

    /** 六面方块条件参数。 */
    public record SurroundingBlocksParameter(
            @SerializedName("blocks") SurroundingBlocks blocks
    ) {
    }

    /** 催化剂在场条件参数。 */
    public record CatalystPresent(
            @SerializedName("items") List<CatalystItems.CatalystEntry> items
    ) {
    }

    /** 流体在场条件参数。 */
    public record FluidPresent(
            @SerializedName("fluid") String fluid,
            @SerializedName("require_source") boolean requireSource
    ) {
    }

    /** 生物群系条件参数，支持注册项或标签。 */
    public record Biome(@SerializedName("biome") String biome) {
    }

    /** 天气条件参数。 */
    public record Weather(@SerializedName("weather") WeatherMode weather) {
    }

    /** 天气状态；ANY 仅用于客户端表示未设置条件。 */
    public enum WeatherMode {
        ANY("condition.itemdespawntowhat.weather.any"),
        CLEAR("condition.itemdespawntowhat.weather.clear"),
        RAINING("condition.itemdespawntowhat.weather.raining"),
        THUNDERING("condition.itemdespawntowhat.weather.thundering");

        private final String descriptionId;

        WeatherMode(String descriptionId) {
            this.descriptionId = descriptionId;
        }

        public String getDescriptionId() {
            return descriptionId;
        }
    }
}
