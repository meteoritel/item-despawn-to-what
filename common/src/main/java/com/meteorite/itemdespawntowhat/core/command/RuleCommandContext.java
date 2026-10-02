package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * 命令树所需的运行时能力（由两端 RuleRuntimeHost 实现并提供）。
 * 单独抽成窄接口是为了让 core/command 不依赖平台运行时类，同时平台侧无需把运行时内部状态提升为公共 API。
 * 所有成员都可能返回 null：服务端启动完成前命令树尚未就绪。
 */
public interface RuleCommandContext {

    // 当前转化运行时（追踪状态、调度器、规则索引）；未引导时为 null
    @Nullable ConversionRuntime runtime();

    // 当前模组级配置（server.json）；未引导时为 null
    @Nullable ServerConfig serverConfig();

    // 编辑网络层上下文（覆盖层目录、类型注册表、加载与重发能力）
    RuleEditServerContext editContext();

    // 覆盖层规则的缺省命名空间
    String overlayNamespace();

    // 当前覆盖层版本戳（乐观并发基准）；不可用时返回 -1
    int overlayVersion();

    // 当前活跃编辑会话数
    int activeSessionCount();

    // 重新加载三层规则并重建索引、回扫全部已加载维度；失败时返回 null
    @Nullable RuleLoadResult<Rule> reloadRules(MinecraftServer server);
}
