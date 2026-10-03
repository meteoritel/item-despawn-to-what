package com.meteorite.itemdespawntowhat.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 消失方式：物品被销毁（消失）的途径类别，一条规则可同时声明多种。
 * 未声明时按 natural 处理（对齐 PLAN §4.2「消失方式集合默认仅自然消失」）。
 * JSON 取值为小写下划线，解析大小写不敏感，未知取值明确报错，不做静默回退。
 */
public enum TriggerKind {

    // 自然消失（生命周期到期）
    NATURAL,
    // 火焰烧毁
    FIRE,
    // 岩浆销毁
    LAVA,
    // 仙人掌销毁
    CACTUS;

    // 全部允许取值（错误提示用）
    private static final String ALLOWED = Arrays.stream(values())
            .map(TriggerKind::key)
            .collect(Collectors.joining(" / "));

    // JSON 取值：小写下划线
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    // 线上编解码器
    public static final Codec<TriggerKind> CODEC = Codec.STRING.comapFlatMap(TriggerKind::parse, TriggerKind::key);

    // 解析 JSON 取值：去空白 + 大小写不敏感
    private static DataResult<TriggerKind> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataResult.error(() -> "消失方式不能为空，允许值: " + ALLOWED);
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (TriggerKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return DataResult.success(kind);
            }
        }
        return DataResult.error(() -> "未知的消失方式: " + raw + "，允许值: " + ALLOWED);
    }
}
