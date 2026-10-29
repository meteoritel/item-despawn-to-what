package com.meteorite.itemdespawntowhat.client;

import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewScenes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import com.meteorite.itemdespawntowhat.mixin.client.DisplayPreviewAccessor;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Fabric 预览复用原版区块模型和液体渲染契约。 */
public final class FabricPreviewGeometry implements PreviewScenes.Geometry {
    /** 显示实体从载荷直接生成渲染快照；其他实体沿用加载后的状态。 */
    @Override public void prepareEntity(@NotNull Entity entity) {
        if (entity instanceof Display) {
            DisplayPreviewAccessor access = (DisplayPreviewAccessor) entity;
            access.previewSetRenderState(access.previewCreateRenderState());
            access.previewUpdateSubState(false, 0);
        }
    }

    /** 按原版方块层启用邻面剔除。 */
    @Override public void block(@NotNull BlockState state, @NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks, @NotNull PoseStack pose,
            @NotNull Function<RenderType, VertexConsumer> buffers, @NotNull RandomSource random) {
        Minecraft.getInstance().getBlockRenderer().renderBatched(state, pos, blocks, pose,
                buffers.apply(ItemBlockRenderTypes.getChunkRenderType(state)), true, random);
    }
    /** 使用原版液体模型和对应渲染层。 */
    @Override public void fluid(@NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks, @NotNull Function<RenderType, VertexConsumer> buffers,
            @NotNull BlockState state, @NotNull FluidState fluid) {
        Minecraft.getInstance().getBlockRenderer().renderLiquid(pos, blocks,
                buffers.apply(ItemBlockRenderTypes.getRenderLayer(fluid)), state, fluid);
    }
}
