package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiBlockPreview;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 方块预览的宿主缓存；只对可见模型测量一次，资源或世界切换时失效。 */
public final class BlockPreviewIcons {
    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    // 图标 wrapper 有界复用：同一注册名在多行预览与多次重装间共享实例，上限与其它图标缓存一致
    private static final Map<String, UiIcon> ICONS = new LinkedHashMap<>(32, 0.75F, true);
    private static Object generation;
    private static Object world;
    // 没有可见模型（告示牌等由 BlockEntityRenderer 绘制的方块）时的中性占位标记
    private static final UiIcon UNAVAILABLE = CatalogPreviewIcons.unavailableMarker(16, 16);

    /** 保存默认方块状态和实际顶点范围，失败不在每一帧重复探测。 */
    private static final class Preview {
        private final BlockState state;
        private @Nullable UiModelBounds bounds;
        private boolean measured;
        private boolean failed;
        private Preview(BlockState state) { this.state = state; }
    }

    private BlockPreviewIcons() { }
    public static void clear() { CACHE.clear(); ICONS.clear(); generation = null; world = null; }

    public static UiIcon icon(String id) {
        String key = normalized(id);
        UiIcon icon = ICONS.computeIfAbsent(key,
                name -> new UiIcon.Rendered(18, 18, (graphics, box) -> draw(graphics, box, name)));
        while (ICONS.size() > 256) ICONS.remove(ICONS.keySet().iterator().next());
        return icon;
    }

    // wrapper 复用不影响失效：每次绘制都重新取资源代号，世界或资源切换时清空测量缓存
    private static void draw(GuiGraphics graphics, UiRect box, String id) {
        Object resources = EntityPreviewIcons.generation();
        Object nextWorld = Minecraft.getInstance().level;
        if (generation != resources || world != nextWorld) {
            CACHE.clear();
            generation = resources;
            world = nextWorld;
        }
        Preview preview = CACHE.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            var block = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
            return block == null ? null : new Preview(block.defaultBlockState());
        });
        while (CACHE.size() > 256) CACHE.remove(CACHE.keySet().iterator().next());
        if (preview != null && !preview.failed) {
            if (!preview.measured) {
                preview.bounds = UiBlockPreview.measure(preview.state);
                preview.measured = true;
            }
            if (preview.bounds != null && UiBlockPreview.render(graphics, box, preview.state,
                    (Util.getMillis() % 10000) / 10000F * (float) (Math.PI * 2), preview.bounds)) {
                return;
            }
            preview.failed = true;
        }
        UNAVAILABLE.render(graphics, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2);
    }

    /**
     * 该方块是否无法静态预览（没有可见模型，或模型渲染失败）。
     *
     * <p>目录悬停提示据此补一条「预览不可用」说明；结论只在图标绘制过一次后有效，
     * 缓存随资源与世界切换失效，与本类的绘制缓存同源。</p>
     *
     * @param id 方块注册名（带或不带命名空间均可）
     * @return true 表示当前给不出静态预览
     */
    public static boolean unavailable(String id) {
        Preview preview = CACHE.get(normalized(id));
        return preview != null && (preview.failed || preview.measured && preview.bounds == null);
    }

    // 缓存键统一成带命名空间的注册名，与目录条目 id 的两种写法对齐
    private static String normalized(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? id : key.toString();
    }
}
