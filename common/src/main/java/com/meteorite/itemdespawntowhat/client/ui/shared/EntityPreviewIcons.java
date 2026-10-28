package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiPreviewCache;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiEntityPreview;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** 宿主的可见实体模型缓存和中性占位标记；上限 256，切服、语言或模型资源变化时失效。 */
public final class EntityPreviewIcons {
    // 没有可见渲染器（NoopRenderer 等）时的中性占位标记，与方块预览共用
    private static final UiIcon UNAVAILABLE = CatalogPreviewIcons.unavailableMarker(16, 16);
    private static final UiPreviewCache<String, UiIcon> ICONS = new UiPreviewCache<>(256);
    private static final UiPreviewCache<String, Preview> CACHE = new UiPreviewCache<>(256);

    /** 失败也缓存，避免缺模型实体在每一帧重复创建或抛错。 */
    private static final class Preview {
        private @Nullable Entity entity;
        private float width;
        private float height;
        private float center;
        private boolean unavailable;
        private @Nullable UiModelBounds bounds;
    }

    private EntityPreviewIcons() { }

    /** 清空实体测量和图标 wrapper，释放本类持有的世界与实体引用。 */
    public static void clear() { CACHE.clear(); ICONS.clear(); }

    public static UiIcon icon(String id, int age) {
        String key = id + "/" + age;
        return ICONS.getOrLoad(key, ignored -> new UiIcon.Rendered(18, 18, (graphics, box) -> draw(graphics, box, id, age)));
    }

    public static boolean unavailable(String id, int age) {
        CACHE.invalidate(PreviewResources.generation());
        Preview preview = CACHE.get(id + "/" + age);
        return preview != null && preview.unavailable;
    }

    // 模组 renderer 可能违反原版非空契约，故仍保留防御检查。
    @SuppressWarnings("ConstantValue")
    private static Preview create(String id, int age) {
        Preview result = new Preview();
        try {
            ResourceLocation key = ResourceLocation.tryParse(id);
            Minecraft minecraft = Minecraft.getInstance();
            if (key == null || minecraft.level == null) { result.unavailable = true; return result; }
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
            result.entity = type == null ? null : type.create(minecraft.level);
            if (result.entity == null) { result.unavailable = true; return result; }
            if (result.entity instanceof AgeableMob mob) mob.setAge(age);
            var renderer = minecraft.getEntityRenderDispatcher().getRenderer(result.entity);
            String rendererName = renderer == null ? "" : renderer.getClass().getSimpleName();
            result.unavailable = renderer == null || rendererName.equals("NoopRenderer")
                    || rendererName.equals("InteractionRenderer") || rendererName.equals("MarkerRenderer")
                    || id.equals("minecraft:falling_block") || id.equals("minecraft:item")
                    || id.equals("minecraft:area_effect_cloud") || id.equals("minecraft:item_display")
                    || id.equals("minecraft:block_display") || id.equals("minecraft:text_display");
            result.width = Math.max(0.35F, result.entity.getBbWidth());
            result.height = Math.max(0.35F, result.entity.getBbHeight());
            result.center = result.entity.getBbHeight() / 2;
            // 原版模型超出碰撞框的已知例外；模组实体仍需人工检查可视尺寸。
            if (id.equals("minecraft:ender_dragon")) { result.width = 16; result.height = 8; result.center = 2; }
            if (id.equals("minecraft:ghast")) { result.height = 6; result.center = 1; }
            if (id.equals("minecraft:squid") || id.equals("minecraft:glow_squid")) { result.height = 2; result.center = 0.2F; }
            if (!result.unavailable) result.bounds = UiEntityPreview.measure(result.entity);
            if (result.bounds == null) result.bounds = new UiModelBounds(0, result.center, 0,
                    result.width * 1.5F, result.height * 1.5F, result.width * 1.5F);
        } catch (RuntimeException exception) { result.unavailable = true; result.entity = null; }
        return result;
    }

    private static void draw(GuiGraphics graphics, UiRect box, String id, int age) {
        CACHE.invalidate(PreviewResources.generation());
        String key = id + "/" + age;
        Preview preview = CACHE.getOrLoad(key, ignored -> create(id, age));
        if (!preview.unavailable && preview.entity != null) {
            try {
                float angle = (Util.getMillis() % 10000) / 10000.0F * (float) (Math.PI * 2);
                if (preview.bounds != null && UiEntityPreview.render(graphics, box, preview.entity, angle, preview.bounds)) return;
                preview.unavailable = true;
            } catch (RuntimeException exception) { preview.unavailable = true; preview.entity = null; }
        }
        UNAVAILABLE.render(graphics, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2);
    }
}
