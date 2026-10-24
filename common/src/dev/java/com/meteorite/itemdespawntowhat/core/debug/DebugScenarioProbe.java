package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 所有场景共用的后端观测与实物校验；性能模式只保留汇总计数，不扫描返还实体。 */
final class DebugScenarioProbe {
    final List<ItemEntity> fixtures = new ArrayList<>();
    private final DebugScenarioRun run;
    private final Map<Item, Long> outputs = new LinkedHashMap<>();
    private final Map<Item, Long> outputDelays = new LinkedHashMap<>();
    private final Map<String, Long> candidates = new LinkedHashMap<>();
    private final Map<String, Long> totals = new LinkedHashMap<>();
    private final List<ItemEntity> returns = new ArrayList<>();
    private @Nullable ItemEntity catalyst;
    private boolean prepared;
    // 首次检查失败后的重试延迟：直接证明失败后真的排到了下一次检查，-1 表示未观察到
    private long firstCheckRetryDelayTicks = -1L;

    // 每轮独立累计，不读取上一轮日志或结算记录。
    DebugScenarioProbe(DebugScenarioRun run) { this.run = run; }

    // 催化剂先入世界并绑定空规则，使用真实物品实体供后端预留与支付。
    void prepare(Vec3 origin) {
        if (prepared) { return; }
        int count = run.definition.spec.catalystCount();
        if (count > 0) {
            AABB area = new AABB(origin, origin).inflate(3);
            if (!run.level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(run.definition.catalystItem)).isEmpty()) {
                throw new IllegalStateException("催化剂场景周围3格已有同类掉落物，请清空后重新触发，避免消耗非测试物品");
            }
            Vec3 position = origin.add(0.5, 0, 0);
            catalyst = new ItemEntity(run.level, position.x, position.y, position.z,
                    new ItemStack(run.definition.catalystItem, count));
            run.decorate(catalyst);
            fixtures.add(catalyst);
            DebugScenarioManager.bind(run, catalyst, false);
            if (!run.level.addFreshEntity(catalyst)) { throw new IllegalStateException("场景催化剂加入世界被拒绝"); }
            run.trace("CATALYST_ADDED", catalyst, "initial_count", count);
        }
        prepared = true;
    }

    // 性能模式按注册表对象累计，避免为每个产物构造物品标识字符串。
    void outputAdded(ItemEntity item, long conversionTick) {
        Item type = item.getItem().getItem();
        outputs.merge(type, (long) item.getItem().getCount(), Long::sum);
        if (!run.benchmark) { outputDelays.putIfAbsent(type, run.level.getGameTime() - conversionTick); }
    }

    // 复用已有后端事件，结算计数与实物计数独立交叉核对。
    void observe(String event, Object... fields) {
        switch (event) {
            case "OUTCOME_PLANNED" -> candidates.merge(String.valueOf(field(fields, "candidate")), 1L, Long::sum);
            case "SETTLEMENT_COMPLETED" -> {
                for (String key : List.of("groups", "consumed_sources", "returned", "pending_units", "pending_delivery",
                        "prepared_groups")) {
                    Object value = field(fields, key);
                    if (!(value instanceof Number number)) { throw new IllegalStateException("结算事件缺少数值：" + key); }
                    totals.merge(key, number.longValue(), Long::sum);
                }
                totals.merge("settlements_completed", 1L, Long::sum);
                // 账本写入合并口径随完成事件上报：本场景所有完成结算的记录级落账次数与被步内合并的变更次数
                for (String key : List.of("ledger_commits", "ledger_deferred_mutations")) {
                    Object value = field(fields, key);
                    if (!(value instanceof Number number)) { throw new IllegalStateException("结算事件缺少数值：" + key); }
                    totals.merge(key, number.longValue(), Long::sum);
                }
            }
            case "CHECK_TASK_FAILED" -> {
                totals.merge("check_failures", 1L, Long::sum);
                // 释放路径的重试延迟为 0（不重试），只有真的重排才有正值可断言
                if (firstCheckRetryDelayTicks < 0L && field(fields, "retry_delay_ticks") instanceof Number delay
                        && delay.longValue() > 0L) {
                    firstCheckRetryDelayTicks = delay.longValue();
                }
            }
            case "EFFECT_CHANCE_SKIPPED" -> totals.merge("chance_skipped", 1L, Long::sum);
            case "PAYMENT_BLOCKED" -> totals.merge("payment_blocked_" + field(fields, "reason"), 1L, Long::sum);
            case "EFFECT_SETTLED" -> {
                if (String.valueOf(field(fields, "outcome")).equals("FAILED")) { totals.merge("failed_effects", 1L, Long::sum); }
            }
            case "REBATE_DELIVERED" -> {
                Object returned = field(fields, "returned");
                if (returned instanceof Number number && number.longValue() > 0) { captureReturns(); }
            }
            default -> { }
        }
    }

    // 小规模功能场景在返还完成或结束时扫描已加载实体，标记组件限定本轮范围。
    private void captureReturns() {
        if (run.benchmark) { return; }
        CompoundTag marker = new CompoundTag();
        marker.putString(Constants.ID_NAMESPACE + "_debug_run", run.id);
        for (Entity entity : run.level.getAllEntities()) {
            if (!(entity instanceof ItemEntity item) || !item.isAlive() || !item.getItem().is(run.definition.sourceItem)
                    || run.sources.contains(item) || run.outputs.contains(item) || fixtures.contains(item)) { continue; }
            var data = item.getItem().get(DataComponents.CUSTOM_DATA);
            if (data == null || !data.matchedBy(marker)) { continue; }
            fixtures.add(item);
            returns.add(item);
            run.decorate(item);
            DebugScenarioManager.bind(run, item, false);
            var state = DropStateStore.get(item);
            run.trace("RETURN_OBSERVED", item, "permanent_protection", state.permanentProtection(),
                    "permanent_conversion_ban", state.permanentConversionBan());
        }
    }

    // 基础不变量和资源声明都走同一个断言器：不按场景名称或场景种类分支，检查口径统一。
    JsonArray checks(JsonObject metrics) {
        captureReturns();
        run.tasks.removeIf(ScheduledTaskHandle::terminal);
        long pendingEffects = run.tasks.stream().filter(task -> task.kind() != ServerTaskKind.CONDITION_CHECK).count();
        long pendingChecks = run.tasks.size() - pendingEffects;
        metrics.addProperty("pending_scene_effects", pendingEffects);
        metrics.addProperty("pending_scene_checks", pendingChecks);
        for (String key : List.of("groups", "consumed_sources", "returned", "pending_units", "pending_delivery",
                "settlements_completed", "chance_skipped", "failed_effects")) {
            metrics.addProperty(key, totals.getOrDefault(key, 0L));
        }
        metrics.addProperty("check_failures", totals.getOrDefault("check_failures", 0L));
        // 账本写入合并口径（本场景聚合）：字段变更次数与被步内合并的变更次数；
        // 成批交付场景里 deferred 明显大于 0，说明提交确实被合并到步末，而不是每堆一次
        metrics.addProperty("settlement_ledger_commits", totals.getOrDefault("ledger_commits", 0L));
        metrics.addProperty("settlement_ledger_deferred_mutations", totals.getOrDefault("ledger_deferred_mutations", 0L));
        // 支付受阻原因分别计数：场景可断言「截断整批时究竟缺的是哪一种资源」
        for (String key : List.of("payment_blocked_catalyst_short", "payment_blocked_threshold_short",
                "payment_blocked_fluid_short", "payment_blocked_capacity_short")) {
            metrics.addProperty(key, totals.getOrDefault(key, 0L));
        }
        metrics.addProperty("first_check_retry_delay_ticks", firstCheckRetryDelayTicks);
        long returned = returns.stream().filter(Entity::isAlive).mapToLong(item -> item.getItem().getCount()).sum();
        long protectedReturns = returns.stream().filter(Entity::isAlive)
                .filter(item -> DropStateStore.get(item).permanentProtection() && DropStateStore.get(item).permanentConversionBan())
                .mapToLong(item -> item.getItem().getCount()).sum();
        metrics.addProperty("live_returned_source_items", returned);
        metrics.addProperty("protected_returned_source_items", protectedReturns);
        metrics.addProperty("remaining_catalyst", catalyst != null && catalyst.isAlive() ? catalyst.getItem().getCount() : 0);
        JsonObject expected = run.definition.expected;
        JsonObject expectedMetrics = expected.getAsJsonObject("metrics");
        for (String key : expectedMetrics.keySet()) {
            if (key.startsWith("events/")) { metrics.addProperty(key, run.count(key.substring(7))); }
            else if (key.startsWith("first_output_delay/")) {
                String itemId = key.substring("first_output_delay/".length());
                long delay = outputDelays.entrySet().stream().filter(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString().equals(itemId))
                        .mapToLong(Map.Entry::getValue).findFirst().orElse(-1);
                metrics.addProperty(key, delay);
            }
        }
        DebugScenarioChecks checks = new DebugScenarioChecks(metrics);
        checks.equal("created_sources", run.definition.sources, run.sources.size());
        checks.equal("window_present", 1, metrics.get("window_present").getAsLong());
        checks.equal("errors", 0, run.count("ERROR"));
        checks.equal("duplicate_conversions", 0, run.count("DUPLICATE_CONVERSION"));
        checks.equal("early_conversions", 0, run.count("EARLY_CONVERSION"));
        checks.equal("pending_scene_effects", 0, pendingEffects);
        checks.equal("failed_effects", 0, totals.getOrDefault("failed_effects", 0L));
        checks.metrics(expectedMetrics);
        checks.counts("output", expected.getAsJsonObject("outputs"), itemCounts(outputs));
        Map<Item, Long> live = new LinkedHashMap<>();
        run.outputs.stream().filter(Entity::isAlive).forEach(item -> live.merge(item.getItem().getItem(), (long) item.getItem().getCount(), Long::sum));
        checks.counts("live_output", expected.getAsJsonObject("outputs"), itemCounts(live));
        if (expected.has("candidates")) { checks.counts("candidate_selections", expected.getAsJsonObject("candidates"), candidates); }
        return checks.results();
    }

    // 结算完成时的账目快照与最终世界产物分开，延迟效果可能在此快照之后才执行。
    JsonObject settlementSnapshot() {
        JsonObject snapshot = new JsonObject();
        totals.forEach(snapshot::addProperty);
        return snapshot;
    }

    // 转换仅在 END 汇总执行，不增加性能场景逐实体字符串分配。
    private static Map<String, Long> itemCounts(Map<Item, Long> counts) {
        Map<String, Long> named = new LinkedHashMap<>();
        counts.forEach((item, count) -> named.put(BuiltInRegistries.ITEM.getKey(item).toString(), count));
        return named;
    }

    // 事件参数沿用后端成对字段格式。
    private static Object field(Object[] fields, String name) {
        for (int i = 0; i + 1 < fields.length; i += 2) { if (name.equals(fields[i])) { return fields[i + 1]; } }
        return "";
    }
}
