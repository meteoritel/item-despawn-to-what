package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 区间类交互核心的共享壳：两个区间控件（{@link UiCyclicRange} 与 {@link UiRangeSlider}）完全相同的
 * 输入生命周期、容器几何与可见性/焦点状态都在这里，数值模型与命中规则留给子类。
 *
 * <p>本类是包内实现细节，不属于 kit 公开面（公开面以 {@code package-info.java} 的登记为准）。
 * 共享方法声明在这里、由公开子类继承，因此 kit 的公开签名不变。</p>
 *
 * <p>子类只提供五个钩子：{@link #hitEnd(double)} 与 {@link #cycleSelectedEnd()} 决定当前端，
 * {@link #canStartPress()} 决定能否开始一次指针交互，
 * {@link #onOperationBegan()} 与 {@link #onOperationFinished(UiInputCapture.EndReason)}
 * 负责各自数值模型的起点快照与提交/回退。</p>
 */
abstract class AbstractRangeCore implements UiInputCapture.Target {

    /** 两端在屏幕上重叠的像素容差；小于等于它视为重叠，按下时切换当前端。 */
    protected static final int OVERLAP_TOLERANCE_PX = 4;

    protected final UiInputCapture capture = new UiInputCapture();
    protected UiRangeEnd selectedEnd = UiRangeEnd.LOW;
    protected @Nullable Function<Double, Component> formatter;
    protected @Nullable Component label;
    // 手柄宽度，参与命中几何；渲染由宿主按自身样式绘制
    protected int thumbWidth = 3;
    protected UiRect bounds = new UiRect(0, 0, 0, 0);
    protected boolean enabled = true;
    protected boolean visible = true;
    protected boolean focused;
    protected boolean dragging;
    protected double dragAnchorPointer;
    protected double dragAnchorValue;
    // 一次操作的起点
    protected boolean operationActive;
    // 合并 key-up 的行为：当前按住的键码
    protected int heldKey = -1;

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
        return this.dragging;
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
            this.endInteraction(UiInputCapture.EndReason.DISABLED);
        }
        this.enabled = enabled;
    }

    public boolean isVisible() {
        return this.visible;
    }

    // 转为隐藏时结束未完成的交互（回退）
    public void setVisible(boolean visible) {
        if (this.visible && !visible) {
            this.endInteraction(UiInputCapture.EndReason.HIDDEN);
        }
        this.visible = visible;
    }

    public boolean canFocus() {
        return this.visible && this.enabled;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isFocused() {
        return this.focused;
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
        if (this.dragging) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        UiRangeEnd target = this.hitEnd(context.pointerX());
        this.selectedEnd = target != null ? target : this.cycleSelectedEnd();
        this.capture.begin(this);
        this.dragging = true;
        this.beginOperation();
        this.dragAnchorPointer = context.pointerX();
        return true;
    }

    // 拖动是否仍由本控件接管：拖动中且捕获属于自己
    protected final boolean canDrag() {
        return this.dragging && this.capture.isCapturedBy(this);
    }

    // 隐藏或禁用时不消费键盘事件
    protected final boolean acceptsKey() {
        return this.visible && this.enabled;
    }

    public boolean mouseReleased(UiInputContext context) {
        if (!this.dragging) {
            return false;
        }
        this.capture.release();
        return true;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.heldKey == -1 || keyCode != this.heldKey) {
            return false;
        }
        this.heldKey = -1;
        // 方向键重复只在 key-up 合并为一次提交
        this.endInteraction(UiInputCapture.EndReason.RELEASE);
        return true;
    }

    // 统一结束入口：宿主隐藏/禁用/卸载/失焦范围切换/关闭以及释放都走这里
    public void endInteraction(UiInputCapture.EndReason reason) {
        Objects.requireNonNull(reason, "reason");
        this.dragging = false;
        this.heldKey = -1;
        if (this.capture.isCapturedBy(this)) {
            this.capture.end(reason);
            return;
        }
        this.finishOperation(reason);
    }

    // 控件从界面卸载
    public void unmount() {
        this.endInteraction(UiInputCapture.EndReason.UNMOUNTED);
    }

    // 焦点作用域切换（模态打开、页面切换）
    public void onFocusScopeChanged() {
        this.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
    }

    // 宿主关闭
    public void onHostClosed() {
        this.endInteraction(UiInputCapture.EndReason.HOST_CLOSED);
    }

    @Override
    public void captureEnded(UiInputCapture.EndReason reason) {
        this.finishOperation(reason);
    }

    /**
     * 取消当前操作；没有进行中的操作或捕获时返回 false，表示调用方不消费该键。
     */
    protected final boolean cancelInteraction() {
        if (!this.operationActive && !this.capture.isCapturedBy(this)) {
            return false;
        }
        this.endInteraction(UiInputCapture.EndReason.CANCEL);
        return true;
    }

    // 操作生命周期：起点快照与提交/回退由子类按数值模型实现
    protected final void beginOperation() {
        if (this.operationActive) {
            return;
        }
        this.operationActive = true;
        this.onOperationBegan();
    }

    protected final void finishOperation(UiInputCapture.EndReason reason) {
        this.dragging = false;
        this.heldKey = -1;
        if (!this.operationActive) {
            return;
        }
        this.operationActive = false;
        this.onOperationFinished(reason);
    }

    /** 操作开始时快照起点并通知监听器。 */
    protected abstract void onOperationBegan();

    /** 操作结束时按 {@code reason} 决定提交还是回退到起点。 */
    protected abstract void onOperationFinished(UiInputCapture.EndReason reason);

    /** 当前端命中的端点；无法区分（两端重叠或端点不构成区间）时返回 null，表示切换到另一端。 */
    protected abstract @Nullable UiRangeEnd hitEnd(double pointerX);

    /** 在可用端之间切换当前端。 */
    protected abstract UiRangeEnd cycleSelectedEnd();

    /** 当前数值模型是否具备开始指针交互的前提（如两端都不存在时不能开始）。 */
    protected abstract boolean canStartPress();
}
