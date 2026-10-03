package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadContext;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadingService;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;

/**
 * 新链路（core/runtime）在 NeoForge 端的引导与生命周期持有者。
 * 职责边界（对应规划书 3.7 / 3.12 与阶段③ 验收）：
 * - 引导：读 config/itemdespawntowhat/server.json → 建内置类型注册表 → 加载三层规则 → 建 ConversionRuntime → 回扫全部已加载维度；
 * - 事件：掉落物进入世界、维度 tick 结束、维度加载/卸载、数据包重载、服务端停止；
 * - 平台差异：寿命取加载器提供的 ItemStack#getEntityLifespan，支持其它模组对掉落物寿命的修改。
 * 当前唯一转化链路；旧 JSON 仅供显式迁移命令读取。
 */
public final class RuleRuntimeHost {

    private static final Logger LOGGER = LogManager.getLogger();
    // 覆盖层缺省命名空间与包层判定 token 都取模组 id
    private static final String MOD_NAMESPACE = Constants.MOD_ID;
    // 模组级配置文件名（固定位于 config/itemdespawntowhat/ 下）
    private static final String SERVER_CONFIG_FILE = "server.json";

    // 当前服务端引用：数据包重载监听器需要用它枚举维度，服务端停止后置空
    private static volatile MinecraftServer currentServer;
    // 当前模组级配置（server.json）；未引导或已停止时为 null
    private static volatile ServerConfig serverConfig;
    // 当前内置类型注册表（数据包重载时复用，避免重复构表）
    private static volatile BuiltinTypeRegistries typeRegistries;
    // 覆盖层根目录（config/<overlay_directory>）
    private static volatile Path overlayRoot;
    // 新链路运行时；未引导或已停止时为 null
    private static volatile ConversionRuntime runtime;

    private RuleRuntimeHost() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 网络编辑层入口：以窄接口暴露运行时的覆盖层路径与重建能力（阶段④）
    public static RuleEditServerContext editContext() {
        return EDIT_CONTEXT;
    }

    // 命令层入口：以窄接口暴露运行时、模组级配置与编辑层统计（阶段⑤ /idtw 命令树）
    public static RuleCommandContext commandContext() {
        return COMMAND_CONTEXT;
    }

    // 命令层能力适配器：字段都是 volatile 静态状态，调用时实时读取
    private static final RuleCommandContext COMMAND_CONTEXT = new RuleCommandContext() {
        @Override
        public ConversionRuntime runtime() {
            return runtime;
        }

        @Override
        public ServerConfig serverConfig() {
            return serverConfig;
        }

        @Override
        public RuleEditServerContext editContext() {
            return EDIT_CONTEXT;
        }

        @Override
        public String overlayNamespace() {
            return MOD_NAMESPACE;
        }

        @Override
        public int overlayVersion() {
            EditSessionManager sessions = RuleEditServerHandler.sessionManager(EDIT_CONTEXT);
            return sessions == null ? -1 : sessions.version();
        }

        @Override
        public int activeSessionCount() {
            EditSessionManager sessions = RuleEditServerHandler.sessionManager(EDIT_CONTEXT);
            return sessions == null ? 0 : sessions.activeSessionCount();
        }

        @Override
        public RuleLoadResult<Rule> reloadRules(MinecraftServer server) {
            return reload(server, server.getResourceManager());
        }
    };

    // 运行时能力适配器：字段都是 volatile 静态状态，调用时实时读取
    private static final RuleEditServerContext EDIT_CONTEXT = new RuleEditServerContext() {
        @Override
        public Path overlayRoot() {
            return overlayRoot;
        }

        @Override
        public String overlayNamespace() {
            return MOD_NAMESPACE;
        }

        @Override
        public BuiltinTypeRegistries typeRegistries() {
            return typeRegistries;
        }

        @Override
        public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
            // 发包方式由平台层提供（Fabric: ServerPlayNetworking / NeoForge: PacketDistributor）
            Services.PLATFORM.sendToPlayer(player, payload);
        }

        @Override
        public RuleLoadResult<Rule> loadMerged(MinecraftServer server) {
            Path overlay = overlayRoot;
            BuiltinTypeRegistries registries = typeRegistries;
            if (overlay == null || registries == null) {
                return emptyResult();
            }
            return loadRules(context(server.getResourceManager(), server.registryAccess(), overlay, registries, server));
        }

