package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 无玩家开发服务端的精选链路入口，报告落盘后正常停服。 */
public final class BackendProbe {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("IDTW.Probe");
    private static List<String> steps;
    private static BackendProbeReport report;
    private static DebugProbePerformance performance;
    private static long stepTimeoutNanos;
    private static String suite;
    private static int cursor;
    private static boolean started;
    private static boolean ended;
    private static long stepStarted;
    private static BackendWorldProbe world;

    private BackendProbe() {}

    /** 仅显式 probeServer 运行配置启动自动化，普通开发游戏保留手动命令。 */
    public static void tick(MinecraftServer server) {
        if (ended || System.getProperty(Constants.ID_NAMESPACE + ".probe.suite") == null) { return; }
        try {
            if (!started) {
                if (server.getTickCount() < 20) { return; }
                started = true;
                suite = System.getProperty(Constants.ID_NAMESPACE + ".probe.suite");
                steps = BackendProbeSuite.steps(suite, System.getProperty(Constants.ID_NAMESPACE + ".probe.phase", "prepare"));
                report = new BackendProbeReport(server, suite, steps);
                server.overworld().setChunkForced(0, 0, true);
                server.overworld().getChunk(0, 0);
                startStep(server);
            }
            JsonObject result;
            if (world != null) { result = world.tick(); }
            else { result = DebugScenarioManager.takeReport(server); }
            if (result != null) {
                result.addProperty("step", steps.get(cursor));
                if (Boolean.getBoolean(Constants.ID_NAMESPACE + ".probe.negative") && cursor == 0) {
                    var checks = new DebugScenarioChecks(new JsonObject());
                    checks.equal("negative_control/intentionally_wrong", 1, 0);
                    result.getAsJsonArray("checks").addAll(checks.results());
                    result.addProperty("verdict", "FAIL");
                }
                report.results.add(result);
                if (world != null) { world.close(); world = null; }
                result.add("performance", performance.finish());
                performance = null;
                report.write("RUNNING", null, null);
                if (!result.get("verdict").getAsString().equals("PASS")) { finish(server, "FAIL", "assertion_failed"); return; }
                cursor++;
                if (cursor == steps.size()) {
                    boolean restart = !System.getProperty(Constants.ID_NAMESPACE + ".probe.phase", "prepare").equals("resume")
                            && (suite.equals("core") || suite.equals("recovery"));
                    if (restart) { BackendWorldProbe.prepareRestart(server); }
                    finish(server, restart ? "RESTART_REQUIRED" : "PASS", "completed");
                } else { startStep(server); }
            } else if (System.nanoTime() - stepStarted > stepTimeoutNanos) {
                finish(server, "FAIL", "step_timeout: " + steps.get(cursor));
            }
        } catch (Exception failure) {
            LOGGER.error("后端探针失败", failure);
            finish(server, "FAIL", failure.toString());
        }
    }

    private static void startStep(MinecraftServer server) throws Exception {
        stepStarted = System.nanoTime();
        String step = steps.get(cursor);
        performance = DebugProbePerformance.start();
        boolean benchmark = step.startsWith("bench:");
        int seconds = step.startsWith("world:") ? 15 : DebugScenarioCatalog.find(step.substring(6), benchmark).seconds();
        stepTimeoutNanos = (seconds + 45L) * 1_000_000_000L;
        LOGGER.info("START {}", step);
        if (step.startsWith("world:")) { world = new BackendWorldProbe(server, step.substring(6)); }
        else if (DebugScenarioManager.startHeadless(server.createCommandSourceStack()
                .withLevel(server.overworld()).withPosition(new Vec3(8, 100, 8)),
                RuleRuntimeHost.core().diagnosticsContext(), step.substring(6), benchmark) != 1) {
            throw new IllegalStateException("场景启动失败: " + step);
        }
        report.write("RUNNING", null, null);
    }

    private static void finish(MinecraftServer server, String verdict, String reason) {
        ended = true;
        JsonObject interrupted = performance == null ? null : performance.finish();
        performance = null;
        if (report != null) { report.write(verdict, reason, interrupted); }
        LOGGER.info("{} {}", verdict, reason);
        server.halt(false);
    }

    /** 正式运行时完成、场景推进之前记录 tick；序列化留到步骤结束。 */
    public static void observeTick(MinecraftServer server) {
        DebugProbePerformance.tick(server, RuleRuntimeHost.core().diagnosticsContext().runtime());
    }

    /** 提前停服输出非成功终态，报告和场景日志保留首错证据。 */
    public static void stopping() {
        if (started && !ended) {
            ended = true;
            JsonObject interrupted = performance == null ? null : performance.finish();
            performance = null;
            if (report != null) { report.write("FAIL", "server_stopped_before_completion", interrupted); }
        }
        if (world != null) { world.close(); world = null; }
    }
}
