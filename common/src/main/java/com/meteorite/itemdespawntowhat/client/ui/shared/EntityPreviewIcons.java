package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiEntityPreview;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
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
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/** 宿主的可见实体模型缓存和中性占位标记；上限 256，切服、语言或模型资源变化时失效。 */
public final class EntityPreviewIcons {
    private static final int MAX_ENTRIES = 256;
    // 没有可见渲染器（NoopRenderer 等）时的中性占位标记，与方块预览共用
    private static final UiIcon UNAVAILABLE = CatalogPreviewIcons.unavailableMarker(16, 16);
    private static final Map<String, UiIcon> ICONS = new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static Object world;
    private static Object language;
    private static Object models;
    // 上次模型探测所在的客户端 tick：同一 tick 内模型资源不可能变化，避免每帧查模型
    private static long probedTick = Long.MIN_VALUE;

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

    public static void clear() { CACHE.clear(); ICONS.clear(); world = null; language = null; models = null; probedTick = Long.MIN_VALUE; }

    public static UiIcon icon(String id, int age) {
        String key = id + "/" + age;
        UiIcon result = ICONS.computeIfAbsent(key, ignored -> new UiIcon.Rendered(18, 18, (graphics, box) -> draw(graphics, box, id, age)));
        while (ICONS.size() > MAX_ENTRIES) ICONS.remove(ICONS.keySet().iterator().next());
        return result;
    }

    // 返回资源代号，目录显示名索引也用同一代号失效。
    public static Object generation() {
        Minecraft minecraft = Minecraft.getInstance();
        Object nextWorld = minecraft.level;
        Object nextLanguage = Language.getInstance();
        if (world != nextWorld || language != nextLanguage) {
            // 世界或语言变化必须当帧生效，不能等下一个 tick
            clear();
            world = nextWorld;
            language = nextLanguage;
        }
        // tick 来源是客户端世界的游戏刻（ClientLevel#tickTime 每 tick +1）：暂停或 /tick 冻结时不前进，
        // 此时资源也不会重载，沿用上次探测结果即可；恢复后第一个 tick 会重新比对模型
        long tick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        // 没有客户端世界就没有 tick 可对齐，也画不出实体预览：沿用上次结论，等进入世界后重探
        if (tick == Long.MIN_VALUE || tick == probedTick) {
            return models;
        }
        Object nextModels = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(Blocks.STONE.defaultBlockState());
        probedTick = tick;
        if (models != nextModels) {
            CACHE.clear();
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
            if (!result.unavailable) result.bounds = UiEntityPreview.measure(result.entity);
            if (result.bounds == null) result.bounds = new UiModelBounds(0, result.center, 0,
                    result.width * 1.5F, result.height * 1.5F, result.width * 1.5F);
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
                if (preview.bounds != null && UiEntityPreview.render(graphics, box, preview.entity, angle, preview.bounds)) return;
                preview.unavailable = true;
            } catch (RuntimeException exception) { preview.unavailable = true; preview.entity = null; }
        }
        UNAVAILABLE.render(graphics, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2);
    }
}
