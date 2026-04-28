package com.meteorite.itemdespawntowhat.mixin;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.server.event.ItemConversionEvent;
import com.meteorite.itemdespawntowhat.server.event.ItemConversionState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements ItemConversionState {

    @Unique
    private static final String CONVERSION_TRACKED_KEY = Constants.MOD_ID + ":conversion_tracked";

    @Unique
    private static final String CHECK_TIMER_KEY = Constants.MOD_ID + ":conversion_check_timer";

    @Unique
    private static final String SELECTED_CONFIG_ID_KEY = Constants.MOD_ID + ":conversion_selected_config_id";

    @Unique
    private boolean itemdespawntowhat$tracked;

    @Unique
    private int itemdespawntowhat$checkTimer;

    @Unique
    private String itemdespawntowhat$selectedConfigId = "";

    @Unique
    private boolean itemdespawntowhat$conversionLocked;

    @Inject(method = "tick", at = @At("HEAD"))
    private void itemdespawntowhat$onTick(CallbackInfo ci) {
        ItemConversionEvent.tickTrackedItem((ItemEntity) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void itemdespawntowhat$writeConversionState(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(CONVERSION_TRACKED_KEY, itemdespawntowhat$tracked);
        tag.putInt(CHECK_TIMER_KEY, itemdespawntowhat$checkTimer);
        if (!itemdespawntowhat$selectedConfigId.isEmpty()) {
            tag.putString(SELECTED_CONFIG_ID_KEY, itemdespawntowhat$selectedConfigId);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void itemdespawntowhat$readConversionState(CompoundTag tag, CallbackInfo ci) {
        itemdespawntowhat$tracked = tag.getBoolean(CONVERSION_TRACKED_KEY);
        itemdespawntowhat$checkTimer = tag.getInt(CHECK_TIMER_KEY);
        itemdespawntowhat$selectedConfigId = tag.contains(SELECTED_CONFIG_ID_KEY, 8)
                ? tag.getString(SELECTED_CONFIG_ID_KEY)
                : "";
        itemdespawntowhat$conversionLocked = false;
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void itemdespawntowhat$restoreState(Entity entity, CallbackInfo ci) {
        if (entity instanceof ItemConversionState source) {
            itemdespawntowhat$copyStateFrom(source);
        }
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private static void itemdespawntowhat$mergeState(ItemEntity target, ItemStack targetStack, ItemEntity source, ItemStack sourceStack, CallbackInfo ci) {
        if (!(target instanceof ItemConversionState targetState) || !(source instanceof ItemConversionState sourceState)) {
            return;
        }

        if (sourceState.itemdespawntowhat$isTracked() && !targetState.itemdespawntowhat$isTracked()) {
            ((ItemEntityMixin) (Object) target).itemdespawntowhat$copyStateFrom(sourceState);
            return;
        }

        if (sourceState.itemdespawntowhat$isTracked()) {
            targetState.itemdespawntowhat$setTracked(true);
            targetState.itemdespawntowhat$setCheckTimer(Math.max(targetState.itemdespawntowhat$getCheckTimer(), sourceState.itemdespawntowhat$getCheckTimer()));
            if (targetState.itemdespawntowhat$getSelectedConfigId().isEmpty()) {
                targetState.itemdespawntowhat$setSelectedConfigId(sourceState.itemdespawntowhat$getSelectedConfigId());
            }
            targetState.itemdespawntowhat$setConversionLocked(targetState.itemdespawntowhat$isConversionLocked() || sourceState.itemdespawntowhat$isConversionLocked());
        }
    }

    @Unique
    private void itemdespawntowhat$copyStateFrom(ItemConversionState source) {
        itemdespawntowhat$tracked = source.itemdespawntowhat$isTracked();
        itemdespawntowhat$checkTimer = source.itemdespawntowhat$getCheckTimer();
        itemdespawntowhat$selectedConfigId = source.itemdespawntowhat$getSelectedConfigId();
        itemdespawntowhat$conversionLocked = source.itemdespawntowhat$isConversionLocked();
    }

    @Override
    public boolean itemdespawntowhat$isTracked() {
        return itemdespawntowhat$tracked;
    }

    @Override
    public void itemdespawntowhat$setTracked(boolean tracked) {
        itemdespawntowhat$tracked = tracked;
    }

    @Override
    public int itemdespawntowhat$getCheckTimer() {
        return itemdespawntowhat$checkTimer;
    }

    @Override
    public void itemdespawntowhat$setCheckTimer(int timer) {
        itemdespawntowhat$checkTimer = timer;
    }

    @Override
    public String itemdespawntowhat$getSelectedConfigId() {
        return itemdespawntowhat$selectedConfigId;
    }

    @Override
    public void itemdespawntowhat$setSelectedConfigId(String selectedConfigId) {
        itemdespawntowhat$selectedConfigId = selectedConfigId == null ? "" : selectedConfigId;
    }

    @Override
    public boolean itemdespawntowhat$isConversionLocked() {
        return itemdespawntowhat$conversionLocked;
    }

    @Override
    public void itemdespawntowhat$setConversionLocked(boolean locked) {
        itemdespawntowhat$conversionLocked = locked;
    }

}
