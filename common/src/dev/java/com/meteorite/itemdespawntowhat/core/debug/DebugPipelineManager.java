package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeDiagnosticsContext;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;

/** 每服务器一条开发流水线：占用场景入口，统一步骤运行驱动串行计划和失败停止。 */
final class DebugPipelineManager {
    private static final String PREFIX = "itemdespawntowhat.command.debug.pipeline.";
    private static final Map<MinecraftServer, CommandRun> ACTIVE = new IdentityHashMap<>();

    private record CommandRun(UUID initiator, ServerLevel level, DebugSequenceRun sequence, RuleRuntimeDiagnosticsContext context) {}

    // 工具类不创建实例。
    private DebugPipelineManager() {}

    // 公共场景入口据此拒绝插队，冷却时也保留占用。
    static boolean active(MinecraftServer server) { return DebugMode.ENABLED && ACTIVE.containsKey(server); }

    // 创建有限计划，不执行 mcfunction 或修改全局规则；每一步仍走既有场景后端。
    static int start(CommandSourceStack source, RuleRuntimeDiagnosticsContext context, String name, int entities, int gap) {
        if (!DebugMode.ENABLED) { return failure(source, "development_only"); }
        if (!(source.getEntity() instanceof ServerPlayer player)) { return failure(source, "player_only"); }
        if (active(source.getServer()) || !DebugScenarioManager.inactive(source.getServer())) { return failure(source, "busy"); }
        if (context.runtime() == null || context.editContext().typeRegistries() == null) { return failure(source, "not_ready"); }
        if (Math.abs(source.getServer().tickRateManager().tickrate() - 20) > 0.01F) { return failure(source, "tick_rate"); }
        if (hasFixtures(source.getServer())) { return failure(source, "residual_fixtures"); }
        if (gap < DebugPipelineCatalog.MIN_GAP_SECONDS || gap > DebugPipelineCatalog.MAX_GAP_SECONDS
                || entities < 0 || entities > DebugScenarioSpec.MAX_BENCHMARK_SOURCES) { return failure(source, "invalid_parameters"); }
        var stage = DebugPipelineCatalog.find(name);
        if (stage.quantity() == DebugPipelineCatalog.Quantity.NONE && entities != 0
                || stage.quantity() == DebugPipelineCatalog.Quantity.FIXED && entities < 1
                || stage.quantity() == DebugPipelineCatalog.Quantity.CEILING && entities < 100) { return failure(source, "invalid_parameters"); }
        // 只解析不执行1001命令，验证命令边界不会创建测试实体。
        var rejected = source.getServer().getCommands().getDispatcher().parse(RuleCommandTree.ROOT + " debug bench normal 1001 10", source);
        if (!rejected.getReader().canRead() && rejected.getExceptions().isEmpty()) { return failure(source, "boundary_failed"); }
        var look = player.getLookAngle();
        var origin = player.position().add(look.x * 3, 1, look.z * 3);
        var plan = DebugSequenceCatalog.plan(name, "prepare", entities, gap);
        var sequence = new DebugSequenceRun(null, plan, source, context, origin, false);
        ACTIVE.put(source.getServer(), new CommandRun(player.getUUID(), player.serverLevel(), sequence, context));
        DebugLog.write(sequence.id, name, "PIPELINE_START", sequence.state());
        source.sendSuccess(() -> Component.translatable(PREFIX + "started", stage.name(), sequence.id, plan.steps().size(), gap), false);
        return 1;
    }

    // 单场景命令与流水线共用步骤所有者；场景内部入口只负责本轮负载。
    static int startSingle(CommandSourceStack source, RuleRuntimeDiagnosticsContext context, String name,
                           boolean benchmark, int entities, int seconds) {
        if (!DebugMode.ENABLED) { return failure(source, "development_only"); }
        if (!(source.getEntity() instanceof ServerPlayer player)) { return failure(source, "player_only"); }
        if (active(source.getServer()) || !DebugScenarioManager.inactive(source.getServer())) { return failure(source, "busy"); }
        if (context.runtime() == null || context.editContext().typeRegistries() == null) { return failure(source, "not_ready"); }
        var spec = DebugScenarioCatalog.find(name, benchmark);
        if (entities < 0 || entities > DebugScenarioSpec.MAX_BENCHMARK_SOURCES || seconds < 10 || seconds > 300
                || !benchmark && entities != spec.sources()) { return failure(source, "invalid_parameters"); }
        var kind = benchmark ? DebugSequenceCatalog.Kind.BENCH : DebugSequenceCatalog.Kind.SCENE;
        var plan = new DebugSequenceCatalog.Plan((benchmark ? "bench/" : "run/") + name,
                List.of(new DebugSequenceCatalog.Step(kind, name, entities, seconds, seconds + 45, 1)), 0, false, false);
        var look = player.getLookAngle();
        var origin = player.position().add(look.x * 3, 1, look.z * 3);
        var sequence = new DebugSequenceRun(null, plan, source, context, origin, false);
        ACTIVE.put(source.getServer(), new CommandRun(player.getUUID(), player.serverLevel(), sequence, context));
        return 1;
    }

