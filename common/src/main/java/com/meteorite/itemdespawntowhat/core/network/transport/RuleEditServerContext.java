package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.service.RuleSaveReloadContext;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 编辑网络层所需的平台能力（由两端 RuleRuntimeHost 实现并提供）。
 * 单独抽成窄接口是为了让 core/network/transport 不反向依赖平台运行时类，
 * 同时平台侧无需把运行时内部状态提升为公共 API。
 */
public interface RuleEditServerContext {

    // 覆盖层根目录（config 下由 server.json 的 overlay_directory 指定）；运行时尚未就绪时为 null
    @Nullable Path overlayRoot();

    // 覆盖层规则的缺省命名空间
    String overlayNamespace();

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
     * 只暴露排期能力而不暴露调度器本身，使网络层与平台宿主都无需依赖运行时实现类。
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
     * @return 发布收尾观测任务；保存链路在公共预算内推进它
     */
    ServerTask publishIndex(MinecraftServer server, RuleIndex index);

    /**
     * 读取服务端缓存的普通物品参考寿命，仅用于 GUI 滑条窗口。
     * @return 参考寿命 tick；零表示平台尚未完成采样
     */
    int referenceLifespanTicks();

    /**
     * 读取服务端配置的单次放置半径上限，随开屏载荷下发，仅用于 GUI 半径滑条的取值窗口。
     * @return max_place_block_radius 的当前取值
     */
    int maxPlaceBlockRadius();

    // 向指定玩家发送一个自定义负载（S2C）；由平台层实现真实的发包方式
    void sendTo(ServerPlayer player, CustomPacketPayload payload);
}
