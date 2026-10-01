package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

/**
 * 一条配置问题记录：统一承载校验、迁移与加载期的错误与告警。
 * origin 用于定位来源（内置数据包 / 世界数据包 / 覆盖层文件路径），fieldPath 用于定位字段。
 */
public record Issue(IssueSeverity severity, String message, @Nullable String origin, @Nullable String fieldPath) {

    // 构造一条错误级问题
    public static Issue error(String message, @Nullable String origin, @Nullable String fieldPath) {
        return new Issue(IssueSeverity.ERROR, message, origin, fieldPath);
    }

    // 构造一条告警级问题
    public static Issue warn(String message, @Nullable String origin, @Nullable String fieldPath) {
        return new Issue(IssueSeverity.WARN, message, origin, fieldPath);
    }

    // 单行文本形式，供日志与命令输出复用
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(severity).append("] ");
        if (origin != null && !origin.isEmpty()) {
            sb.append(origin);
            if (fieldPath != null && !fieldPath.isEmpty()) {
                sb.append('#').append(fieldPath);
            }
            sb.append(": ");
        } else if (fieldPath != null && !fieldPath.isEmpty()) {
            sb.append(fieldPath).append(": ");
        }
        sb.append(message);
        return sb.toString();
    }
}
