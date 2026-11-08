package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;

/** 可测量的原版物品或贴图图块；纹理尺寸显式提供，避免隐含 256 像素图集。 */
public sealed interface UiIcon permits UiIcon.Item, UiIcon.Sprite, UiIcon.Rendered, UiIcon.Themed {
    int width();
    int height();
    void render(GuiGraphics graphics, int x, int y);
    /** 按使用位置提供主题；真实物品和贴图保留自身视觉语义。 */
    default void render(GuiGraphics graphics, int x, int y, UiStyleSource theme) { render(graphics, x, y); }

    /** 在目标框内等比居中绘制；自绘内容按目标尺寸重绘，空框跳过。 */
    default void renderScaled(GuiGraphics graphics, UiRect box, UiStyleSource theme) {
        if (box.width() == 0 || box.height() == 0) return;
        UiRect target = contained(this, box);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(target.x(), target.y(), 0);
            graphics.pose().scale((float) target.width() / width(), (float) target.height() / height(), 1);
            render(graphics, 0, 0, theme);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static UiRect contained(UiIcon icon, UiRect box) {
        double scale = Math.min(box.width() / (double) icon.width(), box.height() / (double) icon.height());
        int width = Math.max(1, (int) Math.round(icon.width() * scale));
        int height = Math.max(1, (int) Math.round(icon.height() * scale));
        return new UiRect(box.x() + (box.width() - width) / 2, box.y() + (box.height() - height) / 2, width, height);
    }

    /** 缓存内容与主题分离的图标；来源在绘制时传入，不捕获屏幕。 */
    record Themed(int width, int height, ThemePainter painter, Runnable preparation) implements UiIcon {
        /** 保持可测量尺寸与有效绘制回调。 */
        public Themed {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid icon size");
            Objects.requireNonNull(painter);
            Objects.requireNonNull(preparation);
        }
        @Override public void prepare() { preparation.run(); }
        @Override public void render(GuiGraphics graphics, int x, int y) { render(graphics, x, y, UiMinecraftTheme.INSTANCE); }
        @Override public void render(GuiGraphics graphics, int x, int y, UiStyleSource theme) {
            painter.render(graphics, new UiRect(x, y, width, height), theme);
        }
        /** 将实际绘制框交给 painter，保留主题和目标像素尺寸。 */
        @Override public void renderScaled(GuiGraphics graphics, UiRect box, UiStyleSource theme) {
            if (box.width() == 0 || box.height() == 0) return;
            painter.render(graphics, contained(this, box), theme);
        }
    }

    /** 只读绘制数据与当前视觉来源。 */
    @FunctionalInterface
    interface ThemePainter {
        /** 在布局框内绘制图标。 */
        void render(GuiGraphics graphics, UiRect bounds, UiStyleSource theme);
    }

    /** 提前登记即将展示的内容；准备工作由图标所有者按共享预算推进。 */
    default void prepare() { }

    /** 固定测量尺寸的受控自定义绘制入口，生命周期与失败处理由宿主负责。 */
    record Rendered(int width, int height, Painter painter, Runnable preparation) implements UiIcon {
        /** 普通自绘图标只提供绘制回调。 */
        public Rendered(int width, int height, Painter painter) { this(width, height, painter, () -> { }); }

        /** 带预热回调的图标在实际绘制前按需登记内容，回调由客户端线程调用。 */
        public Rendered {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid icon size");
            Objects.requireNonNull(painter);
            Objects.requireNonNull(preparation);
        }
        /** 登记即将可见的内容，不在这里执行无预算的实例或 GPU 准备。 */
        @Override public void prepare() { preparation.run(); }
        @Override public void render(GuiGraphics graphics, int x, int y) {
            painter.render(graphics, new UiRect(x, y, width, height));
        }
        /** 自绘内容直接按目标框重绘，避免先绘制为名义尺寸再拉伸。 */
        @Override public void renderScaled(GuiGraphics graphics, UiRect box, UiStyleSource theme) {
            if (box.width() == 0 || box.height() == 0) return;
            painter.render(graphics, contained(this, box));
        }
    }

    /** 绘制器仅接收已有绘制上下文与布局框。 */
    @FunctionalInterface
    interface Painter {
        void render(GuiGraphics graphics, UiRect bounds);
    }

    /** 在内容构建时复制物品，避免调用方修改数量或组件而破坏缓存。 */
    final class Item implements UiIcon {
        private final ItemStack stack;

        public Item(ItemStack stack) { this.stack = Objects.requireNonNull(stack).copy(); }
        @Override public int width() { return 16; }
        @Override public int height() { return 16; }
        @Override public void render(GuiGraphics graphics, int x, int y) {
            UiRenderLayers.item(graphics, new UiRect(x, y, width(), height()), local -> local.renderItem(stack, x, y));
        }
    }

    /** 使用原生大小绘制的贴图区域，宽高同时作为布局尺寸。 */
    record Sprite(ResourceLocation texture, int u, int v, int width, int height,
                  int textureWidth, int textureHeight) implements UiIcon {
        public Sprite {
            Objects.requireNonNull(texture);
            if (u < 0 || v < 0 || width <= 0 || height <= 0
                    || (long) u + width > textureWidth || (long) v + height > textureHeight) {
                throw new IllegalArgumentException("Invalid sprite region");
            }
        }

        @Override public void render(GuiGraphics graphics, int x, int y) {
            graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }
}
