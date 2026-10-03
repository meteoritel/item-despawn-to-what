package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 双端区间交互核心（无渲染、无宿主主题依赖）。
 *
 * <p>核心不变量与契约：</p>
 * <ul>
 *   <li>两端都存在时保持 {@code low <= high}；把一端拖到对端的表现是停在同值，
 *       绝不会交换两个端点的身份（当前端用 {@link UiRangeEnd} 记录）。</li>
 *   <li>两端可以分别用键盘或精确入口访问：{@link #selectEnd(UiRangeEnd)} 选择当前端，
 *       键盘与 {@link #setSelectedValue(double, boolean)} 只作用于当前端。</li>
 *   <li>两端重叠时端点命中无法区分，此时点击会切换当前端，再由后续拖动移动它。</li>
 *   <li>一次单独编辑（一次点击、一次键盘按下或一次精确设置）恰好产生一条操作记录：
 *       没有数值变化就不回调 committed。</li>
 * </ul>
 *
 * <p>本类只关心逻辑与几何，不绘制：区间渲染（P4 时间条/双端轨道）由宿主用 kit 的样式与
 * painter 完成。</p>
 */
public final class UiRangeSlider implements UiFocusTarget, UiInputTarget, UiInputCapture.Target {

    /** 区间交互生命周期回调。 */
    @SuppressWarnings("unused")
    public interface Listener {

        // 一次操作开始（拖动按下、键盘首次调整或精确设置前）
        default void began(UiRangeValue startValue) {
        }

        // 预览：值已变化但尚未提交
        default void previewed(UiRangeValue startValue, UiRangeValue currentValue) {
        }

        // 提交：操作结束时值确实发生了变化
        default void committed(UiRangeValue startValue, UiRangeValue currentValue) {
        }

        // 取消：值已回退到操作开始时的状态
        default void cancelled(UiRangeValue startValue, UiRangeValue restoredValue) {
        }
    }

    /** 空回调。 */
    public static final Listener NONE = new Listener() {
    };

    // 两端在屏幕上重叠的像素容差；小于等于它视为重叠，命中时切换当前端
    private static final int OVERLAP_TOLERANCE_PX = 4;

    private final UiInputCapture capture = new UiInputCapture();
    private UiNumberPolicy policy;
    private UiSliderWindow window;
    private UiRangeValue value;
    private UiRangeEnd selectedEnd = UiRangeEnd.LOW;
    private @Nullable Listener listener;
    private @Nullable Function<Double, Component> formatter;
    private @Nullable Component label;
    // 手柄宽度，供命中几何使用；渲染由宿主按自身样式绘制
    private int thumbWidth = 3;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean backfillInvalid;
    private double pageStep;
    private boolean dragging;
    private double dragAnchorPointer;
    private double dragAnchorValue;
    private boolean dragFineAnchor;
    // 一次操作的起点
    private boolean operationActive;
    private UiRangeValue operationStart = UiRangeValue.EMPTY;
    // 合并 key-up 的行为：当前按住的键码
    private int heldKey = -1;

    // 用完整数值域作窗口，两端初始为域的两端
    public UiRangeSlider(UiNumberPolicy policy) {
        this(policy, new UiSliderWindow(policy.min(), policy.max()), UiRangeValue.of(policy.min(), policy.max()));
    }

    // 指定窗口与初始值
    public UiRangeSlider(UiNumberPolicy policy, UiSliderWindow window, UiRangeValue value) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.window = Objects.requireNonNull(window, "window");
        this.value = Objects.requireNonNull(value, "value");
    }

    public UiNumberPolicy policy() {
        return this.policy;
    }

    // 替换数值策略；当前值不重新吸附，仅重新校验是否越域
    public void setPolicy(UiNumberPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.backfillInvalid = this.outsidePolicy(this.value);
    }

    public UiSliderWindow window() {
        return this.window;
    }

    // 替换轨道显示窗口；不钳制任何已存在的域内值
    public void setWindow(UiSliderWindow window) {
        this.window = Objects.requireNonNull(window, "window");
    }

    public UiRangeValue value() {
        return this.value;
    }

    /**
     * 回填接口：直接设置区间状态，不吸附、不回调、不产生历史。
     *
     * @return 值是否在数值域内且被接受；越域时返回 false 并置 {@link #isBackfillInvalid()}
     */
    public boolean setValue(UiRangeValue next) {
        Objects.requireNonNull(next, "value");
        if (this.outsidePolicy(next)) {
            this.backfillInvalid = true;
            return false;
        }
        this.backfillInvalid = false;
        this.value = next;
        if (!next.isPresent(this.selectedEnd)) {
            this.selectedEnd = next.isPresent(UiRangeEnd.LOW) ? UiRangeEnd.LOW : UiRangeEnd.HIGH;
        }
        return true;
    }

    // 上一次回填是否越域
    public boolean isBackfillInvalid() {
        return this.backfillInvalid;
    }

    public UiRangeEnd selectedEnd() {
        return this.selectedEnd;
    }

    // 选择当前端；所选端不存在时回退到存在的一端
    public boolean selectEnd(UiRangeEnd end) {
        Objects.requireNonNull(end, "end");
        if (!this.value.isPresent(end)) {
            if (!this.value.isPresent(end.other())) {
                return false;
            }
            end = end.other();
        }
        if (this.selectedEnd == end) {
            return false;
        }
        this.selectedEnd = end;
        return true;
    }

    // 在两端之间切换当前端（重叠点击用）
    public UiRangeEnd cycleSelectedEnd() {
        if (this.value.isPresent(UiRangeEnd.LOW) && this.value.isPresent(UiRangeEnd.HIGH)) {
            this.selectedEnd = this.selectedEnd.other();
        } else if (this.value.isPresent(UiRangeEnd.LOW)) {
            this.selectedEnd = UiRangeEnd.LOW;
        } else {
            this.selectedEnd = UiRangeEnd.HIGH;
        }
        return this.selectedEnd;
    }

    // 启用/停用某一端；停用后该端不参与交互，数值以 NaN 表达
    public boolean setEndPresent(UiRangeEnd end, boolean present) {
        Objects.requireNonNull(end, "end");
        if (present == this.value.isPresent(end)) {
            return false;
        }
        if (!present) {
            this.value = this.value.withAbsent(end);
            if (this.selectedEnd == end) {
                this.selectedEnd = this.value.isPresent(end.other()) ? end.other() : end;
            }
            return true;
        }
        double position = end == UiRangeEnd.LOW ? this.policy.min() : this.policy.max();
        if (end == UiRangeEnd.LOW && this.value.isPresent(UiRangeEnd.HIGH)) {
            position = Math.min(position, this.value.high());
        }
        if (end == UiRangeEnd.HIGH && this.value.isPresent(UiRangeEnd.LOW)) {
            position = Math.max(position, this.value.low());
        }
        this.value = this.value.withValue(end, position);
        return true;
    }

    public @Nullable Listener listener() {
        return this.listener;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public UiRect bounds() {
        return this.bounds;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
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

    // 是否正在拖动
    public boolean isDragging() {
        return this.dragging;
    }

    // 大步伐；<=0 或非有限表示不消费 PageUp/PageDown
    public void setPageStep(double pageStep) {
        this.pageStep = Double.isFinite(pageStep) && pageStep > 0.0D ? pageStep : 0.0D;
    }

    public double pageStep() {
        return this.pageStep;
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

    // 端点横坐标（窗口外取比例会超出轨道，由调用方按需钳制）
    public int xOf(double v) {
        double fraction = this.window.clampFraction(this.window.fraction(v));
        return this.trackLeft() + (int) Math.round(fraction * this.trackWidth());
    }

    // 指针位置对应的数值并吸附
    public double valueAtPointer(double pointerX, boolean fine) {
        double fraction = this.trackWidth() <= 0 ? 0.0D
                : Mth.clamp((pointerX - this.trackLeft()) / (double) this.trackWidth(), 0.0D, 1.0D);
        return this.policy.snap(this.window.valueAt(fraction), fine);
    }

    // 指针是否落在命中区
    public boolean containsPointer(double pointerX, double pointerY) {
        return this.bounds.contains(pointerX, pointerY);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!this.visible || !this.enabled || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (!this.containsPointer(context.pointerX(), context.pointerY())) {
            return false;
        }
        if (this.value.isEmpty()) {
            return false;
        }
        if (this.dragging) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        UiRangeEnd target = this.hitEnd(context.pointerX());
        if (target == null) {
            target = this.cycleSelectedEnd();
        } else {
            this.selectedEnd = target;
        }
        this.capture.begin(this);
        this.dragging = true;
        this.beginOperation();
        this.dragAnchorPointer = context.pointerX();
        this.dragAnchorValue = this.value.value(this.selectedEnd);
        this.dragFineAnchor = context.shiftDown();
        this.previewSelected(this.valueAtPointer(context.pointerX(), this.dragFineAnchor), this.dragFineAnchor);
        return true;
    }

    @Override
    public boolean mouseDragged(UiInputContext context) {
        if (!this.dragging || !this.capture.isCapturedBy(this)) {
            return false;
        }
        if (context.shiftDown() != this.dragFineAnchor) {
            // Shift 状态改变时重设锚点，值保持不变
            this.dragFineAnchor = context.shiftDown();
            this.dragAnchorPointer = context.pointerX();
            this.dragAnchorValue = this.value.value(this.selectedEnd);
            return true;
        }
        double perPixel = this.policy.dragValuePerPixel(this.trackWidth(), this.window.span(), this.dragFineAnchor);
        double raw = this.dragAnchorValue + (context.pointerX() - this.dragAnchorPointer) * perPixel;
        this.previewSelected(raw, this.dragFineAnchor);
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        if (!this.dragging) {
            return false;
        }
        this.capture.release();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.visible || !this.enabled) {
            return false;
        }
        boolean fine = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> this.adjustByKey(keyCode, -1.0D, fine);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> this.adjustByKey(keyCode, 1.0D, fine);
            case GLFW.GLFW_KEY_HOME -> this.oneShotSelected(this.window.min(), fine);
            case GLFW.GLFW_KEY_END -> this.oneShotSelected(this.window.max(), fine);
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.pageStep > 0.0D
                    && this.oneShotSelected(this.selectedValue() - this.pageStep, fine);
            case GLFW.GLFW_KEY_PAGE_UP -> this.pageStep > 0.0D
                    && this.oneShotSelected(this.selectedValue() + this.pageStep, fine);
            case GLFW.GLFW_KEY_ESCAPE -> this.cancelInteraction();
            default -> false;
        };
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.heldKey == -1 || keyCode != this.heldKey) {
            return false;
        }
        this.heldKey = -1;
        // 方向键重复只在 key-up 合并为一次提交
        this.endInteraction(UiInputCapture.EndReason.RELEASE);
        return true;
    }

    /**
     * 精确设置当前端的值：先按当前档吸附，再受对端约束；一次调用恰好一条操作。
     *
     * @return 调用是否被接受（端点不存在或禁用时 false）
     */
    public boolean setSelectedValue(double raw, boolean fine) {
        if (!this.enabled || !this.value.isPresent(this.selectedEnd) || this.dragging) {
            return false;
        }
        if (this.operationActive) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        double bounded = this.boundEnd(this.selectedEnd, this.policy.snap(raw, fine));
        UiRangeValue candidate = this.value.withValue(this.selectedEnd, bounded);
        if (candidate.equals(this.value)) {
            return true;
        }
        this.beginOperation();
        this.previewValue(candidate);
        this.endInteraction(UiInputCapture.EndReason.RELEASE);
        return true;
    }

    // 当前端数值；端点不存在时返回 NaN
    public double selectedValue() {
        return this.value.value(this.selectedEnd);
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

    @Override
    public boolean canFocus() {
        return this.visible && this.enabled;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }

    @Override
    public boolean activate() {
        return false;
    }

    @Override
    public @Nullable Component accessibleName() {
        return this.label;
    }

    public @Nullable Component label() {
        return this.label;
    }

    public void setLabel(@Nullable Component label) {
        this.label = label;
    }

    // 手柄宽度，参与命中几何；默认 3 像素
    public int thumbWidth() {
        return this.thumbWidth;
    }

    public void setThumbWidth(int thumbWidth) {
        this.thumbWidth = Math.max(1, thumbWidth);
    }

    // 命中端点；两端重叠（区分不出）时返回 null
    private @Nullable UiRangeEnd hitEnd(double pointerX) {
        boolean low = this.value.isPresent(UiRangeEnd.LOW);
        boolean high = this.value.isPresent(UiRangeEnd.HIGH);
        if (low && high) {
            int lowX = this.xOf(this.value.low());
            int highX = this.xOf(this.value.high());
            if (Math.abs(lowX - highX) <= OVERLAP_TOLERANCE_PX) {
                return null;
            }
            return Math.abs(pointerX - lowX) <= Math.abs(pointerX - highX) ? UiRangeEnd.LOW : UiRangeEnd.HIGH;
        }
        return low ? UiRangeEnd.LOW : UiRangeEnd.HIGH;
    }

    // 把候选值约束在对端之内（不交换身份）
    private double boundEnd(UiRangeEnd end, double candidate) {
        if (end == UiRangeEnd.LOW) {
            double upper = this.value.isPresent(UiRangeEnd.HIGH) ? this.value.high() : this.policy.max();
            return Math.min(candidate, upper);
        }
        double lower = this.value.isPresent(UiRangeEnd.LOW) ? this.value.low() : this.policy.min();
        return Math.max(candidate, lower);
    }

    private boolean adjustByKey(int keyCode, double direction, boolean fine) {
        if (!this.value.isPresent(this.selectedEnd)) {
            return false;
        }
        if (this.heldKey != -1 && this.heldKey != keyCode) {
            this.heldKey = -1;
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        if (!this.operationActive) {
            this.beginOperation();
        }
        this.heldKey = keyCode;
        this.capture.begin(this);
        this.previewSelected(this.selectedValue() + direction * this.policy.step(fine), fine);
        return true;
    }

    private boolean oneShotSelected(double raw, boolean fine) {
        if (!this.value.isPresent(this.selectedEnd) || this.dragging) {
            return false;
        }
        if (this.operationActive) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        double bounded = this.boundEnd(this.selectedEnd, this.policy.snap(raw, fine));
        UiRangeValue candidate = this.value.withValue(this.selectedEnd, bounded);
        if (!candidate.equals(this.value)) {
            this.beginOperation();
            this.previewValue(candidate);
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        return true;
    }

    private boolean cancelInteraction() {
        if (!this.operationActive && !this.capture.isCapturedBy(this)) {
            return false;
        }
        this.endInteraction(UiInputCapture.EndReason.CANCEL);
        return true;
    }

    private void previewSelected(double raw, boolean fine) {
        if (!this.value.isPresent(this.selectedEnd)) {
            return;
        }
        this.previewValue(this.value.withValue(this.selectedEnd, this.boundEnd(this.selectedEnd, raw)));
    }

    private boolean previewValue(UiRangeValue next) {
        if (next.equals(this.value)) {
            return false;
        }
        this.value = next;
        this.backfillInvalid = false;
        if (this.operationActive && this.listener != null) {
            this.listener.previewed(this.operationStart, next);
        }
        return true;
    }

    private void beginOperation() {
        if (this.operationActive) {
            return;
        }
        this.operationActive = true;
        this.operationStart = this.value;
        if (this.listener != null) {
            this.listener.began(this.operationStart);
        }
    }

    private void finishOperation(UiInputCapture.EndReason reason) {
        this.dragging = false;
        this.heldKey = -1;
        if (!this.operationActive) {
            return;
        }
        UiRangeValue start = this.operationStart;
        UiRangeValue current = this.value;
        boolean changed = !current.equals(start);
        this.operationActive = false;
        if (this.listener == null) {
            return;
        }
        if (reason.cancels()) {
            this.value = start;
            this.listener.cancelled(start, start);
        } else if (changed) {
            this.listener.committed(start, current);
        }
    }

    // 值是否越出数值域（不吸附、不钳制）
    private boolean outsidePolicy(UiRangeValue candidate) {
        if (candidate.lowPresent() && (candidate.low() < this.policy.min() || candidate.low() > this.policy.max())) {
            return true;
        }
        return candidate.highPresent()
                && (candidate.high() < this.policy.min() || candidate.high() > this.policy.max());
    }

    public @Nullable Function<Double, Component> formatter() {
        return this.formatter;
    }

    public void setFormatter(@Nullable Function<Double, Component> formatter) {
        this.formatter = formatter;
    }
}
