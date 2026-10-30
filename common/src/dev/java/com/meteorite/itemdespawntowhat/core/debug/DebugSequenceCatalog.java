package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.Constants;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 命令与无人值守共用的有限序列目录，声明只引用既有场景与平台世界步骤。 */
final class DebugSequenceCatalog {
    private static final String RESOURCE = "/" + Constants.ID_NAMESPACE + "-debug/sequences/catalog.json";
    enum Kind { SCENE, BENCH, WORLD }
    record Step(Kind kind, String id, int entities, int seconds, int timeoutSeconds, int round) {
        String key() { return kind.name().toLowerCase(Locale.ROOT) + ":" + id; }
    }
    record Plan(String id, List<Step> steps, int gapSeconds, boolean performanceGate, boolean restart) {}
    private static final class Holder {
        static final Map<String, JsonObject> ENTRIES = load();
    }
    private DebugSequenceCatalog() {}

    static List<JsonObject> entries() { return Holder.ENTRIES.values().stream().map(JsonObject::deepCopy).toList(); }

    static JsonObject find(String id) {
        JsonObject entry = Holder.ENTRIES.get(id);
        if (entry == null) { throw new IllegalArgumentException("未知探针序列：" + id); }
        return entry.deepCopy();
    }

    static Plan plan(String id, String phase, int entities, int gap) {
        if (!Set.of("prepare", "resume").contains(phase)) { throw new IllegalArgumentException("未知阶段：" + phase); }
        if (id.startsWith("run/") || id.startsWith("bench/")) {
            if (!phase.equals("prepare")) { throw new IllegalArgumentException("单场景无恢复阶段"); }
            boolean bench = id.startsWith("bench/");
            var spec = DebugScenarioCatalog.find(id.substring(id.indexOf('/') + 1), bench);
            return new Plan(id, List.of(new Step(bench ? Kind.BENCH : Kind.SCENE, spec.name(),
                    spec.sources(), spec.seconds(), spec.seconds() + 45, 1)), 0, false, false);
        }
        JsonObject entry = find(id);
        int count = entities < 0 ? entry.get("default_entities").getAsBigDecimal().intValueExact() : entities;
        validateQuantity(entry, count);
        List<Step> steps = new ArrayList<>();
        boolean restart = expand(id, phase, count, new HashSet<>(), steps, Holder.ENTRIES);
        if (steps.isEmpty() || steps.size() > 256) { throw new IllegalArgumentException("序列展开数量无效：" + id); }
        int cooldown = gap < 0 ? entry.get("gap_seconds").getAsBigDecimal().intValueExact() : gap;
        if (cooldown < 0 || cooldown > 30) { throw new IllegalArgumentException("冷却越界"); }
        return new Plan(id, List.copyOf(steps), cooldown, entry.get("performance_gate").getAsBoolean(), restart);
    }

