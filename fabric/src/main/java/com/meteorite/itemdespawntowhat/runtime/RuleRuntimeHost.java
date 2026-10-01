package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadContext;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadingService;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 新链路（core/runtime）在 Fabric 端的引导与生命周期持有者。
 * 职责边界（对应规划书 3.7 / 3.12 与阶段③ 验收）：
 * - 引导：读 config/itemdespawntowhat/server.json → 建内置类型注册表 → 加载三层规则 → 建 ConversionRuntime → 回扫全部已加载维度；
 * - 事件：掉落物进入世界、维度 tick 结束、维度加载/卸载、数据包重载、服务端停止；
 * - 平台差异：Fabric 没有加载器提供的物品寿命 API，寿命使用 server.json 的兜底刻数。
 * 与旧链路（ConfigExtractorManager / ConversionTracker）完全并行：只读写 server.json 与 rules 覆盖层，
 * 不触碰旧链路的任何静态状态，便于两套实现对照测试。
 */
public final class RuleRuntimeHost {

    private static final Logger LOGGER = LogManager.getLogger();
    // 覆盖层缺省命名空间与包层判定 token 都取模组 id
    private static final String MOD_NAMESPACE = Constants.MOD_ID;
    // 模组级配置文件名（固定位于 config/itemdespawntowhat/ 下）
    private static final String SERVER_CONFIG_FILE = "server.json";

    // 当前内置类型注册表（数据包重载时复用，避免重复构表）
    private static volatile BuiltinTypeRegistries typeRegistries;
    // 覆盖层根目录（config/<overlay_directory>）
    private static volatile Path overlayRoot;
    // 新链路运行时；未引导或已停止时为 null
    private static volatile ConversionRuntime runtime;

