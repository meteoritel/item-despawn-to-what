package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * catalyst_present 条件的求值器：统计掉落物所在方块格内的催化剂数量。
 * 范围与旧实现一致：物品所在方块位置的 1×1×1 立方体，排除源物品自身与已死亡实体；
 * 命中任意候选引用的堆叠数量累加，达到 count 即成立。标签判定走 TagLookup（带缓存）。
 * 纯谓词：只读取上下文，不修改世界（消耗由 consume_catalyst 效果承担）；不处理 negated。
 */
public final class CatalystPresentEvaluator {

    private CatalystPresentEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 附近命中物品的总数达到 count 即成立
    public static boolean test(CatalystPresentCondition condition, ConditionContext context) {
        if (condition.items().isEmpty() || condition.count() <= 0) {
            // 非法配置（加载期已拒载），fail-closed
            return false;
        }
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(context.pos()));
        long[] total = {0};
        context.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class), searchBox,
                entity -> {
                    if (entity != context.source() && entity.isAlive() && !entity.getItem().isEmpty()
                            && matchesAny(entity.getItem(), condition, context)) {
                        total[0] += entity.getItem().getCount();
                    }
                    return total[0] >= condition.count();
                }, new java.util.ArrayList<ItemEntity>(1), 1);
        return total[0] >= condition.count();
    }

    // 物品是否命中候选引用中的任意一项
    private static boolean matchesAny(ItemStack stack, CatalystPresentCondition condition, ConditionContext context) {
        Optional<ResourceKey<Item>> key = stack.getItemHolder().unwrapKey();
        if (key.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = key.get().location();
        for (TaggedId reference : condition.items()) {
            boolean matched = reference.tag()
                    ? context.tags().itemInTag(reference.id(), itemId)
                    : reference.id().equals(itemId);
            if (matched) {
                return true;
            }
        }
        return false;
    }
}
