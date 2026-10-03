package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric 端缺少自然消失事件、实体状态事件与伤害事件，用最小注入补齐：
 * 自然到期 discard 拦截、三类环境伤害保护、真实致死分支请求转化、合并兼容。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void itemdespawntowhat$beforeNaturalExpiry(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level) || !RuleRuntimeHost.deferNaturalExpiry(level, item)) {
            item.discard();
        }
    }

    // 三类环境伤害保护：判定在原版扣血前，返回 false 与「免疫」语义一致，其余伤害原样放行（D2/D4）
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void itemdespawntowhat$protectFromEnvironment(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        if (DropStateStore.blocksEnvironmentalDamage(self, source, level.getGameTime())) {
            cir.setReturnValue(false);
        }
    }

    // 真实致死分支：原版在 discard 之前调用 ItemStack#onDestroyed(ItemEntity)，此时源栈与实体都在手（D19）
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"))
    private void itemdespawntowhat$onEnvironmentDestroy(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            RuleRuntimeHost.requestEnvironmentalConversion(level, self, source);
        }
    }

    // 合并兼容（D3）：永久标志不一致时禁止原版合并
    @Inject(method = "tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
    private void itemdespawntowhat$blockIncompatibleMerge(ItemEntity itemEntity, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!DropStateStore.get(self).permanentFlagsMatch(DropStateStore.get(itemEntity))) {
            ci.cancel();
        }
    }

    // 临时状态并集（D3）：双方临时到期刻取较晚者写回合并目标，源实体状态清除
    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private static void itemdespawntowhat$unionTemporaryState(ItemEntity destinationEntity, ItemStack destinationStack, ItemEntity originEntity, ItemStack originStack, CallbackInfo ci) {
        DropState merged = DropStateStore.get(destinationEntity).unionTemporary(DropStateStore.get(originEntity));
        DropStateStore.set(destinationEntity, merged);
        DropStateStore.clear(originEntity);
    }
}
