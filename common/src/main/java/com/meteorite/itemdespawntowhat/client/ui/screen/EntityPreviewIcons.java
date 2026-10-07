package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiEntityPreview;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/*** 宿主的可见实体模型缓存和屏障兜底；上限 256，切服、语言或模型资源变化时失效。 */
public final class EntityPreviewIcons {
    private static final int MAX_ENTRIES = 256;
    private static final UiIcon BARRIER = new UiIcon.Item(new ItemStack(Items.BARRIER));
    private static final Map<String, UiIcon> ICONS = new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static Object world;
    private static Object language;
    private static Object models;

    /*** 失败也缓存，避免缺模型实体在每一帧重复创建或抛错。 */
    private static final class Preview {
        private @Nullable Entity entity;
        private float width;
        private float height;
        private float center;
        private boolean unavailable;
    }

    private EntityPreviewIcons() { }

    public static void clear() { CACHE.clear(); ICONS.clear(); world = null; language = null; models = null; }

    public static UiIcon icon(String id, int age) {
        String key = id + "/" + age;
        UiIcon result = ICONS.computeIfAbsent(key, ignored -> new UiIcon.Rendered(18, 18, (graphics, box) -> draw(graphics, box, id, age)));
        while (ICONS.size() > MAX_ENTRIES) ICONS.remove(ICONS.keySet().iterator().next());
        return result;
    }

    // 返回资源代号，目录显示名索引也用同一代号失效。
    public static Object generation() {
        Minecraft minecraft = Minecraft.getInstance();
        Object nextModels = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(Blocks.STONE.defaultBlockState());
        if (world != minecraft.level || language != Language.getInstance() || models != nextModels) {
            CACHE.clear();
            world = minecraft.level;
            language = Language.getInstance();
            models = nextModels;
        }
        return models;
    }

    public static boolean unavailable(String id, int age) {
        generation();
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
        } catch (RuntimeException exception) { result.unavailable = true; result.entity = null; }
        return result;
    }

    private static void draw(GuiGraphics graphics, UiRect box, String id, int age) {
        generation();
        String key = id + "/" + age;
        Preview preview = CACHE.computeIfAbsent(key, ignored -> create(id, age));
        while (CACHE.size() > MAX_ENTRIES) CACHE.remove(CACHE.keySet().iterator().next());
        if (!preview.unavailable && preview.entity != null) {
            try {
                float angle = (Util.getMillis() % 10000) / 10000.0F * (float) (Math.PI * 2);
                if (UiEntityPreview.render(graphics, box, preview.entity, angle, preview.width, preview.height, preview.center)) return;
                preview.unavailable = true;
            } catch (RuntimeException exception) { preview.unavailable = true; preview.entity = null; }
        }
        BARRIER.render(graphics, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2);
    }
}
