package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControl;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 按钮控件。
 * <p>委托 kit 的 {@link UiControl} 处理状态与焦点，按需绘制主题的像素立体边或胶囊底图，
 * 因此同时兼容键盘（Tab / Enter）与鼠标（按下、拖出取消）操作。
 */
public final class UiButton implements UiWidget, UiFocusTarget, UiLayoutSource, UiInteractionSession.Target {

    // 被委托的 kit 控件
    private final UiControl control = new UiControl();
    private UiInteractionSession session;

    /**
     * 焦点能力与激活动作独立声明。
     */
    public UiButton setFocusable(boolean value) {
        control.setFocusable(value);
        return this;
    }

    /**
     * 取消尚未激活的按压。
     */
    public void cancelPress() {
        if (session != null) session.end(this, UiInteractionSession.EndReason.CANCEL);
        control.setPressed(false);
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        control.setPressed(false);
    }

    // 变体
    private final UiButtonVariant variant;
    // 字体（重新配置文本时需要）
    private final Font font;
    private Component label;
    // 当前图标，可为 null
    private UiIcon icon;
    // 提示行
    private final List<Component> tooltip = new ArrayList<>();
    // 点击动作，可为 null
    private UiAction action;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean chipStyle;
    private int hoverTicks;
    private static final int CHIP_PADDING = 4;
    private static final int ICON_GAP = 3;
    // 标签宽度缓存：布局与胶囊绘制都会逐帧问宽度，只在文本或语言变化时重测
    private int cachedLabelWidth = -1;
    private @Nullable Object cachedLabelLanguage;

    // 注入的宿主主题
    private final UiHostTheme theme;

    public UiButton(UiHostTheme theme, Font font, Component label, UiButtonVariant variant, UiAction action) {
        this.theme = UiHostThemes.require(theme);
        control.setFocusable(true);
        this.font = font;
        this.label = label;
        this.variant = variant;
        this.action = action;
        applyContent();
        applyState();
    }

    // 内容写入 kit 控件：文本、颜色、图标、提示与动作
    private void applyContent() {
        control.configure(font, label, theme.controlTextColor(variant.role()), icon, tooltip, action);
        control.setStyle(theme.controlStyle(variant.role()));
    }

    // 状态写入 kit 控件：只改启用与可见，不复制文本、不重建命中目标
    private void applyState() {
        control.setEnabled(enabled && variant.interactive());
        control.setVisible(visible);
    }

    // 标签文本变了，缓存的宽度失效；语言变化由 labelWidth 的令牌比较兜底
    private void invalidateLabelWidth() {
        cachedLabelWidth = -1;
    }

    // 标签宽度：字体实例不随资源热重载更换，因此以当前语言作为文本失效令牌
    private int labelWidth() {
        Object language = Language.getInstance();
        if (cachedLabelWidth >= 0 && cachedLabelLanguage == language) {
            return cachedLabelWidth;
        }
        cachedLabelWidth = font.width(label);
        cachedLabelLanguage = language;
        return cachedLabelWidth;
    }

    // 设置文本；与当前标签结构相同的组件不触发重配，逐帧刷新因此不产生复制与重测量
    public UiButton setLabel(Component label) {
        Component next = Objects.requireNonNull(label, "label");
        if (next.equals(this.label)) {
            return this;
        }
        this.label = next;
        invalidateLabelWidth();
        applyContent();
        return this;
    }

    // 设置图标；与当前图标相同的引用不触发重配
    public UiButton setIcon(UiIcon icon) {
        if (Objects.equals(this.icon, icon)) {
            return this;
        }
        this.icon = icon;
        applyContent();
        return this;
    }

    /**
     * 选择胶囊底图，沿用按钮的点击、禁用与键盘焦点行为。
     *
     * @param chipStyle 是否使用宿主主题的胶囊样式
     * @return 当前按钮
     */
    public UiButton setChipStyle(boolean chipStyle) {
        if (this.chipStyle == chipStyle) {
            return this;
        }
        this.chipStyle = chipStyle;
        return this;
    }

    // 设置点击动作；同一动作不触发重配
    public UiButton setAction(UiAction action) {
        if (this.action == action) {
            return this;
        }
        this.action = action;
        applyContent();
        return this;
    }

    // 设置提示行；内容未变时不重配控件
    public UiButton setTooltip(Component... lines) {
        List<Component> next = List.of(lines);
        if (tooltip.equals(next)) {
            return this;
        }
        tooltip.clear();
        tooltip.addAll(next);
        applyContent();
        return this;
    }