        @Override
        public void rebuildAndRescan(MinecraftServer server) {
            // 与 Fabric 侧行为一致：重载失败必须抛异常，让保存流程回 SAVED_NOT_RELOADED（已写盘但未重载）
            if (applyReload(server.getResourceManager(), server.registryAccess()) == null) {
                throw new IllegalStateException("保存后的运行时重载失败");
            }
        }
    };

    // 运行时就绪前的空加载结果，保证网络层拿到非 null 对象
    private static RuleLoadResult<Rule> emptyResult() {
        IssueCollector issues = new IssueCollector();
        issues.warn("新链路运行时尚未就绪，本次按空规则集处理", null, null);
        return new RuleLoadResult<>(List.of(), issues);
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
        ConversionRuntime newRuntime = new ConversionRuntime(config, registries, neoForgeLifespan());

        RuleLoadResult<Rule> result = loadRules(
                context(server.getResourceManager(), server.registryAccess(), overlay, registries, server));
        newRuntime.replaceRules(result.rules(), result.issues());
        logLoadResult("服务端启动", result);

        currentServer = server;
        serverConfig = config;
        typeRegistries = registries;
        overlayRoot = overlay;
        runtime = newRuntime;
        // 实体状态持续时间取自本次配置：新产物保护与转化冷却各自可为 0（D18，0 表示不授予）
        DropStateStore.configure(config.newProductProtectionTicks(), config.conversionCooldownTicks());

        int levelCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            newRuntime.rescan(level);
            levelCount++;
        }
        LOGGER.info("新链路运行时已就绪：覆盖层 {}，已回扫维度 {} 个", overlay, levelCount);
    }

    // 命令层重载入口：按当前服务端资源管理器重建索引；未就绪或失败时返回 null 供命令层提示
    public static RuleLoadResult<Rule> reload(MinecraftServer server, ResourceManager resourceManager) {
        if (server == null) {
            return null;
        }
        return applyReload(resourceManager, server.registryAccess());
    }

    // 数据包重载（apply 阶段，服务端线程）：重建规则索引并回扫全部已加载维度，使已存在掉落物立即重选（A3）
    // 返回本次加载结果；运行时未就绪（启动前首次数据包加载）或重载失败时返回 null
    private static RuleLoadResult<Rule> applyReload(ResourceManager resourceManager, RegistryAccess registryAccess) {
        ConversionRuntime current = runtime;
        BuiltinTypeRegistries registries = typeRegistries;
        Path overlay = overlayRoot;
        MinecraftServer server = currentServer;
        if (current == null || registries == null || overlay == null || server == null) {
            // 启动前的首次数据包加载：那时维度尚未创建，统一交给 start 处理
            return null;
        }
        try {
            // 使用本次重载传入的资源管理器与注册表访问器，而不是 server 上尚未切换的旧实例
            RuleLoadResult<Rule> result = loadRules(
                    context(resourceManager, registryAccess, overlay, registries, server));
            current.replaceRules(result.rules(), result.issues());
            logLoadResult("数据包重载", result);
            for (ServerLevel level : server.getAllLevels()) {
                current.rescan(level);
            }
            java.util.Objects.requireNonNull(RuleEditServerHandler.sessionManager(EDIT_CONTEXT)).bumpVersion();
            LOGGER.info("数据包重载完成：规则索引已重建并完成回扫");
            return result;
        } catch (RuntimeException e) {
            // 重载失败必须保留旧索引继续服务，且不能让异常导致整次 /reload 失败
            LOGGER.error("数据包重载处理失败，保留上一版规则索引", e);
            return null;
        }
    }

    // 服务端停止：释放全部维度的追踪状态与到期任务
    public static void shutdown() {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.shutdown();
        }
        RuleEditServerHandler.reset();
        // 停服后复位实体状态时长，避免同一进程内下一个服务端实例沿用上一轮配置
        DropStateStore.configure(0, 0);
        runtime = null;
        typeRegistries = null;
        overlayRoot = null;
        serverConfig = null;
        currentServer = null;
    }

    // 掉落物进入世界：仅当存在候选规则时才纳入追踪
    public static void onItemAdded(ServerLevel level, ItemEntity entity) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.onItemAdded(level, entity);
        }
    }

    // 实体卸载与自然消失入口由平台事件或最小 Mixin 转发。
    public static void onItemRemoved(ServerLevel level, ItemEntity item) {
        ConversionRuntime current = runtime;
        if (current != null) { current.onItemRemoved(level, item); }
    }

    // 环境致死请求入口：平台层只在真实致死分支调用（Fabric 由 ItemEntityMixin 注入 onDestroyed 转发）
    public static void requestEnvironmentalConversion(ServerLevel level, ItemEntity item, DamageSource source) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.requestEnvironmentalConversion(level, item, source);
        }
    }

    public static boolean deferNaturalExpiry(ServerLevel level, ItemEntity item) {
        ConversionRuntime current = runtime;
        return current != null && current.deferNaturalExpiry(level, item);
    }

    // 服务器 tick 结束：推进唯一的公共预算；所有维度、所有任务种类共享同一份额度（ADR-0002）
    public static void tickServer(MinecraftServer server) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.onServerTick(server);
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

    // NeoForge 端寿命提供者：走加载器提供的 ItemStack#getEntityLifespan，保留其它模组的寿命修改
    private static LifespanProvider neoForgeLifespan() {
        return (level, entity) -> entity.lifespan;
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
                                           BuiltinTypeRegistries registries, MinecraftServer server) {
        return RuleLoadContext.full(resourceManager, overlay, MOD_NAMESPACE, registryAccess,
                registries.effectTypes(), registries.conditionTypes(),
                packId -> packId.startsWith("mod/")
                        ? com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer.BUILTIN
                        : com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer.WORLD).withServer(server);
    }

    // 执行一次加载 + 语义校验；异常被转为 ERROR 级问题，避免坏配置拖垮服务端启动或重载
    private static RuleLoadResult<Rule> loadRules(RuleLoadContext context) {
        return RuleLoadingService.loadAndValidate(context);
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
