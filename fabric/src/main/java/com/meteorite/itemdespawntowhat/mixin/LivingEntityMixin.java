package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.server.event.DeathLootState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements DeathLootState {

    @Unique
    private boolean itemdespawntowhat$deathLootActive;

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void itemdespawntowhat$startDeathLoot(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        itemdespawntowhat$deathLootActive = true;
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void itemdespawntowhat$finishDeathLoot(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        itemdespawntowhat$deathLootActive = false;
    }

    @Override
    public boolean itemdespawntowhat$isDeathLootActive() {
        return itemdespawntowhat$deathLootActive;
    }

    @Override
    public void itemdespawntowhat$setDeathLootActive(boolean active) {
        itemdespawntowhat$deathLootActive = active;
    }
}
