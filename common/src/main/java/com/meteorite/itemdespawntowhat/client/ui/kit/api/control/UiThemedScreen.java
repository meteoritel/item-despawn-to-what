package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 带完整默认外观的屏幕；背景与内容共用一个稳定 Context，业务排版由子类拥有。 */
public abstract class UiThemedScreen extends UiScreen {
    private final UiThemeContext<? extends UiStyleSource> theme;

    /** 创建 Minecraft 默认屏幕；控件绑定 theme() 即可共享完整外观。 */
    protected UiThemedScreen(Component title) { this(title, UiThemeContext.minecraft()); }

    /** 直接复用宿主 Context，避免屏幕背景与控件出现两个主题所有者。 */
    protected UiThemedScreen(Component title, UiThemeContext<? extends UiStyleSource> theme) {
        super(title);
        this.theme = theme;
    }

    /** 屏幕背景与子控件共同读取的稳定来源。 */
    protected final UiThemeContext<? extends UiStyleSource> theme() { return theme; }

    /** 原版世界背景后绘制主题窗口；宿主可覆写整段背景画法。 */
    protected void renderThemedBackground(GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics, x, y, delta);
        theme.drawWindow(graphics, new UiRect(0, 0, width, height));
    }

    /** 主题发布、布局和作用域准备完成后，在背景上绘制内容。 */
    protected abstract void renderThemedUi(GuiGraphics graphics, int x, int y, float delta);

    @Override protected final void renderUi(GuiGraphics graphics, int x, int y, float delta) {
        renderThemedBackground(graphics, x, y, delta);
        renderThemedUi(graphics, x, y, delta);
    }
}
