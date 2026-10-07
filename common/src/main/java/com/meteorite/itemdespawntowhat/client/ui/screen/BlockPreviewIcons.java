package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiBlockPreview;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/*** 方块预览的宿主缓存；只对可见模型测量一次，资源或世界切换时失效。 */
public final class BlockPreviewIcons {
    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static Object generation;
    private static Object world;
    private static final UiIcon FALLBACK = new UiIcon.Item(new ItemStack(Items.BARRIER));

    /*** 保存默认方块状态和实际顶点范围，失败不在每一帧重复探测。 */
    private static final class Preview {
        private final BlockState state;
        private @Nullable UiModelBounds bounds;
        private boolean measured;
        private boolean failed;
        private Preview(BlockState state) { this.state = state; }
    }

    private BlockPreviewIcons() { }
    public static void clear() { CACHE.clear(); generation = null; world = null; }

    public static UiIcon icon(String id) {
        return new UiIcon.Rendered(18, 18, (graphics, box) -> {
            Object resources = EntityPreviewIcons.generation();
            Object nextWorld = net.minecraft.client.Minecraft.getInstance().level;
            if (generation != resources || world != nextWorld) { CACHE.clear(); generation = resources; world = nextWorld; }
            Preview preview = CACHE.computeIfAbsent(id, key -> {
                ResourceLocation location = ResourceLocation.tryParse(key);
                var block = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
                return block == null ? null : new Preview(block.defaultBlockState());
            });
            while (CACHE.size() > 256) CACHE.remove(CACHE.keySet().iterator().next());
            if (preview != null && !preview.failed) {
                if (!preview.measured) { preview.bounds = UiBlockPreview.measure(preview.state); preview.measured = true; }
                if (preview.bounds != null && UiBlockPreview.render(graphics, box, preview.state,
                        (Util.getMillis() % 10000) / 10000F * (float) (Math.PI * 2), preview.bounds)) return;
                preview.failed = true;
            }
            FALLBACK.render(graphics, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2);
        });
    }
}
