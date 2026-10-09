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
 * 每个引用分别累计堆叠数量，全部达到各自门槛才成立。标签判定走 TagLookup（带缓存）。
 * 纯谓词：只读取上下文，不修改世界（消耗由 consume_catalyst 效果承担）；不处理取反（取反由条件树的 inverted 节点承担）。
 */
public final class CatalystPresentEvaluator {

    private CatalystPresentEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 一次邻域查询后，分别核对每个催化剂引用的门槛。
    public static boolean test(CatalystPresentCondition condition, ConditionContext context) {
        // 正常运行必经规则索引投影（core/runtime/CatalystThresholdProjection）填入有效门槛；
        // 这里对留空门槛按默认 1 兜底，避免绕过索引的调用路径出现拆箱异常或错误门槛。
        if (condition.items().isEmpty()) {
            // 非法配置（加载期已拒载），fail-closed
            return false;
        }
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(context.pos()));
        var nearby = context.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                entity -> entity != context.source() && entity.isAlive() && !entity.getItem().isEmpty());
        for (TaggedId reference : condition.items()) {
            long total = 0;
            int threshold = condition.countFor(reference);
            if (threshold <= 0) return false;
            for (ItemEntity entity : nearby) {
                if (matches(entity.getItem(), reference, context)) total += entity.getItem().getCount();
                if (total >= threshold) break;
            }
            if (total < threshold) return false;
        }
        return true;
    }

    // 物品是否命中候选引用中的任意一项
    private static boolean matches(ItemStack stack, TaggedId reference, ConditionContext context) {
        Optional<ResourceKey<Item>> key = stack.getItemHolder().unwrapKey();
        if (key.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = key.get().location();
        return reference.tag() ? context.tags().itemInTag(reference.id(), itemId) : reference.id().equals(itemId);
    }
}
