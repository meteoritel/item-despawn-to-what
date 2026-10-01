package com.meteorite.itemdespawntowhat.core.load;

import org.jetbrains.annotations.Nullable;

/**
 * 规则文件路径与字段路径的常量及工具。
 * 数据包层目录相对 {@code data/<ns>/}，覆盖层目录相对 {@code config/itemdespawntowhat/}。
 */
public final class RulePaths {

    // 数据包内的规则目录（相对 data/<ns>/）
    public static final String DATAPACK_RULES_DIRECTORY = "idtw/rules";

    // config 覆盖层内的规则目录（相对 config/itemdespawntowhat/）
    public static final String OVERLAY_RULES_DIRECTORY = "rules";

    // 规则文件扩展名
    public static final String RULE_FILE_EXTENSION = ".json";

    private RulePaths() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 去除文件名末尾的 .json 扩展名；无该扩展名时原样返回
    public static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.endsWith(RULE_FILE_EXTENSION)
                ? fileName.substring(0, fileName.length() - RULE_FILE_EXTENSION.length())
                : fileName;
    }

    // 拼接字段路径：前缀为空时返回字段本身，否则以 '.' 连接（如 "[2]" + "source.items" → "[2].source.items"）
    public static String joinFieldPath(@Nullable String prefix, @Nullable String field) {
        String head = prefix == null ? "" : prefix;
        String tail = field == null ? "" : field;
        if (head.isEmpty()) {
            return tail;
        }
        return tail.isEmpty() ? head : head + "." + tail;
    }
}
