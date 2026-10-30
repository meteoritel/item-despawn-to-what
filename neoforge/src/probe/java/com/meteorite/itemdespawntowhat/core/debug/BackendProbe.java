package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/** 无玩家宿主负责存档与跨进程交接，步骤推进由共享序列持有。 */
public final class BackendProbe {
    private static final Logger LOGGER = LogManager.getLogger("IDTW.Probe");
    private static DebugSequenceRun sequence;
    private static BackendProbeReport report;
    private static boolean ended;
    private static int writtenResults = -1;
    private BackendProbe() {}

    /** 存档启动装配世界步骤，每个服务器生命周期重新登记运行。 */
    public static void starting() {
        sequence = null;
        report = null;
        ended = false;
        writtenResults = -1;
        DebugSequenceRun.installWorlds((source, name) -> new BackendWorldProbe(source.getServer(), name));
    }

    /** 显式 probeServer 配置才自动运行，封存报告后正常停服。 */
    public static void tick(MinecraftServer server) {
        String suite = System.getProperty(Constants.ID_NAMESPACE + ".probe.suite");
        if (ended || suite == null) { return; }
        try {
            if (sequence == null) {
                if (server.getTickCount() < 20) { return; }
                String phase = System.getProperty(Constants.ID_NAMESPACE + ".probe.phase", "prepare");
                var context = RuleRuntimeHost.core().diagnosticsContext();
                var plan = DebugSequenceCatalog.plan(suite, phase, -1, -1);
                var source = server.createCommandSourceStack().withLevel(server.overworld()).withPosition(new Vec3(8, 100, 8));
                sequence = new DebugSequenceRun(System.getProperty(Constants.ID_NAMESPACE + ".probe.runId"), plan, source, context,
                        source.getPosition(), Boolean.getBoolean(Constants.ID_NAMESPACE + ".probe.negative"));
                report = new BackendProbeReport(server, sequence, phase, Path.of("probe-report.json"), context);
                server.overworld().setChunkForced(0, 0, true);
                server.overworld().getChunk(0, 0);
            }
            boolean finished = sequence.tick();
            if (finished && sequence.verdict().equals("RESTART_REQUIRED")) { BackendWorldProbe.prepareRestart(server); }
            if (finished || writtenResults != sequence.results.size()) {
                report.write();
                writtenResults = sequence.results.size();
            }
            if (finished) {
                ended = true;
                LOGGER.info("{} {}", sequence.verdict(), sequence.reason());
                server.halt(false);
            }
        } catch (Exception failure) {
            LOGGER.error("后端探针失败", failure);
            if (sequence != null) { sequence.hostFailed(failure); }
            if (report != null) { report.write(); }
            ended = true;
            server.halt(false);
        }
    }

    /** 正式运行时完成后记录 tick，序列化位于步骤窗口之外。 */
    public static void observeTick(MinecraftServer server) {
        DebugProbePerformance.tick(server, RuleRuntimeHost.core().diagnosticsContext().runtime());
    }

    /** 提前停止走统一取消路径并保存未完成结果。 */
    public static void stopping() {
        if (sequence != null && !ended) {
            sequence.cancel("server_stopped_before_completion", null);
            ended = true;
            report.write();
        }
    }
}
