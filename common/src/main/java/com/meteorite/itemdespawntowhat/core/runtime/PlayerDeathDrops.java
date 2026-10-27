package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.state.DropStateStore;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** 玩家死亡掉落排除策略，供 Fabric 的最小掉落入口复用。 */
public final class PlayerDeathDrops {
    private PlayerDeathDrops() {}

    /** 标记必须先于实体入世界；只排除玩家死亡掉落。 */
    public static void mark(Entity owner, ItemEntity item) {
        if (owner instanceof Player player && player.isDeadOrDying()) {
            DropStateStore.excludeDeathDrop(item);
        }
    }
}
