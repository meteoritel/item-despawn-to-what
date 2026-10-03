package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ScheduledTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudgetSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 一轮有界真实负载：准备、预热、测量、动作和清理均在服务端主线程推进。 */
final class DebugScenarioRun {
    final String id = UUID.randomUUID().toString();
    final String name;
    final boolean benchmark;
    final ServerLevel level;
    final UUID initiator;
    final DebugScenarioDefinition definition;
    final List<ItemEntity> sources = new ArrayList<>();
    final List<ItemEntity> outputs = new ArrayList<>();
    final Set<ScheduledTask> tasks = new HashSet<>();
    private final Vec3 origin;
    private final int seconds;
    private final long startedWorldTick;
    private final long startedNanos = System.nanoTime();
    private final Map<String, Long> counters = new HashMap<>();
    private final Set<UUID> committed = new HashSet<>();
    private DebugPerformanceWindow window;
    private String phase = "SETUP";
    private long preparedTick;
    private long lastFrameNanos;
    private long firstConversionTick = -1;
    private long firstOutputTick = -1;
    private int firstConversionAge = -1;
    private int expiryLifespan;
    private long minimumDueAge;
    private String firstRule = "";
    private long conversionsAtWindowStart;
    private long outputAtWindowStart;
    private boolean actionDone;
    private int marks;
    private boolean viewSnapshotted;
    private double viewX;
    private double viewY;
    private double viewZ;
    private float viewYaw;
    private float viewPitch;

    // 固定场景参数，测试源使用独立规则索引，不替换用户运行时索引。
    DebugScenarioRun(String name, boolean benchmark, ServerLevel level, UUID initiator,
                     Vec3 origin, int seconds, DebugScenarioDefinition definition) {
        this.name = name;
        this.benchmark = benchmark;
        this.level = level;
        this.initiator = initiator;
        this.origin = origin;
        this.seconds = seconds;
        this.definition = definition;
        startedWorldTick = level.getGameTime();
    }

    // 每秒输出计数和当前队列，不重复计算分位数或扫描世界实体。
    boolean tick(RuleCommandContext context) {
        ConversionRuntime runtime = java.util.Objects.requireNonNull(context.runtime());
        if (phase.equals("SETUP")) {
            prepare(context);
            if (sources.size() == definition.sources) {
                preparedTick = level.getGameTime();
                phase = "WARMUP";
                log("PREPARED", state());
            }
            if (System.nanoTime() - startedNanos > 30_000_000_000L) {
                throw new IllegalStateException("场景准备超过30秒，请减少实体数量或检查区块是否在推进");
            }
            return false;
        }
        if (phase.equals("WARMUP")) {
            if (level.getGameTime() - preparedTick < 20) {
                if (System.nanoTime() - startedNanos > 30_000_000_000L) { throw new IllegalStateException("预热时世界时间没有正常推进"); }
                return false;
            }
            window = new DebugPerformanceWindow(level, runtime);
            conversionsAtWindowStart = count("CONVERT");
            outputAtWindowStart = count("OUTPUT_ITEMS");
            phase = "MEASURE";
            lastFrameNanos = System.nanoTime();
            log("MEASURE_BEGIN", state());
            if (benchmark) { snapshotInitiatorView(); updateCountdown(); }
            return false;
        }
        window.sample(level.getServer(), level, runtime);
        actions(context);
        if (benchmark) { lockInitiatorView(); }
        long now = System.nanoTime();
        if (now - lastFrameNanos >= 1_000_000_000L) {
            lastFrameNanos = now;
            JsonObject frame = state();
            frame.addProperty("tracked_in_dimension", runtime.trackedCount(level));
            frame.addProperty("total_pending_in_dimension", runtime.pendingTasks(level));
            frame.addProperty("checks_pending", runtime.queueStats(level, false).pending());
            frame.addProperty("effects_pending", runtime.queueStats(level, true).pending());
            frame.addProperty("checks_last_us", runtime.queueStats(level, false).lastMicros());
            frame.addProperty("effects_last_us", runtime.queueStats(level, true).lastMicros());
            SchedulerStats merged = runtime.schedulerStats();
            ServerTickBudgetSnapshot budget = runtime.budgetSnapshot();
            // 预算与计数都是服务器级，看的是所有维度共享后的剩余额度
            frame.addProperty("scheduler_pending_server_wide", merged.pending());
            frame.addProperty("scheduler_steps_server_wide", merged.steps());
            frame.addProperty("scheduler_cancelled_server_wide", merged.cancelled());
            frame.addProperty("scheduler_dropped_server_wide", merged.dropped());
            frame.addProperty("scheduler_max_ready_delay_ticks", merged.maxReadyDelayTicks());
            // 阶段7：本 tick 的服务器级就绪延迟与到期搬运量，用于逐 tick 观察平滑窗口是否铺开
            frame.addProperty("scheduler_last_max_ready_delay_ticks", merged.lastMaxReadyDelayTicks());
            frame.addProperty("scheduler_drained_in_tick", merged.lastDrainedTasks());
            if (budget != null) {
                frame.addProperty("budget_used_us", budget.usedNanos() / 1000);
                frame.addProperty("budget_usage_ratio", budget.usageRatio());
                frame.addProperty("budget_work_units_used", budget.usedWorkUnits());
                frame.addProperty("budget_exhaustion_reason", budget.exhaustionReason());
                frame.addProperty("budget_drained_tasks", budget.drainedTasks());
                frame.addProperty("budget_work_remaining", budget.workRemaining());
            }
            if (!benchmark && !sources.isEmpty()) {
                var current = level.getEntity(sources.getFirst().getUUID());
                frame.addProperty("source_available", current instanceof ItemEntity);
                if (current instanceof ItemEntity item) {
                    var info = runtime.debugInfo(level, item);
                    frame.addProperty("source_age_ticks", item.getAge());
                    frame.addProperty("source_tracked", info.tracked());
                    frame.addProperty("source_failure_count", info.failureCount());
                    frame.addProperty("source_next_check_tick", info.nextCheckTick());
                }
            }
            log("FRAME", frame);
            if (benchmark) { updateCountdown(); }
        }
        return window.elapsedSeconds() >= seconds || window.full();
    }

