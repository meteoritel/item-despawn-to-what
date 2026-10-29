package com.meteorite.itemdespawntowhat.client.register;

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
import net.neoforged.neoforge.client.model.data.ModelData;

/** NeoForge 预览保留模型声明的多渲染层与流体扩展。 */
public final class NeoForgePreviewGeometry implements PreviewScenes.Geometry {
    /** 显示实体从载荷直接生成渲染快照；其他实体沿用加载后的状态。 */
    @Override public void prepareEntity(@NotNull Entity entity) {
        if (entity instanceof Display) {
            DisplayPreviewAccessor access = (DisplayPreviewAccessor) entity;
            access.previewSetRenderState(access.previewCreateRenderState());
            access.previewUpdateSubState(false, 0);
        }
    }

    /** 从独立模型占位数据取得层集合，逐层合并到同一个场景。 */
    @Override public void block(@NotNull BlockState state, @NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks, @NotNull PoseStack pose,
            @NotNull Function<RenderType, VertexConsumer> buffers, @NotNull RandomSource random) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var model = dispatcher.getBlockModel(state);
        ModelData data = model.getModelData(blocks, pos, state, ModelData.EMPTY);
        random.setSeed(state.getSeed(pos));
        for (RenderType layer : model.getRenderTypes(state, random, data))
            dispatcher.renderBatched(state, pos, blocks, pose, buffers.apply(layer), true, random, data, layer);
    }
    /** BlockRenderDispatcher 内部消费 NeoForge 流体扩展。 */
    @Override public void fluid(@NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks, @NotNull Function<RenderType, VertexConsumer> buffers,
            @NotNull BlockState state, @NotNull FluidState fluid) {
        Minecraft.getInstance().getBlockRenderer().renderLiquid(pos, blocks,
                buffers.apply(ItemBlockRenderTypes.getRenderLayer(fluid)), state, fluid);
    }
}
