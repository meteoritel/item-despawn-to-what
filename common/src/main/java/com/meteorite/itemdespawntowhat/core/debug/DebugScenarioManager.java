package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.RunnableTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ScheduledTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 每服务器一轮开发场景；观察真实运行时事件，并只管理本轮实体与效果任务。 */
public final class DebugScenarioManager {
    private static final Logger LOGGER = LogManager.getLogger("IDTW.Debug");
    private static final String PREFIX = "itemdespawntowhat.command.debug.scene.";
    private static final Map<MinecraftServer, DebugScenarioRun> ACTIVE = new IdentityHashMap<>();
    private static final Map<UUID, Binding> ENTITIES = new HashMap<>();

    /** UUID绑定区分测试源与产物，测试产物不进入用户规则链路。 */
    private record Binding(DebugScenarioRun run, boolean source) {}

    // 工具类不创建实例。
    private DebugScenarioManager() {}

    // 开发指令直接创建场景，不读取历史日志，也不写入用户配置。
    public static int start(CommandSourceStack source, RuleCommandContext context, String name,
                            boolean benchmark, int entities, int seconds) {
        if (DebugPipelineManager.active(source.getServer())) { return failure(source, "pipeline_busy"); }
        return startScene(source, context, name, benchmark, entities, seconds, null, null);
    }

    // 流水线仅覆盖起点和关联编号，仍使用同一个场景生命周期。
    static int startPipeline(CommandSourceStack source, RuleCommandContext context, String name,
                             boolean benchmark, int entities, int seconds, Vec3 origin, String pipelineId) {
        return startScene(source, context, name, benchmark, entities, seconds, origin, pipelineId);
    }

    // 流水线读取本轮编号，不暴露或修改业务运行时状态。
    static @Nullable DebugScenarioRun activeRun(MinecraftServer server) { return ACTIVE.get(server); }

    // 中止流水线拥有的场景，防止误结束独立手动场景。
    static void stopPipelineScene(MinecraftServer server, RuleCommandContext context, String reason) {
        var run = ACTIVE.get(server);
        if (run != null && run.pipelineId != null) { finish(server, context, reason); }
    }

    // 所有场景共用开发环境、数量、准备与启动失败处理。
    private static int startScene(CommandSourceStack source, RuleCommandContext context, String name,
                                  boolean benchmark, int entities, int seconds, @Nullable Vec3 fixedOrigin,
                                  @Nullable String pipelineId) {
        if (!DebugMode.ENABLED) { return failure(source, "development_only"); }
        if (!(source.getEntity() instanceof ServerPlayer player)) { return failure(source, "player_only"); }
        if (context.runtime() == null || context.editContext().typeRegistries() == null) { return failure(source, "not_ready"); }
        if (ACTIVE.containsKey(source.getServer())) { return failure(source, "busy"); }
        try {
            var origin = fixedOrigin == null ? source.getPosition().add(player.getLookAngle().x * 3, 1, player.getLookAngle().z * 3) : fixedOrigin;
            var definition = DebugScenarioDefinition.create(source.getServer(), context.editContext().typeRegistries(),
                    name, benchmark, entities, BlockPos.containing(origin).getY());
            var run = new DebugScenarioRun(name, benchmark, player.serverLevel(), player.getUUID(), origin, seconds, definition, pipelineId);
            ACTIVE.put(source.getServer(), run);
            JsonObject data = DebugLog.config(context.serverConfig());
            data.addProperty("format", "idtw-scene-v3");
            data.addProperty("tick_cost_scope", "vanilla_tick_plus_idtw_runtime");
            if (pipelineId != null) { data.addProperty("pipeline", pipelineId); }
            data.addProperty("rule_source", "development-memory");
            data.addProperty("builtin_datapack", false);
            data.addProperty("scenario_resource", DebugScenarioCatalog.ROOT + definition.spec.key() + ".json");
            data.add("expected", definition.expected);
            data.addProperty("platform", com.meteorite.itemdespawntowhat.platform.Services.PLATFORM.getPlatformName());
            data.addProperty("minecraft_version", source.getServer().getServerVersion());
            data.addProperty("java_version", System.getProperty("java.version"));
            data.addProperty("available_processors", Runtime.getRuntime().availableProcessors());
            data.addProperty("max_heap_bytes", Runtime.getRuntime().maxMemory());
            data.addProperty("tick_rate", source.getServer().tickRateManager().tickrate());
            data.addProperty("benchmark", benchmark);
            data.addProperty("per_entity_logging", !benchmark);
            data.addProperty("entities", definition.sources);
            data.addProperty("source_entity_limit", benchmark ? DebugScenarioSpec.MAX_BENCHMARK_SOURCES : DebugScenarioSpec.MAX_FUNCTIONAL_SOURCES);
            data.addProperty("stack_size", definition.stackSize);
            data.addProperty("requested_measurement_seconds", seconds);
            data.addProperty("warmup_world_ticks", 20);
            data.addProperty("dimension", run.level.dimension().location().toString());
            data.addProperty("origin", origin.toString());
            data.addProperty("grid_spacing_blocks", benchmark ? 0.25 : 1.0);
            data.addProperty("merge_disabled_by_unique_component", true);
            data.addProperty("no_gravity", true);
            data.addProperty("expected_conversions", definition.expectedConversions);
            data.addProperty("expected_output_items", definition.expectedOutput);
            data.addProperty("overlay_version", context.overlayVersion());
            data.add("scenario_rules", definition.parameters);
            run.log("START", data);
            source.sendSuccess(() -> Component.translatable(PREFIX + "started", name, run.id, definition.sources), false);
            source.sendSuccess(() -> Component.translatable(definition.spec.descriptionKey()), false);
            return 1;
        } catch (RuntimeException failure) {
            if (ACTIVE.containsKey(source.getServer())) { finish(source.getServer(), context, "start_error"); }
            LOGGER.error("[IDTW_DEBUG] 场景启动失败 scene={}", name, failure);
            source.sendFailure(Component.translatable(PREFIX + "failed", failure.toString()));
            return 0;
        }
    }

