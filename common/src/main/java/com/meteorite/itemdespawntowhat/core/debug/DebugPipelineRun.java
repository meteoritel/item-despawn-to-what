package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** 一次阶段运行：等待真实场景清理回调，双时钟冷却后才启动下一步，结果有界保留。 */
final class DebugPipelineRun {
    final String id = UUID.randomUUID().toString();
    final DebugPipelineCatalog.Stage stage;
    final UUID initiator;
    final ServerLevel level;
    final Vec3 origin;
    final List<DebugPipelineCatalog.PlannedStep> plan;
    final int gapSeconds;
    final JsonArray results = new JsonArray();
    boolean cancelling;
    private int cursor;
    private String phase = "READY";
    private String currentRun = "";
    private long dueNanos;
    private long dueWorldTick;

    // 固定阶段起点，玩家转动视角不会让不同场景换测试位置。
    DebugPipelineRun(DebugPipelineCatalog.Stage stage, ServerPlayer player, int entities, int gapSeconds) {
        this.stage = stage;
        initiator = player.getUUID();
        level = player.serverLevel();
        var look = player.getLookAngle();
        origin = player.position().add(look.x * 3, 1, look.z * 3);
        plan = stage.plan(entities);
        this.gapSeconds = gapSeconds;
    }

    // 等待和推进均使用主线程tick，不阻塞或另建定时线程。
    boolean tick(ServerPlayer player, RuleCommandContext context) {
        if (phase.equals("RUNNING")) { return false; }
        if (phase.equals("COOLDOWN") && (System.nanoTime() < dueNanos || level.getGameTime() < dueWorldTick)) { return false; }
        if (DebugPipelineManager.hasFixtures(level.getServer())) { throw new IllegalStateException("residual_fixtures"); }
        if (cursor == plan.size()) { return true; }
        var planned = plan.get(cursor);
        var step = planned.step();
        phase = "RUNNING";
        currentRun = "";
        JsonObject data = state();
        data.addProperty("scenario", step.scene().key());
        data.addProperty("entities", step.entities());
        data.addProperty("seconds", step.seconds());
        data.addProperty("round", planned.round());
        log("PIPELINE_STEP_START", data);
        int started = DebugScenarioManager.startPipeline(player.createCommandSourceStack(), context,
                step.scene().name(), step.scene().benchmark(), step.entities(), step.seconds(), origin, id);
        if (started != 1) { throw new IllegalStateException("start_failed"); }
        var active = DebugScenarioManager.activeRun(level.getServer());
        if (active != null) { currentRun = active.id; }
        return false;
    }

    // 场景已输出END且解绑后交付结果；功能和性能分别校对，再决定是否继续。
    boolean accept(DebugScenarioRun run, String verdict) {
        if (!phase.equals("RUNNING") || cursor >= plan.size()) { throw new IllegalStateException("unexpected_result"); }
        var planned = plan.get(cursor);
        if (!planned.step().scene().key().equals(run.definition.spec.key())) { throw new IllegalStateException("unexpected_scene"); }
        JsonObject data = new JsonObject();
        data.addProperty("step", cursor + 1);
        data.addProperty("round", planned.round());
        data.addProperty("scenario", run.definition.spec.key());
        data.addProperty("entities", run.definition.sources);
        data.addProperty("scene_run", run.id);
        data.addProperty("functional_verdict", verdict);
        JsonObject report = run.completedReport;
        JsonObject window = report == null ? null : report.getAsJsonObject("window");
        if (window != null) {
            data.addProperty("tps", window.get("observed_ticks_per_second").getAsDouble());
            data.addProperty("tick_p95_us", window.getAsJsonObject("server_tick_cost").get("p95_us").getAsLong());
            data.addProperty("checks_p95_us", window.getAsJsonObject("checks").get("p95_us").getAsLong());
            data.addProperty("effects_p95_us", window.getAsJsonObject("effects").get("p95_us").getAsLong());
        }
        JsonArray performance = new JsonArray();
        if (stage.performanceGate()) {
            threshold(performance, "tps", 19.5, window == null ? -1 : window.get("observed_ticks_per_second").getAsDouble(), true);
            threshold(performance, "tick_p95_us", 50000,
                    window == null ? Double.POSITIVE_INFINITY : window.getAsJsonObject("server_tick_cost").get("p95_us").getAsDouble(), false);
            threshold(performance, "sampled_seconds", planned.step().seconds(),
                    window == null ? 0 : window.get("sampled_seconds").getAsDouble(), true);
            threshold(performance, "sample_limit_reached", 0, window == null || window.get("sample_limit_reached").getAsBoolean() ? 1 : 0, false);
            threshold(performance, "index_changes", 0, window == null ? 1 : window.get("index_changes").getAsInt(), false);
        }
        boolean passed = verdict.equals("PASS");
        for (var check : performance) { if (!check.getAsJsonObject().get("pass").getAsBoolean()) { passed = false; } }
        data.add("performance_checks", performance);
        data.addProperty("pass", passed);
        results.add(data);
        log("PIPELINE_STEP_END", data);
        cursor++;
        currentRun = "";
        if (!passed || cancelling) { return false; }
        phase = "COOLDOWN";
        dueNanos = System.nanoTime() + gapSeconds * 1_000_000_000L;
        dueWorldTick = level.getGameTime() + gapSeconds * 20L;
        log("PIPELINE_COOLDOWN", state());
        return true;
    }

    // 状态查询给出当前步骤与冷却剩余时间，不扫描日志或世界实体。
    JsonObject state() {
        JsonObject data = new JsonObject();
        data.addProperty("pipeline", id);
        data.addProperty("stage", stage.name());
        data.addProperty("phase", phase);
        data.addProperty("step", Math.min(cursor + 1, plan.size()));
        data.addProperty("completed_steps", results.size());
        data.addProperty("total_steps", plan.size());
        data.addProperty("scene_run", currentRun);
        data.addProperty("gap_seconds", gapSeconds);
        double wall = (dueNanos - System.nanoTime()) / 1_000_000_000D;
        double world = (dueWorldTick - level.getGameTime()) / 20D;
        data.addProperty("cooldown_remaining_seconds", phase.equals("COOLDOWN") ? Math.max(0, Math.ceil(Math.max(wall, world))) : 0);
        return data;
    }

    // 阶段编号与场景run编号分开，日志可追溯每一轮。
    void log(String event, JsonObject data) { DebugLog.write(id, "pipeline/" + stage.name(), event, data); }

    // 有限范围阈值只用于阶段性能门槛，不修改原场景PASS定义。
    private static void threshold(JsonArray checks, String name, double bound, double actual, boolean minimum) {
        JsonObject check = new JsonObject();
        check.addProperty("name", name);
        check.addProperty(minimum ? "min" : "max", bound);
        if (Double.isFinite(actual)) { check.addProperty("actual", actual); }
        else { check.addProperty("actual", "missing"); }
        check.addProperty("pass", Double.isFinite(actual) && (minimum ? actual >= bound : actual <= bound));
        checks.add(check);
    }
}
