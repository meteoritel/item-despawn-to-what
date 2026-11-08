package com.meteorite.itemdespawntowhat.core.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.function.Function;

/** 已发布 JSON 格式的连续转换链；缺省版本固定，转换拥有独立文档且不接触存储。 */
public record JsonDocumentMigrations(String versionField, int baselineVersion, int currentVersion,
        List<Function<JsonObject, DataResult<JsonObject>>> steps) {

    /** 转换步骤按基线开始连续登记，构造时发现缺口属于声明错误。 */
    public JsonDocumentMigrations {
        steps = List.copyOf(steps);
        if (baselineVersion < 1 || currentVersion != baselineVersion + steps.size()) {
            throw new IllegalArgumentException("格式版本与连续转换步骤不一致: " + versionField);
        }
    }

    /** 当前文档独立于输入；源版本用于提交报告与保留旧格式备份。 */
    public record Result(JsonObject document, int sourceVersion, int targetVersion) { }

    /**
     * 严格识别文件版本并顺序转换；成功输出总是显式携带目标版本。
     * @param source 原始文档，保持不变
     * @return 完整当前文档或带版本阶段的错误，不交付部分转换结果
     */
    public DataResult<Result> upgrade(JsonObject source) {
        int version = baselineVersion;
        if (source.has(versionField)) {
            JsonElement raw = source.get(versionField);
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                return DataResult.error(() -> versionField + " 必须是整数");
            }
            try {
                version = raw.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException | NumberFormatException invalid) {
                return DataResult.error(() -> versionField + " 必须是范围内的整数: " + raw);
            }
        }
        int sourceVersion = version;
        if (version < baselineVersion || version > currentVersion) {
            return DataResult.error(() -> "不支持的 " + versionField + ": " + sourceVersion
                    + "（支持 " + baselineVersion + ".." + currentVersion + "）");
        }
        JsonObject document = source.deepCopy();
        while (version < currentVersion) {
            int from = version;
            DataResult<JsonObject> converted = steps.get(version - baselineVersion).apply(document);
            if (converted.error().isPresent()) {
                String reason = converted.error().orElseThrow().message();
                return DataResult.error(() -> versionField + " " + from + " -> " + (from + 1) + ": " + reason);
            }
            document = converted.result().orElseThrow();
            document.addProperty(versionField, ++version);
        }
        document.addProperty(versionField, currentVersion);
        return DataResult.success(new Result(document, sourceVersion, currentVersion));
    }
}
