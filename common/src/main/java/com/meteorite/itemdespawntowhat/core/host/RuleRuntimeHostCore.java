package com.meteorite.itemdespawntowhat.core.host;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.DatapackRuleReader;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadContext;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadingService;
import com.meteorite.itemdespawntowhat.core.service.RuleSaveReloadContext;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 新链路（core/runtime）的公共宿主实现：持有一次存档生命周期的运行时状态，装配引导、数据包重载与停服时序，
 * 并以窄接口向网络层与命令层暴露运行时能力。
 * 两端平台宿主只保留 {@link PlatformAdapter} 的适配代码与事件转发；此前的当前服务端获取与重载签名差异由本类统一持有。
 * 生命周期契约：{@link #start(MinecraftServer)} 失败时就地降级回未启动状态，异常不外泄到平台启动回调。
 */
public final class RuleRuntimeHostCore {

    private static final Logger LOGGER = LogManager.getLogger();
    // 覆盖层缺省命名空间与包层判定 token 都取模组 id
    private static final String MOD_NAMESPACE = Constants.MOD_ID;
    // 模组级配置文件名（固定位于 config/itemdespawntowhat/ 下）
    private static final String SERVER_CONFIG_FILE = "server.json";

    private final PlatformAdapter platform;
    // 当前服务端引用：数据包重载监听器需要用它枚举维度，服务端停止后置空
    private volatile MinecraftServer currentServer;
    // 当前模组级配置（server.json）；未引导或已停止时为 null
    private volatile ServerConfig serverConfig;
    // 当前内置类型注册表（数据包重载时复用，避免重复构表）
    private volatile BuiltinTypeRegistries typeRegistries;
    // 覆盖层根目录（config/<overlay_directory>）
    private volatile Path overlayRoot;
    // 新链路运行时；未引导或已停止时为 null
    private volatile ConversionRuntime runtime;
    private RuleSaveReloadContext saveReloadContext;

    // 网络编辑层入口：以窄接口暴露运行时的覆盖层路径与重建能力
    private final RuleEditServerContext editContext = new EditContext();
    // 命令层入口：以窄接口暴露运行时、模组级配置与编辑层统计
    private final RuleCommandContext commandContext = new CommandContext();

    /**
     * 创建一次存档生命周期的宿主实现。
     *
     * @param platform 平台差异适配面；不得为 null
     */
    public RuleRuntimeHostCore(PlatformAdapter platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    /**
     * 平台差异适配面：两端宿主必须实现且仅需实现的差异集合。
     * 寿命提供者及其 GUI 参考值的采样与复位是平台能力差异；包层判定按平台可见的模组信息实现。
     */
    public interface PlatformAdapter {

        /**
         * 创建本次运行时的寿命提供者。
         *
         * @param config 本次加载的模组级配置，供无加载器寿命 API 的平台回退兜底刻数
         * @return 自然寿命判定所需的寿命来源
         */
        LifespanProvider lifespan(ServerConfig config);

        /**
         * 数据包来源层判定：区分内置模组数据包与世界数据包。
         *
         * @return 包层判定器
         */
        PackLayerResolver packLayerResolver();

        /**
         * @return 服务端缓存的 GUI 参考寿命 tick；尚未采样时为零
         */
        int referenceLifespanTicks();

        /**
         * 启动采样：在服务端已就绪、维度已创建之后采集一次参考寿命。
         * 无加载器寿命 API 的平台由运行期事件采样，此处可为空实现。
         *
         * @param server 已启动的服务端
         */
        void sampleReferenceLifespan(MinecraftServer server);

        /** 停服复位：清除平台侧缓存的参考寿命，避免同一进程内切换存档继承上一轮值。 */
        void clearReferenceLifespan();
    }

    /**
     * @return 编辑网络层所需的运行时能力视图；未启动时各项能力返回空值
     */
    public RuleEditServerContext editContext() { return editContext; }

    /**
     * @return 命令层所需的运行时能力视图；未启动时各项能力返回空值
     */
    public RuleCommandContext commandContext() { return commandContext; }

    /**
     * 启动运行时：读配置 → 建注册表 → 加载规则 → 建运行时 → 对全部已加载维度回扫。
     * 必须等到服务端就绪（全部维度已创建）才能回扫，否则会漏掉启动期已存在的掉落物。
     * 回调链上的可预期异常就地降级：实例回到确定的未启动状态并记录原因，异常不外泄到平台启动流程。
     *
     * @param server 已就绪的服务端
     */
    public void start(MinecraftServer server) {
        try {
            // 同一进程内可能先后启动多个服务端（单人世界切换存档）：先释放上一轮状态
            shutdown();
            // 上一生命周期的文件工作未退出时不建立新的覆盖层所有者，避免同一目录出现双写入者
            EditSessionManager.requireFileWorkIdle();

            IssueCollector configIssues = new IssueCollector();
            ServerConfig config = loadServerConfig(configIssues);
            logIssues(configIssues, "读取 server.json");

            BuiltinTypeRegistries registries = BuiltinTypeRegistries.create();
            Path overlay = Services.PLATFORM.getConfigDir().resolve(config.overlayDirectory());
            ConversionRuntime newRuntime = new ConversionRuntime(config, registries, platform.lifespan(config));

            RuleSaveReloadContext saveContext = captureSaveContext(
                    context(server.getResourceManager(), server.registryAccess(), overlay, registries, server));
            RuleLoadResult<Rule> result = RuleLoadingService.loadAndValidate(saveContext.loadContext(), saveContext.baseRules());
            saveContext.baseIssues().forEach(result.issues()::add);
            newRuntime.replaceRules(result.rules(), result.issues());
            logLoadResult("服务端启动", result);

            platform.sampleReferenceLifespan(server);
            currentServer = server;
            serverConfig = config;
            typeRegistries = registries;
            overlayRoot = overlay;
            runtime = newRuntime;
            saveReloadContext = saveContext;
            // 实体状态持续时间取自本次配置：新产物保护与转化冷却各自可为 0（D18，0 表示不授予）
            DropStateStore.configure(config.newProductProtectionTicks(), config.conversionCooldownTicks());

            int levelCount = 0;
            for (ServerLevel level : server.getAllLevels()) {
                newRuntime.rescan(level);
                levelCount++;
            }
            LOGGER.info("新链路运行时已就绪：覆盖层 {}，已回扫维度 {} 个", overlay, levelCount);
        } catch (RuntimeException failure) {
            degradeStartFailure(failure);
        }
    }

    // 启动失败的确定收尾：释放本轮已建立的状态并记录降级原因与影响；清理自身失败也不能让异常逃逸
    private void degradeStartFailure(RuntimeException failure) {
        try {
            shutdown();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            LOGGER.error("启动降级清理未完成，运行时可能残留部分状态", cleanupFailure);
        }
        LOGGER.error("新链路运行时启动失败，已降级为未启动状态：规则转换与编辑功能本次不可用，世界仍可加载或重载；"
                + "修正启动失败原因后重启服务端即可恢复", failure);
    }

    /**
     * 数据包重载：重建规则索引并回扫全部已加载维度，使已存在的掉落物立即按新规则重选（A3）。
     * 调用点必须位于服务端线程；运行时未就绪或重载失败时返回 null 并保留上一版索引。
     *
     * @param server 当前服务端
     * @param resourceManager 本次重载使用的数据包资源管理器
     * @return 本次加载结果；未就绪或失败时为 null
     */
    public RuleLoadResult<Rule> reload(MinecraftServer server, ResourceManager resourceManager) {
        if (server == null) { return null; }
        if (RuleEditServerHandler.deferReload(() -> {
            // 延后到保存收尾执行时重新取当前服务端，避免重载到已停止的实例
            MinecraftServer running = currentServer;
            if (running != null) { reload(running, running.getResourceManager()); }
        })) { return null; }
        ConversionRuntime current = runtime;
        BuiltinTypeRegistries registries = typeRegistries;
        Path overlay = overlayRoot;
        if (current == null || registries == null || overlay == null) {
            // 启动前的首次数据包加载：那时维度尚未创建，统一交给 start 处理
            return null;
        }
        try {
            RuleSaveReloadContext saveContext = captureSaveContext(
                    context(resourceManager, server.registryAccess(), overlay, registries, server));
            RuleLoadResult<Rule> result = RuleLoadingService.loadAndValidate(saveContext.loadContext(), saveContext.baseRules());
            saveContext.baseIssues().forEach(result.issues()::add);
            current.replaceRules(result.rules(), result.issues());
            saveReloadContext = saveContext;
            logLoadResult("数据包重载", result);
            for (ServerLevel level : server.getAllLevels()) {
                current.rescan(level);
            }
            Objects.requireNonNull(RuleEditServerHandler.sessionManager(editContext)).bumpVersion();
            LOGGER.info("数据包重载完成：规则索引已重建并完成回扫");
            return result;
        } catch (RuntimeException e) {
            // 重载失败必须保留旧索引继续服务，不能让异常逃逸到事件回调
            LOGGER.error("数据包重载处理失败，保留上一版规则索引", e);
            return null;
        }
    }

    /** 服务端停止：释放全部维度的追踪状态与到期任务，并清空运行时状态。 */
    public void shutdown() {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.shutdown();
        }
        RuleEditServerHandler.reset();
        platform.clearReferenceLifespan();
        // 停服后复位实体状态时长，避免同一进程内下一个服务端实例沿用上一轮配置
        DropStateStore.configure(0, 0);
        runtime = null;
        saveReloadContext = null;
        typeRegistries = null;
        overlayRoot = null;
        serverConfig = null;
        currentServer = null;
    }

    // 掉落物进入世界：仅当存在候选规则时才纳入追踪
    public void onItemAdded(ServerLevel level, ItemEntity entity) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.onItemAdded(level, entity);
        }
    }

    // 实体卸载与自然消失入口由平台事件或最小 Mixin 转发。
    public void onItemRemoved(ServerLevel level, ItemEntity item) {
        ConversionRuntime current = runtime;
        if (current != null) { current.onItemRemoved(level, item); }
    }

    // 环境致死请求入口：平台层只在真实致死分支调用（Fabric 由 ItemEntityMixin 注入 onDestroyed 转发）
    public void requestEnvironmentalConversion(ServerLevel level, ItemEntity item, DamageSource source) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.requestEnvironmentalConversion(level, item, source);
        }
    }

    /**
     * 自然消失前的延后判定入口。
     *
     * @param level 掉落物所在维度
     * @param item 待判定的掉落物
     * @return 是否已延后本次自然消失
     */
    public boolean deferNaturalExpiry(ServerLevel level, ItemEntity item) {
        ConversionRuntime current = runtime;
        return current != null && current.deferNaturalExpiry(level, item);
    }

    // 服务器 tick 结束：推进唯一的公共预算；所有维度、所有任务种类共享同一份额度
    public void tickServer(MinecraftServer server) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.onServerTick(server);
        }
    }

    // 维度加载：对后加载的维度补一次回扫（启动期维度已由 start 统一回扫）
    public void levelLoaded(ServerLevel level) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.rescan(level);
        }
    }

    // 维度卸载：释放该维度的追踪状态与到期任务，避免强引用维度对象（A4）
    public void levelUnloaded(ServerLevel level) {
        ConversionRuntime current = runtime;
        if (current != null) {
            current.clear(level);
        }
    }

    // 运行时就绪前的空加载结果，保证网络层拿到非 null 对象
    private RuleLoadResult<Rule> emptyResult() {
        IssueCollector issues = new IssueCollector();
        issues.warn("新链路运行时尚未就绪，本次按空规则集处理", null, null);
        return new RuleLoadResult<>(List.of(), issues);
    }

    // 读取模组级配置；缺失即由 ServerConfig.loadOrCreate 落盘默认值（职责在 core，平台层不重复处理）
    private ServerConfig loadServerConfig(IssueCollector issues) {
        return ServerConfig.loadOrCreate(serverConfigFile(), issues);
    }

    // 模组级配置文件固定路径：config/itemdespawntowhat/server.json
    // server.json 内的 overlay_directory 只控制规则覆盖层目录，不影响本文件自身位置，避免引导期自引用
    private Path serverConfigFile() {
        return Services.PLATFORM.getConfigDir()
                .resolve(ServerConfig.DEFAULT_OVERLAY_DIRECTORY)
                .resolve(SERVER_CONFIG_FILE);
    }

    // 组装一次加载所需的全部外部依赖：数据包资源管理器 + 覆盖层 + 注册表 + 包层判定
    private RuleLoadContext context(ResourceManager resourceManager,
                                    RegistryAccess registryAccess,
                                    Path overlay,
                                    BuiltinTypeRegistries registries, MinecraftServer server) {
        return RuleLoadContext.full(resourceManager, overlay, MOD_NAMESPACE, registryAccess,
                registries.effectTypes(), registries.conditionTypes(), platform.packLayerResolver()).withServer(server);
    }

    // 基底只在启动/数据包重载时读取，保存后台不接触 ResourceManager 或平台包判定器。
    private RuleSaveReloadContext captureSaveContext(RuleLoadContext context) {
        IssueCollector issues = new IssueCollector();
        var base = DatapackRuleReader.read(context.resourceManager(), context.layerResolver(), issues);
        return new RuleSaveReloadContext(context, base, issues.issues());
    }

    // 执行一次加载 + 语义校验；异常被转为 ERROR 级问题，避免坏配置拖垮服务端启动或重载
    private RuleLoadResult<Rule> loadRules(RuleLoadContext context) {
        return RuleLoadingService.loadAndValidate(context);
    }

    // 汇总一条加载结果：先打统计行，再逐条输出问题（错误与告警分级）
    private void logLoadResult(String stage, RuleLoadResult<Rule> result) {
        LOGGER.info("{}：{}", stage, RuleLoadingService.summarize(result));
        logIssues(result.issues(), stage);
    }

    // 逐条输出问题；ERROR 记 error 级、WARN 记 warn 级，便于用户按级别过滤
    private void logIssues(IssueCollector issues, String stage) {
        for (Issue issue : issues.issues()) {
            if (issue.severity() == IssueSeverity.ERROR) {
                LOGGER.error("{}问题：{}", stage, issue.format());
            } else {
                LOGGER.warn("{}问题：{}", stage, issue.format());
            }
        }
    }

    // 运行时能力适配器：状态都在宿主实例上，调用时实时读取
    private final class EditContext implements RuleEditServerContext {

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
        public int referenceLifespanTicks() {
            return platform.referenceLifespanTicks();
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
        public RuleSaveReloadContext saveReloadContext() { return saveReloadContext; }

        @Override
        public void scheduleTask(Object realmKey, ServerTask task, int delayTicks) {
            Objects.requireNonNull(runtime).scheduler().schedule(realmKey, task, delayTicks);
        }

        @Override
        public ServerTask applySavedIndex(MinecraftServer server, RuleIndex index) {
            return Objects.requireNonNull(runtime).applySavedIndex(server, index);
        }

        @Override
        public void rebuildAndRescan(MinecraftServer server) {
            // 与数据包重载共用同一条 apply 路径（Fabric 与 NeoForge 两端口径一致）：
            // 重载失败必须抛异常，让保存流程回 SAVED_NOT_RELOADED（已写盘但未重载）
            if (reload(server, server.getResourceManager()) == null) {
                throw new IllegalStateException("保存后的运行时重载失败");
            }
        }
    }

    // 命令层能力适配器：状态都在宿主实例上，调用时实时读取
    private final class CommandContext implements RuleCommandContext {

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
            return editContext;
        }

        @Override
        public String overlayNamespace() {
            return MOD_NAMESPACE;
        }

        @Override
        public int overlayVersion() {
            EditSessionManager sessions = RuleEditServerHandler.sessionManager(editContext);
            return sessions == null ? -1 : sessions.version();
        }

        @Override
        public int activeSessionCount() {
            EditSessionManager sessions = RuleEditServerHandler.sessionManager(editContext);
            return sessions == null ? 0 : sessions.activeSessionCount();
        }

        @Override
        public RuleLoadResult<Rule> reloadRules(MinecraftServer server) {
            return reload(server, server.getResourceManager());
        }
    }
}
