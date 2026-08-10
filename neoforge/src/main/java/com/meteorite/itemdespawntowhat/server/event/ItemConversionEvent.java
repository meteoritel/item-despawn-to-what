package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.server.conversion.ConversionTracker;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import static com.meteorite.itemdespawntowhat.ItemDespawnToWhat.MOD_ID;

/**
 * NeoForge 服务端物品转换事件入口。
 */
@EventBusSubscriber(modid = MOD_ID)
public final class ItemConversionEvent {
    private ItemConversionEvent() {
        throw new UnsupportedOperationException("Utility class");
    }

    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity itemEntity) {
            ConversionTracker.trackIfEligible(itemEntity);
        }
    }

    // 玩家死亡掉落物不参与转换
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        for (ItemEntity itemEntity : event.getDrops()) {
            itemEntity.addTag(Constants.CHECK_LOCK_TAG);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        LevelTaskManager.tick(serverLevel);
        ConversionTracker.tick(serverLevel,
                (itemEntity, level) -> itemEntity.getItem().getEntityLifespan(level));
    }
}
