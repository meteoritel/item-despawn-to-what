package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextMeasurer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
public final class UiControl implements UiButtonWidget, UiInteractionSession.Target {
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private Component label = Component.empty();
    private FormattedCharSequence text = FormattedCharSequence.EMPTY;
    private int textWidth;
    private TextMeasurer measurer;
    private int color;
    /**
     * 上次测量所用的语言与字体：两者变化后视觉顺序文本与宽度都会变，渲染时按令牌重算。
     */
    private @Nullable Object measuredLanguage;
    private @Nullable Font measuredFont;
    @Nullable
    private UiIcon icon;
    private List<Component> tooltip = List.of();
    @Nullable
    private UiAction action;
    @Nullable
    private Component accessibleName;
    private UiTarget target = new UiTarget(UiTarget.Kind.ROW, 0, null, bounds, List.of(), null);
    /**
     * 标签超宽时的滚动计时：只在悬停期间推进，离开即归零。
     */
    private int scrollTicks;
    private UiStyleSource theme = UiMinecraftTheme.INSTANCE;
    private UiControlRole role = UiControlRole.SECONDARY;
    private long themeRevision = -1;
    private UiControlSkin skin = UiMinecraftTheme.INSTANCE.controlSkin(UiControlRole.SECONDARY);
    private UiControlStyle style = skin.style();
    private boolean styleOverride;
    private boolean colorOverride;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean selected;
    private final UiFocusState focusState = new UiFocusState();
    private boolean pressed;
    private boolean focusable;
    private @Nullable UiInteractionSession session;

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
        // 重建同一标签时不打断滚动：浮层按秒重配控件，逐次归零会看到每秒跳回起点。
        if (!this.label.getString().equals(label.getString())) this.scrollTicks = 0;
        this.label = label.copy();
        this.measuredFont = font;
        this.measuredLanguage = Language.getInstance();
        this.text = this.label.getVisualOrderText();
        this.measurer = TextMeasurer.of(font);
        this.textWidth = measurer.width(label);
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

