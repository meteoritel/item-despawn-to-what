package com.meteorite.itemdespawntowhat.platform;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
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

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public DropState getDropState(Entity entity) {
        DropState state = ((AttachmentTarget) entity).getAttached(DROP_STATE);
        return state == null ? DropState.NONE : state;
    }

    @Override
    public void setDropState(Entity entity, DropState state) {
        AttachmentTarget target = (AttachmentTarget) entity;
        // 无状态即显式移除附件（AttachmentTarget#removeAttached(AttachmentType)，已用 javap 核实存在），
        // 避免给每个掉落物都写一份空数据
        if (state == null || state.isEmpty()) {
            target.removeAttached(DROP_STATE);
        } else {
            target.setAttached(DROP_STATE, state);
        }
    }
}
