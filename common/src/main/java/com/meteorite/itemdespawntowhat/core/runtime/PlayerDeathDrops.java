package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** 玩家死亡掉落排除策略，供 Fabric 的最小掉落入口复用。 */
public final class PlayerDeathDrops {
    private PlayerDeathDrops() {}

    // 标记必须先于实体入世界；普通玩家丢物品与生物死亡掉落不会命中。
    public static void mark(Entity owner, ItemEntity item) {
        if (owner instanceof Player player && player.isDeadOrDying()) {
            item.addTag(Constants.CHECK_LOCK_TAG);
        }
    }
}
