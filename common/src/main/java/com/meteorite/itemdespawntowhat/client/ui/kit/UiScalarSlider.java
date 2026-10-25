package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * kit 标量滑块核心：一个值、一条轨道、一套输入契约。
 *
 * <p>值有三条通路，互不混淆：</p>
 * <ul>
 *   <li>数据回填 {@link #setValue(double)}：不吸附、不回调、不产生编辑历史；越界或非有限值被拒绝并置
 *       {@link #isBackfillInvalid()}，供宿主决定如何提示。</li>
 *   <li>用户操作：先 {@link UiValueInteraction#begin}，拖动/按键重复期间 preview，释放或 key-up 时
 *       commit；值没有变化时 commit 返回 false，宿主因此不会写入空历史。</li>
 *   <li>取消：Esc、隐藏、禁用、卸载、焦点作用域切换、宿主关闭都走 {@link #endInteraction}，
 *       回退到本次操作的起点值，绝不提交半途预览。</li>
 * </ul>
 *
 * <p>键盘：Left/Down 减、Right/Up 加，Shift 走细档；Home/End 到显示窗口端点；PageUp/PageDown 仅在
 * 宿主 {@link #setPageStep(double)} 启用大步时消费，未启用时返回 false 交给容器导航。</p>
 *
 * <p>指针：点击轨道定位，随后以该点为锚点拖动；指针离开矩形仍收到释放；拖动中按或松 Shift 会重设
 * 锚点并保持当前值不变，只改变后续灵敏度。所有用户操作的预览值都收在显示窗口内（指针停在窗口
 * 端点，键盘从窗口外的既有值出发时只朝窗口方向收），窗口外的合法值只经数据回填与精确输入进入。</p>
 *
 * <p>本类只依赖 kit 与客户端通用类型：默认绘制使用 {@link UiSliderPainter} 与 {@link UiSliderStyle}，
 * 宿主可替换成主题化或贴图实现。</p>
 */
public final class UiScalarSlider implements UiFocusTarget, UiInputTarget, UiInputCapture.Target {

    /** 默认显示小数位数：整数按 {@link UiNumbers} 的整数直出规则显示。 */
    private static final int VALUE_DECIMALS = 2;

    private final UiInputCapture capture = new UiInputCapture();
    private final UiValueInteraction interaction = new UiValueInteraction();

    private UiNumberPolicy policy;
    private UiSliderWindow window;
    private UiSliderStyle style = UiSliderStyle.pixelDefaults();
    private UiSliderPainter painter = PixelSliderPainter.INSTANCE;
    private double value;
    private double pageStep;
    private boolean backfillInvalid;
    private Component label = Component.empty();
    private @Nullable Function<Double, Component> formatter;
    private @Nullable UiValueInteraction.Listener interactionListener;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean hovered;
    private boolean error;
    private boolean dragging;
    private int heldKey = -1;
    private double dragAnchorPointer;
    private double dragAnchorValue;
    private boolean dragFineAnchor;

    public UiScalarSlider(UiNumberPolicy policy) {
        this(policy, new UiSliderWindow(policy.min(), policy.max()));
    }

    public UiScalarSlider(UiNumberPolicy policy, UiSliderWindow window) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.window = Objects.requireNonNull(window, "window");
        this.value = policy.min();
    }

    public UiNumberPolicy policy() {
        return policy;
    }

    // 换数值策略；不隐式改动当前值，宿主应在装配完成后回填一次
    public UiScalarSlider setPolicy(UiNumberPolicy next) {
        this.policy = Objects.requireNonNull(next, "policy");
        return this;
    }

    public UiSliderWindow window() {
        return window;
    }

    // 换显示窗口；只影响几何映射，不影响合法值
    public UiScalarSlider setWindow(UiSliderWindow next) {
        this.window = Objects.requireNonNull(next, "window");
        return this;
    }

    public UiSliderStyle style() {
        return style;
    }

    public UiScalarSlider setStyle(UiSliderStyle next) {
        this.style = Objects.requireNonNull(next, "style");
        return this;
    }

    public UiSliderPainter painter() {
        return painter;
    }

    public UiScalarSlider setPainter(UiSliderPainter next) {
        this.painter = Objects.requireNonNull(next, "painter");
        return this;
    }

    public double value() {
        return value;
    }

    /**
     * 数据回填：不吸附、不回调、不产生历史。超出合法域或非有限值会被拒绝，控件保持原值并置错误状态。
     *
     * @return 是否接受该值
     */
    public boolean setValue(double next) {
        if (!Double.isFinite(next) || next < policy.min() || next > policy.max()) {
            backfillInvalid = true;
            return false;
        }
        backfillInvalid = false;
        value = next;
        return true;
    }

    // 上一次回填是否被拒绝（越界或非有限）
    public boolean isBackfillInvalid() {
        return backfillInvalid;
    }

    // 宿主显式设置错误状态，用于表达校验失败
    public UiScalarSlider setError(boolean next) {
        this.error = next;
        return this;
    }

    public boolean isError() {
        return error;
    }

    // PageUp/PageDown 的大步长；<= 0 表示不消费这两个键
    public UiScalarSlider setPageStep(double next) {
        this.pageStep = Double.isFinite(next) && next > 0.0D ? next : 0.0D;
        return this;
    }

    public double pageStep() {
        return pageStep;
    }

    public Component label() {
        return label;
    }

    public UiScalarSlider setLabel(Component next) {
        this.label = next == null ? Component.empty() : next;
        return this;
    }

    public @Nullable Function<Double, Component> formatter() {
        return formatter;
    }

    public UiScalarSlider setFormatter(@Nullable Function<Double, Component> next) {
        this.formatter = next;
        return this;
    }

    public @Nullable UiValueInteraction.Listener interactionListener() {
        return interactionListener;
    }

    // 交互生命周期回调；数据回填不会触发
    public UiScalarSlider setInteractionListener(@Nullable UiValueInteraction.Listener next) {
        this.interactionListener = next;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // 禁用时立即结束进行中的交互并回退预览
    public UiScalarSlider setEnabled(boolean next) {
        if (!next && enabled) endInteraction(UiInputCapture.EndReason.DISABLED);
        enabled = next;
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    // 隐藏时立即结束进行中的交互并回退预览
    public UiScalarSlider setVisible(boolean next) {
        if (!next && visible) endInteraction(UiInputCapture.EndReason.HIDDEN);
        visible = next;
        return this;
    }

    public boolean isHovered() {
        return hovered;
    }

    /**
     * 查询拖动或按键预览是否仍在进行，用于收束没有净变化的文本绑定操作。
     *
     * @return 是否存在尚未结束的数值操作
     */
    public boolean isInteracting() {
        return interaction.isActive();
    }

    public boolean isDragging() {
        return dragging;
    }

    // 当前值的显示文本：整数直出、否则定点小数；规则集中在 UiNumbers，避免与宿主各写一份
    public Component formatValue() {
        if (formatter != null) {
            Component custom = formatter.apply(value);
            if (custom != null) {
                return custom;
            }
        }
        return Component.literal(UiNumbers.plain(value, VALUE_DECIMALS));
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
    }

    public int trackLeft() {
        return bounds.x() + style.thumbWidth();
    }

    public int trackRight() {
        return Math.max(trackLeft() + 1, bounds.right() - style.thumbWidth());
    }

    public int trackWidth() {
        return Math.max(1, trackRight() - trackLeft());
    }

    // 指针横坐标换算成窗口内的数值（比例先钳制）
    public double valueAtPointer(double pointerX) {
        double fraction = (pointerX - trackLeft()) / (double) trackWidth();
        return window.valueAt(fraction);
    }

    // 绘制一帧；hovered 只在可见时更新
    public void render(GuiGraphics graphics, Font font, UiInputContext context) {
        if (!visible) {
            return;
        }
        hovered = enabled && bounds.contains(context.pointerX(), context.pointerY());
        painter.paint(graphics, buildFrame(font));
        int textY = bounds.y() + Math.max(0, (bounds.height() - font.lineHeight) / 2);
        String labelText = TextScroll.trimToWidth(font, label.getString(), Math.max(0, bounds.width() / 2));
        if (!labelText.isEmpty()) {
            graphics.drawString(font, labelText, bounds.x(), textY, style.resolveLabelText(enabled, error), false);
        }
        String valueText = formatValue().getString();
        int valueX = Math.max(bounds.x(), bounds.right() - font.width(valueText));
        graphics.drawString(font, valueText, valueX, textY, style.resolveValueText(enabled, error), false);
    }

    private UiSliderPainter.Frame buildFrame(Font font) {
        int trackHeight = style.trackHeight();
        int trackY = bounds.y() + Math.max(0, (bounds.height() - trackHeight) / 2);
        int left = trackLeft();
        int width = trackWidth();
        UiRect track = new UiRect(left, trackY, width, trackHeight);
        double fraction = window.clampFraction(window.fraction(value));
        int filledRight = left + (int) Math.round(fraction * width);
        UiRect filled = new UiRect(left, trackY, Math.max(0, filledRight - left), trackHeight);
        int thumbWidth = style.thumbWidth();
        int thumbX = left + (int) Math.round(fraction * width) - thumbWidth / 2;
        int maxThumbX = Math.max(bounds.x(), bounds.right() - thumbWidth);
        thumbX = Math.clamp(thumbX, bounds.x(), maxThumbX);
        int overhang = style.thumbOverhang();
        UiRect thumb = new UiRect(thumbX, trackY - overhang, thumbWidth, trackHeight + overhang * 2);
        String valueText = formatValue().getString();
        int valueWidth = Math.min(font.width(valueText), bounds.width());
        int textY = bounds.y() + Math.max(0, (bounds.height() - font.lineHeight) / 2);
        UiRect textSlot = new UiRect(Math.max(bounds.x(), bounds.right() - valueWidth), textY,
                Math.max(0, valueWidth), font.lineHeight);
        boolean overflowBelow = value < window.min();
        boolean overflowAbove = value > window.max();
        return new UiSliderPainter.Frame(bounds, track, filled, thumb, textSlot, style,
                enabled, hovered, dragging, focused, dragging, error, overflowBelow, overflowAbove);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!visible || !enabled || context.button() != 0
                || !bounds.contains(context.pointerX(), context.pointerY())) {
            return false;
        }
        capture.begin(this);
        dragging = true;
        if (interaction.begin(value, interactionListener)) {
            preview(valueAtPointer(context.pointerX()));
        }
        // 点击定位后以当前位置为拖动锚点，避免拖动时再次跳变
        dragAnchorPointer = context.pointerX();
        dragAnchorValue = value;
        dragFineAnchor = context.shiftDown();
        return true;
    }

    @Override
    public boolean mouseDragged(UiInputContext context) {
        if (!dragging || !capture.isCapturedBy(this)) {
            return false;
        }
        if (context.shiftDown() != dragFineAnchor) {
            // 拖动中按或松 Shift：先重设锚点，当前值保持不变
            dragFineAnchor = context.shiftDown();
            dragAnchorPointer = context.pointerX();
            dragAnchorValue = value;
        }
        double perPixel = policy.dragValuePerPixel(trackWidth(), window.span(), dragFineAnchor);
        double raw = dragAnchorValue + (context.pointerX() - dragAnchorPointer) * perPixel;
        preview(policy.snap(raw, dragFineAnchor));
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        if (capture.isCapturedBy(this)) {
            capture.release();
        } else {
            finishInteraction(UiInputCapture.EndReason.RELEASE);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        boolean fine = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> {
                adjustByKey(keyCode, -1.0D, fine);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> {
                adjustByKey(keyCode, 1.0D, fine);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                oneShot(window.min(), fine);
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                oneShot(window.max(), fine);
                yield true;
            }
            // 只有宿主显式设置了页步长才消费 PageUp/PageDown，否则交给容器做翻页导航
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                if (pageStep <= 0.0D) yield false;
                oneShot(value - pageStep, fine);
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                if (pageStep <= 0.0D) yield false;
                oneShot(value + pageStep, fine);
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> cancelInteraction();
            default -> false;
        };
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (heldKey != keyCode) {
            return false;
        }
        heldKey = -1;
        endInteraction(UiInputCapture.EndReason.RELEASE);
        return true;
    }

    // 方向键重复：同一次按键重复在 key-up 才提交，换键前先结清上一次
    private void adjustByKey(int keyCode, double direction, boolean fine) {
        if (interaction.isActive() && heldKey != keyCode) {
            endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        if (heldKey != keyCode) {
            heldKey = keyCode;
            capture.begin(this);
        }
        if (!interaction.isActive() && !interaction.begin(value, interactionListener)) {
            heldKey = -1;
            return;
        }
        preview(policy.snap(value + direction * policy.step(fine), fine));
    }

    // 一次性用户操作：Home/End、启用后的 PageUp/PageDown
    private void oneShot(double target, boolean fine) {
        if (dragging) {
            return;
        }
        if (interaction.isActive()) {
            endInteraction(UiInputCapture.EndReason.RELEASE);
        }
        double next = policy.snap(target, fine);
        if (next == value) {
            return;
        }
        if (!interaction.begin(value, interactionListener)) {
            return;
        }
        preview(next);
        interaction.commit();
    }

    // Esc：只回退进行中的交互；没有交互时返回 false 交给宿主关闭页面
    private boolean cancelInteraction() {
        if (!interaction.isActive() && !capture.isCaptured()) {
            return false;
        }
        endInteraction(UiInputCapture.EndReason.CANCEL);
        return true;
    }

    // 预览新值；同值不回调，保证无变化不产生历史。用户操作一律先收进显示窗口，提交仍用精确值
    private boolean preview(double next) {
        double target = clampToWindow(next);
        if (!Double.isFinite(target) || target == value) {
            return false;
        }
        value = target;
        backfillInvalid = false;
        interaction.preview(value);
        return true;
    }

    /**
     * 把用户操作的预览值限制在显示窗口内。
     *
     * <p>显示窗口是交互范围而不是合法域：数据回填与宿主精确输入仍可给出窗口外的合法值。
     * 指针被钳在窗口内，拖动越过两端时停在窗口端点；键盘从窗口外的既有值出发时只允许朝窗口
     * 方向收，不允许继续向外推，避免一次方向键把老值推到更远的位置。</p>
     *
     * @param next 用户操作算出的目标值
     * @return 收进显示窗口后的值
     */
    private double clampToWindow(double next) {
        if (dragging) {
            return Math.clamp(next, window.min(), window.max());
        }
        double anchor = interaction.isActive() ? interaction.startValue() : value;
        double lower = Math.min(window.min(), anchor);
        double upper = Math.max(window.max(), anchor);
        return Math.clamp(next, lower, upper);
    }

    /**
     * 统一结束入口：宿主在隐藏、禁用、卸载、焦点作用域切换、宿主关闭时调用。
     * 只有 {@link UiInputCapture.EndReason#RELEASE} 会提交，其余原因都回退到本次操作起点值。
     */
    public void endInteraction(UiInputCapture.EndReason reason) {
        Objects.requireNonNull(reason, "End reason");
        if (capture.end(reason)) {
            return;
        }
        finishInteraction(reason);
    }

    // 控件从内容树移除时调用
    public void unmount() {
        endInteraction(UiInputCapture.EndReason.UNMOUNTED);
    }

    // 焦点作用域切换（打开模态、切页）时调用
    public void onFocusScopeChanged() {
        endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
    }

    // 宿主关闭（屏幕关闭、断线）时调用
    public void onHostClosed() {
        endInteraction(UiInputCapture.EndReason.HOST_CLOSED);
    }

    @Override
    public void captureEnded(UiInputCapture.EndReason reason) {
        finishInteraction(reason);
    }

    private void finishInteraction(UiInputCapture.EndReason reason) {
        dragging = false;
        heldKey = -1;
        if (!interaction.isActive()) {
            return;
        }
        if (reason.cancels()) {
            value = interaction.startValue();
            interaction.cancel();
        } else {
            interaction.commit();
        }
    }

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean next) {
        this.focused = next;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        return false;
    }

    @Override
    public @Nullable Component accessibleName() {
        return label;
    }
}
