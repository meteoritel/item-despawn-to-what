package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.core.runtime.PlayerDeathDrops;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 玩家死亡附加掉落入口（仅在实体入世界前设置标记），以及三类环境伤害保护的原版门。 */
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

    // 三类环境伤害保护：与 NeoForge 的 EntityInvulnerabilityCheckEvent 同一点位。
    // ItemEntity 与 LivingEntity 的 hurt 都先查这道门，因此掉落物与生物产物共用一套判定。
    @Inject(method = "isInvulnerableTo(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
    private void itemdespawntowhat$protectProductFromEnvironment(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level() instanceof ServerLevel level
                && DropStateStore.blocksEnvironmentalDamage(self, source, level.getGameTime())) {
            cir.setReturnValue(true);
        }
    }
}
