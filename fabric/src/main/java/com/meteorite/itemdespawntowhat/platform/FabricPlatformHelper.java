package com.meteorite.itemdespawntowhat.platform;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;

/** 平台环境与服务端发包适配器；客户端发送在客户端注册器中处理。 */
public class FabricPlatformHelper implements IPlatformHelper {

    // 掉落物状态附件：持久化进实体 NBT（fabric:attachments），随区块/实体存档保存并在加载时恢复
    // 当前 Fabric 版本的附件 API 标记为 Experimental，仅在附件注册与存取处接受此依赖。
    @SuppressWarnings("UnstableApiUsage")
    private static final AttachmentType<DropState> DROP_STATE = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "state"), DropState.CODEC);

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** 按目标连接声明的接收通道查询，独立于服务端本地的模组列表。 */
    @Override
    public boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public DropState getDropState(Entity entity) {
        DropState state = entity.getAttached(DROP_STATE);
        return state == null ? DropState.NONE : state;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void setDropState(Entity entity, DropState state) {
        // 无状态即显式移除附件，避免给每个掉落物都写一份空数据
        if (state == null || state.isEmpty()) {
            entity.removeAttached(DROP_STATE);
        } else {
            entity.setAttached(DROP_STATE, state);
        }
    }
}
