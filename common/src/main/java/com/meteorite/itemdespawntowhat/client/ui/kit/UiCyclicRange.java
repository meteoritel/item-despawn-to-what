package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 周期区间交互核心（无渲染、无宿主主题依赖）。
 *
 * <p>与普通区间不同，周期区间不强制 {@code from <= to}：{@code from > to} 表示区间跨越周期边界，
 * 由宿主按跨周期样式高亮；{@code from == to} 表示单点区间。</p>
 *
 * <p>数值一律折返到 {@code [0, period)}，比较用折返后的值，因此内部不存在「等价但不同」的两个表示。
 * 周期、刻度（吸附网格）与标记都由宿主注入：kit 不读取 {@code Level}、不假定现实世界时间，
 * 也不猜测任何一天/一月多长。</p>
 */
public final class UiCyclicRange implements UiFocusTarget, UiInputTarget, UiInputCapture.Target {

    /** 宿主注入的刻度；label 可为空表示只有刻度线。 */
    public record Tick(double tick, @Nullable Component label) {
    }

    /** 宿主注入的标记；只用于展示，不参与吸附。 */
    public record Marker(double tick, int color) {
    }

    /** 周期区间交互生命周期回调。 */
    @SuppressWarnings("unused")
    public interface Listener {

        // 一次操作开始
        default void began(double from, double to) {
        }

        // 预览：值已变化但尚未提交
        default void previewed(double startFrom, double startTo, double from, double to) {
        }

        // 提交：操作结束时值确实发生了变化
        default void committed(double startFrom, double startTo, double from, double to) {
        }

        // 取消：值已回退到操作开始时的状态
        default void cancelled(double startFrom, double startTo, double restoredFrom, double restoredTo) {
        }
    }

    /** 空回调。 */
    public static final Listener NONE = new Listener() {
    };

    private final UiInputCapture capture = new UiInputCapture();
    private double period;
    private double from;
    private double to;
    private double tickStep = 1.0D;
    private List<Tick> ticks = List.of();
    private List<Marker> markers = List.of();
    private UiRangeEnd selectedEnd = UiRangeEnd.LOW;
    private @Nullable Listener listener;
    private @Nullable Function<Double, Component> formatter;
    private @Nullable Component label;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private int thumbWidth = 3;
    private boolean dragging;
    private double dragAnchorPointer;
    private double dragAnchorValue;
    // 一次操作的起点
    private boolean operationActive;
    private double startFrom;
    private double startTo;
    // 合并 key-up 的行为：当前按住的键码
    private int heldKey = -1;

    public UiCyclicRange(double period) {
        this.period = requirePeriod(period);
    }

    public double period() {
        return this.period;
    }

    // 替换周期并把两端折返到新周期内
    public void setPeriod(double period) {
        this.period = requirePeriod(period);
        this.from = this.wrap(this.from);
        this.to = this.wrap(this.to);
    }

    public double from() {
        return this.from;
    }

    public double to() {
        return this.to;
    }

    /**
     * 回填接口：直接设置两端，不吸附、不回调、不产生历史，不做 {@code min <= max} 约束。
     */
    public void setRange(double from, double to) {
        this.from = this.wrap(from);
        this.to = this.wrap(to);
    }

    // 折返到 [0, period)
    public double wrap(double tick) {
        if (!Double.isFinite(tick)) {
            return 0.0D;
        }
        double wrapped = tick % this.period;
        return wrapped < 0.0D ? wrapped + this.period : wrapped;
    }

    // 从起点顺时针走到终点的长度，落在 [0, period)
    public double span() {
        return this.wrap(this.to - this.from);
    }

    // 两端同刻
    public boolean isSinglePoint() {
        return this.from == this.to;
    }

    // 是否跨周期边界（宿主据此高亮）
    public boolean crossesPeriod() {
        return this.to < this.from;
    }

    public List<Tick> ticks() {
        return this.ticks;
    }

    // 宿主注入刻度
    public void setTicks(List<Tick> ticks) {
        this.ticks = List.copyOf(Objects.requireNonNull(ticks, "ticks"));
    }

    public List<Marker> markers() {
        return this.markers;
    }

    // 宿主注入标记（不参与吸附）
    public void setMarkers(List<Marker> markers) {
        this.markers = List.copyOf(Objects.requireNonNull(markers, "markers"));
    }

    public double tickStep() {
        return this.tickStep;
    }

    // 吸附网格；必须为有限正数
    public void setTickStep(double tickStep) {
        if (!Double.isFinite(tickStep) || tickStep <= 0.0D) {
            throw new IllegalArgumentException("tickStep must be finite and positive");
        }
        this.tickStep = tickStep;
    }

    // 交互窗口上界刻度的默认落点（END 键）；等于最高一格
    public double endTick() {
        return this.wrap(Math.max(0.0D, this.period - this.tickStep));
    }

    // 吸附到刻度网格并折返
    public double snapTick(double raw) {
        if (!Double.isFinite(raw)) {
            return 0.0D;
        }
        return this.wrap(Math.round(raw / this.tickStep) * this.tickStep);
    }

    public UiRangeEnd selectedEnd() {
        return this.selectedEnd;
    }

    public UiRangeEnd selectEnd(UiRangeEnd end) {
        this.selectedEnd = Objects.requireNonNull(end, "end");
        return this.selectedEnd;
    }

    public UiRangeEnd cycleSelectedEnd() {
        this.selectedEnd = this.selectedEnd.other();
        return this.selectedEnd;
    }

    public double selectedTick() {
        return this.selectedEnd == UiRangeEnd.LOW ? this.from : this.to;
    }

