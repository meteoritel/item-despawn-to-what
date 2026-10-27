package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 模组级服务端配置（config/itemdespawntowhat/server.json）。
 * 与规则配置分离：这里是服务器性能与运维参数，两端共用同一实现与同一份文件。
 */
public record ServerConfig(
        Scheduling scheduling,
        BoundedTasks boundedTasks,
        EntityState entityState,
        Positioning positioning,
        Debug debug,
        String overlayDirectory,
        NearbyProductLimits nearbyProducts
) {

    private static final Logger LOGGER = LogManager.getLogger();

    // 默认值：20 tick 检查间隔、退避上限 5 秒、每 tick 最多 512 次检查
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 20;
    public static final int DEFAULT_BACKOFF_MAX_TICKS = 100;
    public static final int DEFAULT_MAX_CHECKS_PER_TICK = 512;
    public static final String DEFAULT_OVERLAY_DIRECTORY = Constants.MOD_ID;
    public static final int DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS = 6000;
    public static final boolean DEFAULT_DEBUG_LOGGING = false;

    // 公共调度预算默认值：10 ms 软预算、512 全局工作量、效果类 64、平滑窗口 20 tick、每 lane 每 tick 最多搬运 64
    public static final int DEFAULT_SERVER_BUDGET_US = 10_000;
    @SuppressWarnings("unused")
    public static final int DEFAULT_MAX_WORK_UNITS_PER_TICK = 512;
    public static final int DEFAULT_EFFECTS_WORK_UNITS_PER_TICK = 64;
    public static final int DEFAULT_CHECK_SPREAD_WINDOW_TICKS = 20;
    public static final int DEFAULT_DISPATCH_BATCH_SIZE = 64;

    // 实体状态默认值：新产物保护 2 秒、转化冷却 5 秒；0 表示不授予（允许显式关闭）
    public static final int DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS = 2;
    public static final int DEFAULT_CONVERSION_COOLDOWN_SECONDS = 5;

    // 位置搜索默认值：每刻最多检查 16 个候选位置（安全生成与返还位置搜索共用）
    public static final int DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK = 16;

    // 调试场景准备参数：每 tick 最多准备 128 个源、准备软预算 2000us（2000us = 2_000_000 纳秒）
    public static final int DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE = 128;
    public static final int DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US = 2000;

    // 单次放置半径上限的默认值：放置开销随半径立方增长，默认只放行 1～8
    public static final int DEFAULT_MAX_PLACE_BLOCK_RADIUS = 8;

    // 回扫每步实体上限：与 position_search_checks_per_tick 同量级，万级物品约数十秒扫完，单 tick 仍受共享预算约束
    public static final int DEFAULT_RESCAN_ENTITIES_PER_STEP = 16;

    // 清理任务每步取消上限：取消回调只同步落账、不做世界写入，单步成本很小；默认每步一个可让单 tick 尖峰最小，
    // 大维度卸载时的取消分散到多个 tick，代价只是取消完成稍晚（取消原因与计数仍逐条可观测）
    public static final int DEFAULT_CANCELS_PER_STEP = 1;

    // 结算准备每步组数的默认值：固定输入按组摊到多步，8 组与每 tick 工作量上限 512 同量级，
    // 大堆叠不会在一步里把整批固定输入一次做完
    public static final int DEFAULT_PREPARE_GROUPS_PER_STEP = 8;

    // 结算恢复重试上限默认值：运行期持续失败时最多重试 3 次，超过后保留账本等下次启动全量扫描
    public static final int DEFAULT_SETTLEMENT_RECOVERY_MAX_ATTEMPTS = 3;

    /**
     * 公共调度预算与检查节奏；Java 内分组，JSON 字段仍平铺在根对象。
     * @param checkIntervalTicks 检查间隔刻数
     * @param backoffMaxTicks 退避上限刻数
     * @param maxChecksPerTick 每 tick 检查上限，也是工作量键缺省时的回退值
     * @param serverBudgetUs 共享时间软预算，单位微秒
     * @param maxWorkUnitsPerTick 显式工作量上限；空值回退到 maxChecksPerTick
     * @param effectsWorkUnitsPerTick 效果类工作量上限
     * @param checkSpreadWindowTicks 检查平滑窗口刻数
     * @param dispatchBatchSize 派发搬运批量上限
     */
    public record Scheduling(
            int checkIntervalTicks,
            int backoffMaxTicks,
            int maxChecksPerTick,
            int serverBudgetUs,
            Optional<Integer> maxWorkUnitsPerTick,
            int effectsWorkUnitsPerTick,
            int checkSpreadWindowTicks,
            int dispatchBatchSize
    ) {
        /** 本组缺省配置。 */
        public static final Scheduling DEFAULT = new Scheduling(
                DEFAULT_CHECK_INTERVAL_TICKS,
                DEFAULT_BACKOFF_MAX_TICKS,
                DEFAULT_MAX_CHECKS_PER_TICK,
                DEFAULT_SERVER_BUDGET_US,
                Optional.empty(),
                DEFAULT_EFFECTS_WORK_UNITS_PER_TICK,
                DEFAULT_CHECK_SPREAD_WINDOW_TICKS,
                DEFAULT_DISPATCH_BATCH_SIZE
        );

        private static final MapCodec<Scheduling> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 1200).optionalFieldOf("check_interval_ticks", DEFAULT_CHECK_INTERVAL_TICKS)
                        .forGetter(Scheduling::checkIntervalTicks),
                Codec.intRange(1, 72000).optionalFieldOf("backoff_max_ticks", DEFAULT_BACKOFF_MAX_TICKS)
                        .forGetter(Scheduling::backoffMaxTicks),
                Codec.intRange(1, 100000).optionalFieldOf("max_checks_per_tick", DEFAULT_MAX_CHECKS_PER_TICK)
                        .forGetter(Scheduling::maxChecksPerTick),
                Codec.intRange(100, 100000).optionalFieldOf("server_budget_us", DEFAULT_SERVER_BUDGET_US)
                        .forGetter(Scheduling::serverBudgetUs),
                Codec.intRange(1, 100000).optionalFieldOf("max_work_units_per_tick")
                        .forGetter(Scheduling::maxWorkUnitsPerTick),
                Codec.intRange(1, 100000).optionalFieldOf("effects_work_units_per_tick", DEFAULT_EFFECTS_WORK_UNITS_PER_TICK)
                        .forGetter(Scheduling::effectsWorkUnitsPerTick),
                Codec.intRange(1, 1200).optionalFieldOf("check_spread_window_ticks", DEFAULT_CHECK_SPREAD_WINDOW_TICKS)
                        .forGetter(Scheduling::checkSpreadWindowTicks),
                Codec.intRange(1, 100000).optionalFieldOf("dispatch_batch_size", DEFAULT_DISPATCH_BATCH_SIZE)
                        .forGetter(Scheduling::dispatchBatchSize)
        ).apply(instance, Scheduling::new));
    }

    /**
     * 有界后台任务的单步上限与恢复次数；Java 内分组，JSON 字段仍平铺在根对象。
     * @param rescanEntitiesPerStep 回扫每步实体上限
     * @param cancelsPerStep 清理每步取消上限
     * @param prepareGroupsPerStep 结算准备每步组数
     * @param settlementRecoveryMaxAttempts 恢复交付重试上限
     */
    public record BoundedTasks(
            int rescanEntitiesPerStep,
            int cancelsPerStep,
            int prepareGroupsPerStep,
            int settlementRecoveryMaxAttempts
    ) {
        /** 本组缺省配置。 */
        public static final BoundedTasks DEFAULT = new BoundedTasks(
                DEFAULT_RESCAN_ENTITIES_PER_STEP,
                DEFAULT_CANCELS_PER_STEP,
                DEFAULT_PREPARE_GROUPS_PER_STEP,
                DEFAULT_SETTLEMENT_RECOVERY_MAX_ATTEMPTS
        );

        private static final MapCodec<BoundedTasks> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 4096).optionalFieldOf("rescan_entities_per_step", DEFAULT_RESCAN_ENTITIES_PER_STEP)
                        .forGetter(BoundedTasks::rescanEntitiesPerStep),
                Codec.intRange(1, 4096).optionalFieldOf("cancels_per_step", DEFAULT_CANCELS_PER_STEP)
                        .forGetter(BoundedTasks::cancelsPerStep),
                Codec.intRange(1, 4096).optionalFieldOf("prepare_groups_per_step", DEFAULT_PREPARE_GROUPS_PER_STEP)
                        .forGetter(BoundedTasks::prepareGroupsPerStep),
                Codec.intRange(1, 100).optionalFieldOf("settlement_recovery_max_attempts", DEFAULT_SETTLEMENT_RECOVERY_MAX_ATTEMPTS)
                        .forGetter(BoundedTasks::settlementRecoveryMaxAttempts)
        ).apply(instance, BoundedTasks::new));
    }

    /**
     * 实体生命周期与新产物状态时长；Java 内分组，JSON 字段仍平铺在根对象。
     * @param fabricLifespanFallbackTicks Fabric 寿命回退刻数
     * @param newProductProtectionSeconds 新产物保护秒数，0 表示关闭
     * @param conversionCooldownSeconds 转化冷却秒数，0 表示关闭
     */
    public record EntityState(
            int fabricLifespanFallbackTicks,
            int newProductProtectionSeconds,
            int conversionCooldownSeconds
    ) {
        /** 本组缺省配置。 */
        public static final EntityState DEFAULT = new EntityState(
                DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS,
                DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS,
                DEFAULT_CONVERSION_COOLDOWN_SECONDS
        );

        private static final MapCodec<EntityState> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 72000).optionalFieldOf("fabric_lifespan_fallback_ticks", DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS)
                        .forGetter(EntityState::fabricLifespanFallbackTicks),
                Codec.intRange(0, 3600).optionalFieldOf("new_product_protection_seconds", DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS)
                        .forGetter(EntityState::newProductProtectionSeconds),
                Codec.intRange(0, 3600).optionalFieldOf("conversion_cooldown_seconds", DEFAULT_CONVERSION_COOLDOWN_SECONDS)
                        .forGetter(EntityState::conversionCooldownSeconds)
        ).apply(instance, EntityState::new));
    }

    /**
     * 位置搜索与方块放置范围；Java 内分组，JSON 字段仍平铺在根对象。
     * @param positionSearchChecksPerTick 每 tick 位置候选检查上限
     * @param maxPlaceBlockRadius 规则允许的最大放置半径
     */
    public record Positioning(
            int positionSearchChecksPerTick,
            int maxPlaceBlockRadius
    ) {
        /** 本组缺省配置。 */
        public static final Positioning DEFAULT = new Positioning(
                DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK,
                DEFAULT_MAX_PLACE_BLOCK_RADIUS
        );

        private static final MapCodec<Positioning> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 4096).optionalFieldOf("position_search_checks_per_tick", DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK)
                        .forGetter(Positioning::positionSearchChecksPerTick),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_place_block_radius", DEFAULT_MAX_PLACE_BLOCK_RADIUS)
                        .forGetter(Positioning::maxPlaceBlockRadius)
        ).apply(instance, Positioning::new));
    }

    /**
     * 日志与调试场景准备预算；Java 内分组，JSON 字段仍平铺在根对象。
     * @param debugLogging 调试日志开关
     * @param debugScenarioPrepareBatchSize 每 tick 准备源数量上限
     * @param debugScenarioPrepareBudgetUs 准备软预算，单位微秒
     */
    public record Debug(
            boolean debugLogging,
            int debugScenarioPrepareBatchSize,
            int debugScenarioPrepareBudgetUs
    ) {
        /** 本组缺省配置。 */
        public static final Debug DEFAULT = new Debug(
                DEFAULT_DEBUG_LOGGING,
                DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE,
                DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US
        );

        private static final MapCodec<Debug> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("debug_logging", DEFAULT_DEBUG_LOGGING)
                        .forGetter(Debug::debugLogging),
                Codec.intRange(1, 100000).optionalFieldOf("debug_scenario_prepare_batch_size", DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE)
                        .forGetter(Debug::debugScenarioPrepareBatchSize),
                Codec.intRange(1, 100000).optionalFieldOf("debug_scenario_prepare_budget_us", DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US)
                        .forGetter(Debug::debugScenarioPrepareBudgetUs)
        ).apply(instance, Debug::new));
    }

    /** 缺失字段取对应组默认值；工作量上限未配置时保留空值以执行检查上限回退。 */
    public static final ServerConfig DEFAULT = new ServerConfig(
            Scheduling.DEFAULT, BoundedTasks.DEFAULT, EntityState.DEFAULT, Positioning.DEFAULT, Debug.DEFAULT,
            DEFAULT_OVERLAY_DIRECTORY, NearbyProductLimits.DEFAULT);

    /** 五组 MapCodec 直接合并键，只有 nearby_products 保留独立嵌套对象。 */
    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Scheduling.MAP_CODEC.forGetter(ServerConfig::scheduling),
            BoundedTasks.MAP_CODEC.forGetter(ServerConfig::boundedTasks),
            EntityState.MAP_CODEC.forGetter(ServerConfig::entityState),
            Positioning.MAP_CODEC.forGetter(ServerConfig::positioning),
            Debug.MAP_CODEC.forGetter(ServerConfig::debug),
            Codec.STRING.optionalFieldOf("overlay_directory", DEFAULT_OVERLAY_DIRECTORY)
                    .forGetter(ServerConfig::overlayDirectory),
            NearbyProductLimits.CODEC.optionalFieldOf("nearby_products", NearbyProductLimits.DEFAULT)
                    .forGetter(ServerConfig::nearbyProducts)
    ).apply(instance, ServerConfig::new));

    /** 检查间隔刻数。 */
    public int checkIntervalTicks() {
        return scheduling.checkIntervalTicks();
    }

    /** 退避上限刻数。 */
    public int backoffMaxTicks() {
        return scheduling.backoffMaxTicks();
    }

    /** 每 tick 检查上限，也是工作量键缺省时的回退值。 */
    public int maxChecksPerTick() {
        return scheduling.maxChecksPerTick();
    }

    /** 共享时间软预算，单位微秒。 */
    public int serverBudgetUs() {
        return scheduling.serverBudgetUs();
    }

    /** 显式工作量上限；空值回退到 maxChecksPerTick。 */
    public Optional<Integer> maxWorkUnitsPerTick() {
        return scheduling.maxWorkUnitsPerTick();
    }

    /** 效果类工作量上限。 */
    public int effectsWorkUnitsPerTick() {
        return scheduling.effectsWorkUnitsPerTick();
    }

    /** 检查平滑窗口刻数。 */
    public int checkSpreadWindowTicks() {
        return scheduling.checkSpreadWindowTicks();
    }

    /** 派发搬运批量上限。 */
    public int dispatchBatchSize() {
        return scheduling.dispatchBatchSize();
    }

    /** 回扫每步实体上限。 */
    public int rescanEntitiesPerStep() {
        return boundedTasks.rescanEntitiesPerStep();
    }

    /** 清理每步取消上限。 */
    public int cancelsPerStep() {
        return boundedTasks.cancelsPerStep();
    }

    /** 结算准备每步组数。 */
    public int prepareGroupsPerStep() {
        return boundedTasks.prepareGroupsPerStep();
    }

    /** 恢复交付重试上限。 */
    public int settlementRecoveryMaxAttempts() {
        return boundedTasks.settlementRecoveryMaxAttempts();
    }

    /** Fabric 寿命回退刻数。 */
    public int fabricLifespanFallbackTicks() {
        return entityState.fabricLifespanFallbackTicks();
    }

    /** 新产物保护秒数，0 表示关闭。 */
    public int newProductProtectionSeconds() {
        return entityState.newProductProtectionSeconds();
    }

    /** 转化冷却秒数，0 表示关闭。 */
    public int conversionCooldownSeconds() {
        return entityState.conversionCooldownSeconds();
    }

    /** 每 tick 位置候选检查上限。 */
    public int positionSearchChecksPerTick() {
        return positioning.positionSearchChecksPerTick();
    }

    /** 规则允许的最大放置半径。 */
    public int maxPlaceBlockRadius() {
        return positioning.maxPlaceBlockRadius();
    }

    /** 调试日志开关。 */
    public boolean debugLogging() {
        return debug.debugLogging();
    }

    /** 每 tick 准备源数量上限。 */
    public int debugScenarioPrepareBatchSize() {
        return debug.debugScenarioPrepareBatchSize();
    }

    /** 准备软预算，单位微秒。 */
    public int debugScenarioPrepareBudgetUs() {
        return debug.debugScenarioPrepareBudgetUs();
    }

    /**
     * 由本配置换算出的运行期上限：规则加载与保存校验据此拒载超出上限的规则。
     *
     * @return 运行期上限
     */
    public RuleRuntimeLimits runtimeLimits() {
        return new RuleRuntimeLimits(maxPlaceBlockRadius());
    }

    /** 未显式配置工作量上限时，回退到 max_checks_per_tick。 */
    public int effectiveMaxWorkUnitsPerTick() {
        return maxWorkUnitsPerTick().orElse(maxChecksPerTick());
    }

    /** 新产物临时保护刻数，20 刻/秒，允许 0。 */
    public int newProductProtectionTicks() {
        return Math.max(0, newProductProtectionSeconds()) * 20;
    }

    /** 转化冷却刻数，20 刻/秒，允许 0。 */
    public int conversionCooldownTicks() {
        return Math.max(0, conversionCooldownSeconds()) * 20;
    }

    // 从文件读取；文件不存在时写出默认配置并返回默认值，解析失败时记录问题并返回默认值
    public static ServerConfig loadOrCreate(Path file, IssueCollector issues) {
        if (file == null) {
            return DEFAULT;
        }
        if (!Files.isRegularFile(file)) {
            // 缺失即落盘，方便用户直接编辑；失败只告警，不影响服务端启动
            try {
                DEFAULT.save(file);
                LOGGER.info("已生成默认模组配置：{}", file);
            } catch (IOException e) {
                LOGGER.warn("写出默认模组配置失败：{}", file, e);
            }
            return DEFAULT;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            RuleDecodeBudget.ParseResult document = RuleDecodeBudget.parse(text);
            if (!document.ok()) {
                // 嵌套超深与语法非法同样按解析失败上报，与下方 CODEC 分支共用同一前缀
                issues.error("server.json 解析失败: " + document.failureReason(), file.toString(), null);
                return DEFAULT;
            }
            JsonElement json = document.element();
            DataResult<ServerConfig> result = CODEC.parse(JsonOps.INSTANCE, json);
            Optional<ServerConfig> parsed = result.resultOrPartial(message ->
                    issues.error("server.json 解析失败: " + message, file.toString(), null));
            return parsed.orElse(DEFAULT);
        } catch (IOException | RuntimeException e) {
            issues.error("server.json 读取失败: " + e, file.toString(), null);
            return DEFAULT;
        }
    }

    // 把当前配置写回文件（缺失目录会自动创建）
    public void save(Path file) throws IOException {
        DataResult<JsonElement> encoded = CODEC.encodeStart(JsonOps.INSTANCE, this);
        JsonObject json = encoded.result().orElseThrow().getAsJsonObject();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
    }

    /** 按检查间隔指数退避，封顶 backoffMaxTicks；失败次数从 0 开始。 */
    public int backoffTicks(int failureCount) {
        int base = Math.max(1, checkIntervalTicks());
        // 指数退避并以 backoffMaxTicks 封顶：间隔较小时也会收敛到上限
        int shift = Math.clamp(failureCount - 1, 0, 20);
        long ticks = (long) base << shift;
        return (int) Math.min(ticks, backoffMaxTicks());
    }
}
