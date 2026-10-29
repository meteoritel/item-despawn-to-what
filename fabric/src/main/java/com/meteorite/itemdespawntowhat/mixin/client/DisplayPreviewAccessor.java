package com.meteorite.itemdespawntowhat.mixin.client;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 仅由独立预览实例调用，直接装配显示快照，保持实体不 tick。 */
@Mixin(Display.class)
public interface DisplayPreviewAccessor {
    /** 创建当前 NBT 对应的静态变换与 billboard 状态。 */
    @Invoker("createFreshRenderState")
    Display.RenderState previewCreateRenderState();

    /** 安装渲染快照。 */
    @Accessor("renderState")
    void previewSetRenderState(Display.RenderState state);

    /** 装配 block/item/text 专属的渲染快照。 */
    @Invoker("updateRenderSubState")
    void previewUpdateSubState(boolean interpolate, float progress);

}
