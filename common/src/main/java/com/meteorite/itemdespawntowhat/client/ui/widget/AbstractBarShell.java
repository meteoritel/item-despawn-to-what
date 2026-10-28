package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueTextBinding;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 两个区间宿主控件（{@link UiCyclicTimeBar}、{@link UiRangeBar}）共用的外壳：标签行与轨道行的几何、
 * 独立提示行、精确输入行、焦点与捕获生命周期，以及指针与键盘的统一分发。
 * <p>外壳只依赖两个 kit 核心（{@code UiCyclicRange}、{@code UiRangeSlider}）共同实现的输入契约
 * {@link UiInputTarget} 与 {@link UiInteractionSession.Target}；两者尚无共同类型接口，其余核心能力
 * （拖动查询、边界与可见性写入、结束交互、选中端）经 protected 适配钩子由子类提供，
 * 外壳因此既不需要按类型分支，也不需要两侧各写一套调用。</p>
 */
abstract class AbstractBarShell<C extends UiInteractionSession.Target & UiFocusTarget>
        implements UiWidget, UiFocusTarget {

    // 标签行高度
    static final int LABEL_HEIGHT = 10;
    // 轨道行高度
    static final int TRACK_ROW_HEIGHT = 12;
    // 独立提示行的高度（聚焦时显示精确输入的按键提示）
    static final int HINT_HEIGHT = 9;
    // 提示与相邻文字之间的留白
    static final int HINT_GAP = 4;
    // 行内提示可用的最小宽度：再窄就不显示，避免只画出半截文字
    static final int MIN_HINT_WIDTH = 64;

    protected final UiHostTheme theme;
    protected final Font font;
    protected final C core;
    protected final UiTextInput precise;

    private @Nullable Consumer<UiRangeEnd> preciseEditor;
    protected UiRect bounds = new UiRect(0, 0, 0, 0);
    protected UiRect trackRow = new UiRect(0, 0, 0, 0);
    // 独立提示行；宿主高度不足时为 null（提示改在标签行内显示）
    protected @Nullable UiRect hintRow;
    protected boolean enabled = true;
    protected boolean visible = true;
    protected boolean preciseActive;

    /**
     * 装配公共外壳与精确输入行；核心由子类构造，主题只经宿主主题注入。
     *
     * @param theme            宿主主题
     * @param font             文本测量与绘制字体
     * @param preciseHint      精确输入行的提示文案
     * @param preciseMaxLength 精确输入允许的最大字符数
     * @param core             kit 侧交互核心
     */
    AbstractBarShell(UiHostTheme theme, Font font, Component preciseHint, int preciseMaxLength, C core) {
        this.theme = UiHostThemes.require(theme);
        this.font = Objects.requireNonNull(font, "font");
        this.core = Objects.requireNonNull(core, "core");
        this.precise = new UiTextInput(this.theme, font, preciseHint);
        this.precise.setMaxLength(preciseMaxLength);
        this.precise.setVisible(false);
        this.precise.setOnCommit(this::commitPrecise);
        this.precise.setOnValueChanged(() -> {
        });
        this.precise.setOnInteractionEnd(reason -> {
            if (reason.cancels()) preciseBinding().cancelText();
            else preciseBinding().commitText(precise.value());
        });
        this.precise.setOnCancel(() -> {
            closePrecise();
            coreEndInteraction(UiInteractionSession.EndReason.CANCEL);
        });
    }

    // ---- 几何装配：子类在公共两行之后追加自己的行 ----

    /**
     * 排布标签行与轨道行；返回轨道行高度，供子类排布后续行。
     *
     * @return 轨道行实际高度
     */
    protected final int layoutRows(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int rowHeight = Math.min(TRACK_ROW_HEIGHT, Math.max(4, bounds.height() - LABEL_HEIGHT));
        this.trackRow = new UiRect(x, y + LABEL_HEIGHT, bounds.width(), rowHeight);
        return rowHeight;
    }

    // 高度预算够时提示独占一行；不够时留空，提示改在标签行内绘制
    protected final void layoutHintRow(int usedHeight) {
        this.hintRow = bounds.height() - usedHeight >= HINT_HEIGHT
                ? new UiRect(bounds.x(), bounds.y() + usedHeight, bounds.width(), HINT_HEIGHT) : null;
    }

    // 精确输入行紧贴子类给定的行下沿，宽度至少容纳一次完整输入
    protected final void layoutPreciseRow(int top) {
        precise.setBounds(bounds.x(), top, Math.max(24, bounds.width()), theme.rowHeight());
    }

    protected final void layoutCoreBounds() {
        coreSetBounds(trackRow.x(), trackRow.y(), trackRow.width(), trackRow.height());
    }

    // ---- 可见性与禁用：状态与核心保持同步，关闭精确输入不留在半开状态 ----

    protected final void applyEnabled(boolean next) {
        this.enabled = next;
        coreSetEnabled(next);
        if (!next) {
            closePrecise();
        }
    }

    protected final void applyVisible(boolean next) {
        this.visible = next;
        coreSetVisible(next);
        if (!next) {
            closePrecise();
        }
    }

    // ---- UiWidget / UiFocusTarget ----

    @Override
    public final UiRect bounds() {
        return bounds;
    }

    @Override
    public final boolean isVisible() {
        return visible;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public UiFocusState focusState() {
        return core.focusState();
    }

    @Override
    public final boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public final boolean isFocused() {
        return focusState().containsFocus();
    }

    @Override
    public final void focusChanged(boolean focused) {
        core.focusChanged(focused);
    }


    @Override
    public final boolean activate() {
        return openPrecise();
    }

    // ---- 输入分发 ----

    @Override
    public final boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (!visible || !enabled || !acceptsClick(button)) {
            return false;
        }
        if (preciseActive && precise.mousePressed(context)) {
            return true;
        }
        if (handleShellClick(context)) {
            return true;
        }
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        closePrecise();
        return core.mousePressed(context);
    }

    // 只响应特定按键的控件覆盖本方法（周期条只接受左键）
    protected boolean acceptsClick(int button) {
        return true;
    }

    // 外壳在边界判定前处理的点击（周期条的预设按钮）；返回 true 表示本次已消费
    protected boolean handleShellClick(UiInputContext context) {
        return false;
    }

    @Override
    public final boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (coreDragging()) {
            return core.mouseDragged(context, dragX, dragY);
        }
        return preciseActive && precise.mouseDragged(context, dragX, dragY);
    }

    @Override
    public final boolean mouseReleased(UiInputContext context) {
        if (coreDragging()) {
            return core.mouseReleased(context);
        }
        return preciseActive && precise.mouseReleased(context);
    }

    @Override
    public final boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (preciseActive && precise.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (core.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return openPrecise();
        }
        return false;
    }

    @Override
    public final boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (preciseActive && precise.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return core.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public final boolean charTyped(char codePoint, int modifiers) {
        return preciseActive && precise.charTyped(codePoint, modifiers);
    }

    // ---- 精确输入 ----

    // 复合表单可将精确编辑交给已有字段输入，统一缓冲与错误定位。
    public final void setPreciseEditor(@Nullable Consumer<UiRangeEnd> editor) {
        preciseEditor = editor;
    }

    /**
     * 打开当前端的精确输入；宿主提供外部编辑器时将焦点交给该编辑器。
     *
     * @return 当前控件是否接受精确编辑请求
     */
    public final boolean openPrecise() {
        if (!enabled || !visible) {
            return false;
        }
        if (preciseEditor != null) {
            preciseEditor.accept(coreSelectedEnd());
            return true;
        }
        preciseActive = true;
        precise.setVisible(true);
        focusState().focusWithin(precise);
        preciseBinding().acceptText();
        preciseBinding().previewValue();
        precise.selectAll();
        return true;
    }

    private void commitPrecise(@Nullable String text) {
        preciseBinding().commitText(text == null ? "" : text);
        closePrecise();
    }

    protected final void closePrecise() {
        preciseActive = false;
        precise.setVisible(false);
        if (focusState().containsFocus()) focusState().request();
    }

    // ---- 提示行绘制：两控件的提示内容不同，占位与裁剪规则相同 ----

    // 标签行内的聚焦提示：占用标签与右侧文本之间的空白，宽度不足时整体略过
    protected final void drawInlineHint(GuiGraphics graphics, int labelEnd, int rightX, Component hint) {
        int start = labelEnd + HINT_GAP;
        int available = rightX - HINT_GAP - start;
        if (available < MIN_HINT_WIDTH) {
            return;
        }
        String text = TextScroll.trimToWidth(font, hint.getString(), available);
        graphics.drawString(font, text, start, bounds.y(), theme.palette().textDisabled(), false);
    }

    // 聚焦且宿主给足高度时的独立提示行
    protected final void drawHintRow(GuiGraphics graphics, Component hint) {
        UiRect row = hintRow;
        if (!isFocused() || row == null) {
            return;
        }
        String text = TextScroll.trimToWidth(font, hint.getString(), Math.max(1, row.width()));
        graphics.drawString(font, text, row.x(), row.y(), theme.palette().textDisabled(), false);
    }

    // ---- 捕获与宿主生命周期：两侧顺序一致，统一结束精确输入 ----

    public final void endInteraction(UiInteractionSession.EndReason reason) {
        coreEndInteraction(reason);
        precise.endInteraction(reason);
        closePrecise();
    }


    // ---- 子类实现 ----

    @Override
    public abstract void setBounds(int x, int y, int width, int height);

    @Override
    public abstract void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY);

    @Override
    public abstract Component accessibleName();

    // 精确输入的取值、回填与格式化规则随核心不同，由子类装配
    protected abstract UiValueTextBinding<Double, String> preciseBinding();

    // ---- kit 核心适配钩子：两个核心暂无共同类型接口，外壳按能力取用 ----

    protected abstract boolean coreDragging();

    protected abstract void coreSetBounds(int x, int y, int width, int height);

    protected abstract void coreSetEnabled(boolean enabled);

    protected abstract void coreSetVisible(boolean visible);

    protected abstract void coreEndInteraction(UiInteractionSession.EndReason reason);


    protected abstract UiRangeEnd coreSelectedEnd();


}
