package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.core.runtime.PlayerDeathDrops;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 玩家死亡附加掉落入口：仅在实体入世界前设置标记。 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Redirect(
            method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z")
    )
    private boolean itemdespawntowhat$lockDeathDrop(Level level, Entity entity) {
        if (entity instanceof ItemEntity item) { PlayerDeathDrops.mark((Entity) (Object) this, item); }
        return level.addFreshEntity(entity);
    }
}