    // 复用已有两平台开发tick，无定时线程，无额外Mixin。
    @SuppressWarnings("resource")
    static void tick(MinecraftServer server) {
        var run = ACTIVE.get(server);
        if (run == null) { return; }
        ServerPlayer player = server.getPlayerList().getPlayer(run.initiator());
        if (player == null) { halt(server, "player_left"); return; }
        if (player.serverLevel() != run.level() || server.getLevel(run.level().dimension()) != run.level()) {
            halt(server, "dimension_changed"); return;
        }
        if (Math.abs(server.tickRateManager().tickrate() - 20) > 0.01F) { halt(server, "tick_rate"); return; }
        try {
            if (run.sequence().tick()) { finish(server, run.sequence().verdict(), run.sequence().reason()); }
        } catch (RuntimeException failure) {
            var data = run.sequence().state();
            data.addProperty("error", failure.toString());
            DebugLog.write(run.sequence().id, run.sequence().plan.id(), "PIPELINE_ERROR", data);
            halt(server, failure.getMessage() != null && failure.getMessage().equals("residual_fixtures")
                    ? "residual_fixtures" : "execution_error");
        }
    }

    // stop 子命令：冷却期间也能取消后续计划。
    static int stop(CommandSourceStack source) {
        if (!active(source.getServer())) { return failure(source, "not_running"); }
        halt(source.getServer(), "manual_stop");
        return 1;
    }

    // 流水线状态覆盖当前场景和轮间等待，聊天不打印实现细节。
    static int status(CommandSourceStack source) {
        var run = ACTIVE.get(source.getServer());
        if (run == null) { return failure(source, "not_running"); }
        var state = run.sequence().state();
        source.sendSuccess(() -> Component.translatable(PREFIX + "status", run.sequence().plan.id(), run.sequence().id,
                state.get("step").getAsInt(), run.sequence().plan.steps().size(), Component.translatable(PREFIX + "phase." + state.get("phase").getAsString()),
                state.get("cooldown_remaining_seconds").getAsInt()), false);
        return 1;
    }

    // 先撤销阶段计划再关闭平台运行时，让步骤资源先于平台运行时释放。
    static void shutdown(MinecraftServer server) {
        if (active(server)) { halt(server, "server_stopping"); }
    }

    // 只在阶段开始和冷却结束扫描已加载实体，性能测量期间不增加世界扫描。
    static boolean hasFixtures(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity item) || !item.isAlive()) { continue; }
                if (item.getTags().contains(Constants.ID_NAMESPACE + "_debug_fixture")) { return true; }
                var data = item.getItem().get(DataComponents.CUSTOM_DATA);
                if (data != null && data.copyTag().contains(Constants.ID_NAMESPACE + "_debug_run")) { return true; }
            }
        }
        return false;
    }

    // 中止本轮并保留真实 INCOMPLETE 结果，取消后续步骤。
    private static void halt(MinecraftServer server, String reason) {
        var run = ACTIVE.get(server);
        if (run == null) { return; }
        run.sequence().cancel(reason, null);
        finish(server, run.sequence().verdict(), reason);
    }

    // 汇总所有已完成步骤及跳过数量，不保留服务器静态引用。
    private static void finish(MinecraftServer server, String verdict, String reason) {
        var run = ACTIVE.remove(server);
        if (run == null) { return; }
        JsonObject data = run.sequence().state();
        data.addProperty("verdict", verdict);
        data.addProperty("stop_reason", reason);
        new BackendProbeReport(server, run.sequence(), "command", Path.of("probe-runs", run.sequence().id, "probe-report.json"), run.context()).write();
        DebugLog.write(run.sequence().id, run.sequence().plan.id(), "PIPELINE_END", data);
        var player = server.getPlayerList().getPlayer(run.initiator());
        if (player != null) {
            player.sendSystemMessage(Component.translatable(PREFIX + "finished", run.sequence().plan.id(),
                    Component.translatable("itemdespawntowhat.command.debug.scene.verdict." + verdict),
                    run.sequence().results.size(), run.sequence().plan.steps().size(), Component.translatable(PREFIX + "reason." + reason)));
        }
    }

    // 所有错误提示使用双语本地化键。
    private static int failure(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(PREFIX + "error." + key));
        return 0;
    }
}
