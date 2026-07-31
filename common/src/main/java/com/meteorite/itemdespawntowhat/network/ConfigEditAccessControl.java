package com.meteorite.itemdespawntowhat.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 配置编辑功能的服务端授权入口。
 */
public final class ConfigEditAccessControl {

    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private ConfigEditAccessControl() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 与 conversion_config 命令保持相同的权限规则
    public static boolean canEdit(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        return server != null
                && (server.isSingleplayer()
                || player.createCommandSourceStack().hasPermission(REQUIRED_PERMISSION_LEVEL));
    }
}
