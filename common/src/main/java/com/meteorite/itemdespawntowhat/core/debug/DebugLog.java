package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 开发控制台的稳定单行日志；场景编号关联参数、真实过程、预期与实际结果。 */
final class DebugLog {
    private static final Logger LOGGER = LogManager.getLogger("IDTW.Debug");

    // 工具类不创建实例。
    private DebugLog() {}

    // 使用 INFO，IDEA 默认控制台即可看到，不依赖服务端配置的 debug_logging。
    static void write(String run, String scene, String event, JsonObject data) {
        LOGGER.info("[IDTW_DEBUG] run={} scene={} event={} {}", run, scene, event, data);
    }

    // 启动参数只记录一次，不扫描磁盘规则、不读取历史日志。
    static void ready(MinecraftServer server, RuleCommandContext context) {
        JsonObject data = config(context.serverConfig());
        data.addProperty("development", true);
        data.addProperty("platform", Services.PLATFORM.getPlatformName());
        data.addProperty("minecraft_version", server.getServerVersion());
        data.addProperty("java_version", System.getProperty("java.version"));
        data.addProperty("max_heap_bytes", Runtime.getRuntime().maxMemory());
        var runtime = context.runtime();
        data.addProperty("active_rules", runtime == null ? 0 : runtime.index().ordered().size());
        write("server", "environment", "READY", data);
    }

    // 显式保留默认参数，避免 Codec 省略默认字段造成校对歧义。
    static JsonObject config(ServerConfig config) {
        JsonObject data = new JsonObject();
        if (config == null) { return data; }
        data.addProperty("check_interval_ticks", config.checkIntervalTicks());
        data.addProperty("backoff_max_ticks", config.backoffMaxTicks());
        data.addProperty("max_checks_per_tick", config.maxChecksPerTick());
        data.addProperty("overlay_directory", config.overlayDirectory());
        data.addProperty("fabric_lifespan_fallback_ticks", config.fabricLifespanFallbackTicks());
        data.addProperty("debug_logging_on_disk_setting", config.debugLogging());
        data.addProperty("queue_soft_budget_us", com.meteorite.itemdespawntowhat.core.runtime.TickScheduler.SOFT_BUDGET_NANOS / 1000);
        return data;
    }

    // 后端事件记录当时的实体参数，不用事后重新求值冒充实际结果。
    static JsonObject item(ItemEntity item) {
        JsonObject data = new JsonObject();
        data.addProperty("entity", item.getUUID().toString());
        data.addProperty("age_ticks", item.getAge());
        data.addProperty("count", item.getItem().getCount());
        data.addProperty("world_tick", item.level().getGameTime());
        data.addProperty("position", item.position().toString());
        return data;
    }
}
