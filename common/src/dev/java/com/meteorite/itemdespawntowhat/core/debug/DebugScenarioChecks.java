package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.Set;

/** 唯一场景断言格式：精确值、数值上下界及对实际指标的引用，所有结果逐项输出。 */
final class DebugScenarioChecks {
    private final JsonArray results = new JsonArray();
    private final JsonObject metrics;

    // 断言只消费观测值，不重新运行规则或效果来推测结果。
    DebugScenarioChecks(JsonObject metrics) { this.metrics = metrics; }

    // 资源指标支持精确值或 min/max 范围；拼写错误必须报错，不能默认为通过。
    void metrics(JsonObject expected) {
        expected.entrySet().forEach(entry -> {
            JsonElement actual = metrics.get(entry.getKey());
            if (actual == null) { throw new IllegalArgumentException("未观测的场景指标：" + entry.getKey()); }
            check(entry.getKey(), entry.getValue(), actual);
        });
    }

    // 共用基础约束无需在每份资源重复声明。
    void equal(String name, long expected, long actual) { check(name, new JsonPrimitive(expected), new JsonPrimitive(actual)); }

    void equal(String name, boolean expected, boolean actual) { check(name, new JsonPrimitive(expected), new JsonPrimitive(actual)); }

    void minimum(String name, double bound, double actual) { range(name, "min", bound, actual); }

    void maximum(String name, double bound, double actual) { range(name, "max", bound, actual); }

    void missing(String name) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("expected", "observed");
        result.add("actual", JsonNull.INSTANCE);
        result.addProperty("evaluation_error", "missing_observation");
        result.addProperty("pass", false);
        results.add(result);
    }

    private void range(String name, String kind, double bound, double actual) {
        if (!Double.isFinite(actual)) { missing(name); return; }
        JsonObject expected = new JsonObject();
        expected.addProperty(kind, bound);
        check(name, expected, new JsonPrimitive(actual));
    }

    // 未声明的实际物品与候选也按零预期校对，避免漏报多产。
    void counts(String prefix, JsonObject expected, Map<String, Long> actual) {
        expected.entrySet().forEach(entry -> check(prefix + "/" + entry.getKey(), entry.getValue(),
                new JsonPrimitive(actual.getOrDefault(entry.getKey(), 0L))));
        actual.forEach((key, value) -> { if (!expected.has(key)) { equal(prefix + "/" + key, 0, value); } });
    }

    // END 和 CHECK_FAILED 使用同一份断言结果。
    JsonArray results() { return results; }

    // 范围只允许数值操作；@名称引用同一轮实际指标（如平台寿命或最早提交年龄）。
    private void check(String name, JsonElement expected, JsonElement actual) {
        boolean passed;
        JsonElement resolved;
        if (expected.isJsonObject()) {
            JsonObject range = expected.getAsJsonObject();
            if (range.isEmpty() || !Set.of("min", "max").containsAll(range.keySet())) {
                throw new IllegalArgumentException("非法场景断言范围：" + name);
            }
            JsonObject resolvedRange = new JsonObject();
            passed = true;
            for (String key : range.keySet()) {
                JsonElement bound = operand(range.get(key));
                resolvedRange.add(key, bound);
                int compared = actual.getAsBigDecimal().compareTo(bound.getAsBigDecimal());
                if (key.equals("min") ? compared < 0 : compared > 0) { passed = false; }
            }
            resolved = resolvedRange;
        } else {
            resolved = operand(expected);
            passed = resolved.equals(actual);
        }
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.add("expected", resolved.deepCopy());
        result.add("actual", actual.deepCopy());
        result.addProperty("pass", passed);
        results.add(result);
    }

    // 引用不存在时报告定义错误，与实际值不符合预期区分。
    private JsonElement operand(JsonElement expected) {
        if (expected.isJsonPrimitive() && expected.getAsJsonPrimitive().isString() && expected.getAsString().startsWith("@")) {
            JsonElement value = metrics.get(expected.getAsString().substring(1));
            if (value == null) { throw new IllegalArgumentException("断言引用了未知指标：" + expected); }
            return value;
        }
        return expected;
    }
}
