package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.server.event.DeathLootState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Redirect(
            method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z")
    )
    private boolean itemdespawntowhat$lockDeathDrop(Level level, Entity entity) {
        if (this instanceof DeathLootState deathLootState && deathLootState.itemdespawntowhat$isDeathLootActive() && entity instanceof ItemEntity itemEntity) {
            // Death drops stay locked for their whole lifetime.
            itemEntity.addTag(Constants.CHECK_LOCK_TAG);
        }
        return level.addFreshEntity(entity);
    }
}
