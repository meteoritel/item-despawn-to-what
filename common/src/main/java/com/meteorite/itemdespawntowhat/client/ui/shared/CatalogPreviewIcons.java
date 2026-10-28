package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiPreviewCache;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 目录条目图标分发：物品、方块、实体、流体与标签引用共用一个入口，其余目录类型回落文字槽。 */
public final class CatalogPreviewIcons {

    // 占位标记的边长上限（与图标槽一致）
    private static final int MARKER_MAX_SIZE = 16;
    // 图标缓存上限：与实体/方块预览缓存同一口径，超出后淘汰最久未用的项
    private static final int MAX_CACHED_ICONS = 256;
    // 物品图标按「类型 + 规范化注册名」复用：目录网格与配方列表共用同一实例，ItemStack 只建一次
    private static final UiPreviewCache<IconKey, Optional<UiIcon>> ICONS = new UiPreviewCache<>(MAX_CACHED_ICONS);
    // 占位标记取宿主灰阶色，不引入状态色
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    /** 物品图标缓存键；ResourceLocation 自带值语义，两种 id 写法先规范成同一注册名。 */
    private record IconKey(RuleCatalogType type, ResourceLocation id) { }

    private CatalogPreviewIcons() { }

    /** 清空物品图标缓存；物品注册表在运行期不变，资源重载后由宿主按缓存生命周期调用。 */
    public static void clear() {
        ICONS.clear();
    }

    /**
     * 无法静态预览时的中性占位标记：等宽描边方框加居中问号。
     *
     * <p>不使用屏障方块等自带含义的原版图标，避免玩家把「这个方块不可破坏」当成预览结果；
     * 具体原因由宿主在悬停提示中给出（见 {@code gui.itemdespawntowhat.edit.preview.unavailable}）。</p>
     *
     * @param width  图标声明宽度
     * @param height 图标声明高度
     * @return 在给定尺寸内居中绘制占位标记的图标
     */
    public static UiIcon unavailableMarker(int width, int height) {
        return new UiIcon.Rendered(width, height, (graphics, box) -> {
            int size = Math.min(MARKER_MAX_SIZE, Math.min(box.width(), box.height()));
            if (size <= 0) {
                return;
            }
            int x = box.x() + (box.width() - size) / 2;
            int y = box.y() + (box.height() - size) / 2;
            drawMarker(graphics, x, y, size);
        });
    }

    // 1px 描边方框 + 居中问号：不依赖主题的填充细节，任何主题下都读得出「无预览」
    private static void drawMarker(GuiGraphics graphics, int x, int y, int size) {
        int border = THEME.palette().textDisabled();
        graphics.fill(x, y, x + size, y + 1, border);
        graphics.fill(x, y + size - 1, x + size, y + size, border);
        graphics.fill(x, y, x + 1, y + size, border);
        graphics.fill(x + size - 1, y, x + size, y + size, border);
        Font font = Minecraft.getInstance().font;
        String mark = "?";
        graphics.drawString(font, mark, x + (size - font.width(mark)) / 2, y + (size - font.lineHeight) / 2 + 1,
                THEME.palette().textSecondary(), false);
    }

    // 物品、方块、实体和流体图标；其他目录类型回落文字槽。
    public static @Nullable UiIcon iconFor(@Nullable RuleCatalogType type, @Nullable String id) {
        if (type == null || id == null || id.isBlank()) {
            return null;
        }
        if (id.startsWith("#") && (type == RuleCatalogType.ITEM || type == RuleCatalogType.BLOCK
                || type == RuleCatalogType.ENTITY || type == RuleCatalogType.FLUID))
            return TagPreviewIcons.resolve(type, id).icon();
        String normalized = id.startsWith("#") ? id.substring(1) : id;
        ResourceLocation key = ResourceLocation.tryParse(
                normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
        if (key == null) {
            return null;
        }
        if (type == RuleCatalogType.ENTITY && !id.startsWith("#")) return EntityPreviewIcons.icon(key.toString(), 0);
        if (type == RuleCatalogType.BLOCK && !id.startsWith("#")) return BlockPreviewIcons.icon(key.toString());
        if (type == RuleCatalogType.FLUID && !id.startsWith("#")) return FluidPreviewIcons.icon(key.toString());
        if (type == RuleCatalogType.ITEM) return itemIcon(key);
        // 方块/实体/流体已在上面走各自缓存；其余目录类型没有图标槽，回落文字展示
        return null;
    }

    // 物品图标有界复用：键是注册名而不是 ItemStack，未注册物品以 Optional.empty 缓存，避免每帧重查注册表
    private static @Nullable UiIcon itemIcon(ResourceLocation key) {
        ICONS.invalidate(PreviewResources.generation());
        IconKey cacheKey = new IconKey(RuleCatalogType.ITEM, key);
        return ICONS.getOrLoad(cacheKey, ignored -> {
            ItemStack stack = stackOf(BuiltInRegistries.ITEM.getOptional(key).orElse(null));
            return stack.isEmpty() ? Optional.empty() : Optional.of(new UiIcon.Item(stack));
        }).orElse(null);
    }

    private static ItemStack stackOf(@Nullable Item item) {
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
