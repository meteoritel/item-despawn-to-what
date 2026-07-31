package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.server.conversion.ItemConversionProcessor;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

import static com.meteorite.itemdespawntowhat.ItemDespawnToWhat.MOD_ID;

/**
 * NeoForge 服务端物品转换事件入口。
 */
@EventBusSubscriber(modid = MOD_ID)
public final class ItemConversionEvent {
    private static final NeoForgeItemConversionStateAccess STATE = NeoForgeItemConversionStateAccess.INSTANCE;

    private ItemConversionEvent() {
        throw new UnsupportedOperationException("Utility class");
    }

    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity itemEntity) {
            ItemConversionProcessor.trackIfEligible(itemEntity, STATE);
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
        if (!ItemConversionProcessor.shouldCheck(serverLevel)) {
            return;
        }

        for (ItemEntity itemEntity : collectTrackedItemEntities(serverLevel)) {
            int entityLifespan = itemEntity.getItem().getEntityLifespan(serverLevel);
            ItemConversionProcessor.tickTrackedItem(itemEntity, STATE, entityLifespan);
        }
    }

    private static List<ItemEntity> collectTrackedItemEntities(ServerLevel level) {
        List<ItemEntity> result = new ArrayList<>();
        level.getEntities(EntityType.ITEM,
                itemEntity -> itemEntity.isAlive()
                        && level.isLoaded(itemEntity.blockPosition())
                        && STATE.isTracked(itemEntity)
                        && !itemEntity.getTags().contains(Constants.CHECK_LOCK_TAG)
                        && itemEntity.getAge() < itemEntity.getItem().getEntityLifespan(level),
                result);
        return result;
    }
}
