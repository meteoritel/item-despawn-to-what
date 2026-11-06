package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeContext;
import net.minecraft.client.gui.GuiGraphics;

/** 根持有的宿主主题上下文；通用控件与业务外壳共享一次发布后的主题。 */
public final class UiHostThemeContext extends UiThemeContext<UiHostTheme> implements UiHostTheme {
    private final UiIcon[][] icons = resolveIcons();

    private UiIcon[][] resolveIcons() {
        UiIcon[][] resolved = new UiIcon[UiHostIcon.values().length][2];
        for (UiHostIcon role : UiHostIcon.values()) {
            for (int state = 0; state < 2; state++) {
                boolean disabled = state == 1;
                resolved[role.ordinal()][state] = new UiIcon.Rendered(16, 16,
                        (graphics, box) -> current().icon(role, disabled).render(graphics, box.x(), box.y()));
            }
        }
        return resolved;
    }

    /** 创建具有宿主扩展的稳定主题来源。 */
    public UiHostThemeContext(UiHostTheme initial) { super(initial); }
    @Override public UiHostPalette palette() { return current().palette(); }
    /** 稳定 wrapper 随发布读取当前 Adapter，控件无需替换图标身份。 */
    @Override public UiIcon icon(UiHostIcon role, boolean disabled) { return icons[role.ordinal()][disabled ? 1 : 0]; }

    @Override public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) { current().drawSurface(graphics, rect, fill, border); }
    @Override public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) { current().drawBevel(graphics, rect, light, dark); }

}
