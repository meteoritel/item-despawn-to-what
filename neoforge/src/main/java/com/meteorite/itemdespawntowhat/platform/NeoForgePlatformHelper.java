package com.meteorite.itemdespawntowhat.platform;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

/** 平台环境与服务端发包适配器；客户端发送在客户端注册器中处理。 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    // 掉落物状态的 NBT 键：写在 NeoForge 的实体持久数据（NeoForgeData）里，随实体存档保存（D7）
    private static final String DROP_STATE_KEY = Constants.MOD_ID + ":state";

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public DropState getDropState(Entity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(DROP_STATE_KEY, Tag.TAG_COMPOUND)) {
            return DropState.NONE;
        }
        return DropState.CODEC.parse(NbtOps.INSTANCE, root.getCompound(DROP_STATE_KEY))
                .result().orElse(DropState.NONE);
    }

    @Override
    public void setDropState(Entity entity, DropState state) {
        CompoundTag root = entity.getPersistentData();
        // 无状态即移除键，避免给每个掉落物都写一份空数据
        if (state == null || state.isEmpty()) {
            root.remove(DROP_STATE_KEY);
            return;
        }
        DropState.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .result().ifPresent(tag -> root.put(DROP_STATE_KEY, tag));
    }
}