    // 只对已登记UUID使用场景索引；返回null表示继续用用户的普通规则索引。
    public static @Nullable List<Rule> candidates(ItemEntity item) {
        if (!DebugMode.ENABLED) { return null; }
        Binding binding = ENTITIES.get(item.getUUID());
        if (binding == null) { return null; }
        return binding.source ? binding.run.definition.index.candidates(BuiltInRegistries.ITEM.getKey(item.getItem().getItem())) : List.of();
    }

    // 全局规则为空时，reload回扫仍需重建本轮测试源的检查任务。
    public static boolean inactive(MinecraftServer server) { return !DebugMode.ENABLED || !ACTIVE.containsKey(server); }

    // 性能源关闭已有逐实体运行日志，即使用户配置debug_logging=true也不刷屏。
    public static boolean allowsRuntimeLogging(ItemEntity source) {
        Binding binding = DebugMode.ENABLED ? ENTITIES.get(source.getUUID()) : null;
        return binding == null || !binding.run.benchmark;
    }

    // 实体入世界之前登记，保证两平台原生加入事件看到相同场景规则。
    static void bind(DebugScenarioRun run, ItemEntity item, boolean source) { ENTITIES.put(item.getUUID(), new Binding(run, source)); }

    // 清理按本轮实体集合删除UUID绑定，不扫描其他服务器或普通实体。
    static void unbind(DebugScenarioRun run) {
        run.sources.forEach(item -> ENTITIES.remove(item.getUUID()));
        run.outputs.forEach(item -> ENTITIES.remove(item.getUUID()));
        run.probe.fixtures.forEach(item -> ENTITIES.remove(item.getUUID()));
    }

    // 后端观察入口只做有界计数；只有功能场景构建过程日志。
    public static void observe(ItemEntity source, String event, Object... fields) {
        if (!DebugMode.ENABLED) { return; }
        Binding binding = ENTITIES.get(source.getUUID());
        if (binding != null && binding.source) { binding.run.observe(event, source, fields); }
    }

    // 用实际提交参数校对轮次与规则，不再次执行条件预测。
    public static void converted(ItemEntity source, Rule rule, int rounds) {
        if (!DebugMode.ENABLED) { return; }
        Binding binding = ENTITIES.get(source.getUUID());
        if (binding != null && binding.source) { binding.run.converted(source, rule.id().toString(), rounds); }
    }

    // 为实际队列任务保留本轮句柄，完成时释放，停止时可精确取消而不清空普通队列。
    public static ScheduledTask schedule(ItemEntity source, ServerScheduler scheduler, Object realmKey,
                                         ServerTaskKind kind, long dueTick, String name, Runnable action) {
        Binding binding = DebugMode.ENABLED ? ENTITIES.get(source.getUUID()) : null;
        if (binding == null || !binding.source) {
            return scheduler.scheduleAt(realmKey, new RunnableTask(kind, name, action), dueTick);
        }
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = scheduler.scheduleAt(realmKey, new RunnableTask(kind, name, () -> {
            binding.run.tasks.remove(holder[0]);
            action.run();
        }), dueTick);
        binding.run.tasks.add(holder[0]);
        return holder[0];
    }

