package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiNineSlice;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** 状态图像映射：缺省高亮回到 normal，按下、选择、焦点回到高亮，禁用回到 normal。 */
public record UiSkinImages(Image normal, Image hovered, Image pressed, Image selected, Image disabled, Image focused) {
    /** 构造时解析全部回退；绘制期不创建资源或状态表。 */
    public UiSkinImages {
        Objects.requireNonNull(normal, "normal");
        hovered = hovered == null ? normal : hovered;
        pressed = pressed == null ? hovered : pressed;
        selected = selected == null ? hovered : selected;
        disabled = disabled == null ? normal : disabled;
        focused = focused == null ? hovered : focused;
    }

    /** 使用同一图像覆盖所有状态。 */
    public UiSkinImages(Image normal) { this(normal, null, null, null, null, null); }

    /** 用原版 sprite 图集的普通与高亮资源创建映射；资源包重载沿用原版所有权。 */
    public static UiSkinImages sprites(ResourceLocation normal, ResourceLocation highlighted) {
        return new UiSkinImages(Image.sprite(normal), Image.sprite(highlighted), null, null, null, null);
    }

    /** 背景图像只读几何；实现负责恢复自己改变的颜色、pose 与裁剪。 */
    @FunctionalInterface
    public interface Image {
        /** 绘制已确定的目标矩形。 */
        void paint(GuiGraphics graphics, UiRect bounds);

        /** 原版 GUI sprite，缩放规则由 sprite 元数据提供。 */
        static Image sprite(ResourceLocation location) {
            Objects.requireNonNull(location, "location");
            return (g, r) -> g.blitSprite(location, r.x(), r.y(), r.width(), r.height());
        }

        /** 显式九宫格贴图，极小尺寸由九宫格实现夹紧四角。 */
        static Image nineSlice(UiNineSlice slice) { return slice::render; }

        /** 为贴图显式染色；绘制结束恢复 GUI 默认白色。 */
        default Image tinted(int argb) {
            return (g, r) -> {
                g.setColor(((argb >>> 16) & 255) / 255F, ((argb >>> 8) & 255) / 255F,
                        (argb & 255) / 255F, ((argb >>> 24) & 255) / 255F);
                try { paint(g, r); } finally { g.setColor(1, 1, 1, 1); }
            };
        }
    }

    /** 保留控件结构与文字，只替换背景；焦点覆盖 normal/hover，按下与禁用优先。 */
    public UiControlSkin control(UiControlSkin base) {
        return base.withPainter((g, frame) -> paint(g, frame.bounds(), frame.state(), frame.focused()));
    }

    /** 保留表面颜色描述，只替换背景。 */
    public UiSurfaceSkin surface(UiSurfaceSkin base) {
        return base.withPainter((g, r, state, skin) -> paint(g, r, state, false));
    }

    /** 当前映射画轨道，thumb 画手柄；文字与输入继续由滑块控件管理。 */
    public UiSliderSkin slider(UiSliderSkin base, UiSkinImages thumb) {
        return new UiSliderSkin(base.style(), (g, frame) -> {
            UiControlStyle.State state = !frame.enabled() ? UiControlStyle.State.DISABLED
                    : frame.dragging() || frame.pressed() ? UiControlStyle.State.PRESSED
                    : frame.hovered() ? UiControlStyle.State.HOVER : UiControlStyle.State.NORMAL;
            paint(g, frame.track(), state, frame.focused());
            thumb.paint(g, frame.thumb(), state, frame.focused());
            if (frame.error()) g.renderOutline(frame.bounds().x(), frame.bounds().y(),
                    frame.bounds().width(), frame.bounds().height(), frame.style().errorColor());
        });
    }

    private void paint(GuiGraphics graphics, UiRect rect, UiControlStyle.State state, boolean focus) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        Image image = switch (state) {
            case DISABLED -> disabled;
            case PRESSED -> pressed;
            case SELECTED -> selected;
            case HOVER -> focus ? focused : hovered;
            case NORMAL -> focus ? focused : normal;
        };
        image.paint(graphics, rect);
    }
}
