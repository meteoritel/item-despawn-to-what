package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.service.RuleAuthoringContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** 编辑网络上下文：业务能力与本次服务端的传输出口在宿主装配。 */
public interface RuleEditServerContext extends RuleAuthoringContext {
    /** 当前服务端的传输状态；未启动时为空。 */
    @org.jetbrains.annotations.Nullable RuleEditTransport transport();

    /** 向指定玩家下发线上载荷。 */
    void sendTo(ServerPlayer player, CustomPacketPayload payload);
}
