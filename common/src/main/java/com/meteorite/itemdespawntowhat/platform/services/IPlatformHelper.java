package com.meteorite.itemdespawntowhat.platform.services;

import com.meteorite.itemdespawntowhat.core.state.DropState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;

/** 两端共用的平台环境查询、服务端发包与实体层掉落物状态存取能力。 */
public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path getConfigDir();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    // 向指定玩家发送服务端负载。
    default void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new UnsupportedOperationException("sendToPlayer not implemented for " + getPlatformName());
    }

    // 读取实体层掉落物转化状态。
    // 未实现平台静默降级为无状态：状态读取位于伤害判定等世界逻辑热路径上，绝不抛异常。
    default DropState getDropState(Entity entity) {
        return DropState.NONE;
    }

    // 写入实体层掉落物转化状态；null 表示清除。
    // 未实现平台静默降级为空操作：状态写入绝不影响世界逻辑执行。
    default void setDropState(Entity entity, DropState state) {
        // 未实现平台无操作
    }
}
