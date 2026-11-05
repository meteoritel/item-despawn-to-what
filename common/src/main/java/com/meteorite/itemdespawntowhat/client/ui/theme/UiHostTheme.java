package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;

/**
 * 宿主主题的完整视觉入口：扩展 kit 的通用 skin 与度量，补充业务配色、语义表面和图标角色。
 * <p>主题 Adapter 提供不可变定义；屏幕通过稳定 Context 发布正文与画布组合，
 * 消费者只读取所属来源，无需管理资源图集或刷新次序。</p>
 */
public interface UiHostTheme extends UiStyleSource {

    /** 按职责组合的不可变业务视觉值。 */
    UiHostPalette palette();

    /** 按用途解析状态色与画法，调用方不依赖贴图布局。 */
    UiControlSkin surfaceSkin(UiHostSurface role);

    /** 绘制常态语义表面。 */
    default void drawSurface(GuiGraphics graphics, UiRect rect, UiHostSurface role) {
        drawSurface(graphics, rect, role, UiControlStyle.State.NORMAL);
    }

    /** 使用完整 skin 绘制表面状态。 */
    default void drawSurface(GuiGraphics graphics, UiRect rect, UiHostSurface role, UiControlStyle.State state) {
        UiControlSkin skin = surfaceSkin(role);
        skin.painter().paint(graphics, new UiControlPainter.Frame(rect, skin.style(), state, false));
    }

    @Override default void drawWindow(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiHostSurface.WINDOW); }
    @Override default void drawInset(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiHostSurface.INSET); }
    @Override default void drawHeader(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiHostSurface.HEADER); }
    @Override default void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered) {
        drawSurface(graphics, rect, UiHostSurface.CHIP, selected ? UiControlStyle.State.SELECTED
                : hovered ? UiControlStyle.State.HOVER : UiControlStyle.State.NORMAL);
    }

    // ---- 结构绘制 ----

    /** 仅绘制几何立体边；颜色由所属主题提供。 */
    void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark);

    // ---- 宿主业务视觉 ----

    /** 按业务用途解析图标；禁用状态与底色配套，调用方不接触图集行号。 */
    UiIcon icon(UiHostIcon role, boolean disabled);

    /** 显式颜色的平面几何原语，供业务高亮使用。 */
    void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border);
}
