package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.UiLabelPresentation;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 固定矩形的轻量控件，测量及命中结果只在配置或文本资源改变时创建，不负责输入分发。
 *
 * <p>控件自带语义状态（普通/悬停/按下/选中/禁用/焦点）：颜色从 {@link UiControlStyle} 取，
 * 禁用与不可见的控件不参与命中、动作与焦点序列。状态只影响绘制与命中，不触发重新测量，
 * 因此状态变化不需要重建内容树。</p>
 *
 * <p>绘制只在文字或图标确实越出控件矩形时才设置裁剪，未越界时不提交绘制批次。</p>
 */
public final class UiControl implements UiButtonWidget, UiShellWidget, UiInteractionSession.Target {
    private final UiWidgetShell shell = new UiWidgetShell(this, this::cancelPress);
    private final UiLabelPresentation label;
    private @Nullable Font configuredFont;
    private int color;
    @Nullable
    private UiIcon icon;
    private List<Component> tooltip = List.of();
    @Nullable
    private UiAction action;
    private UiPointerHit.TextTooltip target = new UiPointerHit.TextTooltip(bounds(), List.of());
    private UiStyleSource theme = UiMinecraftTheme.INSTANCE;
    private UiControlRole role = UiControlRole.SECONDARY;
    private long themeRevision = -1;
    private UiControlSkin skin = UiMinecraftTheme.INSTANCE.controlSkin(UiControlRole.SECONDARY);
    private UiControlStyle style = skin.style();
    private boolean styleOverride;
    private @Nullable UiControlSkin skinOverride;
    private boolean colorOverride;
    private boolean selected;
    private boolean pressed;
    private @Nullable UiInteractionSession session;

    /** 创建由调用方显式声明焦点能力的按钮。 */
    public UiControl() {
        this(new UiLabelPresentation());
    }

    /** 与宿主的特殊绘制共享标签，保持同一份测量和悬停状态。 */
    public UiControl(UiLabelPresentation label) {
        this.label = Objects.requireNonNull(label, "label");
        shell.setFocusable(false);
    }

    private void cancelPress(UiInteractionSession.EndReason reason) {
        endInteraction(reason);
        pressed = false;
    }

    /**
     * 重配控件内容并重建命中目标；同一标签重复配置不打断标签滚动。
     *
     * @param font    文本测量用字体
     * @param label   标签文本
     * @param color   文本颜色
     * @param icon    图标，可为 null
     * @param tooltip 提示行
     * @param action  激活动作；为 null 时取消按下态，焦点能力由 setFocusable 声明
     */
    public void configure(Font font, Component label, int color, @Nullable UiIcon icon,
                          List<Component> tooltip, @Nullable UiAction action) {
        this.label.setText(label);
        this.configuredFont = font;
        this.label.width(font);
        this.color = color;
        this.colorOverride = true;
        this.icon = icon;
        this.tooltip = List.copyOf(tooltip);
        this.action = action;
        // 动作被拿掉时结束按压，焦点资格仍由显式声明决定。
        if (action == null) {
            endInteraction(UiInteractionSession.EndReason.DISABLED);
            this.pressed = false;
        }
        rebuildTarget();
    }

    /** 显式覆盖文字色，保留控件动作、测量与交互状态。 */
    public void setTextColor(int color) {
        this.color = color;
        colorOverride = true;
    }

    /** 使用主题文字色配置内容，保留皮肤与交互状态。 */
    public void configure(Font font, Component label, @Nullable UiIcon icon, List<Component> tooltip, @Nullable UiAction action) {
        configure(font, label, theme.controlTextColor(role), icon, tooltip, action);
        colorOverride = false;
    }

