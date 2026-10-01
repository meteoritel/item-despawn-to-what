package com.meteorite.itemdespawntowhat.core.load;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 文件解析后、模型解码前的原始规则条目。
 * body 始终保留文件中的原始 JSON（不提前丢弃字段）；disabled / delete 是覆盖层控制字段，
 * 由合并阶段消费，不作为规则字段解码。
 * 合并前的条目中，disabled / delete 表示"声明"；合并结果中 delete 恒为 false，
 * disabled 表示"该规则已被覆盖层停用"（解码时据此注入 enabled=false）。
 */
public record RawRuleEntry(
        // 有效 id：文件中显式声明，或由文件路径推导；无法确定时为 null
        @Nullable ResourceLocation id,
        // 原始 JSON 对象（未加工）
        JsonObject body,
        // 来源标识
        RuleOrigin origin,
        // 是否声明 disabled 控制字段
        boolean disabled,
        // 是否声明 delete 控制字段
        boolean delete,
        // 该条在本文件中的位置（数组元素形如 [2]，单条对象文件为空串）
        String fieldPath
) {

    public RawRuleEntry {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(origin, "origin");
        fieldPath = fieldPath == null ? "" : fieldPath;
    }

    // 是否为覆盖层控制条目：只表达停用 / 删除，不携带规则内容
    public boolean isControl() {
        return disabled || delete;
    }
}
