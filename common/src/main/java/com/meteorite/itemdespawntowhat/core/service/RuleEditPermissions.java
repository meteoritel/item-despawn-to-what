package com.meteorite.itemdespawntowhat.core.service;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** 命令与编辑协议共用的管理权限：单人存档房主或达到要求等级的执行者。 */
public final class RuleEditPermissions {
    private RuleEditPermissions() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判定管理权限；开放局域网后仍只对房主保留直接访问资格。
     *
     * @param source 命令或玩家对应的执行上下文
     * @return 房主或权限等级达标时为 true
     */
    public static boolean hasAccess(CommandSourceStack source) {
        return source.hasPermission(RuleEditSessionLimits.REQUIRED_PERMISSION_LEVEL)
                || source.getEntity() instanceof ServerPlayer player
                && source.getServer().isSingleplayerOwner(player.getGameProfile());
    }
}
