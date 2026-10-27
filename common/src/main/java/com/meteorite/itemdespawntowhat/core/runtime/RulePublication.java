package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.type.BuiltinTypeRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 规则发布：持有生效索引、发布代次与候选结构摘要，负责一次换入并逐维排队回扫。
 * 设计要点：
 * - 预构建与换入分离：{@link #prepare} 只读规则与注册表视图，构建失败时旧索引仍完整可用；
 * - 换入是唯一实现 {@link #install}：索引与候选摘要一次性替换，随后统一失效各维度追踪与惰性缓存；
 * - 回扫排队单维失败只记录并补排有界重试，避免一个维度的问题让其它维度排不上回扫。
 */
final class RulePublication {

    private static final Logger LOGGER = LogManager.getLogger();

    private final ServerConfig config;
    private final WorldRescan rescan;
    // 逐维重建追踪：宿主负责取（必要时创建）维度状态，发布层不持有维度状态
    private final Consumer<ServerLevel> rescanAll;
    // 换入时失效各维度追踪与惰性缓存；摘除只按维度 realm，不能连带取消保存与快照任务的 realm
    private final Runnable reloadLevels;
    // 候选结构摘要缓存：按规则实例缓存编码结果，随规则索引代次失效（见 CandidateStructureVersions）
    private final CandidateStructureVersions candidateStructureVersions;
    private volatile RuleIndex index = RuleIndex.empty();
    private long ruleGeneration;

    RulePublication(ServerConfig config, BuiltinTypeRegistries types, WorldRescan rescan,
                    Consumer<ServerLevel> rescanAll, Runnable reloadLevels) {
        this.config = config;
        this.rescan = rescan;
        this.rescanAll = rescanAll;
        this.reloadLevels = reloadLevels;
        this.candidateStructureVersions = new CandidateStructureVersions(types);
    }

    /** 当前生效的规则索引（replace / publish / publishDecoded 时整体替换） */
    RuleIndex index() {
        return index;
    }

    /** 候选结构摘要：同一规则实例的编码结果按代次缓存，规则内容变即失效。 */
    int versionOf(Rule rule, HolderLookup.Provider registries) {
        return candidateStructureVersions.versionOf(rule, registries);
    }

    /**
     * 规则热替换：重建索引并清空全部追踪状态；启动等没有并发旧索引的链路使用。
     * 只换入，不排队回扫——启动期维度尚未就绪，由宿主逐维做世界扫描。
     */
    void replace(List<LoadedRule<Rule>> rules, IssueCollector issues) {
        // 启动装配时运行时尚未对外发布，不需要保护旧索引；候选摘要留给首次结算按需编码
        install(RuleIndex.build(rules, issues), Map.of());
    }

    /**
     * 预构建规则发布物：只读规则与注册表视图，构建新索引与候选结构摘要，
     * 不触碰在跑的索引、追踪表与任务队列，因此构建失败时旧规则仍完整可用。
     *
     * @param rules 已加载规则
     * @param issues 问题收集器：构建期问题记录在此，不改动在跑索引
     * @param registries 当前注册表视图，用于编码候选结构摘要
     * @return 尚未发布的索引与候选摘要
     */
    ConversionRuntime.PreparedRules prepare(List<LoadedRule<Rule>> rules, IssueCollector issues,
                                            HolderLookup.Provider registries) {
        RuleIndex built = RuleIndex.build(rules, issues);
        List<Rule> runnable = new ArrayList<>(built.ordered().size());
        for (LoadedRule<Rule> entry : built.ordered()) {
            runnable.add(entry.value());
        }
        return new ConversionRuntime.PreparedRules(built, candidateStructureVersions.prepare(runnable, registries));
    }

    /**
     * 发布预构建物：换入索引与候选结构摘要，逐维排队回扫重建追踪。数据包重载使用本入口。
     * 发布只做内存容器替换，不在方法内构建索引。
     *
     * @param server 当前服务端，用于定位已加载维度
     * @param prepared 已构建完成的发布物
     * @return 发布收尾观测任务；保存链路在公共预算内推进它，其余调用方可直接丢弃
     */
    ServerTask publish(MinecraftServer server, ConversionRuntime.PreparedRules prepared) {
        install(prepared.index(), prepared.candidateVersions());
        scheduleLevelRescans(server);
        return new PublicationTask(server, ruleGeneration);
    }

    /**
     * 发布已解码索引：保存与启停链路只持有索引，候选结构摘要随整索引重建而整表失效。
     * 其余行为与 {@link #publish(MinecraftServer, ConversionRuntime.PreparedRules)} 相同。
     *
     * @param server 当前服务端，用于定位已加载维度
     * @param prepared 完整校验后的索引
     * @return 发布收尾观测任务
     */
    ServerTask publishDecoded(MinecraftServer server, RuleIndex prepared) {
        install(prepared, Map.of());
        scheduleLevelRescans(server);
        return new PublicationTask(server, ruleGeneration);
    }

    /** 停服收尾：索引退回空表并丢弃候选摘要缓存，使再次构造的实例从零开始。 */
    void close() {
        index = RuleIndex.empty();
        candidateStructureVersions.clear();
    }

    // 换入的唯一实现：索引与候选摘要一次性替换，随后统一失效各维度的追踪与缓存。
    private void install(RuleIndex preparedIndex, Map<Rule, Integer> candidateVersions) {
        ruleGeneration++;
        this.index = preparedIndex;
        // 候选结构摘要与规则内容绑定：换索引即换缓存，游标不会复用旧结构判定
        candidateStructureVersions.install(candidateVersions);
        reloadLevels.run();
        if (config.debugLogging()) {
            LOGGER.info("规则索引已重建：{} 条可运行规则", index.ordered().size());
        }
    }

    // 发布后的回扫排队：单维异常只记录并继续，避免一个维度的问题让其它维度排不上回扫
    private void scheduleLevelRescans(MinecraftServer server) {
        RuntimeException failure = null;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                rescanAll.accept(level);
            } catch (RuntimeException e) {
                if (failure == null) { failure = e; } else { failure.addSuppressed(e); }
                // 该维度追踪已被换入清空：补排有界重试，避免在下次回扫或维度加载前失联
                rescan.retry(level, 1);
            }
        }
        if (failure != null) {
            // 索引已换入，此时不能再声称保留了旧索引；差异只是回扫排队，留待下次回扫或维度重载补齐
            LOGGER.error("规则发布已应用，但部分维度回扫排队失败；这些维度将在下次回扫或维度重载时补齐", failure);
        }
    }

    /**
     * 发布收尾观测任务：等到本次发布排下的逐维回扫全部结束，或本次发布被更新的发布取代。
     * 不持有资源，取消与重排都由各维度回扫任务自己负责；保存链路在公共预算内推进它，
     * 使回执在追踪重建完成前保持已写盘未重载。
     */
    private final class PublicationTask implements ServerTask {

        private final MinecraftServer server;
        private final long generation;

        private PublicationTask(MinecraftServer server, long generation) {
            this.server = server;
            this.generation = generation;
        }

        @Override
        public ServerTaskKind kind() {
            return ServerTaskKind.MAINTENANCE;
        }

        @Override
        public String tracingName() {
            return "rule-publication";
        }

        @Override
        public StepResult step(com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget budget) {
            if (generation != ruleGeneration) {
                // 已被更新的发布取代：本次收尾没有意义，直接结束
                return StepResult.done(1);
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (rescan.isPending(level)) {
                    return StepResult.yield(1, 1);
                }
            }
            return StepResult.done(1);
        }
    }
}
