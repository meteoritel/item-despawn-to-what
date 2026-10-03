package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.IdentityHashMap;
import java.util.Map;

/** 每服务器一条开发流水线：占用场景入口，真实结束回调驱动串行计划和失败停止。 */
final class DebugPipelineManager {
    private static final String PREFIX = "itemdespawntowhat.command.debug.pipeline.";
    private static final Map<MinecraftServer, DebugPipelineRun> ACTIVE = new IdentityHashMap<>();

    // 工具类不创建实例。
    private DebugPipelineManager() {}

    // 公共场景入口据此拒绝插队，冷却时也保留占用。
    static boolean active(MinecraftServer server) { return DebugMode.ENABLED && ACTIVE.containsKey(server); }

    // 创建有限计划，不执行mcfunction或修改全局规则；场景仍走原有后端。
    static int start(CommandSourceStack source, RuleCommandContext context, String name, int entities, int gap) {
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
        var rejected = source.getServer().getCommands().getDispatcher().parse("idtw debug bench normal 1001 10", source);
        if (!rejected.getReader().canRead() && rejected.getExceptions().isEmpty()) { return failure(source, "boundary_failed"); }
        var run = new DebugPipelineRun(stage, player, entities, gap);
        ACTIVE.put(source.getServer(), run);
        var data = run.state();
        data.addProperty("requested_entities", entities);
        data.addProperty("source_entity_limit", DebugScenarioSpec.MAX_BENCHMARK_SOURCES);
        data.addProperty("quantity_boundary_rejected", true);
        data.addProperty("performance_gate", stage.performanceGate());
        data.addProperty("origin", run.origin.toString());
        run.log("PIPELINE_START", data);
        source.sendSuccess(() -> Component.translatable(PREFIX + "started", stage.name(), run.id, run.plan.size(), gap), false);
        return 1;
    }

    // 复用已有两平台开发tick，无定时线程，无额外Mixin。
    static void tick(MinecraftServer server, RuleCommandContext context) {
        var run = ACTIVE.get(server);
        if (run == null) { return; }
        ServerPlayer player = server.getPlayerList().getPlayer(run.initiator);
        if (player == null) { halt(server, context, "player_left"); return; }
        if (player.serverLevel() != run.level || server.getLevel(run.level.dimension()) != run.level) {
            halt(server, context, "dimension_changed"); return;
        }
        if (Math.abs(server.tickRateManager().tickrate() - 20) > 0.01F) { halt(server, context, "tick_rate"); return; }
        try {
            if (run.tick(player, context)) { finish(server, "PASS", "completed"); }
        } catch (RuntimeException failure) {
            var data = run.state();
            data.addProperty("error", failure.toString());
            run.log("PIPELINE_ERROR", data);
            halt(server, context, failure.getMessage() != null && failure.getMessage().equals("residual_fixtures")
                    ? "residual_fixtures" : "execution_error");
        }
    }

    // 必须在场景END与解绑之后调用，清理失败不能被当作阶段通过。
    static void sceneFinished(MinecraftServer server, DebugScenarioRun scene, String verdict) {
        var run = ACTIVE.get(server);
        if (run == null || !run.id.equals(scene.pipelineId)) { return; }
        try {
            boolean passed = run.accept(scene, verdict);
            if (!run.cancelling && !passed) { finish(server, verdict.equals("INCOMPLETE") ? "INCOMPLETE" : "FAIL", "step_failed"); }
        } catch (RuntimeException failure) {
            var data = run.state();
            data.addProperty("error", failure.toString());
            run.log("PIPELINE_ERROR", data);
            finish(server, "INCOMPLETE", "execution_error");
        }
    }

    // 兼容原debug stop：冷却期间也能取消后续计划。
    static int stop(CommandSourceStack source, RuleCommandContext context) {
        if (!active(source.getServer())) { return failure(source, "not_running"); }
        halt(source.getServer(), context, "manual_stop");
        return 1;
    }

    // 流水线状态覆盖当前场景和轮间等待，聊天不打印实现细节。
    static int status(CommandSourceStack source) {
        var run = ACTIVE.get(source.getServer());
        if (run == null) { return failure(source, "not_running"); }
        var state = run.state();
        source.sendSuccess(() -> Component.translatable(PREFIX + "status", run.stage.name(), run.id,
                state.get("step").getAsInt(), run.plan.size(), Component.translatable(PREFIX + "phase." + state.get("phase").getAsString()),
                state.get("cooldown_remaining_seconds").getAsInt()), false);
        return 1;
    }

    // 先撤销阶段计划再关闭平台运行时，避免停服回调启动下一步。
    static void shutdown(MinecraftServer server, RuleCommandContext context) {
        if (active(server)) { halt(server, context, "server_stopping"); }
    }

    // 只在阶段开始和冷却结束扫描已加载实体，性能测量期间不增加世界扫描。
    static boolean hasFixtures(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity item) || !item.isAlive()) { continue; }
                if (item.getTags().contains("idtw_debug_fixture")) { return true; }
                var data = item.getItem().get(DataComponents.CUSTOM_DATA);
                if (data != null && data.copyTag().contains("idtw_debug_run")) { return true; }
            }
        }
        return false;
    }

    // 中止本轮并保留真实INCOMPLETE结果；回调只记结果，不推进后续步骤。
    private static void halt(MinecraftServer server, RuleCommandContext context, String reason) {
        var run = ACTIVE.get(server);
        if (run == null) { return; }
        run.cancelling = true;
        try { DebugScenarioManager.stopPipelineScene(server, context, reason); }
        finally { finish(server, "INCOMPLETE", reason); }
    }

    // 汇总所有已完成步骤及跳过数量，不保留服务器静态引用。
    private static void finish(MinecraftServer server, String verdict, String reason) {
        var run = ACTIVE.remove(server);
        if (run == null) { return; }
        JsonObject data = run.state();
        data.addProperty("verdict", verdict);
        data.addProperty("stop_reason", reason);
        data.addProperty("skipped_steps", run.plan.size() - run.results.size());
        data.add("results", run.results);
        run.log("PIPELINE_END", data);
        var player = server.getPlayerList().getPlayer(run.initiator);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(PREFIX + "finished", run.stage.name(),
                    Component.translatable("itemdespawntowhat.command.debug.scene.verdict." + verdict),
                    run.results.size(), run.plan.size(), Component.translatable(PREFIX + "reason." + reason)));
        }
    }

    // 所有错误提示使用双语本地化键。
    private static int failure(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(PREFIX + "error." + key));
        return 0;
    }
}
