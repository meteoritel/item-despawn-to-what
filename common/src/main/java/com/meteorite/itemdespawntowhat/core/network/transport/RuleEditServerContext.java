package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
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

    // 规则落盘后重建索引并对全部已加载维度回扫
    void rebuildAndRescan(MinecraftServer server);

    // 向指定玩家发送一个自定义负载（S2C）；由平台层实现真实的发包方式
    void sendTo(ServerPlayer player, CustomPacketPayload payload);
}
