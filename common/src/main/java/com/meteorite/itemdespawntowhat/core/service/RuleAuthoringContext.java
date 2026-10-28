package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.type.BuiltinTypeRegistries;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 编辑业务所需的加载、排期与发布能力，由运行时宿主提供。
 * 服务和续接任务通过本接口使用宿主能力，世界访问与运行时发布由服务端主线程执行。
 */
public interface RuleAuthoringContext {

    /** @return 本次服务端的编辑服务，未启动或已退役时为空 */
    @Nullable com.meteorite.itemdespawntowhat.core.service.RuleEditService editService();

    // 覆盖层根目录（config 下由 server.json 的 overlay_directory 指定）；运行时尚未就绪时为 null
    @Nullable Path overlayRoot();

    // 当前内置类型注册表；运行时尚未就绪时为 null
    @Nullable BuiltinTypeRegistries typeRegistries();

    // 执行一次三层加载 + 语义校验（只读，不改变运行时索引）
    RuleLoadResult<Rule> loadMerged(MinecraftServer server);

    /**
     * 获取当前数据包基底的保存重载快照；仅在服务端线程调用。
     * @return 基底快照，运行时未就绪时为 null
     */
    @Nullable RuleSaveReloadContext saveReloadContext();

    /**
     * 把已构建好的任务排入服务器公共预算调度器，延迟若干 tick 后执行。
     * 只暴露排期能力而不暴露调度器本身，使编辑任务无需持有运行时调度器。
     *
     * @param realmKey realm 标识，通常是维度键
     * @param task 待执行任务
     * @param delayTicks 相对当前刻的延迟刻数，负值按 0 处理
     */
    void scheduleTask(Object realmKey, ServerTask task, int delayTicks);

    /**
     * 发布已解码的索引：换入索引、候选结构摘要整表失效，并逐维排队回扫。
     * @param server 当前服务端
     * @param index 后台基于校验后模型准备的索引
     * @param sources 与规则来自同批读取的原始来源索引
     * @return 发布收尾观测任务；保存链路在公共预算内推进它
     */
    ServerTask publishIndex(MinecraftServer server, RuleIndex index, RuleSourceIndex sources);

    /** 当前运行时发布的同批原始来源。 */
    RuleSourceIndex publishedSources();

    /**
     * 读取服务端缓存的普通物品参考寿命，仅用于 GUI 滑条窗口。
     * @return 参考寿命 tick；零表示平台尚未完成采样
     */
    int referenceLifespanTicks();

    /**
     * 读取当前规则限额，随开屏载荷下发供编辑器设置合法域与交互窗口。
     * @return 服务端当前规则限额
     */
    RuleRuntimeLimits runtimeLimits();

}
