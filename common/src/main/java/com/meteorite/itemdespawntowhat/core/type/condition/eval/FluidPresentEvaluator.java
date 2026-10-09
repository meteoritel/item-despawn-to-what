package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.condition.FluidPresentCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FluidState;

/**
 * fluid_present 条件的求值器：判定掉落物所在方块位置的流体。
 * 语义与旧实现一致：位置无流体直接不成立；require_source 为真时要求流体源方块；
 * fluid 为 null 表示任意流体，非空时按 id 或标签匹配，非源模式下额外接受同一流体族的流动变体。
 * 纯谓词：只读取上下文，不修改世界；不处理取反（取反由条件树的 inverted 节点承担）。
 */
public final class FluidPresentEvaluator {

    private FluidPresentEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 位置流体判定
    public static boolean test(FluidPresentCondition condition, ConditionContext context) {
        FluidState fluidState = context.level().getBlockState(context.pos()).getFluidState();
        if (fluidState.isEmpty()) {
            return false;
        }
        if (condition.requireSource() && !fluidState.isSource()) {
            return false;
        }
        if (condition.references().isEmpty()) {
            // 未指定流体：任意流体（源模式约束已在上面生效）
            return true;
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType());
        for (TaggedId reference : condition.references()) {
            if (matches(reference, fluidId, fluidState, condition, context)) return true;
        }
        return false;
    }

    private static boolean matches(TaggedId reference, ResourceLocation fluidId, FluidState fluidState,
                                   FluidPresentCondition condition, ConditionContext context) {
        if (reference.tag()) {
            return context.tags().fluidInTag(reference.id(), fluidId);
        }
        if (reference.id().equals(fluidId)) {
            return true;
        }
        if (condition.requireSource()) {
            return false;
        }
        // 非源模式：允许同一流体族的流动变体（如 minecraft:water 命中流动的水）
        return fluidState.getType().isSame(BuiltInRegistries.FLUID.get(reference.id()));
    }
}
