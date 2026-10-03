package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScalarSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueInteraction;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 数值滑杆：左侧标签，右侧当前值，中间像素轨道与滑块。
 * <p>数值一律同时显示为文本，颜色不是唯一线索。
 * <p>本类是宿主适配层：数值域、窗口、吸附、捕获、preview/commit/cancel 全部委托给 kit 的
 * {@link UiScalarSlider}，公开方法签名与旧版保持一致（fluent setter 返回自身）。
 * <p>{@link #setOnChanged(DoubleConsumer)} 保留旧的「每次变化都回调」语义，桥接到 kit 的
 * preview 回调；需要在提交时才落盘的调用方请直接用 kit 的交互回调或 {@link #core()}。
 */
public final class UiSlider implements UiWidget, UiFocusTarget, UiInputTarget {

    // 轨道高度
    public static final int TRACK_HEIGHT = 4;
    // 滑块宽度
    public static final int THUMB_WIDTH = 3;

    private final UiScalarSlider core;
    private double min;
    private double max;
    private double step;

    public UiSlider(Font font, double min, double max, double step) {
        Objects.requireNonNull(font, "font");
        this.min = sanitizeMin(min);
        this.max = sanitizeMax(this.min, max);
        this.step = sanitizeStep(step);
        this.core = new UiScalarSlider(buildPolicy(), new UiSliderWindow(this.min, this.max));
        this.core.setStyle(UiTheme.sliderStyle());
    }

    // kit 核心，供需要窗口/样式/提交回调等新能力的调用方直接使用
    public UiScalarSlider core() {
        return this.core;
    }

    // 设置取值域与步长（保留旧行为：把当前值钳制回新取值域，不按档吸附）
    public UiSlider setRange(double nextMin, double nextMax, double nextStep) {
        this.min = sanitizeMin(nextMin);
        this.max = sanitizeMax(this.min, nextMax);
        this.step = sanitizeStep(nextStep);
        this.core.setPolicy(buildPolicy());
        this.core.setWindow(new UiSliderWindow(this.min, this.max));
        setValue(this.core.value());
        return this;
    }

    public double value() {
        return this.core.value();
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double step() {
        return this.step;
    }

    // 回填当前值：不吸附、不回调、不产生历史；越界与非法值按取值域钳制后写入
    public UiSlider setValue(double next) {
        double finite = Double.isFinite(next) ? next : this.min;
        this.core.setValue(Math.clamp(finite, this.min, this.max));
        return this;
    }

    // 精确回填：越界或非有限时返回 false 并保留原值（不吸附、不回调）
    public boolean trySetValue(double next) {
        return this.core.setValue(next);
    }

    // 上一次精确回填是否越出取值域
    public boolean isBackfillInvalid() {
        return this.core.isBackfillInvalid();
    }

    public UiSlider setLabel(Component next) {
        this.core.setLabel(next == null ? Component.empty() : next);
        return this;
    }

    public UiSlider setFormatter(@Nullable Function<Double, Component> next) {
        this.core.setFormatter(next);
        return this;
    }

    // 旧的逐次回调语义：值每变化一次就回调一次（桥接到 kit 的 preview 回调）
    public UiSlider setOnChanged(@Nullable DoubleConsumer listener) {
        this.core.setInteractionListener(listener == null ? null : new UiValueInteraction.Listener() {
            @Override
            public void previewed(double startValue, double currentValue) {
                listener.accept(currentValue);
            }
        });
        return this;
    }

    public UiSlider setEnabled(boolean next) {
        this.core.setEnabled(next);
        return this;
    }

    public boolean isEnabled() {
        return this.core.isEnabled();
    }

    public UiSlider setVisible(boolean next) {
        this.core.setVisible(next);
        return this;
    }

    public UiSlider setError(boolean next) {
        this.core.setError(next);
        return this;
    }

    // 是否正在拖动
    public boolean isDragging() {
        return this.core.isDragging();
    }

    // 统一结束入口：宿主隐藏/禁用/卸载/失焦作用域切换/关闭时调用，会回退未提交的预览
    public void endInteraction(UiInputCapture.EndReason reason) {
        this.core.endInteraction(reason);
    }

    // 控件从界面卸载
    public void unmount() {
        this.core.unmount();
    }

    // 焦点作用域切换（模态打开、页面切换）
    public void onFocusScopeChanged() {
        this.core.onFocusScopeChanged();
    }

    // 宿主关闭
    public void onHostClosed() {
        this.core.onHostClosed();
    }

    // 当前值的显示文本
    public Component formatValue() {
        return this.core.formatValue();
    }

    @Override
    public boolean isVisible() {
        return this.core.isVisible();
    }

    @Override
    public UiRect bounds() {
        return this.core.bounds();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.core.setBounds(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!this.core.isVisible()) {
            return;
        }
        this.core.render(graphics, renderFont, pointer(mouseX, mouseY, -1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.core.mousePressed(pointer(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.core.mouseDragged(pointer(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.core.mouseReleased(pointer(mouseX, mouseY, button));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.core.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.core.keyReleased(keyCode, scanCode, modifiers);
    }

    // UiWidget 与 UiInputTarget 都带默认实现，这里显式覆盖以消除「不相关默认值」冲突
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    @Override
    public boolean canFocus() {
        return this.core.canFocus();
    }

    @Override
    public void setFocused(boolean next) {
        this.core.setFocused(next);
    }

    @Override
    public boolean isFocused() {
        return this.core.isFocused();
    }

    @Override
    public boolean activate() {
        return this.core.activate();
    }

    @Override
    public @Nullable Component accessibleName() {
        return this.core.accessibleName();
    }

    // 旧签名没有修饰键参数，这里按当前键盘状态补齐 Shift/Ctrl/Alt 快照
    private static UiInputContext pointer(double mouseX, double mouseY, int button) {
        int modifiers = 0;
        if (Screen.hasShiftDown()) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (Screen.hasControlDown()) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (Screen.hasAltDown()) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return UiInputContext.pointer(mouseX, mouseY, button, modifiers);
    }

    // 用当前取值域与步长构造 kit 数值策略；step<=0 时沿用旧版键盘回退档
    private UiNumberPolicy buildPolicy() {
        double coarse = this.step > 0.0D ? this.step : Math.max(1.0E-4D, (this.max - this.min) / 100.0D);
        return UiNumberPolicy.of(this.min, this.max, coarse);
    }

    private static double sanitizeMin(double nextMin) {
        return Double.isFinite(nextMin) ? nextMin : 0.0D;
    }

    private static double sanitizeMax(double safeMin, double nextMax) {
        return Double.isFinite(nextMax) && nextMax > safeMin ? nextMax : safeMin + 1.0D;
    }

    private static double sanitizeStep(double nextStep) {
        return Double.isFinite(nextStep) && nextStep > 0.0D ? nextStep : 0.0D;
    }

}
