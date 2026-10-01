package com.meteorite.itemdespawntowhat.core.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 内置类型共用的枚举编解码器。
 * JSON 取值统一为小写下划线（如 creative_only），解析时大小写不敏感，未知取值给出可选值列表。
 */
public final class EnumCodecs {

    private EnumCodecs() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 构造小写下划线形式的枚举编解码器
    public static <E extends Enum<E>> Codec<E> lowerCase(Class<E> enumType) {
        return Codec.STRING.comapFlatMap(
                raw -> parse(enumType, raw),
                value -> value.name().toLowerCase(Locale.ROOT)
        );
    }

    // 解析单个取值：大小写不敏感，未知取值给出可读错误
    private static <E extends Enum<E>> DataResult<E> parse(Class<E> enumType, String raw) {
        if (raw == null || raw.isBlank()) {
            return DataResult.error(() -> "取值不能为空，可选: " + candidates(enumType));
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (E candidate : enumType.getEnumConstants()) {
            if (candidate.name().equals(normalized)) {
                return DataResult.success(candidate);
            }
        }
        return DataResult.error(() -> "未知取值: " + raw + "，可选: " + candidates(enumType));
    }

    // 逗号分隔的可选值列表（小写下划线形式）
    private static <E extends Enum<E>> String candidates(Class<E> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
