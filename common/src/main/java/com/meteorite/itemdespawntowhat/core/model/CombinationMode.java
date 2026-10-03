package com.meteorite.itemdespawntowhat.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 候选结果组合模式：同一转化组选出候选结果的方式。
 * round_robin（默认，轮询）与 priority（优先，按声明顺序取第一个可用候选）由阶段 4 执行侧落地，
 * 阶段 1 只负责承载、往返与本层校验。
 */
public enum CombinationMode {

    // 轮询：按声明顺序轮流选取（缺省）
    ROUND_ROBIN,
    // 优先：按声明顺序取第一个可用候选
    PRIORITY;

    // 全部允许取值（错误提示用）
    private static final String ALLOWED = Arrays.stream(values())
            .map(CombinationMode::key)
            .collect(Collectors.joining(" / "));

    // JSON 取值：小写下划线
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    // 线上编解码器
    public static final Codec<CombinationMode> CODEC = Codec.STRING.comapFlatMap(CombinationMode::parse, CombinationMode::key);

    // 解析 JSON 取值：去空白 + 大小写不敏感
    private static DataResult<CombinationMode> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataResult.error(() -> "组合模式不能为空，允许值: " + ALLOWED);
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (CombinationMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return DataResult.success(mode);
            }
        }
        return DataResult.error(() -> "未知的组合模式: " + raw + "，允许值: " + ALLOWED);
    }
}
