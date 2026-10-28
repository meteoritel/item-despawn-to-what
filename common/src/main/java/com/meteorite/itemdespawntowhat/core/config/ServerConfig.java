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
import java.util.Objects;
import java.util.List;
import java.util.stream.Stream;

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
    public static final int DEFAULT_MAX_WORK_UNITS_PER_TICK = DEFAULT_MAX_CHECKS_PER_TICK;
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
    public static final int DEFAULT_MAX_EFFECT_OFFSET = 6;

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
        private static final ConfigField<Integer> CHECK_INTERVAL_TICKS = ConfigField.integer(
                "check_interval_ticks", DEFAULT_CHECK_INTERVAL_TICKS, 1, 1200);
        private static final ConfigField<Integer> BACKOFF_MAX_TICKS = ConfigField.integer(
                "backoff_max_ticks", DEFAULT_BACKOFF_MAX_TICKS, 1, 72000);
        private static final ConfigField<Integer> MAX_CHECKS_PER_TICK = ConfigField.integer(
                "max_checks_per_tick", DEFAULT_MAX_CHECKS_PER_TICK, 1, 100000);
        private static final ConfigField<Integer> SERVER_BUDGET_US = ConfigField.integer(
                "server_budget_us", DEFAULT_SERVER_BUDGET_US, 100, 100000);
        private static final ConfigField<Optional<Integer>> MAX_WORK_UNITS_PER_TICK = ConfigField.optionalInteger(
                "max_work_units_per_tick", 1, 100000, MAX_CHECKS_PER_TICK);
        private static final ConfigField<Integer> EFFECTS_WORK_UNITS_PER_TICK = ConfigField.integer(
                "effects_work_units_per_tick", DEFAULT_EFFECTS_WORK_UNITS_PER_TICK, 1, 100000);
        private static final ConfigField<Integer> CHECK_SPREAD_WINDOW_TICKS = ConfigField.integer(
                "check_spread_window_ticks", DEFAULT_CHECK_SPREAD_WINDOW_TICKS, 1, 1200);
        private static final ConfigField<Integer> DISPATCH_BATCH_SIZE = ConfigField.integer(
                "dispatch_batch_size", DEFAULT_DISPATCH_BATCH_SIZE, 1, 100000);
        private static final List<ConfigField<?>> FIELDS = List.of(
                CHECK_INTERVAL_TICKS, BACKOFF_MAX_TICKS, MAX_CHECKS_PER_TICK, SERVER_BUDGET_US, MAX_WORK_UNITS_PER_TICK, EFFECTS_WORK_UNITS_PER_TICK, CHECK_SPREAD_WINDOW_TICKS, DISPATCH_BATCH_SIZE);

        /** 本组缺省配置。 */
        public static final Scheduling DEFAULT = new Scheduling(
                CHECK_INTERVAL_TICKS.defaultValue(),
                BACKOFF_MAX_TICKS.defaultValue(),
                MAX_CHECKS_PER_TICK.defaultValue(),
                SERVER_BUDGET_US.defaultValue(),
                MAX_WORK_UNITS_PER_TICK.defaultValue(),
                EFFECTS_WORK_UNITS_PER_TICK.defaultValue(),
                CHECK_SPREAD_WINDOW_TICKS.defaultValue(),
                DISPATCH_BATCH_SIZE.defaultValue()
        );

        private static final MapCodec<Scheduling> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CHECK_INTERVAL_TICKS.codec()
                        .forGetter(Scheduling::checkIntervalTicks),
                BACKOFF_MAX_TICKS.codec()
                        .forGetter(Scheduling::backoffMaxTicks),
                MAX_CHECKS_PER_TICK.codec()
                        .forGetter(Scheduling::maxChecksPerTick),
                SERVER_BUDGET_US.codec()
                        .forGetter(Scheduling::serverBudgetUs),
                MAX_WORK_UNITS_PER_TICK.codec()
                        .forGetter(Scheduling::maxWorkUnitsPerTick),
                EFFECTS_WORK_UNITS_PER_TICK.codec()
                        .forGetter(Scheduling::effectsWorkUnitsPerTick),
                CHECK_SPREAD_WINDOW_TICKS.codec()
                        .forGetter(Scheduling::checkSpreadWindowTicks),
                DISPATCH_BATCH_SIZE.codec()
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
        private static final ConfigField<Integer> RESCAN_ENTITIES_PER_STEP = ConfigField.integer(
                "rescan_entities_per_step", DEFAULT_RESCAN_ENTITIES_PER_STEP, 1, 4096);
        private static final ConfigField<Integer> CANCELS_PER_STEP = ConfigField.integer(
                "cancels_per_step", DEFAULT_CANCELS_PER_STEP, 1, 4096);
        private static final ConfigField<Integer> PREPARE_GROUPS_PER_STEP = ConfigField.integer(
                "prepare_groups_per_step", DEFAULT_PREPARE_GROUPS_PER_STEP, 1, 4096);
        private static final ConfigField<Integer> SETTLEMENT_RECOVERY_MAX_ATTEMPTS = ConfigField.integer(
                "settlement_recovery_max_attempts", DEFAULT_SETTLEMENT_RECOVERY_MAX_ATTEMPTS, 1, 100);
        private static final List<ConfigField<?>> FIELDS = List.of(
                RESCAN_ENTITIES_PER_STEP, CANCELS_PER_STEP, PREPARE_GROUPS_PER_STEP, SETTLEMENT_RECOVERY_MAX_ATTEMPTS);

        /** 本组缺省配置。 */
        public static final BoundedTasks DEFAULT = new BoundedTasks(
                RESCAN_ENTITIES_PER_STEP.defaultValue(),
                CANCELS_PER_STEP.defaultValue(),
                PREPARE_GROUPS_PER_STEP.defaultValue(),
                SETTLEMENT_RECOVERY_MAX_ATTEMPTS.defaultValue()
        );

        private static final MapCodec<BoundedTasks> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                RESCAN_ENTITIES_PER_STEP.codec()
                        .forGetter(BoundedTasks::rescanEntitiesPerStep),
                CANCELS_PER_STEP.codec()
                        .forGetter(BoundedTasks::cancelsPerStep),
                PREPARE_GROUPS_PER_STEP.codec()
                        .forGetter(BoundedTasks::prepareGroupsPerStep),
                SETTLEMENT_RECOVERY_MAX_ATTEMPTS.codec()
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
        private static final ConfigField<Integer> FABRIC_LIFESPAN_FALLBACK_TICKS = ConfigField.integer(
                "fabric_lifespan_fallback_ticks", DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS, 1, 72000);
        private static final ConfigField<Integer> NEW_PRODUCT_PROTECTION_SECONDS = ConfigField.integer(
                "new_product_protection_seconds", DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS, 0, 3600);
        private static final ConfigField<Integer> CONVERSION_COOLDOWN_SECONDS = ConfigField.integer(
                "conversion_cooldown_seconds", DEFAULT_CONVERSION_COOLDOWN_SECONDS, 0, 3600);
        private static final List<ConfigField<?>> FIELDS = List.of(
                FABRIC_LIFESPAN_FALLBACK_TICKS, NEW_PRODUCT_PROTECTION_SECONDS, CONVERSION_COOLDOWN_SECONDS);

        /** 本组缺省配置。 */
        public static final EntityState DEFAULT = new EntityState(
                FABRIC_LIFESPAN_FALLBACK_TICKS.defaultValue(),
                NEW_PRODUCT_PROTECTION_SECONDS.defaultValue(),
                CONVERSION_COOLDOWN_SECONDS.defaultValue()
        );

        private static final MapCodec<EntityState> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                FABRIC_LIFESPAN_FALLBACK_TICKS.codec()
                        .forGetter(EntityState::fabricLifespanFallbackTicks),
                NEW_PRODUCT_PROTECTION_SECONDS.codec()
                        .forGetter(EntityState::newProductProtectionSeconds),
                CONVERSION_COOLDOWN_SECONDS.codec()
                        .forGetter(EntityState::conversionCooldownSeconds)
        ).apply(instance, EntityState::new));
    }

    /**
     * 位置搜索与方块放置范围；Java 内分组，JSON 字段仍平铺在根对象。
     * @param positionSearchChecksPerTick 每 tick 位置候选检查上限
     * @param maxPlaceBlockRadius 规则允许的最大放置半径
     * @param maxEffectOffsetX 行为 X 轴偏移绝对值上限，0 固定该轴
     * @param maxEffectOffsetY 行为 Y 轴偏移绝对值上限，0 固定该轴
     * @param maxEffectOffsetZ 行为 Z 轴偏移绝对值上限，0 固定该轴
     */
    public record Positioning(
            int positionSearchChecksPerTick,
            int maxPlaceBlockRadius,
            int maxEffectOffsetX,
            int maxEffectOffsetY,
            int maxEffectOffsetZ
    ) {
        private static final ConfigField<Integer> POSITION_SEARCH_CHECKS_PER_TICK = ConfigField.integer(
                "position_search_checks_per_tick", DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK, 1, 4096);
        private static final ConfigField<Integer> MAX_PLACE_BLOCK_RADIUS = ConfigField.integer(
                "max_place_block_radius", DEFAULT_MAX_PLACE_BLOCK_RADIUS, 1, Integer.MAX_VALUE);
        private static final ConfigField<Integer> MAX_EFFECT_OFFSET_X = ConfigField.integer(
                "max_effect_offset_x", DEFAULT_MAX_EFFECT_OFFSET, 0, Integer.MAX_VALUE);
        private static final ConfigField<Integer> MAX_EFFECT_OFFSET_Y = ConfigField.integer(
                "max_effect_offset_y", DEFAULT_MAX_EFFECT_OFFSET, 0, Integer.MAX_VALUE);
        private static final ConfigField<Integer> MAX_EFFECT_OFFSET_Z = ConfigField.integer(
                "max_effect_offset_z", DEFAULT_MAX_EFFECT_OFFSET, 0, Integer.MAX_VALUE);
        private static final List<ConfigField<?>> FIELDS = List.of(
                POSITION_SEARCH_CHECKS_PER_TICK, MAX_PLACE_BLOCK_RADIUS,
                MAX_EFFECT_OFFSET_X, MAX_EFFECT_OFFSET_Y, MAX_EFFECT_OFFSET_Z);

        /** 本组缺省配置。 */
        public static final Positioning DEFAULT = new Positioning(
                POSITION_SEARCH_CHECKS_PER_TICK.defaultValue(), MAX_PLACE_BLOCK_RADIUS.defaultValue(),
                MAX_EFFECT_OFFSET_X.defaultValue(), MAX_EFFECT_OFFSET_Y.defaultValue(), MAX_EFFECT_OFFSET_Z.defaultValue()
        );

        private static final MapCodec<Positioning> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                POSITION_SEARCH_CHECKS_PER_TICK.codec().forGetter(Positioning::positionSearchChecksPerTick),
                MAX_PLACE_BLOCK_RADIUS.codec().forGetter(Positioning::maxPlaceBlockRadius),
                MAX_EFFECT_OFFSET_X.codec().forGetter(Positioning::maxEffectOffsetX),
                MAX_EFFECT_OFFSET_Y.codec().forGetter(Positioning::maxEffectOffsetY),
                MAX_EFFECT_OFFSET_Z.codec().forGetter(Positioning::maxEffectOffsetZ)
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
        private static final ConfigField<Boolean> DEBUG_LOGGING = ConfigField.bool("debug_logging", DEFAULT_DEBUG_LOGGING);
        private static final ConfigField<Integer> DEBUG_SCENARIO_PREPARE_BATCH_SIZE = ConfigField.integer(
                "debug_scenario_prepare_batch_size", DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE, 1, 100000);
        private static final ConfigField<Integer> DEBUG_SCENARIO_PREPARE_BUDGET_US = ConfigField.integer(
                "debug_scenario_prepare_budget_us", DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US, 1, 100000);
        private static final List<ConfigField<?>> FIELDS = List.of(
                DEBUG_LOGGING, DEBUG_SCENARIO_PREPARE_BATCH_SIZE, DEBUG_SCENARIO_PREPARE_BUDGET_US);

        /** 本组缺省配置。 */
        public static final Debug DEFAULT = new Debug(
                DEBUG_LOGGING.defaultValue(),
                DEBUG_SCENARIO_PREPARE_BATCH_SIZE.defaultValue(),
                DEBUG_SCENARIO_PREPARE_BUDGET_US.defaultValue()
        );

        private static final MapCodec<Debug> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                DEBUG_LOGGING.codec()
                        .forGetter(Debug::debugLogging),
                DEBUG_SCENARIO_PREPARE_BATCH_SIZE.codec()
                        .forGetter(Debug::debugScenarioPrepareBatchSize),
                DEBUG_SCENARIO_PREPARE_BUDGET_US.codec()
                        .forGetter(Debug::debugScenarioPrepareBudgetUs)
        ).apply(instance, Debug::new));
    }

    private static final ConfigField<String> OVERLAY_DIRECTORY = ConfigField.string("overlay_directory", DEFAULT_OVERLAY_DIRECTORY);
    private static final ConfigField<NearbyProductLimits> NEARBY_PRODUCTS = ConfigField.object(
            "nearby_products", NearbyProductLimits.CODEC, NearbyProductLimits.DEFAULT, NearbyProductLimits.FIELDS);
    static final List<ConfigField<?>> FIELDS = Stream.of(Scheduling.FIELDS, BoundedTasks.FIELDS,
            EntityState.FIELDS, Positioning.FIELDS, Debug.FIELDS, List.of(OVERLAY_DIRECTORY, NEARBY_PRODUCTS))
            .flatMap(List::stream).toList();

    /** 缺失字段取对应组默认值；工作量上限未配置时保留空值以执行检查上限回退。 */
    public static final ServerConfig DEFAULT = new ServerConfig(
            Scheduling.DEFAULT, BoundedTasks.DEFAULT, EntityState.DEFAULT, Positioning.DEFAULT, Debug.DEFAULT,
            OVERLAY_DIRECTORY.defaultValue(), NEARBY_PRODUCTS.defaultValue());

    /** 五组 MapCodec 直接合并键，只有 nearby_products 保留独立嵌套对象。 */
    static final MapCodec<ServerConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Scheduling.MAP_CODEC.forGetter(ServerConfig::scheduling),
            BoundedTasks.MAP_CODEC.forGetter(ServerConfig::boundedTasks),
            EntityState.MAP_CODEC.forGetter(ServerConfig::entityState),
            Positioning.MAP_CODEC.forGetter(ServerConfig::positioning),
            Debug.MAP_CODEC.forGetter(ServerConfig::debug),
            OVERLAY_DIRECTORY.codec()
                    .forGetter(ServerConfig::overlayDirectory),
            NEARBY_PRODUCTS.codec()
                    .forGetter(ServerConfig::nearbyProducts)
    ).apply(instance, ServerConfig::new));

    /** 模组配置的读写入口，字段缺省和区间由同一声明构造。 */
    public static final Codec<ServerConfig> CODEC = MAP_CODEC.codec();

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

    /** 行为 X 轴偏移的绝对值上限，单位为格。 */
    public int maxEffectOffsetX() { return positioning.maxEffectOffsetX(); }

    /** 行为 Y 轴偏移的绝对值上限，单位为格。 */
    public int maxEffectOffsetY() { return positioning.maxEffectOffsetY(); }

    /** 行为 Z 轴偏移的绝对值上限，单位为格。 */
    public int maxEffectOffsetZ() { return positioning.maxEffectOffsetZ(); }

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
        return new RuleRuntimeLimits(maxPlaceBlockRadius(), maxEffectOffsetX(), maxEffectOffsetY(), maxEffectOffsetZ());
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

    /**
     * 读取模组运行配置；缺失时生成默认文件，未知键按完整路径告警，解析失败保留原文件并回退默认值。
     * @param file 配置文件路径；为空时直接返回默认配置
     * @param issues 接收字段告警和读取错误
     * @return 本次读取的配置或默认配置
     */
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
            JsonElement json = Objects.requireNonNull(document.element());
            ConfigField.warnUnknown(json, FIELDS, issues, file.toString(), "");
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
