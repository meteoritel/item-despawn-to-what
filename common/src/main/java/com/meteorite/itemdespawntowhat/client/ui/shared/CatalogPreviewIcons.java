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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 目录条目图标分发：物品、方块、实体、流体与标签引用共用一个入口，其余目录类型回落文字槽。 */
public final class CatalogPreviewIcons {

    // 占位标记的边长上限（与图标槽一致）
    private static final int MARKER_MAX_SIZE = 16;
    // 状态效果图标按原版物品栏效果图标的边长绘制
    private static final int EFFECT_ICON_SIZE = 18;
    // 图标缓存上限：与统一场景请求容量同一口径，超出后淘汰最久未用的项
    private static final int MAX_CACHED_ICONS = 256;
    // 状态效果贴图按注册名缓存；物品与场景主体的缓存由 PreviewIcons 按完整 PreviewSubject 持有
    private static final UiPreviewCache<IconKey, Optional<UiIcon>> ICONS = new UiPreviewCache<>(MAX_CACHED_ICONS);
    // 占位标记取宿主灰阶色，不引入状态色
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    private record IconKey(RuleCatalogType type, ResourceLocation id) { }

    private CatalogPreviewIcons() { }

    /** 清空状态效果贴图缓存；物品主体缓存由 PreviewIcons 管理。 */
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

    /** 准备中的中性槽位，与不可用状态的问号标记区分。 */
    public static UiIcon preparingMarker(int width, int height) {
        return new UiIcon.Rendered(width, height, (graphics, box) -> {
            int size = Math.min(MARKER_MAX_SIZE, Math.min(box.width(), box.height()));
            int x = box.x() + (box.width() - size) / 2;
            int y = box.y() + (box.height() - size) / 2;
            int color = THEME.palette().controlFill();
            int inset = Math.min(2, size / 3);
            graphics.fill(x + inset, y + inset, x + size - inset, y + size - inset, color);
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
        if (id.startsWith("#")) {
            return CatalogTypePresentation.of(type).supportsTags()
                    ? TagPreviewIcons.resolve(type, id).icon() : null;
        }
        return CatalogTypePresentation.of(type).icon(id);
    }

    // 状态效果图标有界复用：贴图来自 mob_effects 图集，缺项的第三方效果以 Optional.empty 缓存
    static @Nullable UiIcon mobEffectIcon(ResourceLocation key) {
        ICONS.invalidate(PreviewResources.generation());
        IconKey cacheKey = new IconKey(RuleCatalogType.MOB_EFFECT, key);
        return ICONS.getOrLoad(cacheKey, ignored -> Optional.ofNullable(effectSprite(key))).orElse(null);
    }

    // 效果贴图取自 mob_effects 图集；注册表里没有该效果时回落文字，缺贴图的第三方效果由图集的 missingno 兜底
    private static @Nullable UiIcon effectSprite(ResourceLocation key) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(key).orElse(null);
        if (effect == null) {
            return null;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures()
                .get(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        return new UiIcon.Rendered(EFFECT_ICON_SIZE, EFFECT_ICON_SIZE,
                (graphics, box) -> graphics.blit(box.x(), box.y(), 0, box.width(), box.height(), sprite));
    }

    // 物品场景按完整 ItemStack 身份缓存，组件变化不会被注册名级 wrapper 合并
    static @Nullable UiIcon itemIcon(ResourceLocation key) {
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        ItemStack stack = stackOf(item);
        return stack.isEmpty() ? null : PreviewIcons.item(stack);
    }

    private static ItemStack stackOf(@Nullable Item item) {
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    static @Nullable UiIcon blockIcon(ResourceLocation id) {
        return PreviewIcons.block(id.toString());
    }

    static @Nullable UiIcon entityIcon(ResourceLocation id) {
        return PreviewIcons.entity(id.toString(), 0);
    }

    static @Nullable UiIcon fluidIcon(ResourceLocation id) {
        return PreviewIcons.fluid(id.toString());
    }

}