    // 设置是否启用；状态没变时不动控件，避免逐帧重配
    public UiButton setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        if (!enabled) cancelPress();
        this.enabled = enabled;
        applyState();
        return this;
    }

    // 设置是否可见；状态没变时不动控件，避免逐帧重配
    public UiButton setVisible(boolean visible) {
        if (this.visible == visible) {
            return this;
        }
        if (!visible) cancelPress();
        this.visible = visible;
        applyState();
        return this;
    }

    public Component label() {
        return label;
    }

    public UiButtonVariant variant() {
        return variant;
    }

    public boolean isPressed() {
        return control.isPressed();
    }

    // 按文本宽度计算合适的按钮宽度（含左右内边距与图标）
    public int preferredWidth(int padding) {
        int width = labelWidth() + padding * 2;
        if (icon != null) {
            width += icon.width() + ICON_GAP;
        }
        return width;
    }

    /**
     * 导出按钮的排版快照。
     *
     * <p>文本带宽取控件内容宽：胶囊按钮要扣掉左右内边距，普通按钮按整宽算，
     * 这样外部探针能直接看出标签在当前宽度下是否被裁切。不可见或尚未布局（宽高为 0）
     * 时不产出节点，避免报出屏幕上并不存在的矩形。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        UiRect rect = bounds();
        if (!visible || rect.width() <= 0 || rect.height() <= 0) {
            return List.of();
        }
        int budget = chipStyle ? Math.max(0, rect.width() - CHIP_PADDING * 2) : rect.width();
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, rect, label, labelWidth(), budget,
                control.isEnabled(), true, List.of()));
    }

    @Override
    public UiRect bounds() {
        return control.bounds();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        control.setBounds(x, y, width, height);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        if (chipStyle) {
            renderChip(graphics, font, mouseX, mouseY);
            return;
        }
        control.render(graphics, font, mouseX, mouseY);
        theme.drawBevel(graphics, control.bounds(), theme.palette().controlBevelLight(), theme.palette().controlBevelDark());
    }

    private void renderChip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        UiRect rect = bounds();
        boolean hovered = control.isHittable() && rect.contains(mouseX, mouseY);
        if (control.isEnabled()) theme.drawChip(graphics, rect, isPressed(), hovered);
        else theme.drawSegment(graphics, rect, false, false, false);
        int iconWidth = icon == null ? 0 : icon.width() + ICON_GAP;
        int available = Math.max(0, rect.width() - CHIP_PADDING * 2);
        int contentWidth = Math.min(available, labelWidth() + iconWidth);
        int x = rect.x() + (rect.width() - contentWidth) / 2;
        // 胶囊按钮在窄布局中仍须把图标与文字裁剪到自身范围。
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        try {
            if (icon != null) icon.render(graphics, x, rect.y() + (rect.height() - icon.height()) / 2);
            hoverTicks = hovered ? hoverTicks + 1 : 0;
            // 胶囊使用浅色底，不能沿用主按钮深色底对应的白字。
            int color = !control.isEnabled() ? theme.palette().textDisabled()
                    : variant == UiButtonVariant.DANGER ? theme.palette().danger() : theme.palette().textPrimary();
            TextScroll.draw(graphics, font, label.getString(), x + iconWidth,
                    rect.y() + (rect.height() - font.lineHeight) / 2,
                    Math.max(0, contentWidth - iconWidth), color, hovered, hoverTicks, false);
        } finally {
            graphics.disableScissor();
        }
        if (control.isFocusVisible()) theme.drawFocusOutline(graphics, rect);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (!visible || button != 0 || !control.isHittable()) {
            return false;
        }
        if (!control.bounds().contains(mouseX, mouseY)) {
            return false;
        }
        context.scope().pointerHit(this);
        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, button);
        control.setPressed(true);
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (button != 0 || !control.isPressed()) {
            return false;
        }
        boolean inside = control.isHittable() && control.bounds().contains(mouseX, mouseY);
        control.setPressed(false);
        if (inside) {
            control.activate();
        }
        return true;
    }

    @Override
    public UiFocusState focusState() {
        return control.focusState();
    }

    @Override
    public boolean canFocus() {
        return visible && control.canFocus();
    }

    @Override
    public void focusChanged(boolean focused) {
        control.focusChanged(focused);
    }

    @Override
    public boolean isFocused() {
        return control.isFocused();
    }

    @Override
    public boolean activate() {
        if (!visible || !control.isHittable()) {
            return false;
        }
        return control.activate();
    }

    @Override
    public Component accessibleName() {
        return control.accessibleName();
    }

    public UiButton setAccessibleName(Component name) {
        control.setAccessibleName(name);
        return this;
    }
}