    private static boolean expand(String id, String phase, int entities, Set<String> visiting, List<Step> out, Map<String, JsonObject> entries) {
        if (!visiting.add(id)) { throw new IllegalArgumentException("序列引用循环：" + id); }
        JsonObject entry = entries.get(id);
        if (entry == null) { throw new IllegalArgumentException("未知序列引用：" + id); }
        boolean restart = entry.has("restart") && entry.get("restart").getAsBoolean();
        if (phase.equals("resume") && !entry.has("resume")) {
            for (JsonElement raw : entry.getAsJsonArray("steps")) {
                JsonObject step = raw.getAsJsonObject();
                if (step.has("sequence")) { expand(step.get("sequence").getAsString(), phase, entities, visiting, out, entries); }
            }
        } else {
            int rounds = entry.get("rounds").getAsBigDecimal().intValueExact();
            for (int round = 1; round <= rounds; round++) {
                for (JsonElement raw : entry.getAsJsonArray(phase.equals("resume") ? "resume" : "steps")) {
                    JsonObject step = raw.getAsJsonObject();
                    if (step.has("sequence")) {
                        restart |= expand(step.get("sequence").getAsString(), phase, entities, visiting, out, entries);
                        continue;
                    }
                    Kind kind = Kind.valueOf(step.get("kind").getAsString().toUpperCase(Locale.ROOT));
                    String key = step.get("id").getAsString();
                    if (kind == Kind.WORLD && !Set.of("damage", "loading", "budget", "state", "save", "restart").contains(key)) {
                        throw new IllegalArgumentException("未知世界步骤：" + key);
                    }
                    DebugScenarioSpec spec = kind == Kind.WORLD ? null : DebugScenarioCatalog.find(key, kind == Kind.BENCH);
                    int count = step.has("entities") ? step.get("entities").getAsString().equals("$entities")
                            ? entities : step.get("entities").getAsBigDecimal().intValueExact() : spec == null ? 0 : spec.sources();
                    if (entry.get("quantity").getAsString().equals("ceiling") && count > entities) { continue; }
                    int seconds = step.has("seconds") ? step.get("seconds").getAsBigDecimal().intValueExact() : spec == null ? 15 : spec.seconds();
                    if (count < 0 || count > DebugScenarioSpec.MAX_BENCHMARK_SOURCES || seconds < 10 || seconds > 300
                            || spec != null && !spec.benchmark() && count != spec.sources()) {
                        throw new IllegalArgumentException("序列步骤参数无效：" + key);
                    }
                    int timeout = step.has("timeout_seconds") ? step.get("timeout_seconds").getAsBigDecimal().intValueExact() : seconds + 45;
                    if (timeout < seconds || timeout > 600) { throw new IllegalArgumentException("步骤超时无效：" + key); }
                    if (out.size() >= 256) { throw new IllegalArgumentException("序列展开超过256步"); }
                    out.add(new Step(kind, key, count, seconds, timeout, round));
                }
            }
        }
        visiting.remove(id);
        return phase.equals("prepare") && restart;
    }

    private static void validateQuantity(JsonObject entry, int count) {
        String quantity = entry.get("quantity").getAsString();
        if (!Set.of("none", "fixed", "ceiling").contains(quantity) || count < 0 || count > 1000
                || quantity.equals("none") && count != 0 || quantity.equals("fixed") && count == 0
                || quantity.equals("ceiling") && count < 100) { throw new IllegalArgumentException("序列数量无效"); }
    }

    private static Map<String, JsonObject> load() {
        Map<String, JsonObject> entries = new LinkedHashMap<>();
        try (var stream = DebugSequenceCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) { throw new IllegalStateException("缺少序列目录"); }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema_version").getAsBigDecimal().intValueExact() != 1) { throw new IllegalArgumentException("序列版本无效"); }
            for (JsonElement raw : root.getAsJsonArray("sequences")) {
                JsonObject entry = raw.getAsJsonObject();
                String id = entry.get("id").getAsString();
                if (!id.matches("[a-z][a-z0-9_]*") || Set.of("help", "stop", "status").contains(id)
                        || entries.putIfAbsent(id, entry) != null) { throw new IllegalArgumentException("序列标识无效：" + id); }
                int rounds = entry.get("rounds").getAsBigDecimal().intValueExact();
                if (rounds < 1 || rounds > 3) { throw new IllegalArgumentException("轮次越界：" + id); }
                validateQuantity(entry, entry.get("default_entities").getAsBigDecimal().intValueExact());
            }
        } catch (IOException failure) { throw new IllegalStateException("读取序列目录失败", failure); }
        for (JsonObject entry : entries.values()) {
            int gap = entry.get("gap_seconds").getAsBigDecimal().intValueExact();
            if (gap < 0 || gap > 30) { throw new IllegalArgumentException("冷却越界"); }
            boolean restart = entry.has("restart") && entry.get("restart").getAsBoolean();
            if (restart != entry.has("resume")) { throw new IllegalArgumentException("重启与恢复步骤必须同时声明"); }
            entry.get("performance_gate").getAsBoolean();
            for (String phase : entry.has("resume") ? List.of("prepare", "resume") : List.of("prepare")) {
                List<Step> steps = new ArrayList<>();
                expand(entry.get("id").getAsString(), phase, entry.get("default_entities").getAsBigDecimal().intValueExact(),
                        new HashSet<>(), steps, entries);
                if (steps.isEmpty()) { throw new IllegalArgumentException("序列没有步骤"); }
            }
        }
        return Collections.unmodifiableMap(entries);
    }
}
