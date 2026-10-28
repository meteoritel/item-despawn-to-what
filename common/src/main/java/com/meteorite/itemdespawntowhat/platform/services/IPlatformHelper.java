package com.meteorite.itemdespawntowhat.platform.services;

import com.meteorite.itemdespawntowhat.core.state.DropState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;

/** 两端共用的平台环境查询、服务端发包与实体层掉落物状态存取能力。 */
public interface IPlatformHelper {

    /** @return 平台名，Fabric 端为 {@code Fabric}，NeoForge 端为 {@code NeoForge} */
    String getPlatformName();

    /**
     * @param modId 待查询的模组 id
     * @return 该模组是否已加载
     */
    boolean isModLoaded(String modId);

    /** @return 是否运行在开发环境 */
    boolean isDevelopmentEnvironment();

    /** @return 当前平台的配置目录 */
    Path getConfigDir();

    /** @return 环境名，开发环境为 {@code development}，其余为 {@code production} */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * 查询目标玩家的当前连接是否声明可接收指定载荷；未提供查询能力的平台保持编辑入口关闭。
     *
     * @param player 目标玩家
     * @param type 下行载荷类型
     * @return 当前连接是否具备该接收通道
     */
    default boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return false;
    }

    /**
     * 向指定玩家发送服务端负载。
     *
     * @param player 目标玩家
     * @param payload 待发送的负载
     * @throws UnsupportedOperationException 平台未实现该能力时
     */
    default void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new UnsupportedOperationException("sendToPlayer not implemented for " + getPlatformName());
    }

    /**
     * 读取实体层掉落物转化状态。
     * 未实现平台静默降级为无状态：状态读取位于伤害判定等世界逻辑热路径上，绝不抛异常。
     *
     * @param entity 目标实体
     * @return 已存状态；无状态时为 {@link DropState#NONE}
     */
    default DropState getDropState(Entity entity) {
        return DropState.NONE;
    }

    /**
     * 写入实体层掉落物转化状态；null 表示清除。
     * 未实现平台静默降级为空操作：状态写入绝不影响世界逻辑执行。
     *
     * @param entity 目标实体
     * @param state 待写入状态
     */
    default void setDropState(Entity entity, DropState state) {
    }
}
