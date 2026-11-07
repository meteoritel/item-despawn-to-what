package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 原控件组合的本地状态：矩形、资格、焦点视图与显式名称。
 * 实际焦点和持续输入仍由作用域持有，包装与核心可以共享同一个壳。
 */
public final class UiWidgetShell {
    private final UiFocusTarget owner;
    private final @Nullable Consumer<UiInteractionSession.EndReason> invalidated;
    private final UiFocusState focusState = new UiFocusState();
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focusable = true;
    private @Nullable Component accessibleName;

    /**
     * 绑定实际控件；失效时结束其作用域会话并清理焦点。
     */
    public UiWidgetShell(UiFocusTarget owner) {
        this(owner, null);
    }

    /**
     * 自定义收尾替代默认会话结束，用于私有反馈或子目标；资格更新后执行。
     */
    public UiWidgetShell(UiFocusTarget owner,
                         @Nullable Consumer<UiInteractionSession.EndReason> invalidated) {
        this.owner = owner;
        this.invalidated = invalidated;
    }

    /**
     * 当前 GUI 逻辑矩形。
     */
    public UiRect bounds() {
        return bounds;
    }

    /**
     * 负宽高按零处理；相同几何保留实例，移动控件保持当前交互。
     */
    public void setBounds(int x, int y, int width, int height) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        if (bounds.x() != x || bounds.y() != y || bounds.width() != w || bounds.height() != h) {
            bounds = new UiRect(x, y, w, h);
        }
    }

    /**
     * 持久焦点视图，供实际控件及其包装共享。
     */
    public UiFocusState focusState() {
        return focusState;
    }

    /**
     * 当前可用性。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 禁用时只结束本目标的交互；复合子目标由额外收尾处理。
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!enabled) invalidate(UiInteractionSession.EndReason.DISABLED);
    }

    /**
     * 当前可见性。
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 隐藏时结束交互，清理属于本目标的焦点。
     */
    public void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        if (!visible) invalidate(UiInteractionSession.EndReason.HIDDEN);
    }

    /**
     * 参与普通命中与悬停的共同资格。
     */
    public boolean isInteractive() {
        return visible && enabled;
    }

    /**
     * 本地焦点资格；控件可再叠加选项非空等业务条件。
     */
    public boolean canFocus() {
        return focusable && isInteractive();
    }

    /**
     * 声明焦点能力，独立于激活动作。
     */
    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
        if (!focusable) focusState.clear();
    }

    /**
     * 配置显式名称；null 恢复控件当前内容派生的名称。
     */
    public void setAccessibleName(@Nullable Component name) {
        accessibleName = name == null ? null : name.copy();
    }

    /**
     * 显式名称优先，回退值由控件当前内容提供。
     */
    public @Nullable Component accessibleName(@Nullable Component fallback) {
        return accessibleName == null ? fallback : accessibleName;
    }

    /**
     * 按键盘焦点视图与当前主题绘制实际焦点矩形。
     */
    public void renderFocusOutline(GuiGraphics graphics, UiRect rect, UiStyleSource theme) {
        if (focusState.isVisible()) theme.drawFocusOutline(graphics, rect);
    }

    private void invalidate(UiInteractionSession.EndReason reason) {
        UiInputScope scope = focusState.scope();
        if (invalidated != null) {
            invalidated.accept(reason);
        } else if (scope != null && owner instanceof UiInteractionSession.Target target) {
            scope.interaction().end(target, reason);
        }
        // 清焦点会正常提交编辑，须先按失效原因取消；回调重新启用后保留新焦点。
        if (!isInteractive()) focusState.clear();
    }
}