    // 每 tick 的源数量与准备软预算改由 server.json 提供（debug_scenario_prepare_batch_size / debug_scenario_prepare_budget_us）；准备耗时不混入测量窗口。
    private void prepare(RuleCommandContext context) {
        ConversionRuntime runtime = java.util.Objects.requireNonNull(context.runtime());
        ServerConfig config = context.serverConfig();
        if (config == null) {
            config = ServerConfig.DEFAULT;
        }
        // 默认 128 与 2000us 与原硬编码 128 / 2_000_000L 逐位等价；这两键只影响场景准备，不参与 server_budget_us
        int batchSize = config.debugScenarioPrepareBatchSize();
        long deadline = System.nanoTime() + (long) config.debugScenarioPrepareBudgetUs() * 1000L;
        for (int batch = 0; batch < batchSize && sources.size() < definition.sources && System.nanoTime() < deadline; batch++) {
            int number = sources.size();
            Vec3 position = position(number);
            if (!LoadedChunks.containsArea(level, BlockPos.containing(position), 1)
                    || !level.isPositionEntityTicking(BlockPos.containing(position))) {
                throw new IllegalStateException("场景区域需要已加载且正在推进的区块：" + position);
            }
            ItemEntity item = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(Items.PAPER, definition.stackSize));
            decorate(item);
            if (number == 0) {
                long earliest = definition.index.ordered().stream().mapToLong(entry ->
                        (long) entry.value().triggerAfterSeconds() * 20).min().orElse(0);
                minimumDueAge = Math.min(earliest, runtime.lifespanTicks(level, item) - 1L);
            }
            if (name.equals("expiry")) { setExpiryAge(runtime, item); }
            if (name.equals("excluded")) {
                if (number == 0) { item.addTag(Constants.CHECK_LOCK_TAG); }
                else { item.setUnlimitedLifetime(); }
            }
            sources.add(item);
            DebugScenarioManager.bind(this, item, true);
            if (!level.addFreshEntity(item)) { throw new IllegalStateException("测试源实体加入世界被拒绝"); }
            trace("SOURCE_ADDED", item, "initial_count", definition.stackSize);
        }
    }

    // 0.25格网格固定负载密度；独立组件防合并，实体数不会悄悄减少。
    private Vec3 position(int number) {
        if (!benchmark) { return origin.add(number * 1.0, 0, 0); }
        int width = Math.max(1, (int) Math.ceil(Math.sqrt(definition.sources)));
        int row = number / width;
        return origin.add((number % width - (width - 1) / 2D) * 0.25, 0,
                (row - (width - 1) / 2D) * 0.25);
    }

    // 通过原版实体NBT设置年龄，下一次实体tick真实进入自然消失入口。
    private void setExpiryAge(ConversionRuntime runtime, ItemEntity item) {
        expiryLifespan = runtime.lifespanTicks(level, item);
        if (expiryLifespan > 32767) { throw new IllegalStateException("expiry场景年龄超出原版NBT short范围：" + expiryLifespan); }
        CompoundTag saved = item.saveWithoutId(new CompoundTag());
        saved.putShort("Age", (short) (expiryLifespan - 1));
        item.load(saved);
    }

    // 防捡取与合并，不改地形、天气和时间；额外组件只附在本轮实体上。
    void decorate(ItemEntity item) {
        CompoundTag marker = new CompoundTag();
        marker.putString("idtw_debug_run", id);
        marker.putString("idtw_debug_entity", item.getUUID().toString());
        item.getItem().set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        item.addTag("idtw_debug_fixture");
        item.setNeverPickUp();
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
    }

    // 功能场景主动操作世界中的测试实体，随后仍由原调度与求值链路决定结果。
    private void actions(RuleCommandContext context) {
        if (benchmark || actionDone || count("RETRY") == 0 && !name.equals("reload")) { return; }
        if (name.equals("retry") && level.getGameTime() - startedWorldTick >= 100) {
            ItemEntity source = sources.getFirst();
            source.setPos(source.getX(), origin.y + 3, source.getZ());
            actionDone = true;
            trace("ACTION", source, "action", "move_source_to_matching_y");
        } else if (name.equals("reload") && count("CONVERT") > 0) {
            if (context.reloadRules(level.getServer()) == null) { throw new IllegalStateException("场景中的规则重载失败"); }
            actionDone = true;
            JsonObject data = state();
            data.addProperty("overlay_version", context.overlayVersion());
            log("ACTION_RELOAD", data);
        }
    }

    // 测量期锁定发起者视角：仅在偏离快照阈值时用服务端传送纠正并发包，静止时零开销。
    private ServerPlayer initiatorPlayer() {
        return level.getServer().getPlayerList().getPlayer(initiator);
    }

    private void snapshotInitiatorView() {
        ServerPlayer player = initiatorPlayer();
        if (player == null) { return; }
        viewX = player.getX();
        viewY = player.getY();
        viewZ = player.getZ();
        viewYaw = player.getYRot();
        viewPitch = player.getXRot();
        viewSnapshotted = true;
    }

    private void lockInitiatorView() {
        if (!viewSnapshotted) { return; }
        ServerPlayer player = initiatorPlayer();
        if (player == null) { return; }
        double dx = player.getX() - viewX;
        double dy = player.getY() - viewY;
        double dz = player.getZ() - viewZ;
        if (dx * dx + dy * dy + dz * dz > 1.0E-6
                || Math.abs(player.getYRot() - viewYaw) > 0.01F
                || Math.abs(player.getXRot() - viewPitch) > 0.01F) {
            player.teleportTo(level, viewX, viewY, viewZ, viewYaw, viewPitch);
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    // 动作栏倒计时只在测量期逐秒刷新，结束时由 releaseInitiator 清空。
    private void updateCountdown() {
        ServerPlayer player = initiatorPlayer();
        if (player == null) { return; }
        int remaining = (int) Math.max(0.0, Math.ceil(seconds - window.elapsedSeconds()));
        player.displayClientMessage(Component.translatable(
                "itemdespawntowhat.command.debug.scene.countdown", remaining), true);
    }

    private void releaseInitiator() {
        ServerPlayer player = initiatorPlayer();
        if (player != null) { player.displayClientMessage(Component.empty(), true); }
    }

    // 真实后端入口递增计数；性能场景不创建逐实体JSON，不写逐实体日志。
    void observe(String event, ItemEntity item, Object... fields) {
        if (phase.equals("CLEANUP")) { return; }
        increment(event, 1);
        trace(event, item, fields);
    }

    // 可读过程参数只用于小规模功能场景。
    void trace(String event, ItemEntity item, Object... fields) {
        if (benchmark || phase.equals("CLEANUP")) { return; }
        JsonObject data = DebugLog.item(item);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            Object value = fields[i + 1];
            String key = fields[i].toString();
            if (value instanceof Number number) { data.addProperty(key, number); }
            else if (value instanceof Boolean flag) { data.addProperty(key, flag); }
            else { data.addProperty(key, String.valueOf(value)); }
        }
        log(event, data);
    }

    // 提交与产出分别计数，不能用执行器返回来宣称所有产物已经生成。
    void converted(ItemEntity item, String rule, int rounds) {
        if (!committed.add(item.getUUID())) { increment("DUPLICATE_CONVERSION", 1); }
        if (firstConversionTick < 0) {
            firstConversionTick = level.getGameTime();
            firstConversionAge = item.getAge();
            firstRule = rule;
        }
        observe("CONVERT", item, "rule", rule, "rounds", rounds);
    }

    // 世界成功接收产物后统计数量与类型，准备失败不会冒充产出成功。
    void outputAdded(ItemEntity item) {
        if (firstOutputTick < 0) { firstOutputTick = level.getGameTime(); }
        increment("OUTPUT_ITEMS", item.getItem().getCount());
        if (!item.getItem().is(Items.PRISMARINE_SHARD)) { increment("UNEXPECTED_OUTPUT", item.getItem().getCount()); }
        observe("OUTPUT_ADDED", item, "actual_items", item.getItem().getCount());
    }

    // 注记是操作者观察，和后端事件明确分开。
    boolean mark(String text) {
        if (marks >= 64) { return false; }
        marks++;
        JsonObject data = state();
        int end = Math.min(160, text.length());
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) { end--; }
        data.addProperty("text", text.substring(0, end));
        log("MARK", data);
        return true;
    }

    // 状态和周期汇总为有界计数，不扫描普通世界实体。
    JsonObject state() {
        JsonObject data = new JsonObject();
        data.addProperty("phase", phase);
        data.addProperty("created_sources", sources.size());
        data.addProperty("requested_sources", definition.sources);
        data.addProperty("elapsed_wall_seconds", (System.nanoTime() - startedNanos) / 1_000_000_000D);
        data.addProperty("world_tick", level.getGameTime());
        data.addProperty("marks", marks);
        JsonObject events = new JsonObject();
        counters.forEach(events::addProperty);
        for (String key : List.of("TRACKED", "AGE_NOT_READY", "CONDITION_FALSE", "CONDITION_TRUE", "RETRY", "CONVERT",
                "OUTPUT_ITEMS", "NATURAL_EXPIRY_DEFERRED", "EXCLUDED", "ERROR", "DUPLICATE_CONVERSION", "UNEXPECTED_OUTPUT")) {
            if (!events.has(key)) { events.addProperty(key, 0); }
        }
        data.add("actual_events", events);
        return data;
    }

    // 先封存预期与实际，再取消本轮任务并清理实体；普通队列任务保持原状。
    String finish(RuleCommandContext context, String reason) {
        JsonObject end = state();
        end.addProperty("stop_reason", reason);
        end.add("expected", expected());
        long liveOutput = outputs.stream().filter(Entity::isAlive).mapToLong(item -> item.getItem().getCount()).sum();
        long remainingSource = sources.stream().filter(Entity::isAlive).mapToLong(item -> item.getItem().getCount()).sum();
        end.addProperty("remaining_source_items_at_end", remainingSource);
        end.addProperty("live_output_items_at_end", liveOutput);
        end.addProperty("first_conversion_age", firstConversionAge);
        end.addProperty("first_rule", firstRule);
        end.addProperty("first_output_delay_ticks", firstOutputTick < 0 || firstConversionTick < 0 ? -1 : firstOutputTick - firstConversionTick);
        end.addProperty("conversions_before_window", conversionsAtWindowStart);
        end.addProperty("output_items_before_window", outputAtWindowStart);
        if (window != null) {
            end.add("window", window.summary(level));
            end.addProperty("conversions_in_window", count("CONVERT") - conversionsAtWindowStart);
            end.addProperty("output_items_in_window", count("OUTPUT_ITEMS") - outputAtWindowStart);
        }
        String verdict = reason.equals("completed") ? verify(liveOutput, remainingSource) ? "PASS" : "FAIL" : "INCOMPLETE";
        end.addProperty("verdict", verdict);
        if (benchmark) { releaseInitiator(); }
        phase = "CLEANUP";
        try { cleanup(context.runtime()); }
        finally {
            end.addProperty("remaining_scene_tasks", tasks.size());
            var runtime = context.runtime();
            if (runtime != null) {
                end.addProperty("checks_pending_after_cleanup", runtime.queueStats(level, false).pending());
                end.addProperty("effects_pending_after_cleanup", runtime.queueStats(level, true).pending());
                // 清理后仍遗留的服务器级任务数：用于证明延后任务没被静默丢弃
                end.addProperty("scheduler_pending_after_cleanup", runtime.schedulerStats().pending());
                end.addProperty("scheduler_dropped_total", runtime.schedulerStats().dropped());
            }
            DebugScenarioManager.unbind(this);
            log("END", end);
        }
        return verdict;
    }

    // 预期字段全部显式输出，包括应当为零的量。
    private JsonObject expected() {
        JsonObject data = new JsonObject();
        data.addProperty("conversions", definition.expectedConversions);
        data.addProperty("output_items", definition.expectedOutput);
        data.addProperty("output_item", "minecraft:prismarine_shard");
        data.addProperty("delay_ticks", name.equals("delay") || name.equals("reload") ? 100 : 0);
        data.addProperty("minimum_due_age_ticks", minimumDueAge);
        data.addProperty("remaining_source_items", expectedSourceItems());
        return data;
    }

    // 转化场景应消耗源；排除和持续失败负载应保留原有源数量。
    private long expectedSourceItems() {
        return definition.expectedConversions == 0 ? (long) definition.sources * definition.stackSize : 0;
    }

    // 校对真实次数与留存产物；额外场景约束由真实事件证据决定。
    private boolean verify(long liveOutput, long remainingSource) {
        if (sources.size() != definition.sources || window == null || count("ERROR") != 0
                || count("DUPLICATE_CONVERSION") != 0 || count("UNEXPECTED_OUTPUT") != 0
                || count("CONVERT") != definition.expectedConversions
                || count("OUTPUT_ITEMS") != definition.expectedOutput || liveOutput != definition.expectedOutput) { return false; }
        if (remainingSource != expectedSourceItems()) { return false; }
        if (definition.expectedConversions > 0 && firstConversionAge < minimumDueAge) { return false; }
        return switch (name) {
            case "retry" -> count("CONDITION_FALSE") > 0 && count("RETRY") > 0 && (benchmark || actionDone);
            case "delay" -> firstOutputTick - firstConversionTick >= 100;
            case "reload" -> actionDone && firstOutputTick - firstConversionTick >= 100;
            case "expiry" -> count("NATURAL_EXPIRY_DEFERRED") > 0 && firstConversionAge >= expiryLifespan - 1;
            case "priority" -> firstRule.equals("itemdespawntowhat:debug/priority") && count("AGE_NOT_READY") > 0;
            case "excluded" -> count("EXCLUDED") == 2 && sources.stream().allMatch(Entity::isAlive);
            default -> true;
        };
    }

    // 已完成任务已从集合释放，停止时仅取消本轮尚在队列中的效果。
    private void cleanup(ConversionRuntime runtime) {
        tasks.forEach(task -> task.cancel(CancelReason.SCENARIO_STOP));
        tasks.clear();
        for (ItemEntity source : sources) {
            if (runtime != null) { runtime.onItemRemoved(level, source); }
            discardLoaded(source);
        }
        for (ItemEntity output : outputs) { discardLoaded(output); }
    }

    // 不加载区块；若实体已重新加载，按UUID移除当前实例。
    private void discardLoaded(ItemEntity item) {
        for (var currentLevel : level.getServer().getAllLevels()) {
            Entity loaded = currentLevel.getEntity(item.getUUID());
            if (loaded != null) { loaded.discard(); }
        }
        if (!item.isRemoved()) { item.discard(); }
    }

    // 所有行都带同一编号，IDEA可直接按run过滤。
    void log(String event, JsonObject data) { DebugLog.write(id, (benchmark ? "bench/" : "run/") + name, event, data); }

    // 场景只维护有限事件名称的累计计数。
    private void increment(String event, long amount) { counters.merge(event, amount, Long::sum); }

    // 不存在的事件视为零，避免缺失字段被误读为已发生。
    private long count(String event) { return counters.getOrDefault(event, 0L); }
}
