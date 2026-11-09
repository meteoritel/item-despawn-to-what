package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;

import java.util.Objects;

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
 * <p>本类只关心逻辑与几何，不绘制：区间渲染（时间条与双端轨道）由宿主用 kit 的样式与
 * painter 完成。</p>
 */
public final class UiRangeSlider extends AbstractRangeCore<UiRangeValue> {

    private UiNumberPolicy policy;
    private UiSliderWindow window;
    private UiRangeValue value;
    private boolean backfillInvalid;
    private double pageStep;

    // 用完整数值域作窗口，两端初始为域的两端
    public UiRangeSlider(UiNumberPolicy policy) {
        this(policy, new UiSliderWindow(policy.min(), policy.max()), UiRangeValue.of(policy.min(), policy.max()));
    }

    // 指定窗口与初始值
    public UiRangeSlider(UiNumberPolicy policy, UiSliderWindow window, UiRangeValue value) {
        super(Objects::equals);
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
    @Override
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

    /**
     * 判断区间是否仍在预览，供宿主延后提交关联输入。
     *
     * @return 鼠标或键盘操作尚未结束时为 true
     */
    public boolean isInteracting() {
        return this.interaction.isActive();
    }

    // 大步伐；<=0 或非有限表示不消费 PageUp/PageDown
    public void setPageStep(double pageStep) {
        this.pageStep = Double.isFinite(pageStep) && pageStep > 0.0D ? pageStep : 0.0D;
    }

    public double pageStep() {
        return this.pageStep;
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

    /** {@inheritDoc} */
    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!beginPress(context)) return false;
        boolean fine = context.shiftDown();
        previewSelected(valueAtPointer(context.pointerX(), fine), fine);
        interaction.anchor(context.pointerX(), selectedValue(), fine);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (!isDragging()) return false;
        if (interaction.reanchor(context.pointerX(), selectedValue(), context.shiftDown())) return true;
        double perPixel = policy.dragValuePerPixel(trackWidth(), window.span(), interaction.isFine());
        previewSelected(interaction.dragValue(context.pointerX(), perPixel), interaction.isFine());
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!canFocus() || !isFocused()) {
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

    /**
     * 精确设置当前端的值：先按当前档吸附，再受对端约束；一次调用恰好一条操作。
     *
     * @return 调用是否被接受（端点不存在或禁用时 false）
     */
    public boolean setSelectedValue(double raw, boolean fine) {
        return isEnabled() && oneShotSelected(raw, fine);
    }

    /**
     * 提交当前端的精确输入；缺失端按合法域与对端约束补齐，已有端保持当前档吸附策略。
     * <p>补端与改值都通过同一操作生命周期通知监听器，不产生额外宿主提交。
     *
     * @param raw  输入数值，必须有限
     * @param fine 已有端是否使用细调档
     * @return 控件是否接受输入
     */
    public boolean setSelectedValueOrPresent(double raw, boolean fine) {
        if (!Double.isFinite(raw) || !isEnabled() || isDragging()) return false;
        if (value.isPresent(selectedEnd)) return setSelectedValue(raw, fine);
        return interaction.changeOnce(
                () -> value.withValue(selectedEnd, boundEnd(selectedEnd, policy.clamp(raw))), this::previewValue);
    }

    // 当前端数值；端点不存在时返回 NaN
    public double selectedValue() {
        return this.value.value(this.selectedEnd);
    }

    // 命中端点；两端重叠（区分不出）时返回 null
    @Override
    @Nullable UiRangeEnd hitEnd(double pointerX) {
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

    // 两端都不存在时没有可操作的端点
    @Override
    boolean canStartPress() {
        return !this.value.isEmpty();
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
        if (!value.isPresent(selectedEnd)) return false;
        interaction.beginKey(focusState(), keyCode);
        previewSelected(selectedValue() + direction * policy.step(fine), fine);
        return true;
    }

    private boolean oneShotSelected(double raw, boolean fine) {
        if (!value.isPresent(selectedEnd) || isDragging()) return false;
        return interaction.changeOnce(
                () -> value.withValue(selectedEnd, boundEnd(selectedEnd, policy.snap(raw, fine))), this::previewValue);
    }

    private void previewSelected(double raw, boolean fine) {
        if (!this.value.isPresent(this.selectedEnd)) {
            return;
        }
        // 拖动与连按方向键同样要过策略档位：snap 内含 clamp，
        // 否则会预览出声明范围与精度之外的值（控件显示与校验报警因此不一致）
        double bounded = this.boundEnd(this.selectedEnd, this.policy.snap(raw, fine));
        this.previewValue(this.value.withValue(this.selectedEnd, bounded));
    }

    private void previewValue(UiRangeValue next) {
        if (next.equals(this.value)) {
            return;
        }
        this.value = next;
        this.backfillInvalid = false;
        this.interaction.preview(next);
    }

    @Override
    UiRangeValue snapshotValue() {
        return value;
    }

    @Override
    void restoreValue(UiRangeValue restored) {
        value = restored;
    }

    // 值是否越出数值域（不吸附、不钳制）
    private boolean outsidePolicy(UiRangeValue candidate) {
        if (candidate.lowPresent() && (candidate.low() < this.policy.min() || candidate.low() > this.policy.max())) {
            return true;
        }
        return candidate.highPresent()
                && (candidate.high() < this.policy.min() || candidate.high() > this.policy.max());
    }
}