    /** 绑定根主题；显式皮肤、结构与文字覆盖继续有效。 */
    public void setTheme(UiStyleSource theme, UiControlRole role) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.role = Objects.requireNonNull(role, "role");
        themeRevision = -1;
        refreshTheme();
    }

    private void refreshTheme() {
        if (themeRevision == theme.revision()) return;
        skin = skinOverride == null ? theme.controlSkin(role) : skinOverride;
        if (!styleOverride) style = skin.style();
        themeRevision = theme.revision();
    }

    /** 当前标签，可用于旁白与结构化快照。 */
    public Component label() { return label.text(); }

    /** 按同一字体量取标签与图标的期望宽度。 */
    public int preferredWidth(Font font, int padding) {
        int textWidth = label.width(font);
        return textWidth + (icon == null ? 0 : icon.width() + (textWidth == 0 ? 0 : 3)) + padding * 2;
    }

    /** 配置时记录的字体同时用于模态按钮测量与绘制。 */
    @Override
    public int preferredWidth(int padding) {
        return configuredFont == null ? padding * 2 : preferredWidth(configuredFont, padding);
    }

    /** 当前矩形与可读名称的只读快照。 */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, bounds(), accessibleName(), configuredFont == null ? 0 : label.width(configuredFont),
                bounds().width(), isEnabled(), isVisible(), List.of()));
    }

    /** 更新矩形并同步提示目标，几何变化保持按压会话。 */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        UiRect previous = shell.bounds();
        shell.setBounds(x, y, width, height);
        if (shell.bounds() != previous) rebuildTarget();
    }

    /** {@inheritDoc} */
    @Override
    public UiWidgetShell shell() {
        return shell;
    }

    /** 显式结构覆盖使用纯色画法；后调用的 setSkin 可替换完整皮肤。 */
    public void setStyle(UiControlStyle style) {
        this.style = Objects.requireNonNull(style);
        styleOverride = true;
    }

    /** 显式覆盖结构、画法与主题文字；独立文字色覆盖仍优先，换根主题保持本皮肤。 */
    public void setSkin(UiControlSkin skin) {
        skinOverride = Objects.requireNonNull(skin, "skin");
        styleOverride = false;
        themeRevision = -1;
        refreshTheme();
    }

    /** 清除局部结构与完整皮肤覆盖，恢复所属主题；文字覆盖独立保留。 */
    @SuppressWarnings("unused")
    public void clearSkinOverride() {
        skinOverride = null;
        styleOverride = false;
        themeRevision = -1;
        refreshTheme();
    }

    public UiControlStyle style() {
        refreshTheme();
        return style;
    }

    /** 禁用时结束按压，绘制继续使用禁用皮肤。 */
    @Override
    public UiControl setEnabled(boolean next) {
        shell.setEnabled(next);
        return this;
    }

    /** 隐藏时结束按压并退出焦点序列。 */
    public void setVisible(boolean next) {
        shell.setVisible(next);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    // 按下态是「这个目标可被激活」的反馈：纯标签（无 action）按住时不应显示按钮底色。
    public void setPressed(boolean pressed) {
        this.pressed = pressed && isHittable() && action != null;
    }

    public boolean isPressed() {
        return pressed;
    }

    /**
     * 可命中与可参与悬停的条件：禁用或不可见的目标不参与命中。
     */
    public boolean isHittable() {
        return shell.isInteractive();
    }

    /**
     * 供焦点调试与旁白使用的可读名称；未显式设置时按标签、再按首行提示回退。
     */
    public void setAccessibleName(@Nullable Component name) {
        shell.setAccessibleName(name);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Component accessibleName() {
        Component fallback = !label().getString().isEmpty() || tooltip.isEmpty() ? label() : tooltip.getFirst();
        return shell.accessibleName(fallback);
    }

    /** 可进入焦点序列的条件：显式声明可聚焦，并且当前可见、可用。 */
    public boolean isFocusable() {
        return shell.canFocus();
    }

    /**
     * 显式声明是否参与焦点导航，独立于点击动作。
     */
    public void setFocusable(boolean next) {
        shell.setFocusable(next);
    }

    /**
     * 命中后登记实际焦点与按压；释放仍命中本控件才激活。
     */
    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.button() != 0 || hit(context.pointerX(), context.pointerY()) == null || action == null)
            return false;

        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, 0);
        setPressed(true);
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        if (context.button() != 0 || !pressed) return false;
        pressed = false;
        if (hit(context.pointerX(), context.pointerY()) != null) activate();
        return true;
    }

    /**
     * 控件失效时取消其按压，不影响同作用域其它目标。
     */
    public void endInteraction(UiInteractionSession.EndReason reason) {
        if (session != null) session.end(this, reason);
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        pressed = false;
    }

    private void rebuildTarget() {
        target = new UiPointerHit.TextTooltip(bounds(), tooltip);
    }

    /** 命中当前可见且可用的矩形时返回只读提示目标，否则返回 null。 */
    @Nullable
    public UiPointerHit.TextTooltip hit(double x, double y) {
        if (!isHittable()) return null;
        return bounds().contains(x, y) ? target : null;
    }

    /**
     * 执行动作并把「是否消费」交还调用方；禁用、不可见或无动作时一律不消费。
     */
    public boolean activate() {
        if (!isHittable() || action == null) return false;
        action.run();
        return true;
    }

    /**
     * 绘制控件；只在内容确实越出控件矩形时才设置裁剪（见 {@code needsClip}）。
     *
     * @param incomingPointer 已解析的绘制指针与作用域门禁
     */
    public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        if (!isVisible()) return;
        refreshTheme();
        int textWidth = label.width(font);
        boolean hovered = isEnabled() && pointer.contains(bounds());
        (styleOverride ? UiControlPainter.FILL : skin.painter()).paint(graphics,
                new UiControlPainter.Frame(bounds(), style, stateFor(hovered), isFocusVisible()));
        int iconWidth = icon == null ? 0 : icon.width();
        int iconHeight = icon == null ? 0 : icon.height();
        int contentWidth = textWidth + (icon == null ? 0 : iconWidth + (textWidth == 0 ? 0 : 3));
        int x = bounds().x() + Math.max(2, (bounds().width() - contentWidth) / 2);
        int iconY = bounds().y() + (bounds().height() - iconHeight) / 2;
        int textX = icon == null ? x : x + iconWidth + 3;
        int textY = bounds().y() + (bounds().height() - font.lineHeight) / 2;
        int textMax = Math.max(0, bounds().right() - 2 - textX);
        boolean clip = needsClip(x, iconY, iconWidth, iconHeight, textX, textY, textWidth, font.lineHeight);
        if (clip) UiRenderLayers.enableScissor(graphics, bounds());
        try {
            if (icon != null) icon.render(graphics, x, iconY, theme);
            label.render(graphics, font, new UiRect(textX, textY, textMax, font.lineHeight), textColor(), hovered);
        } finally {
            if (clip) UiRenderLayers.disableScissor(graphics);
        }
        if (isFocusVisible()) renderFocusOutline(graphics);
    }

    // 需要外层裁剪的条件：图标或文字实际占用的矩形越出控件边界。文本超宽时 TextScroll 自带带内裁剪
    // （带右界为 bounds().right() - 2），这里只补它不覆盖的部分：图标越界与垂直越界。
    private boolean needsClip(int iconX, int iconY, int iconWidth, int iconHeight,
                              int textX, int textY, int textWidth, int lineHeight) {
        if (icon != null && (iconX < bounds().x() || iconX + iconWidth > bounds().right()
                || iconY < bounds().y() || iconY + iconHeight > bounds().bottom())) {
            return true;
        }
        // +1 是 TextScroll 文本带下沿多出的 1 像素；空标签不绘制，不因此触发裁剪。
        return textWidth > 0 && (textX < bounds().x() || textY < bounds().y() || textY + lineHeight + 1 > bounds().bottom());
    }

    private UiControlStyle.State stateFor(boolean hovered) {
        if (!isEnabled()) return UiControlStyle.State.DISABLED;
        if (pressed) return UiControlStyle.State.PRESSED;
        if (hovered) return UiControlStyle.State.HOVER;
        if (selected) return UiControlStyle.State.SELECTED;
        return UiControlStyle.State.NORMAL;
    }

    // 禁用态只降不透明度，保留调用方给定的语义色相，避免又多出一套硬编码文本色。
    private int textColor() {
        if (!colorOverride) return isEnabled() ? skin.textColor() : skin.disabledTextColor();
        return isEnabled() ? color : (color & 0x00FFFFFF) | 0x88000000;
    }

    /** 焦点轮廓画在矩形内侧：不侵入相邻控件，也不会被控件自身的裁剪吃掉。 */
    public void renderFocusOutline(GuiGraphics graphics) {
        int outline = style.focusOutline();
        graphics.fill(bounds().x(), bounds().y(), bounds().right(), bounds().y() + 1, outline);
        graphics.fill(bounds().x(), bounds().bottom() - 1, bounds().right(), bounds().bottom(), outline);
        graphics.fill(bounds().x(), bounds().y() + 1, bounds().x() + 1, bounds().bottom() - 1, outline);
        graphics.fill(bounds().right() - 1, bounds().y() + 1, bounds().right(), bounds().bottom() - 1, outline);
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit.TextTooltip target = hit(x, y);
        return target == null ? null : UiPointerHit.leaf(this, x, y).withTooltip(target);
    }

}
