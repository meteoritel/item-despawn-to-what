package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/*** 流体使用真实静态材质与方块染色绘制立体方块；水、岩浆不依赖不可见的方块模型。 */
public final class FluidPreviewIcons {
    private static final int ICON_SIZE = 18;
    private static final int FACE_SIZE = 16;
    private static final int COLOR_CHANNEL_MASK = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final float PITCH = (float) Math.toRadians(22);
    private static final float YAW = (float) Math.toRadians(45);
    private static final float QUARTER_TURN = (float) (Math.PI / 2);
    private static final float DEPTH_SCALE = 0.1F;
    private static final float FRONT_SHADE = 0.8F;
    private static final float SIDE_SHADE = 0.6F;
    private static final Quaternionf VIEW_ROTATION = new Quaternionf().rotationX(PITCH).rotateY(YAW);
    private static final Quaternionf SIDE_ROTATION = new Quaternionf().rotationY(-QUARTER_TURN);
    private static final Quaternionf FRONT_ROTATION = new Quaternionf();
    private static final Quaternionf TOP_ROTATION = new Quaternionf().rotationX(-QUARTER_TURN);
    private static final UiModelBounds BOUNDS = new UiModelBounds(FACE_SIZE / 2F, FACE_SIZE / 2F,
            FACE_SIZE / 2F, FACE_SIZE, FACE_SIZE, FACE_SIZE);

    private FluidPreviewIcons() { }

    // 未知引用仍由校验处理；选择目录只过滤实际注册的空流体。
    public static boolean isEmpty(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.FLUID.getOptional(key)
                .map(fluid -> fluid.defaultFluidState().isEmpty()).orElse(false);
    }

    public static @Nullable Component label(ResourceLocation id) {
        return BuiltInRegistries.FLUID.getOptional(id)
                .map(fluid -> {
                    var state = fluid.defaultFluidState();
                    Component name = state.createLegacyBlock().getBlock().getName();
                    return state.isSource() ? name : Component.translatable(
                            "gui.itemdespawntowhat.edit.fluid.flowing", name);
                }).orElse(null);
    }

    public static @Nullable UiIcon icon(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        var fluid = key == null ? null : BuiltInRegistries.FLUID.getOptional(key).orElse(null);
        if (fluid == null || fluid.defaultFluidState().isEmpty()) return null;
        BlockState state = fluid.defaultFluidState().createLegacyBlock();
        return new UiIcon.Rendered(ICON_SIZE, ICON_SIZE, (graphics, box) -> render(graphics, box, state));
    }

    // 每帧从模型图集取材质，资源重载不会留下旧 sprite；注册表与模型查询均为直接索引。
    private static void render(GuiGraphics graphics, UiRect box, BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureAtlasSprite sprite = stillTexture(minecraft, state);
        int tint = minecraft.getBlockColors().getColor(state, null, null, 0);
        float scale = BOUNDS.scale(box, PITCH);
        UiRenderLayers.draw(graphics, UiRenderLayers.ICON, () -> {
            graphics.pose().translate(box.x() + box.width() / 2F, box.y() + box.height() / 2F, 0);
            graphics.pose().last().pose().scale(1, 1, DEPTH_SCALE);
            graphics.pose().scale(scale, scale, scale);
            graphics.pose().mulPose(VIEW_ROTATION);
            graphics.pose().translate(-BOUNDS.centerX(), -BOUNDS.centerY(), -BOUNDS.centerZ());
            face(graphics, sprite, tint, SIDE_SHADE, FACE_SIZE, 0, SIDE_ROTATION);
            face(graphics, sprite, tint, FRONT_SHADE, 0, 0, FRONT_ROTATION);
            face(graphics, sprite, tint, 1, 0, FACE_SIZE, TOP_ROTATION);
        });
    }

    // NeoForge 将无 ModelData 的原版接口标记为弃用；静态图标使用两端共用的默认材质接口。
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private static TextureAtlasSprite stillTexture(Minecraft minecraft, BlockState state) {
        return minecraft.getBlockRenderer().getBlockModel(state).getParticleIcon();
    }

    private static void face(GuiGraphics graphics, TextureAtlasSprite sprite, int tint, float shade,
                             int x, int z, Quaternionf rotation) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, 0, z);
            graphics.pose().mulPose(rotation);
            float red = (tint >> RED_SHIFT & COLOR_CHANNEL_MASK) / (float) COLOR_CHANNEL_MASK;
            float green = (tint >> GREEN_SHIFT & COLOR_CHANNEL_MASK) / (float) COLOR_CHANNEL_MASK;
            float blue = (tint & COLOR_CHANNEL_MASK) / (float) COLOR_CHANNEL_MASK;
            graphics.blit(0, 0, 0, FACE_SIZE, FACE_SIZE, sprite, red * shade, green * shade, blue * shade, 1);
        } finally {
            graphics.pose().popPose();
        }
    }
}
