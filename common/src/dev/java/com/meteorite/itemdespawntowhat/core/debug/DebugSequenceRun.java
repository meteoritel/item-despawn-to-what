package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeDiagnosticsContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 一次串行计划拥有步骤、deadline、双时钟冷却和最终判定，宿主只处理入口与停服。 */
final class DebugSequenceRun {
    interface WorldStep {
        void start() throws Exception;
        JsonObject tick() throws Exception;
        void close();
    }
    interface WorldFactory { WorldStep create(CommandSourceStack source, String id); }
    private static WorldFactory worlds;
    final String id;
    final DebugSequenceCatalog.Plan plan;
    final JsonArray results = new JsonArray();
    private final CommandSourceStack source;
    private final RuleRuntimeDiagnosticsContext context;
    private final Vec3 origin;
    private final boolean negative;
    private int cursor;
    private StepRun current;
    private long dueNanos;
    private long dueTick;
    private String verdict = "RUNNING";
    private String reason = "";

    DebugSequenceRun(String id, DebugSequenceCatalog.Plan plan, CommandSourceStack source,
                     RuleRuntimeDiagnosticsContext context, Vec3 origin, boolean negative) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.plan = plan;
        this.source = source;
        this.context = context;
        this.origin = origin;
        this.negative = negative;
    }

    static void installWorlds(WorldFactory factory) { worlds = factory; }

    boolean tick() {
        if (!verdict.equals("RUNNING")) { return true; }
        try {
            if (current == null) {
                if (System.nanoTime() < dueNanos || source.getLevel().getGameTime() < dueTick) { return false; }
                if (cursor == plan.steps().size()) {
                    verdict = plan.restart() ? "RESTART_REQUIRED" : "PASS";
                    reason = "completed";
                    return true;
                }
                current = new StepRun(plan.steps().get(cursor));
                current.start();
            }
            JsonObject result = current.tick();
            if (result == null) { return false; }
            if (negative && cursor == 0) {
                DebugScenarioChecks checks = new DebugScenarioChecks(new JsonObject());
                checks.equal("negative_control/intentionally_wrong", true, false);
                result.getAsJsonArray("checks").addAll(checks.results());
                result.addProperty("verdict", "FAIL");
            }
            results.add(result);
            current = null;
            cursor++;
            if (!result.get("verdict").getAsString().equals("PASS")) {
                verdict = result.get("verdict").getAsString();
                reason = "step_failed";
                return true;
            }
            dueNanos = System.nanoTime() + plan.gapSeconds() * 1_000_000_000L;
            dueTick = source.getLevel().getGameTime() + plan.gapSeconds() * 20L;
        } catch (Exception failure) { cancel("execution_error", failure); }
        return !verdict.equals("RUNNING");
    }

    void cancel(String stopReason, Exception failure) {
        if (!verdict.equals("RUNNING")) { return; }
        reason = stopReason;
        verdict = "INCOMPLETE";
        if (current != null) {
            results.add(current.finish(null, stopReason, failure));
            current = null;
        }
    }

    void hostFailed(Exception failure) {
        if (verdict.equals("RUNNING")) { cancel("host_error", failure); }
        else { verdict = "INCOMPLETE"; reason = "host_error: " + failure; }
    }

    String verdict() { return verdict; }
    String reason() { return reason; }

    JsonObject state() {
        JsonObject data = new JsonObject();
        data.addProperty("run_id", id);
        data.addProperty("pipeline", id);
        data.addProperty("stage", plan.id());
        data.addProperty("phase", current == null ? cursor == 0 ? "READY" : "COOLDOWN" : "RUNNING");
        data.addProperty("step", Math.min(cursor + 1, plan.steps().size()));
        data.addProperty("completed_steps", results.size());
        data.addProperty("total_steps", plan.steps().size());
        data.addProperty("verdict", verdict);
        data.addProperty("reason", reason);
        data.addProperty("cooldown_remaining_seconds", current == null ? Math.max(0, Math.ceil(Math.max(
                (dueNanos - System.nanoTime()) / 1_000_000_000D, (dueTick - source.getLevel().getGameTime()) / 20D))) : 0);
        data.add("results", results);
        JsonArray remaining = new JsonArray();
        for (int i = cursor; i < plan.steps().size(); i++) { remaining.add(plan.steps().get(i).key()); }
        data.add("remaining", remaining);
        return data;
    }

    /** 步骤在准备前登记到序列；异常、取消、超时共用一次资源清理和结果封存。 */
    private final class StepRun {
        private final DebugSequenceCatalog.Step step;
        private final long deadline;
        private final DebugProbePerformance performance;
        private WorldStep world;
        private JsonObject terminal;
        private boolean sceneStarted;

        StepRun(DebugSequenceCatalog.Step step) {
            this.step = step;
            deadline = System.nanoTime() + step.timeoutSeconds() * 1_000_000_000L;
            performance = DebugProbePerformance.start();
        }

        void start() throws Exception {
            DebugProbePerformance.phase("SETUP");
            if (step.kind() == DebugSequenceCatalog.Kind.WORLD) {
                if (worlds == null) { throw new IllegalStateException("世界步骤未装配"); }
                world = worlds.create(source, step.id());
                world.start();
            } else {
                DebugScenarioManager.takeReport(source.getServer());
                sceneStarted = true;
                int result = DebugScenarioManager.startPipeline(source, context, step.id(),
                        step.kind() == DebugSequenceCatalog.Kind.BENCH, step.entities(), step.seconds(), origin, id);
                if (result != 1) { throw new IllegalStateException("场景启动失败：" + step.key()); }
            }
        }

        JsonObject tick() throws Exception {
            JsonObject result = world == null ? DebugScenarioManager.takeReport(source.getServer()) : world.tick();
            if (result != null) { return finish(result, "completed", null); }
            if (System.nanoTime() >= deadline) { return finish(null, "step_timeout", null); }
            return null;
        }

        JsonObject finish(JsonObject result, String stopReason, Exception failure) {
            if (terminal != null) { return terminal; }
            JsonObject data = result == null ? new JsonObject() : result.deepCopy();
            terminal = data;
            if (!data.has("checks")) { data.add("checks", new JsonArray()); }
            if (!data.has("verdict")) { data.addProperty("verdict", "INCOMPLETE"); }
            if (failure != null) { data.addProperty("error", failure.toString()); }
            data.addProperty("step", step.key());
            data.addProperty("round", step.round());
            data.addProperty("stop_reason", stopReason);
            try {
                if (world != null) { world.close(); }
                else if (sceneStarted && result == null) {
                    DebugScenarioManager.stopPipelineScene(source.getServer(), context, stopReason);
                    JsonObject stopped = DebugScenarioManager.takeReport(source.getServer());
                    if (stopped != null && stopped.has("checks")) {
                        data.add("scene", stopped);
                        data.getAsJsonArray("checks").addAll(stopped.getAsJsonArray("checks"));
                    }
                }
            } catch (RuntimeException cleanup) {
                data.addProperty("cleanup_error", cleanup.toString());
                data.addProperty("verdict", "INCOMPLETE");
            }
            try {
                if (plan.performanceGate()) {
                    DebugScenarioChecks checks = new DebugScenarioChecks(new JsonObject());
                    JsonObject window = data.getAsJsonObject("window");
                    if (window == null) { checks.missing("performance/window"); }
                    else {
                        checks.minimum("performance/tps", 19.5, window.get("observed_ticks_per_second").getAsDouble());
                        checks.maximum("performance/tick_p95_us", 50000, window.getAsJsonObject("server_tick_cost").get("p95_us").getAsDouble());
                        checks.minimum("performance/sampled_seconds", step.seconds(), window.get("sampled_seconds").getAsDouble());
                        checks.equal("performance/sample_limit_reached", false, window.get("sample_limit_reached").getAsBoolean());
                        checks.equal("performance/index_changes", 0, window.get("index_changes").getAsLong());
                        checks.equal("performance/tick_timing_missing_ticks", 0, window.get("tick_timing_missing_ticks").getAsLong());
                    }
                    data.getAsJsonArray("checks").addAll(checks.results());
                    for (var check : checks.results()) {
                        if (!check.getAsJsonObject().get("pass").getAsBoolean() && data.get("verdict").getAsString().equals("PASS")) {
                            data.addProperty("verdict", "FAIL");
                        }
                    }
                }
            } catch (RuntimeException evaluation) {
                var checks = new DebugScenarioChecks(new JsonObject());
                checks.missing("performance/evaluation_error");
                data.getAsJsonArray("checks").addAll(checks.results());
                data.addProperty("evaluation_error", evaluation.toString());
                data.addProperty("verdict", "INCOMPLETE");
            } finally { data.add("performance", performance.finish()); }
            terminal = data;
            DebugLog.write(id, plan.id(), "SEQUENCE_STEP_END", data);
            return terminal;
        }
    }
}
