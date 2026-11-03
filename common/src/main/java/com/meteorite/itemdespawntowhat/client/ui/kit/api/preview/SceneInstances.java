package com.meteorite.itemdespawntowhat.client.ui.kit.api.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiModelBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;


/** 实例只在渲染线程创建和访问，不加入世界、不推进 tick。 */
final class SceneInstances {
    final List<BlockEntity> blocks = new ArrayList<>();
    final List<Entity> entities = new ArrayList<>();

    static boolean needsLevel(PreviewSubject subject, @Nullable PreviewStructure structure) {
        return subject instanceof PreviewSubject.Entity || structure != null && (!structure.entities().isEmpty()
                || structure.blocks().stream().anyMatch(cell -> structure.palette().get(cell.state()).hasBlockEntity()));
    }

    void prepare(PreviewSubject subject, @Nullable PreviewStructure structure, PreviewScenes.Geometry geometry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (subject instanceof PreviewSubject.Entity actor) addEntity(actor.data(), Vec3.ZERO);
        if (structure == null) { entities.forEach(geometry::prepareEntity); return; }
        for (PreviewStructure.Cell cell : structure.blocks()) {
            var state = structure.palette().get(cell.state());
            if (state.getBlock() instanceof EntityBlock factory) {
                BlockEntity instance = factory.newBlockEntity(cell.pos(), state);
                if (instance == null) continue;
                var level = Objects.requireNonNull(minecraft.level, "方块实体需要客户端世界");
                instance.setLevel(level);
                CompoundTag data = cell.data();
                if (data != null) instance.loadWithComponents(data, level.registryAccess());
                blocks.add(instance);
            }
        }
        for (PreviewStructure.Actor actor : structure.entities()) addEntity(actor.data(), actor.pos());
        entities.forEach(geometry::prepareEntity);
    }

    private void addEntity(CompoundTag data, Vec3 pos) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = EntityType.create(data, Objects.requireNonNull(minecraft.level, "实体需要客户端世界"))
                .orElseThrow(() -> new IllegalArgumentException("实体类型不可创建: " + data.getString("id")));
        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
        entity.yRotO = entity.getYRot(); entity.xRotO = entity.getXRot();
        entity.xOld = pos.x; entity.yOld = pos.y; entity.zOld = pos.z;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = entity.getYRot();
            living.yHeadRot = living.yHeadRotO = entity.getYRot();
        }
        entities.add(entity);
        for (Tag passenger : data.getList("Passengers", Tag.TAG_COMPOUND))
            addEntity((CompoundTag) passenger, pos);
    }

    @Nullable UiModelBounds measure() {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf camera = new Quaternionf(dispatcher.cameraOrientation());
        try {
            dispatcher.overrideCameraOrientation(new Quaternionf());
            return UiModelBounds.measure(this::draw);
        } finally { dispatcher.overrideCameraOrientation(camera); }
    }

    void draw(PoseStack pose, MultiBufferSource buffers) {
        for (BlockEntity instance : blocks) {
            pose.pushPose();
            try {
                BlockPos p = instance.getBlockPos();
                pose.translate(p.getX(), p.getY(), p.getZ());
                drawBlock(instance, pose, buffers);
            } finally { pose.popPose(); }
        }
        for (Entity instance : entities) {
            pose.pushPose();
            try {
                pose.translate(instance.getX(), instance.getY(), instance.getZ());
                drawEntity(instance, pose, buffers);
            } finally { pose.popPose(); }
        }
    }

    private static <T extends BlockEntity> void drawBlock(T instance, PoseStack pose, MultiBufferSource buffers) {
        BlockEntityRenderer<T> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(instance);
        if (renderer != null) renderer.render(instance, 0, pose, buffers, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static <T extends Entity> void drawEntity(T entity, PoseStack pose, MultiBufferSource buffers) {
        EntityRenderer<? super T> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        Vec3 offset = renderer.getRenderOffset(entity, 0);
        pose.translate(offset.x, offset.y, offset.z);
        renderer.render(entity, entity.getYRot(), 0, pose, buffers, 15728880);
    }

    static @Nullable UiModelBounds union(@Nullable UiModelBounds a, @Nullable UiModelBounds b) {
        if (a == null) return b;
        if (b == null) return a;
        float x0 = Math.min(a.centerX() - a.width() / 2, b.centerX() - b.width() / 2);
        float y0 = Math.min(a.centerY() - a.height() / 2, b.centerY() - b.height() / 2);
        float z0 = Math.min(a.centerZ() - a.depth() / 2, b.centerZ() - b.depth() / 2);
        float x1 = Math.max(a.centerX() + a.width() / 2, b.centerX() + b.width() / 2);
        float y1 = Math.max(a.centerY() + a.height() / 2, b.centerY() + b.height() / 2);
        float z1 = Math.max(a.centerZ() + a.depth() / 2, b.centerZ() + b.depth() / 2);
        return new UiModelBounds((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, x1 - x0, y1 - y0, z1 - z0);
    }

}
