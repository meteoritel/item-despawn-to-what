package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fabric 缺少自然消失事件，只拦截 tick 中第二处、自然寿命到期的 discard 调用。 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void itemdespawntowhat$beforeNaturalExpiry(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level) || !RuleRuntimeHost.deferNaturalExpiry(level, item)) {
            item.discard();
        }
    }
}
