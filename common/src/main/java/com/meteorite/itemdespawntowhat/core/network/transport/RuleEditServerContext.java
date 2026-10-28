package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.service.RuleAuthoringContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** 编辑网络上下文：业务能力与本次服务端的传输出口在宿主装配。 */
public interface RuleEditServerContext extends RuleAuthoringContext {
    /** 当前服务端的传输状态；未启动时为空。 */
    @org.jetbrains.annotations.Nullable RuleEditTransport transport();

    /**
     * 目标玩家的当前连接是否可接收编辑器打开载荷；用于创建编辑会话前的能力判定。
     *
     * @param player 目标玩家
     * @return 当前连接是否具备编辑通道
     */
    boolean canReachClient(ServerPlayer player);

    /** 向声明可接收该类型的玩家连接下发线上载荷。 */
    void sendTo(ServerPlayer player, CustomPacketPayload payload);
}
