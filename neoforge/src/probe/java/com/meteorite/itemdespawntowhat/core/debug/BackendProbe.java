package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 无玩家开发服务端的精选链路入口，报告落盘后正常停服。 */
public final class BackendProbe {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("IDTW.Probe");
    private static final Path REPORT = Path.of("probe-report.json");
    private static final List<String> CHAINS = List.of("natural", "settlement", "damage", "recovery", "loading", "budget", "state", "save");
    private static final JsonArray RESULTS = new JsonArray();
    private static final List<String> STEPS = new ArrayList<>();
    private static String suite;
    private static int cursor;
    private static boolean started;
    private static boolean ended;
    private static long stepStarted;
    private static BackendWorldProbe world;

    private BackendProbe() {}

    /** 仅显式 probeServer 运行配置启动自动化，普通开发游戏保留手动命令。 */
    public static void tick(MinecraftServer server) {
        if (ended || System.getProperty("idtw.probe.suite") == null) { return; }
        try {
            if (!started) {
                if (server.getTickCount() < 20) { return; }
                started = true;
                suite = System.getProperty("idtw.probe.suite");
                if (!suite.equals("core") && !CHAINS.contains(suite)) { throw new IllegalArgumentException("未知链路: " + suite); }
                server.overworld().setChunkForced(0, 0, true);
                server.overworld().getChunk(0, 0);
                if (System.getProperty("idtw.probe.phase", "prepare").equals("resume")) {
                    STEPS.add("world:restart");
                } else {
                    for (String chain : CHAINS) {
                        if (!suite.equals("core") && !suite.equals(chain)) { continue; }
                        switch (chain) {
                            case "natural" -> STEPS.addAll(List.of("scene:priority", "scene:expiry"));
                            case "settlement" -> STEPS.addAll(List.of("scene:source_cost", "scene:catalyst_limited", "scene:delay"));
                            case "recovery" -> STEPS.addAll(List.of("scene:prepare_failure", "scene:settlement_failure_recovery"));
                            case "budget" -> STEPS.add("world:budget");
                            default -> STEPS.add("world:" + chain);
                        }
                    }
                }
                startStep(server);
            }
            JsonObject result;
            if (world != null) { result = world.tick(); }
            else { result = DebugScenarioManager.takeReport(server); }
            if (result != null) {
                result.addProperty("step", STEPS.get(cursor));
                if (Boolean.getBoolean("idtw.probe.negative") && cursor == 0) {
                    var checks = new DebugScenarioChecks(new JsonObject());
                    checks.equal("negative_control/intentionally_wrong", 1, 0);
                    result.getAsJsonArray("checks").addAll(checks.results());
                    result.addProperty("verdict", "FAIL");
                }
                RESULTS.add(result);
                write("RUNNING", null);
                if (!result.get("verdict").getAsString().equals("PASS")) { finish(server, "FAIL", "assertion_failed"); return; }
                if (world != null) { world.close(); world = null; }
                cursor++;
                if (cursor == STEPS.size()) {
                    boolean restart = !System.getProperty("idtw.probe.phase", "prepare").equals("resume")
                            && (suite.equals("core") || suite.equals("recovery"));
                    if (restart) { BackendWorldProbe.prepareRestart(server); }
                    finish(server, restart ? "RESTART_REQUIRED" : "PASS", "completed");
                } else { startStep(server); }
            } else if (System.nanoTime() - stepStarted > 60_000_000_000L) {
                finish(server, "FAIL", "step_timeout: " + STEPS.get(cursor));
            }
        } catch (Exception failure) {
            LOGGER.error("后端探针失败", failure);
            finish(server, "FAIL", failure.toString());
        }
    }

    private static void startStep(MinecraftServer server) throws Exception {
        stepStarted = System.nanoTime();
        String step = STEPS.get(cursor);
        LOGGER.info("START {}", step);
        if (step.startsWith("world:")) { world = new BackendWorldProbe(server, step.substring(6)); }
        else if (DebugScenarioManager.startHeadless(server.createCommandSourceStack()
                .withLevel(server.overworld()).withPosition(new Vec3(8, 100, 8)),
                RuleRuntimeHost.commandContext(), step.substring(6)) != 1) {
            throw new IllegalStateException("场景启动失败: " + step);
        }
        write("RUNNING", null);
    }

    private static void finish(MinecraftServer server, String verdict, String reason) {
        ended = true;
        write(verdict, reason);
        LOGGER.info("{} {}", verdict, reason);
        server.halt(false);
    }

    private static void write(String verdict, String reason) {
        JsonObject report = new JsonObject();
        report.addProperty("format", "idtw-backend-probe-v1");
        report.addProperty("suite", suite);
        report.addProperty("phase", System.getProperty("idtw.probe.phase", "prepare"));
        report.addProperty("verdict", verdict);
        report.addProperty("reason", reason);
        report.add("results", RESULTS);
        JsonArray remaining = new JsonArray();
        for (int i = RESULTS.size(); i < STEPS.size(); i++) { remaining.add(STEPS.get(i)); }
        report.add("remaining", remaining);
        try { Files.writeString(REPORT, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** 提前停服输出非成功终态，报告和场景日志保留首错证据。 */
    public static void stopping() {
        if (started && !ended) { ended = true; write("FAIL", "server_stopped_before_completion"); }
        if (world != null) { world.close(); world = null; }
    }
}
