package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 开发阶段计划的唯一目录；复用场景声明，不创建数据包或另一条规则执行线路。 */
final class DebugPipelineCatalog {
    static final int DEFAULT_GAP_SECONDS = 5;
    static final int MIN_GAP_SECONDS = 3;
    static final int MAX_GAP_SECONDS = 30;
    private static final String ROOT = "/idtw-debug/pipelines/";

    /** 无数量参数、固定数量及逐档数量上限三种阶段参数。 */
    enum Quantity { NONE, FIXED, CEILING }

    /** 已解析场景引用；-1 数量表示启动时由阶段参数代入。 */
    record Step(DebugScenarioSpec scene, int entities, int seconds) {}

    /** 展开后的轮次，原始场景配置保持不可变。 */
    record PlannedStep(Step step, int round) {}

    /** 阶段声明与展开；重复次数和档位都由开发资源提供。 */
    record Stage(String name, Quantity quantity, int defaultEntities, int rounds,
                 boolean performanceGate, List<Step> steps) {
        // 启动时形成有限串行计划；ceiling 只包含不超过上限的声明档位。
        List<PlannedStep> plan(int entities) {
            List<PlannedStep> plan = new ArrayList<>();
            for (int round = 1; round <= rounds; round++) {
                for (Step step : steps) {
                    if (quantity == Quantity.CEILING && step.entities() > entities) { continue; }
                    int count = step.entities() < 0 ? entities : step.entities();
                    plan.add(new PlannedStep(new Step(step.scene(), count, step.seconds()), round));
                }
            }
            return List.copyOf(plan);
        }
    }

    /** 发布环境不触发资源读取。 */
    private static final class Holder {
        private static final Map<String, Stage> STAGES = load();
    }

    // 工具类不创建实例。
    private DebugPipelineCatalog() {}

    // 同一目录提供字面量补全与帮助，新增阶段无需修改 Java 名称列表。
    static List<Stage> stages() { return List.copyOf(Holder.STAGES.values()); }

    // 阶段只能来自 manifest，禁止任意路径读取。
    static Stage find(String name) {
        if (!DebugMode.ENABLED) { throw new IllegalStateException("测试阶段仅在开发环境启用"); }
        Stage stage = Holder.STAGES.get(name);
        if (stage == null) { throw new IllegalArgumentException("未知测试阶段：" + name); }
        return stage;
    }

    // 严格校验元数据、场景引用和数量，保证自动运行无法绕过1000上限。
    private static Map<String, Stage> load() {
        if (!DebugMode.ENABLED) { return Map.of(); }
        Map<String, Stage> stages = new LinkedHashMap<>();
        for (JsonElement entry : read("manifest.json").getAsJsonArray()) {
            String name = entry.getAsString();
            if (!name.matches("[a-z][a-z0-9_]*") || Set.of("help", "status", "stop").contains(name)) {
                throw new IllegalArgumentException("非法或保留阶段名称：" + name);
            }
            JsonObject body = read(name + ".json").getAsJsonObject();
            known(body, Set.of("schema_version", "quantity", "default_entities", "rounds", "performance_gate", "steps"));
            if (integer(body.get("schema_version")) != 1) { throw new IllegalArgumentException("不支持的阶段版本：" + name); }
            Quantity quantity = Quantity.valueOf(body.get("quantity").getAsString().toUpperCase(Locale.ROOT));
            int defaultEntities = integer(body.get("default_entities"));
            int rounds = integer(body.get("rounds"));
            if (rounds < 1 || rounds > 3 || defaultEntities > DebugScenarioSpec.MAX_BENCHMARK_SOURCES
                    || quantity == Quantity.NONE && defaultEntities != 0 || quantity != Quantity.NONE && defaultEntities < 1
                    || quantity == Quantity.CEILING && defaultEntities < 100) {
                throw new IllegalArgumentException("阶段默认数量或重复次数越界：" + name);
            }
            List<Step> steps = new ArrayList<>();
            for (JsonElement raw : body.getAsJsonArray("steps")) {
                JsonObject step = raw.getAsJsonObject();
                known(step, Set.of("scenario", "entities", "seconds"));
                String key = step.get("scenario").getAsString();
                if (!key.matches("(run|bench)/[a-z][a-z0-9_]*")) { throw new IllegalArgumentException("非法场景引用：" + key); }
                DebugScenarioSpec spec = DebugScenarioCatalog.find(key.substring(key.indexOf('/') + 1), key.startsWith("bench/"));
                JsonElement count = step.get("entities");
                boolean variable = count.isJsonPrimitive() && count.getAsJsonPrimitive().isString() && count.getAsString().equals("$entities");
                int entities = variable ? -1 : integer(count);
                int seconds = integer(step.get("seconds"));
                if (variable && (quantity != Quantity.FIXED || !spec.benchmark())
                        || !variable && (entities < 0 || entities > DebugScenarioSpec.MAX_BENCHMARK_SOURCES)
                        || !spec.benchmark() && entities != spec.sources()
                        || seconds < 10 || seconds > 300) { throw new IllegalArgumentException("阶段步骤参数无效：" + key); }
                steps.add(new Step(spec, entities, seconds));
            }
            if (steps.isEmpty() || steps.size() * rounds > 64) { throw new IllegalArgumentException("阶段步骤数量必须为1～64：" + name); }
            Stage stage = new Stage(name, quantity, defaultEntities, rounds, body.get("performance_gate").getAsBoolean(), List.copyOf(steps));
            if (stages.putIfAbsent(name, stage) != null) { throw new IllegalArgumentException("重复阶段：" + name); }
        }
        return Collections.unmodifiableMap(stages);
    }

    // 小数不能悄悄变成另一种压力负载。
    private static int integer(JsonElement value) { return value.getAsBigDecimal().intValueExact(); }

    // 拼写错误在开发命令注册时显式失败。
    private static void known(JsonObject body, Set<String> fields) {
        for (String field : body.keySet()) { if (!fields.contains(field)) { throw new IllegalArgumentException("未知阶段字段：" + field); } }
    }

    // 类路径资源独立于 builtin datapack 和 overlay。
    private static JsonElement read(String name) {
        try (var stream = DebugPipelineCatalog.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) { throw new IllegalArgumentException("阶段资源缺失：" + name); }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failure) { throw new IllegalStateException("阶段资源读取失败：" + name, failure); }
    }
}
