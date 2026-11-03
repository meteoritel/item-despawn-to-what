package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiPredicate;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 区间类交互核心的共享壳：两个区间控件（{@link UiCyclicRange} 与 {@link UiRangeSlider}）完全相同的
 * 输入生命周期、容器几何与可见性/焦点状态都在这里，数值模型与命中规则留给子类。
 *
 * <p>本类是包内实现细节，不属于 kit 公开面（公开面以 {@code package-info.java} 的登记为准）。
 * 外壳通过 UiRangeControl 使用公共能力，数值快照由具体核心提供。</p>
 *
 * <p>值快照由具体模型提供，操作状态统一由 UiValueInteraction 持有；外壳只依赖 UiRangeControl。</p>
 */
abstract class AbstractRangeCore<V> implements UiRangeControl {

    /**
     * 两端在屏幕上重叠的像素容差；小于等于它视为重叠，按下时切换当前端。
     */
    protected static final int OVERLAP_TOLERANCE_PX = 4;

    protected UiInteractionSession capture;
    protected UiRangeEnd selectedEnd = UiRangeEnd.LOW;
    protected @Nullable Function<Double, Component> formatter;
    protected @Nullable Component label;
    // 手柄宽度，参与命中几何；渲染由宿主按自身样式绘制
    protected int thumbWidth = 3;
    protected UiRect bounds = new UiRect(0, 0, 0, 0);
    protected boolean enabled = true;
    protected boolean visible = true;
    protected final UiFocusState focusState = new UiFocusState();
    protected double dragAnchorPointer;
    protected double dragAnchorValue;
    protected final UiValueInteraction<V> interaction;
    protected @Nullable UiValueInteraction.Listener<V> listener;

    protected AbstractRangeCore(BiPredicate<V, V> equivalent) {
        interaction = new UiValueInteraction<>(Objects::nonNull, equivalent);
    }

    /** 当前值操作监听器。 */
    public @Nullable UiValueInteraction.Listener<V> listener() {
        return listener;
    }

    /** 设置后续值操作的监听器；已开始的操作保持自己的监听器快照。 */
    public void setListener(@Nullable UiValueInteraction.Listener<V> listener) {
        this.listener = listener;
    }

    public UiRect bounds() {
        return this.bounds;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
    }

    public int thumbWidth() {
        return this.thumbWidth;
    }

    public void setThumbWidth(int thumbWidth) {
        this.thumbWidth = Math.max(1, thumbWidth);
    }

    public int trackLeft() {
        return this.bounds.x() + this.thumbWidth;
    }

    public int trackRight() {
        return Math.max(this.trackLeft() + 1, this.bounds.right() - this.thumbWidth);
    }

    public int trackWidth() {
        return Math.max(1, this.trackRight() - this.trackLeft());
    }

    // 指针是否落在命中区
    public boolean containsPointer(double pointerX, double pointerY) {
        return this.bounds.contains(pointerX, pointerY);
    }

    public boolean isDragging() {
        return capture != null && capture.owns(this) && capture.matches(UiInteractionSession.Source.POINTER, 0);
    }

    public UiRangeEnd selectedEnd() {
        return this.selectedEnd;
    }

    public @Nullable Function<Double, Component> formatter() {
        return this.formatter;
    }

