package com.meteorite.itemdespawntowhat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.meteorite.itemdespawntowhat.runtime.FabricLifespanReference;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 只观察自然寿命比较值；较低优先级尽量接在其它模组的默认优先级修改之后。 */
@Mixin(value = ItemEntity.class, priority = 900)
public abstract class ItemLifespanReferenceMixin extends Entity {
    protected ItemLifespanReferenceMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    // 原版 tick 中唯一的自然消失阈值；目标被其它模组整体替换时允许不命中。
    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=6000"), require = 0, allow = 1)
    private int itemdespawntowhat$observeLifespan(int original) {
        if (!level().isClientSide && FabricLifespanReference.ticks() == 0) {
            FabricLifespanReference.observe(original);
        }
        return original;
    }
}
