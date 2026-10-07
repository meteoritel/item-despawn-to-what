package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
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
        int checkIntervalTicks,
        int backoffMaxTicks,
        int maxChecksPerTick,
        String overlayDirectory,
        int fabricLifespanFallbackTicks,
        boolean debugLogging,
        int serverBudgetUs,
        Optional<Integer> maxWorkUnitsPerTick,
        int effectsWorkUnitsPerTick,
        int checkSpreadWindowTicks,
        int dispatchBatchSize,
        int newProductProtectionSeconds,
        int conversionCooldownSeconds,
        int positionSearchChecksPerTick,
        int debugScenarioPrepareBatchSize,
        int debugScenarioPrepareBudgetUs
,
        NearbyProductLimits nearbyProducts
) {

    private static final Logger LOGGER = LogManager.getLogger();

    // 默认值：20 tick 检查间隔、退避上限 5 秒、每 tick 最多 512 次检查
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 20;
    public static final int DEFAULT_BACKOFF_MAX_TICKS = 100;
    public static final int DEFAULT_MAX_CHECKS_PER_TICK = 512;
    public static final String DEFAULT_OVERLAY_DIRECTORY = "itemdespawntowhat";
    public static final int DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS = 6000;
    public static final boolean DEFAULT_DEBUG_LOGGING = false;

    // 公共调度预算默认值：2 ms 软预算、512 全局工作量、效果类 64、平滑窗口 20 tick、每 lane 每 tick 最多搬运 64
    public static final int DEFAULT_SERVER_BUDGET_US = 2000;
    @SuppressWarnings("unused")
    public static final int DEFAULT_MAX_WORK_UNITS_PER_TICK = 512;
    public static final int DEFAULT_EFFECTS_WORK_UNITS_PER_TICK = 64;
    public static final int DEFAULT_CHECK_SPREAD_WINDOW_TICKS = 20;
    public static final int DEFAULT_DISPATCH_BATCH_SIZE = 64;

    // 实体状态默认值：新产物保护 2 秒、转化冷却 5 秒；0 表示不授予（允许显式关闭）
    public static final int DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS = 2;
    public static final int DEFAULT_CONVERSION_COOLDOWN_SECONDS = 5;

    // 位置搜索默认值：每刻最多检查 16 个候选位置（阶段 5 安全生成与返还位置搜索共用）
    public static final int DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK = 16;

    // 阶段7 调试场景准备参数：每 tick 最多准备 128 个源、准备软预算 2000us（与原硬编码 128 / 2_000_000L 逐位等价）
    public static final int DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE = 128;
    public static final int DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US = 2000;

    // 默认配置，也是缺失字段时的兜底；max_work_units_per_tick 缺省为空表示回退到 max_checks_per_tick
    public static final ServerConfig DEFAULT = new ServerConfig(
            DEFAULT_CHECK_INTERVAL_TICKS,
            DEFAULT_BACKOFF_MAX_TICKS,
            DEFAULT_MAX_CHECKS_PER_TICK,
            DEFAULT_OVERLAY_DIRECTORY,
            DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS,
            DEFAULT_DEBUG_LOGGING,
            DEFAULT_SERVER_BUDGET_US,
            Optional.empty(),
            DEFAULT_EFFECTS_WORK_UNITS_PER_TICK,
            DEFAULT_CHECK_SPREAD_WINDOW_TICKS,
            DEFAULT_DISPATCH_BATCH_SIZE,
            DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS,
            DEFAULT_CONVERSION_COOLDOWN_SECONDS,
            DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK,
            DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE,
            DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US
    );

    private static final MapCodec<ServerConfig> BASE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, 1200).optionalFieldOf("check_interval_ticks", DEFAULT_CHECK_INTERVAL_TICKS)
                    .forGetter(ServerConfig::checkIntervalTicks),
            Codec.intRange(1, 72000).optionalFieldOf("backoff_max_ticks", DEFAULT_BACKOFF_MAX_TICKS)
                    .forGetter(ServerConfig::backoffMaxTicks),
            Codec.intRange(1, 100000).optionalFieldOf("max_checks_per_tick", DEFAULT_MAX_CHECKS_PER_TICK)
                    .forGetter(ServerConfig::maxChecksPerTick),
            Codec.STRING.optionalFieldOf("overlay_directory", DEFAULT_OVERLAY_DIRECTORY)
                    .forGetter(ServerConfig::overlayDirectory),
            Codec.intRange(1, 72000).optionalFieldOf("fabric_lifespan_fallback_ticks", DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS)
                    .forGetter(ServerConfig::fabricLifespanFallbackTicks),
            Codec.BOOL.optionalFieldOf("debug_logging", DEFAULT_DEBUG_LOGGING)
                    .forGetter(ServerConfig::debugLogging),
            Codec.intRange(100, 100000).optionalFieldOf("server_budget_us", DEFAULT_SERVER_BUDGET_US)
                    .forGetter(ServerConfig::serverBudgetUs),
            // 可选键：缺失时回退到 max_checks_per_tick，避免旧配置被无条件压低预算
            Codec.intRange(1, 100000).optionalFieldOf("max_work_units_per_tick")
                    .forGetter(ServerConfig::maxWorkUnitsPerTick),
            Codec.intRange(1, 100000).optionalFieldOf("effects_work_units_per_tick", DEFAULT_EFFECTS_WORK_UNITS_PER_TICK)
                    .forGetter(ServerConfig::effectsWorkUnitsPerTick),
            Codec.intRange(1, 1200).optionalFieldOf("check_spread_window_ticks", DEFAULT_CHECK_SPREAD_WINDOW_TICKS)
                    .forGetter(ServerConfig::checkSpreadWindowTicks),
            Codec.intRange(1, 100000).optionalFieldOf("dispatch_batch_size", DEFAULT_DISPATCH_BATCH_SIZE)
                    .forGetter(ServerConfig::dispatchBatchSize),
            // 实体状态：允许 0 表示不授予保护/冷却，所以下限为 0（其余键下限均为 1）
            Codec.intRange(0, 3600).optionalFieldOf("new_product_protection_seconds", DEFAULT_NEW_PRODUCT_PROTECTION_SECONDS)
                    .forGetter(ServerConfig::newProductProtectionSeconds),
            Codec.intRange(0, 3600).optionalFieldOf("conversion_cooldown_seconds", DEFAULT_CONVERSION_COOLDOWN_SECONDS)
                    .forGetter(ServerConfig::conversionCooldownSeconds),
            // 位置搜索每刻候选检查上限：0 会让搜索停摆，故下限为 1
            Codec.intRange(1, 4096).optionalFieldOf("position_search_checks_per_tick", DEFAULT_POSITION_SEARCH_CHECKS_PER_TICK)
                    .forGetter(ServerConfig::positionSearchChecksPerTick),
            // 阶段7：只影响 debug 场景的准备阶段（每 tick 源数量上限、准备软预算），不参与 server_budget_us
            Codec.intRange(1, 100000).optionalFieldOf("debug_scenario_prepare_batch_size", DEFAULT_DEBUG_SCENARIO_PREPARE_BATCH_SIZE)
                    .forGetter(ServerConfig::debugScenarioPrepareBatchSize),
            // 单位微秒：2000us 即原硬编码的 2_000_000L 纳秒
            Codec.intRange(1, 100000).optionalFieldOf("debug_scenario_prepare_budget_us", DEFAULT_DEBUG_SCENARIO_PREPARE_BUDGET_US)
                    .forGetter(ServerConfig::debugScenarioPrepareBudgetUs)
    ).apply(instance, ServerConfig::new));

    // 旧调用点沿用原参数列表，邻近阈值采用服务器统一默认。
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ServerConfig(int checkIntervalTicks, int backoffMaxTicks, int maxChecksPerTick, String overlayDirectory, int fabricLifespanFallbackTicks, boolean debugLogging, int serverBudgetUs, Optional<Integer> maxWorkUnitsPerTick, int effectsWorkUnitsPerTick, int checkSpreadWindowTicks, int dispatchBatchSize, int newProductProtectionSeconds, int conversionCooldownSeconds, int positionSearchChecksPerTick, int debugScenarioPrepareBatchSize, int debugScenarioPrepareBudgetUs) {
        this(checkIntervalTicks, backoffMaxTicks, maxChecksPerTick, overlayDirectory, fabricLifespanFallbackTicks, debugLogging, serverBudgetUs, maxWorkUnitsPerTick, effectsWorkUnitsPerTick, checkSpreadWindowTicks, dispatchBatchSize, newProductProtectionSeconds, conversionCooldownSeconds, positionSearchChecksPerTick, debugScenarioPrepareBatchSize, debugScenarioPrepareBudgetUs, NearbyProductLimits.DEFAULT);
    }

    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BASE_CODEC.forGetter(value -> value),
            NearbyProductLimits.CODEC.optionalFieldOf("nearby_products", NearbyProductLimits.DEFAULT)
                    .forGetter(ServerConfig::nearbyProducts)
    ).apply(instance, (base, limits) -> new ServerConfig(base.checkIntervalTicks(), base.backoffMaxTicks(), base.maxChecksPerTick(), base.overlayDirectory(), base.fabricLifespanFallbackTicks(), base.debugLogging(), base.serverBudgetUs(), base.maxWorkUnitsPerTick(), base.effectsWorkUnitsPerTick(), base.checkSpreadWindowTicks(), base.dispatchBatchSize(), base.newProductProtectionSeconds(), base.conversionCooldownSeconds(), base.positionSearchChecksPerTick(), base.debugScenarioPrepareBatchSize(), base.debugScenarioPrepareBudgetUs(), limits)));

    // 全局工作量上限：未显式配置时回退到旧的 max_checks_per_tick，保证旧配置意图不丢失
    public int effectiveMaxWorkUnitsPerTick() {
        return maxWorkUnitsPerTick.orElse(maxChecksPerTick);
    }

    // 公共调度器的软预算纳秒值
    public long serverBudgetNanos() {
        return Math.max(1L, serverBudgetUs) * 1000L;
    }

    // 新产物临时保护刻数（20 刻/秒，允许 0）
    public int newProductProtectionTicks() {
        return Math.max(0, newProductProtectionSeconds) * 20;
    }

    // 转化冷却刻数（20 刻/秒，允许 0）
    public int conversionCooldownTicks() {
        return Math.max(0, conversionCooldownSeconds) * 20;
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
            JsonElement json = JsonParser.parseString(text);
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

    // 退避序列：1s → 2s → 4s → 封顶 backoffMaxTicks（失败次数从 0 开始）
    public int backoffTicks(int failureCount) {
        int base = Math.max(1, checkIntervalTicks);
        // 指数退避并以 backoffMaxTicks 封顶：间隔较小时也会收敛到上限
        int shift = Math.clamp(failureCount - 1, 0, 20);
        long ticks = (long) base << shift;
        return (int) Math.min(ticks, backoffMaxTicks);
    }
}
