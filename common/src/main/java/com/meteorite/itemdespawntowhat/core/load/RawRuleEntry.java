package com.meteorite.itemdespawntowhat.core.load;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 文件解析后、模型解码前的原始规则条目。
 * body 始终保留文件中的原始 JSON（不提前丢弃字段）；启用状态属本服状态层，不在条目里表达，
 * 因此同一条目在不同存档里可以有不同的生效启用值。disabled / delete 不再是控制字段，
 * 出现时按普通未知字段处理，只告警、不影响合并与解码。
 */
public record RawRuleEntry(
        // 有效 id：文件中显式声明，或由文件路径推导；无法确定时为 null
        @Nullable ResourceLocation id,
        // 原始 JSON 对象（未加工）
        JsonObject body,
        // 来源标识
        RuleOrigin origin,
        // 该条在本文件中的位置（数组元素形如 [2]，单条对象文件为空串）
        String fieldPath
) {

    public RawRuleEntry {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(origin, "origin");
        fieldPath = fieldPath == null ? "" : fieldPath;
    }
}
