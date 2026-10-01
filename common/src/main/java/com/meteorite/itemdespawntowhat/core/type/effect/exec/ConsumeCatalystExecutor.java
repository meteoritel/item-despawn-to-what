package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeCatalystEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * consume_catalyst 执行器：在 radius 范围内由近及远消耗 items 命中的催化剂物品，总量不超过 count。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeCatalystExecutor {

    private ConsumeCatalystExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(ConsumeCatalystEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        ItemEntity source = context.source();
        Vec3 position = context.position();
        BlockPos center = BlockPos.containing(position.x, position.y, position.z);
        AABB box = EffectTargets.blockBox(center, effect.radius());
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, box,
                entity -> entity != source && entity.isAlive() && !entity.getItem().isEmpty());
        if (candidates.isEmpty()) {
            return;
        }
        // 由近及远消耗，使结果不依赖实体遍历顺序
        candidates.sort(Comparator.comparingDouble((ItemEntity entity) -> entity.distanceToSqr(position)));
        int remaining = effect.count();
        for (ItemEntity candidate : candidates) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = candidate.getItem();
            if (!matchesAny(effect.items(), stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) {
                candidate.discard();
            }
        }
    }

    // 物品栈是否命中 items 中的任一引用（非 tag 比物品，tag 比标签）
    private static boolean matchesAny(List<TaggedId> references, ItemStack stack) {
        for (TaggedId reference : references) {
            if (reference == null) {
                continue;
            }
            if (reference.tag()) {
                if (stack.is(TagKey.create(Registries.ITEM, reference.id()))) {
                    return true;
                }
                continue;
            }
            if (!BuiltInRegistries.ITEM.containsKey(reference.id())) {
                continue;
            }
            if (stack.is(BuiltInRegistries.ITEM.get(reference.id()))) {
                return true;
            }
        }
        return false;
    }
}
