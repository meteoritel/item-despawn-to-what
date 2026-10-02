package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.core.runtime.PlayerDeathDrops;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 玩家背包死亡掉落入口；返回给 ServerPlayer 入世界前标记，普通丢物品不受影响。 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void itemdespawntowhat$markDeathDrop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName,
                                               CallbackInfoReturnable<ItemEntity> callback) {
        ItemEntity item = callback.getReturnValue();
        if (item != null) { PlayerDeathDrops.mark((Player) (Object) this, item); }
    }
}