    private RuleRuntimeHost() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 服务端就绪：读配置 → 建注册表 → 加载规则 → 建运行时 → 对全部已加载维度回扫
    // 必须等到 ServerStarted（全部维度已创建）才能回扫，否则会漏掉启动期已存在的掉落物
    public static void start(MinecraftServer server) {
        // 同一进程内可能先后启动多个服务端（单人世界切换存档）：先释放上一轮状态
        shutdown();

        IssueCollector configIssues = new IssueCollector();
        ServerConfig config = loadServerConfig(configIssues);
        logIssues(configIssues, "读取 server.json");

        BuiltinTypeRegistries registries = BuiltinTypeRegistries.create();
        Path overlay = Services.PLATFORM.getConfigDir().resolve(config.overlayDirectory());
        ConversionRuntime newRuntime = new ConversionRuntime(config, registries, fabricLifespan(config));

        RuleLoadResult<Rule> result = loadRules(
                context(server.getResourceManager(), server.registryAccess(), overlay, registries), "服务端启动");
        newRuntime.replaceRules(result.rules(), result.issues());
        logLoadResult("服务端启动", result);

        typeRegistries = registries;
        overlayRoot = overlay;
        runtime = newRuntime;

        int levelCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            newRuntime.rescan(level);
            levelCount++;
        }
        LOGGER.info("新链路运行时已就绪：覆盖层 {}，已回扫维度 {} 个", overlay, levelCount);
    }

    // 数据包重载：重建规则索引并回扫全部已加载维度，使已存在的掉落物立即按新规则重选（A3）
    // 调用点必须位于服务端线程（Fabric 的 END_DATA_PACK_RELOAD 已由 handleAsync(server) 保证）
    public static void reload(MinecraftServer server, ResourceManager resourceManager) {
        ConversionRuntime current = runtime;
        BuiltinTypeRegistries registries = typeRegistries;
        Path overlay = overlayRoot;
        if (current == null || registries == null || overlay == null) {
            // 启动前的首次数据包加载：那时维度尚未创建，统一交给 start 处理
            return;
        }
        try {
            RuleLoadResult<Rule> result = loadRules(
                    context(resourceManager, server.registryAccess(), overlay, registries), "数据包重载");
            current.replaceRules(result.rules(), result.issues());
            logLoadResult("数据包重载", result);
            for (ServerLevel level : server.getAllLevels()) {
                current.rescan(level);
            }
            LOGGER.info("数据包重载完成：规则索引已重建并完成回扫");
        } catch (RuntimeException e) {
            // 重载失败必须保留旧索引继续服务，不能让异常逃逸到事件回调
            LOGGER.error("数据包重载处理失败，保留上一版规则索引", e);
        }
    }

    // 服务端停止：释放全部维度的追踪状态与到期任务
    public static void shutdown() {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.shutdown();
        }
        runtime = null;
        typeRegistries = null;
        overlayRoot = null;
    }

    // 掉落物进入世界：仅当存在候选规则时才纳入追踪
    public static void onItemAdded(ServerLevel level, ItemEntity entity) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.onItemAdded(level, entity);
        }
    }

    // 维度 tick 结束：先执行到期任务，再按检查间隔做失效清理
    public static void tickLevel(ServerLevel level) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.onLevelTick(level);
        }
    }

    // 维度加载：对后加载的维度补一次回扫（启动期维度已由 start 统一回扫）
    public static void levelLoaded(ServerLevel level) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.rescan(level);
        }
    }

    // 维度卸载：释放该维度的追踪状态与到期任务，避免强引用维度对象（A4）
    public static void levelUnloaded(ServerLevel level) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.clear(level);
        }
    }

    // Fabric 端寿命提供者：使用 server.json 的兜底刻数，与 NeoForge 端语义对齐（规划书 3.12）
    private static LifespanProvider fabricLifespan(ServerConfig config) {
        return (level, entity) -> config.fabricLifespanFallbackTicks();
    }

    // 读取模组级配置；缺失即由 ServerConfig.loadOrCreate 落盘默认值（职责在 core，平台层不重复处理）
    private static ServerConfig loadServerConfig(IssueCollector issues) {
        return ServerConfig.loadOrCreate(serverConfigFile(), issues);
    }

    // 模组级配置文件固定路径：config/itemdespawntowhat/server.json
    // server.json 内的 overlay_directory 只控制规则覆盖层目录，不影响本文件自身位置，避免引导期自引用
    private static Path serverConfigFile() {
        return Services.PLATFORM.getConfigDir()
                .resolve(ServerConfig.DEFAULT_OVERLAY_DIRECTORY)
                .resolve(SERVER_CONFIG_FILE);
    }

    // 组装一次加载所需的全部外部依赖：数据包资源管理器 + 覆盖层 + 注册表 + 包层判定
    private static RuleLoadContext context(ResourceManager resourceManager,
                                           RegistryAccess registryAccess,
                                           Path overlay,
                                           BuiltinTypeRegistries registries) {
        return RuleLoadContext.full(resourceManager, overlay, MOD_NAMESPACE, registryAccess,
                registries.effectTypes(), registries.conditionTypes(),
                PackLayerResolver.byPackIdToken(MOD_NAMESPACE));
    }

    // 执行一次加载 + 语义校验；异常被转为 ERROR 级问题，避免坏配置拖垮服务端启动或重载
    private static RuleLoadResult<Rule> loadRules(RuleLoadContext context, String stage) {
        try {
            return RuleLoadingService.loadAndValidate(context);
        } catch (RuntimeException e) {
            LOGGER.error("{}：规则加载抛出异常，本次以空规则集继续", stage, e);
            IssueCollector issues = new IssueCollector();
            issues.error("规则加载抛出异常: " + e.getClass().getName(), null, null);
            return new RuleLoadResult<>(List.of(), issues);
        }
    }

    // 汇总一条加载结果：先打统计行，再逐条输出问题（错误与告警分级）
    private static void logLoadResult(String stage, RuleLoadResult<Rule> result) {
        LOGGER.info("{}：{}", stage, RuleLoadingService.summarize(result));
        logIssues(result.issues(), stage);
    }

    // 逐条输出问题；ERROR 记 error 级、WARN 记 warn 级，便于用户按级别过滤
    private static void logIssues(IssueCollector issues, String stage) {
        for (Issue issue : issues.issues()) {
            if (issue.severity() == IssueSeverity.ERROR) {
                LOGGER.error("{}问题：{}", stage, issue.format());
            } else {
                LOGGER.warn("{}问题：{}", stage, issue.format());
            }
        }
    }
}