    public @Nullable Listener listener() {
        return this.listener;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public @Nullable Function<Double, Component> formatter() {
        return this.formatter;
    }

    public void setFormatter(@Nullable Function<Double, Component> formatter) {
        this.formatter = formatter;
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

    // 刻度在轨道上的比例；刻度已折返，因此总在 [0,1)
    public double fractionOf(double tick) {
        return this.wrap(tick) / this.period;
    }

    public int xOf(double tick) {
        return this.trackLeft() + (int) Math.round(this.fractionOf(tick) * this.trackWidth());
    }

    // 指针位置对应的刻度并吸附
    public double tickAtPointer(double pointerX) {
        double fraction = Mth.clamp((pointerX - this.trackLeft()) / (double) this.trackWidth(), 0.0D, 1.0D);
        return this.snapTick(fraction * this.period);
    }

    public boolean containsPointer(double pointerX, double pointerY) {
        return this.bounds.contains(pointerX, pointerY);
    }

    // 是否正在拖动
    public boolean isDragging() {
        return this.dragging;
    }

    // 当前端是否被按下（供宿主渲染）
    public boolean isPressed() {
        return this.dragging || this.heldKey != -1;
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!this.visible || !this.enabled || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (!this.containsPointer(context.pointerX(), context.pointerY())) {
            return false;
        }
        if (this.dragging) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        UiRangeEnd target = this.hitEnd(context.pointerX());
        if (target == null) {
            this.cycleSelectedEnd();
        } else {
            this.selectedEnd = target;
        }
        this.capture.begin(this);
        this.dragging = true;
        this.beginOperation();
        this.dragAnchorPointer = context.pointerX();
        this.dragAnchorValue = this.selectedTick();
        this.previewSelected(this.tickAtPointer(context.pointerX()));
        return true;
    }

    @Override
    public boolean mouseDragged(UiInputContext context) {
        if (!this.dragging || !this.capture.isCapturedBy(this)) {
            return false;
        }
        double perPixel = this.trackWidth() <= 0 ? 0.0D : this.period / this.trackWidth();
        double raw = this.dragAnchorValue + (context.pointerX() - this.dragAnchorPointer) * perPixel;
        this.previewSelected(raw);
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
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> this.adjustByKey(keyCode, -1.0D);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> this.adjustByKey(keyCode, 1.0D);
            case GLFW.GLFW_KEY_HOME -> this.oneShotSelected(0.0D);
            case GLFW.GLFW_KEY_END -> this.oneShotSelected(this.endTick());
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
     * 精确设置当前端刻度；一次调用恰好一条操作。
     */
    public boolean setSelectedTick(double raw) {
        if (!this.enabled || this.dragging) {
            return false;
        }
        if (this.operationActive) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        return this.oneShotSelected(raw);
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

    // 命中端点；两端重叠（区分不出）时返回 null，由调用方切换当前端
    private @Nullable UiRangeEnd hitEnd(double pointerX) {
        int lowX = this.xOf(this.from);
        int highX = this.xOf(this.to);
        if (Math.abs(lowX - highX) <= 4) {
            return null;
        }
        return Math.abs(pointerX - lowX) <= Math.abs(pointerX - highX) ? UiRangeEnd.LOW : UiRangeEnd.HIGH;
    }

    private boolean adjustByKey(int keyCode, double direction) {
        if (this.heldKey != -1 && this.heldKey != keyCode) {
            this.heldKey = -1;
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        if (!this.operationActive) {
            this.beginOperation();
        }
        this.heldKey = keyCode;
        this.capture.begin(this);
        this.previewSelected(this.selectedTick() + direction * this.tickStep);
        return true;
    }

    private boolean oneShotSelected(double raw) {
        if (this.dragging) {
            return false;
        }
        if (this.operationActive) {
            this.endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        double next = this.snapTick(raw);
        if (next == this.selectedTick()) {
            return true;
        }
        this.beginOperation();
        this.previewSelected(next);
        this.endInteraction(UiInputCapture.EndReason.RELEASE);
        return true;
    }

    private boolean cancelInteraction() {
        if (!this.operationActive && !this.capture.isCapturedBy(this)) {
            return false;
        }
        this.endInteraction(UiInputCapture.EndReason.CANCEL);
        return true;
    }

    private boolean previewSelected(double raw) {
        double next = this.snapTick(raw);
        if (this.selectedEnd == UiRangeEnd.LOW) {
            if (next == this.from) {
                return false;
            }
            this.from = next;
        } else {
            if (next == this.to) {
                return false;
            }
            this.to = next;
        }
        if (this.operationActive && this.listener != null) {
            this.listener.previewed(this.startFrom, this.startTo, this.from, this.to);
        }
        return true;
    }

    private void beginOperation() {
        if (this.operationActive) {
            return;
        }
        this.operationActive = true;
        this.startFrom = this.from;
        this.startTo = this.to;
        if (this.listener != null) {
            this.listener.began(this.startFrom, this.startTo);
        }
    }

    private void finishOperation(UiInputCapture.EndReason reason) {
        this.dragging = false;
        this.heldKey = -1;
        if (!this.operationActive) {
            return;
        }
        double beganFrom = this.startFrom;
        double beganTo = this.startTo;
        double currentFrom = this.from;
        double currentTo = this.to;
        boolean changed = currentFrom != beganFrom || currentTo != beganTo;
        this.operationActive = false;
        if (this.listener == null) {
            return;
        }
        if (reason.cancels()) {
            this.from = beganFrom;
            this.to = beganTo;
            this.listener.cancelled(beganFrom, beganTo, beganFrom, beganTo);
        } else if (changed) {
            this.listener.committed(beganFrom, beganTo, currentFrom, currentTo);
        }
    }

    private static double requirePeriod(double period) {
        if (!Double.isFinite(period) || period <= 0.0D) {
            throw new IllegalArgumentException("period must be finite and positive");
        }
        return period;
    }
}