    public void setFormatter(@Nullable Function<Double, Component> formatter) {
        this.formatter = formatter;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    // 转为禁用时结束未完成的交互（回退）
    public void setEnabled(boolean enabled) {
        if (this.enabled && !enabled) {
            this.endInteraction(UiInteractionSession.EndReason.DISABLED);
        }
        this.enabled = enabled;
    }

    public boolean isVisible() {
        return this.visible;
    }

    // 转为隐藏时结束未完成的交互（回退）
    public void setVisible(boolean visible) {
        if (this.visible && !visible) {
            this.endInteraction(UiInteractionSession.EndReason.HIDDEN);
        }
        this.visible = visible;
    }

    public UiFocusState focusState() {
        return focusState;
    }

    @Override
    public boolean canFocus() {
        return this.visible && this.enabled;
    }


    public boolean isFocused() {
        return focusState.isFocused();
    }

    public boolean activate() {
        return false;
    }

    public @Nullable Component accessibleName() {
        return this.label;
    }

    public @Nullable Component label() {
        return this.label;
    }

    public void setLabel(@Nullable Component label) {
        this.label = label;
    }

    /**
     * 指针按下的共享前置：可见/可用/左键/命中/可交互性校验，选定当前端并开始捕获与操作。
     *
     * @return 是否接受本次按下；false 表示调用方直接返回 false
     */
    protected final boolean beginPress(UiInputContext context) {
        if (!this.visible || !this.enabled || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (!this.containsPointer(context.pointerX(), context.pointerY())) {
            return false;
        }
        if (!this.canStartPress()) {
            return false;
        }
        if (isDragging()) {
            this.endInteraction(UiInteractionSession.EndReason.RELEASE);
        }
        UiRangeEnd target = this.hitEnd(context.pointerX());
        this.selectedEnd = target != null ? target : this.cycleSelectedEnd();

        this.capture = context.scope().interaction();
        this.capture.begin(this, UiInteractionSession.Source.POINTER, context.button());

        this.beginOperation();
        this.dragAnchorPointer = context.pointerX();
        return true;
    }

    // 拖动是否仍由本控件接管：拖动中且捕获属于自己
    protected final boolean canDrag() {
        return isDragging();
    }

    // 隐藏或禁用时不消费键盘事件
    protected final boolean acceptsKey() {
        return this.visible && this.enabled && isFocused();
    }

    public boolean mouseReleased(UiInputContext context) {
        if (!isDragging()) {
            return false;
        }
        capture.end(this, UiInteractionSession.EndReason.RELEASE);
        return true;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (capture == null || !capture.owns(this) || !capture.matches(UiInteractionSession.Source.KEY, keyCode))
            return false;
        endInteraction(UiInteractionSession.EndReason.RELEASE);
        return true;
    }

    // 统一结束入口：宿主隐藏/禁用/卸载/失焦范围切换/关闭以及释放都走这里
    public void endInteraction(UiInteractionSession.EndReason reason) {
        Objects.requireNonNull(reason, "reason");


        if ((capture != null && capture.owns(this))) {
            capture.end(this, reason);
            return;
        }
        this.finishOperation(reason);
    }


    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        this.finishOperation(reason);
    }

    /**
     * 取消当前操作；没有进行中的操作或捕获时返回 false，表示调用方不消费该键。
     */
    protected final boolean cancelInteraction() {
        if (!this.interaction.isActive() && !(capture != null && capture.owns(this))) {
            return false;
        }
        this.endInteraction(UiInteractionSession.EndReason.CANCEL);
        return true;
    }

    protected final void beginOperation() {
        interaction.begin(snapshotValue(), listener);
    }

    protected final void finishOperation(UiInteractionSession.EndReason reason) {
        if (!interaction.isActive()) return;
        if (reason.cancels()) {
            restoreValue(interaction.startValue());
            interaction.cancel();
        } else {
            interaction.commit();
        }
    }

    /** 读取本模型的不可变值快照。 */
    protected abstract V snapshotValue();

    /** 取消时在通知监听器前恢复实际模型。 */
    protected abstract void restoreValue(V value);

    /**
     * 当前端命中的端点；无法区分（两端重叠或端点不构成区间）时返回 null，表示切换到另一端。
     */
    protected abstract @Nullable UiRangeEnd hitEnd(double pointerX);

    /**
     * 在可用端之间切换当前端。
     */
    protected abstract UiRangeEnd cycleSelectedEnd();

    /**
     * 当前数值模型是否具备开始指针交互的前提（如两端都不存在时不能开始）。
     */
    protected abstract boolean canStartPress();
}