    /** 绑定根主题；后续显式 setStyle 和带 color 的 configure 各自覆盖对应默认项。 */
    public void setTheme(UiStyleSource theme, UiControlRole role) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.role = Objects.requireNonNull(role, "role");
        themeRevision = -1;
        styleOverride = false;
        refreshTheme();
    }

    private void refreshTheme() {
        if (themeRevision == theme.revision()) return;
        skin = theme.controlSkin(role);
        if (!styleOverride) style = skin.style();
        themeRevision = theme.revision();
    }

    /** 当前标签，可用于旁白与结构化快照。 */
    public Component label() { return label; }

    /** 按同一字体量取标签与图标的期望宽度。 */
    public int preferredWidth(Font font, int padding) {
        refreshMetrics(font);
        return textWidth + (icon == null ? 0 : icon.width() + (textWidth == 0 ? 0 : 3)) + padding * 2;
    }

    /** 配置时记录的字体同时用于模态按钮测量与绘制。 */
    @Override
    public int preferredWidth(int padding) {
        return measuredFont == null ? padding * 2 : preferredWidth(measuredFont, padding);
    }

    /** 当前矩形与可读名称的只读快照。 */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, bounds, accessibleName(), textWidth,
                bounds.width(), enabled, visible, List.of()));
    }

    // 负宽高按 0 钳制（与 UiDocument.setViewport / UiScrollView 同口径）；零宽高控件不可命中。
    public void setBounds(int x, int y, int width, int height) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        if (bounds.x() == x && bounds.y() == y && bounds.width() == w && bounds.height() == h) return;
        bounds = new UiRect(x, y, w, h);
        rebuildTarget();
    }

    public UiRect bounds() {
        return bounds;
    }

    public void setStyle(UiControlStyle style) {
        this.style = Objects.requireNonNull(style);
        styleOverride = true;
    }

    public UiControlStyle style() {
        refreshTheme();
        return style;
    }

    // 禁用控件仍照常绘制（走禁用底色与淡化文字），但不命中、不激活、不进焦点序列。
    public UiControl setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            endInteraction(UiInteractionSession.EndReason.DISABLED);
            pressed = false;

            focusState.clear();
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // 不可见控件完全不绘制、不命中、不参与焦点；浮层分页窗口用它隐藏窗口外的行。
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            endInteraction(UiInteractionSession.EndReason.HIDDEN);
            pressed = false;

            focusState.clear();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }


    @Override
    public boolean isFocused() {
        return focusState.isFocused();
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
        return enabled && visible;
    }

    public UiFocusState focusState() {
        return focusState;
    }

    @Override
    public boolean canFocus() {
        return isFocusable();
    }

    /**
     * 供焦点调试与旁白使用的可读名称；未显式设置时按标签、再按首行提示回退。
     */
    public void setAccessibleName(@Nullable Component name) {
        this.accessibleName = name == null ? null : name.copy();
    }

    @Override
    public @Nullable Component accessibleName() {
        if (accessibleName != null) return accessibleName;
        // 图标/符号按钮的 label 常为空或不具名，真正的名称只在提示里：回退到首行提示。
        if (!label.getString().isEmpty()) return label;
        return tooltip.isEmpty() ? label : tooltip.getFirst();
    }

    /** 可进入焦点序列的条件：显式声明可聚焦，并且当前可见、可用。 */
    public boolean isFocusable() {
        return focusable && isHittable();
    }

    /**
     * 显式声明是否参与焦点导航，独立于点击动作。
     */
    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    /**
     * 命中后登记实际焦点与按压；释放仍命中本控件才激活。
     */
    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.button() != 0 || hit(context.pointerX(), context.pointerY()) == null || action == null)
            return false;

        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, context.button());
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
        target = new UiTarget(icon == null ? UiTarget.Kind.ROW : UiTarget.Kind.ICON,
                0, label, bounds, tooltip, action);
    }

    @Nullable
    public UiTarget hit(double x, double y) {
        if (!isHittable()) return null;
        return bounds.contains(x, y) ? target : null;
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
        if (!visible) return;
        refreshTheme();
        refreshMetrics(font);
        boolean hovered = enabled && pointer.contains(bounds);
        (styleOverride ? UiControlPainter.FILL : skin.painter()).paint(graphics,
                new UiControlPainter.Frame(bounds, style, stateFor(hovered), isFocusVisible()));
        int iconWidth = icon == null ? 0 : icon.width();
        int iconHeight = icon == null ? 0 : icon.height();
        int contentWidth = textWidth + (icon == null ? 0 : iconWidth + (textWidth == 0 ? 0 : 3));
        int x = bounds.x() + Math.max(2, (bounds.width() - contentWidth) / 2);
        int iconY = bounds.y() + (bounds.height() - iconHeight) / 2;
        int textX = icon == null ? x : x + iconWidth + 3;
        int textY = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        int textMax = Math.max(0, bounds.right() - 2 - textX);
        boolean clip = needsClip(x, iconY, iconWidth, iconHeight, textX, textY, font.lineHeight);
        if (clip) UiTransform.enableScissor(graphics, bounds);
        try {
            if (icon != null) icon.render(graphics, x, iconY);
            // 放不下时不再静默裁掉：留在矩形内、悬停滚动。
            scrollTicks = hovered ? scrollTicks + 1 : 0;
            TextScroll.draw(graphics, font, text, textWidth, textX, textY, textMax, textColor(), hovered, scrollTicks);
        } finally {
            if (clip) UiTransform.disableScissor(graphics);
        }
        if (isFocusVisible()) renderFocusOutline(graphics);
    }

    // 语言或字体实例变化后标签文本与宽度都会变：这里按令牌廉价重算，宿主不必为语言热切换逐帧重配。
    private void refreshMetrics(Font font) {
        Object language = Language.getInstance();
        if (font == measuredFont && language == measuredLanguage) return;
        measuredFont = font;
        measuredLanguage = language;
        this.text = this.label.getVisualOrderText();
        this.measurer = TextMeasurer.of(font);
        this.textWidth = measurer.width(this.label);
    }

    // 需要外层裁剪的条件：图标或文字实际占用的矩形越出控件边界。文本超宽时 TextScroll 自带带内裁剪
    // （带右界为 bounds.right() - 2），这里只补它不覆盖的部分：图标越界与垂直越界。
    private boolean needsClip(int iconX, int iconY, int iconWidth, int iconHeight,
                              int textX, int textY, int lineHeight) {
        if (icon != null && (iconX < bounds.x() || iconX + iconWidth > bounds.right()
                || iconY < bounds.y() || iconY + iconHeight > bounds.bottom())) {
            return true;
        }
        // +1 是 TextScroll 文本带下沿多出的 1 像素；空标签不绘制，不因此触发裁剪。
        return textWidth > 0 && (textX < bounds.x() || textY < bounds.y() || textY + lineHeight + 1 > bounds.bottom());
    }

    private UiControlStyle.State stateFor(boolean hovered) {
        if (!enabled) return UiControlStyle.State.DISABLED;
        if (pressed) return UiControlStyle.State.PRESSED;
        if (hovered) return UiControlStyle.State.HOVER;
        if (selected) return UiControlStyle.State.SELECTED;
        return UiControlStyle.State.NORMAL;
    }

    // 禁用态只降不透明度，保留调用方给定的语义色相，避免又多出一套硬编码文本色。
    private int textColor() {
        if (!colorOverride) return enabled ? skin.textColor() : skin.disabledTextColor();
        return enabled ? color : (color & 0x00FFFFFF) | 0x88000000;
    }

    // 焦点轮廓画在矩形内侧：不侵入相邻控件，也不会被控件自身的裁剪吃掉。
    public void renderFocusOutline(GuiGraphics graphics) {
        int outline = style.focusOutline();
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, outline);
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), outline);
        graphics.fill(bounds.x(), bounds.y() + 1, bounds.x() + 1, bounds.bottom() - 1, outline);
        graphics.fill(bounds.right() - 1, bounds.y() + 1, bounds.right(), bounds.bottom() - 1, outline);
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        UiTarget target = hit(x, y);
        return target == null ? null : UiPointerHit.leaf(this, x, y).withTooltip(target);
    }

}
