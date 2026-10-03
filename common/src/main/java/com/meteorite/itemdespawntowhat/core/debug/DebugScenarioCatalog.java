package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 开发专用场景目录：manifest 是场景注册的唯一来源，发布环境不读取资源。 */
final class DebugScenarioCatalog {
    static final String ROOT = "/idtw-debug/scenarios/";

    /** 延迟到首次开发命令注册时读取，避免发布环境解析场景资源。 */
    private static final class Holder {
        private static final Map<String, DebugScenarioSpec> ENTRIES = load();
    }

    // 工具类不创建实例。
    private DebugScenarioCatalog() {}

    // 同一个目录驱动命令注册、帮助及场景查找。
    static List<DebugScenarioSpec> entries(boolean benchmark) {
        return Holder.ENTRIES.values().stream().filter(spec -> spec.benchmark() == benchmark).toList();
    }

    // 固定 manifest 白名单，禁止把指令参数拼成任意资源路径。
    static DebugScenarioSpec find(String name, boolean benchmark) {
        if (!DebugMode.ENABLED) { throw new IllegalStateException("场景仅在开发环境启用"); }
        DebugScenarioSpec spec = Holder.ENTRIES.get((benchmark ? "bench/" : "run/") + name);
        if (spec == null) { throw new IllegalArgumentException("场景未登记：" + name); }
        return spec;
    }

    // 目录完整校验后才发布不可变集合，禁止重复注册和路径穿越。
    private static Map<String, DebugScenarioSpec> load() {
        if (!DebugMode.ENABLED) { return Map.of(); }
        Map<String, DebugScenarioSpec> entries = new LinkedHashMap<>();
        for (JsonElement entry : read("manifest.json").getAsJsonArray()) {
            String key = entry.getAsString();
            if (!key.matches("(run|bench)/[a-z][a-z0-9_]*")) { throw new IllegalArgumentException("非法场景路径：" + key); }
            if (entries.putIfAbsent(key, parse(key, read(key + ".json").getAsJsonObject())) != null) {
                throw new IllegalArgumentException("场景重复登记：" + key);
            }
        }
        return java.util.Collections.unmodifiableMap(entries);
    }

    // 范围与枚举错误在命令注册时显式报告，不等到场景中途才发现。
    private static DebugScenarioSpec parse(String key, JsonObject body) {
        rejectUnknown(body, Set.of("schema_version", "inputs", "seconds", "setup", "actions", "expected", "rules"));
        if (body.get("schema_version").getAsInt() != 1) { throw new IllegalArgumentException("不支持的场景版本：" + key); }
        JsonObject input = body.getAsJsonObject("inputs");
        rejectUnknown(input, Set.of("sources", "stack_size", "catalyst_count", "source_item", "catalyst_item"));
        boolean benchmark = key.startsWith("bench/");
        int sources = integer(input, "sources", benchmark ? 0 : 1,
                benchmark ? DebugScenarioSpec.MAX_BENCHMARK_SOURCES : DebugScenarioSpec.MAX_FUNCTIONAL_SOURCES);
        int stack = integer(input, "stack_size", 1, 64);
        int catalyst = integer(input, "catalyst_count", 0, 64);
        int seconds = integer(body, "seconds", 10, 300);
        List<DebugScenarioSpec.Action> actions = new ArrayList<>();
        for (JsonElement element : body.getAsJsonArray("actions")) {
            JsonObject action = element.getAsJsonObject();
            rejectUnknown(action, Set.of("type", "after_ticks", "after_event", "offset_y"));
            actions.add(new DebugScenarioSpec.Action(DebugScenarioSpec.ActionType.valueOf(action.get("type").getAsString().toUpperCase(Locale.ROOT)),
                    integer(action, "after_ticks", 0, 6000), action.get("after_event").getAsString(), integer(action, "offset_y", -64, 64)));
        }
        if (actions.size() > 16) { throw new IllegalArgumentException("场景动作超过16个：" + key); }
        DebugScenarioSpec.Setup setup = DebugScenarioSpec.Setup.valueOf(body.get("setup").getAsString().toUpperCase(Locale.ROOT));
        if (setup == DebugScenarioSpec.Setup.EXCLUDED && sources != 2) { throw new IllegalArgumentException("excluded准备方式需要2个源：" + key); }
        if (benchmark && (catalyst != 0 || !actions.isEmpty() || setup != DebugScenarioSpec.Setup.NONE)) {
            throw new IllegalArgumentException("性能场景只允许普通源准备，不运行功能动作或催化剂夹具：" + key);
        }
        JsonObject expected = body.getAsJsonObject("expected");
        rejectUnknown(expected, Set.of("metrics", "outputs", "candidates"));
        if (!expected.getAsJsonObject("metrics").has("conversions") || !expected.getAsJsonObject("metrics").has("remaining_source_items")) {
            throw new IllegalArgumentException("场景必须声明提交次数和剩余源预期：" + key);
        }
        if (!expected.has("outputs")) { throw new IllegalArgumentException("场景必须声明实物产出预期：" + key); }
        return new DebugScenarioSpec(key, sources, stack, seconds, catalyst,
                ResourceLocation.parse(input.get("source_item").getAsString()), ResourceLocation.parse(input.get("catalyst_item").getAsString()),
                setup, List.copyOf(actions), expected, body.getAsJsonArray("rules"));
    }

    // 不允许未知元数据悄悄失效；规则本体交给正式 Codec 与语义校验。
    private static void rejectUnknown(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) { if (!allowed.contains(key)) { throw new IllegalArgumentException("未知场景字段：" + key); } }
    }

    // 输入必须是精确整数，不能把小数截断成另一个测试负载。
    private static int integer(JsonObject object, String key, int minimum, int maximum) {
        int value = object.get(key).getAsBigDecimal().intValueExact();
        if (value < minimum || value > maximum) { throw new IllegalArgumentException("场景字段超出范围：" + key); }
        return value;
    }

    // 使用类路径读取，IDEA 运行和两种打包方式使用同一路径。
    private static JsonElement read(String path) {
        try (var stream = DebugScenarioCatalog.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) { throw new IllegalArgumentException("场景资源缺失：" + path); }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failure) { throw new IllegalStateException("读取场景资源失败：" + path, failure); }
    }
}
