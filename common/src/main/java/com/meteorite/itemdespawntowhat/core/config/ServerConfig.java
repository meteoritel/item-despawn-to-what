package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
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
        boolean debugLogging
) {

    private static final Logger LOGGER = LogManager.getLogger();

    // 默认值：20 tick 检查间隔、退避上限 5 秒、每 tick 最多 512 次检查
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 20;
    public static final int DEFAULT_BACKOFF_MAX_TICKS = 100;
    public static final int DEFAULT_MAX_CHECKS_PER_TICK = 512;
    public static final String DEFAULT_OVERLAY_DIRECTORY = "itemdespawntowhat";
    public static final int DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS = 6000;
    public static final boolean DEFAULT_DEBUG_LOGGING = false;

    // 默认配置，也是缺失字段时的兜底
    public static final ServerConfig DEFAULT = new ServerConfig(
            DEFAULT_CHECK_INTERVAL_TICKS,
            DEFAULT_BACKOFF_MAX_TICKS,
            DEFAULT_MAX_CHECKS_PER_TICK,
            DEFAULT_OVERLAY_DIRECTORY,
            DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS,
            DEFAULT_DEBUG_LOGGING
    );

    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
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
                    .forGetter(ServerConfig::debugLogging)
    ).apply(instance, ServerConfig::new));

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
        int shift = Math.min(Math.max(0, failureCount), 20);
        long ticks = (long) base << shift;
        return (int) Math.min(ticks, backoffMaxTicks);
    }
}
