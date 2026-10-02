package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/***
 * 结构化问题：跨网络传输，客户端不得解析中文日志或字符串拼接来决定状态。
 * severity 取值 error/warning/info；origin 取值 overlay/datapack/runtime/network；
 * messageCode 为全限定翻译 key，messageArgs 供 Component.translatable 使用。
 */
public record RuleIssue(String severity, @Nullable String ruleId, String origin,
                        String fieldPath, String messageCode, List<String> messageArgs,
                        String fallbackMessage) {

    // 严重度取值
    public static final String SEVERITY_ERROR = "error";
    public static final String SEVERITY_WARNING = "warning";
    public static final String SEVERITY_INFO = "info";

    // 来源取值
    public static final String ORIGIN_OVERLAY = "overlay";
    public static final String ORIGIN_DATAPACK = "datapack";
    public static final String ORIGIN_RUNTIME = "runtime";
    public static final String ORIGIN_NETWORK = "network";

    // 线上 JSON 键；字段名与契约 §3.5 一一对应
    public static final String KEY_SEVERITY = "severity";
    public static final String KEY_RULE_ID = "rule_id";
    public static final String KEY_ORIGIN = "origin";
    public static final String KEY_FIELD_PATH = "field_path";
    public static final String KEY_MESSAGE_CODE = "message_code";
    public static final String KEY_MESSAGE_ARGS = "message_args";
    public static final String KEY_FALLBACK_MESSAGE = "fallback_message";

    public RuleIssue {
        severity = severity == null || severity.isBlank() ? SEVERITY_ERROR : severity;
        origin = origin == null || origin.isBlank() ? ORIGIN_RUNTIME : origin;
        fieldPath = fieldPath == null ? "" : fieldPath;
        messageCode = messageCode == null ? "" : messageCode;
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        fallbackMessage = fallbackMessage == null ? "" : fallbackMessage;
    }

    // 错误问题
    public static RuleIssue error(String messageCode, String fallbackMessage, List<String> args) {
        return new RuleIssue(SEVERITY_ERROR, null, ORIGIN_RUNTIME, "", messageCode, args, fallbackMessage);
    }

    // 警告问题
    public static RuleIssue warning(String messageCode, String fallbackMessage, List<String> args) {
        return new RuleIssue(SEVERITY_WARNING, null, ORIGIN_RUNTIME, "", messageCode, args, fallbackMessage);
    }

    // 问题序列化：未识别字段不参与，网络对端只认本契约字段
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_SEVERITY, severity);
        if (ruleId == null) {
            json.add(KEY_RULE_ID, com.google.gson.JsonNull.INSTANCE);
        } else {
            json.addProperty(KEY_RULE_ID, ruleId);
        }
        json.addProperty(KEY_ORIGIN, origin);
        json.addProperty(KEY_FIELD_PATH, fieldPath);
        json.addProperty(KEY_MESSAGE_CODE, messageCode);
        JsonArray args = new JsonArray();
        for (String arg : messageArgs) {
            args.add(arg);
        }
        json.add(KEY_MESSAGE_ARGS, args);
        json.addProperty(KEY_FALLBACK_MESSAGE, fallbackMessage);
        return json;
    }

    // 问题反序列化；非对象返回 null，调用方按缺失处理
    public static @Nullable RuleIssue fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        List<String> args = new ArrayList<>();
        if (json.has(KEY_MESSAGE_ARGS) && json.get(KEY_MESSAGE_ARGS).isJsonArray()) {
            for (JsonElement arg : json.getAsJsonArray(KEY_MESSAGE_ARGS)) {
                args.add(arg.isJsonPrimitive() ? arg.getAsString() : arg.toString());
            }
        }
        String ruleId = json.has(KEY_RULE_ID) && json.get(KEY_RULE_ID).isJsonPrimitive()
                ? json.get(KEY_RULE_ID).getAsString() : null;
        return new RuleIssue(
                string(json, KEY_SEVERITY, SEVERITY_ERROR),
                ruleId,
                string(json, KEY_ORIGIN, ORIGIN_RUNTIME),
                string(json, KEY_FIELD_PATH, ""),
                string(json, KEY_MESSAGE_CODE, ""),
                args,
                string(json, KEY_FALLBACK_MESSAGE, ""));
    }

    // 读取字符串字段；缺失或非字符串返回默认值
    private static String string(JsonObject json, String key, String fallback) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            return json.get(key).getAsString();
        }
        return fallback;
    }
}