    // 产物加入前隔离它的规则和合并行为，避免测试产物触发用户的其他规则。
    public static void prepareOutput(EffectContext context, Entity entity) {
        if (!DebugMode.ENABLED || !(entity instanceof ItemEntity item)) { return; }
        Binding binding = ENTITIES.get(context.source().getUUID());
        if (binding == null) { return; }
        binding.run.decorate(item);
        binding.run.outputs.add(item);
        bind(binding.run, item, false);
    }

    // 必须在addFreshEntity成功后调用，最终校对依据实际加入世界的产物数量。
    public static void outputAdded(EffectContext context, Entity entity) {
        if (!DebugMode.ENABLED || !(entity instanceof ItemEntity item)) { return; }
        Binding binding = ENTITIES.get(context.source().getUUID());
        if (binding != null) { binding.run.outputAdded(item); }
    }

    // 现有双平台tick事件转发到同一推进器；维度失效或异常时结束并清理。
    public static void tick(MinecraftServer server, RuleCommandContext context) {
        DebugScenarioRun run = ACTIVE.get(server);
        if (run == null) { return; }
        try {
            if (server.getLevel(run.level.dimension()) != run.level) {
                finish(server, context, "dimension_unloaded");
            } else if (run.tick(context)) { finish(server, context, "completed"); }
        } catch (RuntimeException failure) {
            LOGGER.error("[IDTW_DEBUG] run={} 场景执行失败", run.id, failure);
            JsonObject data = run.state();
            data.addProperty("error", failure.toString());
            run.log("ERROR", data);
            finish(server, context, "error");
        }
    }

    // 手动停止只结束本轮负载，提前停止的结果明确为INCOMPLETE。
    public static int stop(CommandSourceStack source, RuleCommandContext context) {
        if (DebugPipelineManager.active(source.getServer())) { return DebugPipelineManager.stop(source, context); }
        if (!ACTIVE.containsKey(source.getServer())) { return failure(source, "not_running"); }
        return finish(source.getServer(), context, "manual_stop");
    }

    // 查询仅返回活动场景的操作状态，不触发读取日志或新一轮采样。
    public static int status(CommandSourceStack source) {
        if (DebugPipelineManager.active(source.getServer())) { return DebugPipelineManager.status(source); }
        DebugScenarioRun run = ACTIVE.get(source.getServer());
        if (run == null) { return failure(source, "not_running"); }
        source.sendSuccess(() -> Component.translatable(PREFIX + "status", run.name, run.id,
                Component.translatable(PREFIX + "phase." + run.state().get("phase").getAsString()),
                run.sources.size(), run.definition.sources), false);
        return 1;
    }

    // 标记用户的肉眼观察，性能结果中保留数量以识别日志扰动。
    public static int mark(CommandSourceStack source, String text) {
        DebugScenarioRun run = ACTIVE.get(source.getServer());
        if (run == null) { return failure(source, "not_running"); }
        if (!run.mark(text)) { return failure(source, "mark_limit"); }
        source.sendSuccess(() -> Component.translatable(PREFIX + "marked", run.id), false);
        return 1;
    }

    // 发起者离线或停服时继续将结果交付到控制台，并释放静态引用。
    public static void shutdown(MinecraftServer server, RuleCommandContext context) {
        if (ACTIVE.containsKey(server)) { finish(server, context, "server_stopping"); }
    }

    // 统一结束路径；异常不能阻止正常停服或留下服务器引用。
    private static int finish(MinecraftServer server, RuleCommandContext context, String reason) {
        DebugScenarioRun run = ACTIVE.remove(server);
        if (run == null) { return 0; }
        String verdict = "INCOMPLETE";
        try {
            verdict = run.finish(context, reason);
            String displayVerdict = verdict;
            var player = server.getPlayerList().getPlayer(run.initiator);
            var recipient = player == null ? server.createCommandSourceStack() : player.createCommandSourceStack();
            recipient.sendSuccess(() -> Component.translatable(PREFIX + "finished", run.name, run.id,
                    Component.translatable(PREFIX + "verdict." + displayVerdict)), false);
            return 1;
        } catch (RuntimeException failure) {
            LOGGER.error("[IDTW_DEBUG] run={} 场景清理失败", run.id, failure);
            return 0;
        } finally {
            unbind(run);
            DebugPipelineManager.sceneFinished(server, run, verdict);
        }
    }

    // 错误反馈保持本地化。
    private static int failure(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(PREFIX + key));
        return 0;
    }
}
