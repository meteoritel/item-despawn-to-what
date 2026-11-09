package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidgetShell;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import java.util.List;
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
public final class UiScalarSlider implements UiFocusTarget, UiInputTarget, UiInteractionSession.Target {

    /**
     * 默认显示小数位数：整数按 {@link UiNumbers} 的整数直出规则显示。
     */
    private static final int VALUE_DECIMALS = 2;

    /** 文字行与轨道之间的最小间距；放不下时退回旧的居中画法。 */
    private static final int TRACK_GAP = 2;

    /** 标签与数值之间保留的最小间距。 */
    private static final int TEXT_GAP = 4;

    private final UiWidgetShell shell = new UiWidgetShell(this, this::endInteraction);
    private final SliderInteraction<Double> interaction = new SliderInteraction<>(this,
            Double::isFinite, (first, second) -> first.doubleValue() == second.doubleValue(),
            this::value, restored -> this.value = restored, this::interactionListener);

    private UiNumberPolicy policy;
    private UiSliderWindow window;
    private UiStyleSource theme = UiMinecraftTheme.INSTANCE;
    private UiSliderRole role = UiSliderRole.COMPACT;
    private long themeRevision = -1;
    private UiSliderStyle style = UiMinecraftTheme.INSTANCE.sliderSkin(role).style();
    private UiSliderPainter painter = UiMinecraftTheme.INSTANCE.sliderSkin(role).painter();
    private boolean styleOverride;
    private boolean painterOverride;
    private double value;
    private double pageStep;
    private boolean backfillInvalid;
    private Component label = Component.empty();
    private @Nullable Function<Double, Component> formatter;
    private @Nullable UiValueInteraction.Listener<Double> interactionListener;
    private boolean hovered;
    private boolean error;
    /** 上一帧标签是否被可用宽度截断；命中时据此附带完整标签提示。 */
    private boolean labelTrimmed;
    private UiRect labelBounds = new UiRect(0, 0, 0, 0);

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

    /** 绑定根主题与档位；显式 setStyle/setPainter 分别覆盖对应默认项。 */
    public UiScalarSlider setTheme(UiStyleSource theme, UiSliderRole role) {
        if (this.theme == theme && this.role == role) return this;
        this.theme = Objects.requireNonNull(theme, "theme");
        this.role = Objects.requireNonNull(role, "role");
        styleOverride = false;
        painterOverride = false;
        themeRevision = -1;
        refreshTheme();
        return this;
    }

    private void refreshTheme() {
        if (themeRevision == theme.revision()) return;
        UiSliderSkin skin = theme.sliderSkin(role);
        if (!styleOverride) style = skin.style();
        if (!painterOverride) painter = skin.painter();
        themeRevision = theme.revision();
    }

    public UiSliderStyle style() {
        refreshTheme();
        return style;
    }

    public UiScalarSlider setStyle(UiSliderStyle next) {
        this.style = Objects.requireNonNull(next, "style");
        styleOverride = true;
        return this;
    }

    public UiSliderPainter painter() {
        refreshTheme();
        return painter;
    }

    public UiScalarSlider setPainter(UiSliderPainter next) {
        this.painter = Objects.requireNonNull(next, "painter");
        painterOverride = true;
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

    /** 当前后续操作使用的标量快照监听器。 */
    public @Nullable UiValueInteraction.Listener<Double> interactionListener() {
        return interactionListener;
    }

    /** 设置标量快照监听器；程序回填保持静默。 */
    public UiScalarSlider setInteractionListener(@Nullable UiValueInteraction.Listener<Double> next) {
        this.interactionListener = next;
        return this;
    }

    /** 当前可用性由共享状态壳持有。 */
    public boolean isEnabled() {
        return shell.isEnabled();
    }

    /** 禁用时立即结束进行中的交互并回退预览 */
    public UiScalarSlider setEnabled(boolean next) {
        shell.setEnabled(next);
        return this;
    }

    /** 当前可见性由共享状态壳持有。 */
    public boolean isVisible() {
        return shell.isVisible();
    }

    /** 隐藏时立即结束进行中的交互并回退预览 */
    public UiScalarSlider setVisible(boolean next) {
        shell.setVisible(next);
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

    /** 是否持有本目标的指针拖动会话。 */
    public boolean isDragging() {
        return interaction.isDragging();
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

    /** {@inheritDoc} */
    @Override
    public UiRect bounds() {
        return shell.bounds();
    }

    /** 更新 GUI 逻辑矩形；负宽高按零处理，同步当前布局几何。 */
    public void setBounds(int x, int y, int width, int height) {
        shell.setBounds(x, y, width, height);
    }

    /** 手柄中心可移动的轨道左端，使用当前皮肤宽度。 */
    public int trackLeft() {
        refreshTheme();
        return SliderTrack.left(bounds(), style.thumbWidth());
    }

    /** 手柄中心可移动的轨道右端，使用当前皮肤宽度。 */
    public int trackRight() {
        refreshTheme();
        return SliderTrack.right(bounds(), style.thumbWidth());
    }

    /** 轨道至少保留一个 GUI 逻辑像素，供绘制与指针换算共用。 */
    public int trackWidth() {
        refreshTheme();
        return SliderTrack.width(bounds(), style.thumbWidth());
    }

    /**
     * 指针横坐标换算成窗口内的数值。
     *
     * <p>比例先钳制到轨道内，结果再按当前档位吸附：定位与拖动必须共用同一套吸附策略，
     * 否则点击会带出全精度小数（例如 56.04395604395606），既进精确输入框也随草稿提交。</p>
     *
     * @param pointerX 指针横坐标（GUI 坐标）
     * @param fine     是否按精细档位吸附
     * @return 吸附并钳制到合法域后的数值
     */
    public double valueAtPointer(double pointerX, boolean fine) {
        double fraction = (pointerX - trackLeft()) / (double) trackWidth();
        return policy.snap(window.valueAt(fraction), fine);
    }

    /** 绘制一帧；hovered 只在可见时更新 */
    public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        refreshTheme();
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        if (!isVisible()) {
            return;
        }
        hovered = isEnabled() && pointer.forTarget(this).contains(bounds());
        painter.paint(graphics, buildFrame(font));
        int textY = textRowY(font);
        String valueText = trimmedValue(font);
        int valueWidth = font.width(valueText);
        // 数值优先完整可见：标签按剩余宽度截断，悬停时用提示补全
        int labelMax = Math.max(0, bounds().width() - valueWidth - TEXT_GAP);
        String labelText = TextScroll.trimToWidth(font, label.getString(), labelMax);
        labelTrimmed = labelMax > 0 && font.width(label.getString()) > labelMax;
        labelBounds = new UiRect(bounds().x(), textY, Math.min(font.width(labelText), labelMax), font.lineHeight);
        if (!labelText.isEmpty()) {
            graphics.drawString(font, labelText, bounds().x(), textY, style.resolveLabelText(isEnabled(), error), false);
        }
        int valueX = Math.max(bounds().x(), bounds().right() - valueWidth);
        graphics.drawString(font, valueText, valueX, textY, style.resolveValueText(isEnabled(), error), false);
    }

    /** 标签被截断时把完整标签挂到命中上，由宿主现有的提示出口绘制 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!canFocus() || !bounds().contains(x, y)) {
            return null;
        }
        UiPointerHit hit = UiPointerHit.leaf(this, x, y);
        return labelTrimmed && labelBounds.width() > 0
                ? hit.withTextTooltip(List.of(label), labelBounds) : hit;
    }

    // 文字行与轨道分层：带标签且高度够时文字贴上沿、轨道贴下沿；
    // 无标签的数值行保持原有居中画法，避免数值上移后与宿主行标签的基线错位。
    private boolean splitTextRow(Font font) {
        return !label.getString().isEmpty()
                && bounds().height() - (font.lineHeight + style.trackHeight() + style.thumbOverhang() * 2) >= TRACK_GAP;
    }

    private int textRowY(Font font) {
        return splitTextRow(font)
                ? bounds().y()
                : bounds().y() + Math.max(0, (bounds().height() - font.lineHeight) / 2);
    }

    private int trackTop(Font font) {
        return splitTextRow(font)
                ? bounds().bottom() - style.trackHeight() - style.thumbOverhang()
                : bounds().y() + Math.max(0, (bounds().height() - style.trackHeight()) / 2);
    }

    // 数值文本按控件宽度收口，避免超长值压住标签或越出右边界
    private String trimmedValue(Font font) {
        return TextScroll.trimToWidth(font, formatValue().getString(), Math.max(0, bounds().width() - TEXT_GAP));
    }

    private UiSliderPainter.Frame buildFrame(Font font) {
        int trackHeight = style.trackHeight();
        int trackY = trackTop(font);
        int left = trackLeft();
        int width = trackWidth();
        UiRect track = new UiRect(left, trackY, width, trackHeight);
        double fraction = window.clampFraction(window.fraction(value));
        int filledRight = left + (int) Math.round(fraction * width);
        UiRect filled = new UiRect(left, trackY, Math.max(0, filledRight - left), trackHeight);
        int thumbWidth = style.thumbWidth();
        int thumbX = left + (int) Math.round(fraction * width) - thumbWidth / 2;
        int maxThumbX = Math.max(bounds().x(), bounds().right() - thumbWidth);
        thumbX = Math.clamp(thumbX, bounds().x(), maxThumbX);
        int overhang = style.thumbOverhang();
        UiRect thumb = new UiRect(thumbX, trackY - overhang, thumbWidth, trackHeight + overhang * 2);
        String valueText = trimmedValue(font);
        int valueWidth = Math.min(font.width(valueText), bounds().width());
        int textY = textRowY(font);
        UiRect textSlot = new UiRect(Math.max(bounds().x(), bounds().right() - valueWidth), textY,
                Math.max(0, valueWidth), font.lineHeight);
        boolean overflowBelow = value < window.min();
        boolean overflowAbove = value > window.max();
        return new UiSliderPainter.Frame(bounds(), track, filled, thumb, textSlot, style,
                isEnabled(), hovered, isDragging(), isFocusVisible(), isDragging(), error, overflowBelow, overflowAbove);
    }

    /** {@inheritDoc} */
    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!shell.isInteractive() || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !bounds().contains(context.pointerX(), context.pointerY())) return false;
        boolean fine = context.shiftDown();
        if (interaction.beginPointer(context)) preview(valueAtPointer(context.pointerX(), fine));
        interaction.anchor(context.pointerX(), value, fine);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (!isDragging()) return false;
        if (interaction.reanchor(context.pointerX(), value, context.shiftDown())) return true;
        double perPixel = policy.dragValuePerPixel(trackWidth(), window.span(), interaction.isFine());
        preview(policy.snap(interaction.dragValue(context.pointerX(), perPixel), interaction.isFine()));
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean mouseReleased(UiInputContext context) {
        return interaction.releasePointer(context.button());
    }

    /** {@inheritDoc} */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible() || !isEnabled() || !isFocused()) {
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

    /** {@inheritDoc} */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return interaction.releaseKey(keyCode);
    }

    // 方向键重复：同一次按键重复在 key-up 才提交，换键前先结清上一次
    private void adjustByKey(int keyCode, double direction, boolean fine) {
        interaction.beginKey(focusState(), keyCode);
        preview(policy.snap(value + direction * policy.step(fine), fine));
    }

    // 一次性用户操作：Home/End、启用后的 PageUp/PageDown
    private void oneShot(double target, boolean fine) {
        interaction.changeOnce(() -> policy.snap(target, fine), this::preview);
    }

    // Esc：只回退进行中的交互；没有交互时返回 false 交给宿主关闭页面
    private boolean cancelInteraction() {
        return interaction.cancel();
    }

    // 预览新值；同值不回调，保证无变化不产生历史。用户操作一律先收进显示窗口，提交仍用精确值
    private void preview(double next) {
        double target = clampToWindow(next);
        if (!Double.isFinite(target) || target == value) {
            return;
        }
        value = target;
        backfillInvalid = false;
        interaction.preview(value);
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
        if (isDragging()) {
            return Math.clamp(next, window.min(), window.max());
        }
        double anchor = interaction.isActive() ? interaction.startValue() : value;
        double lower = Math.min(window.min(), anchor);
        double upper = Math.max(window.max(), anchor);
        return Math.clamp(next, lower, upper);
    }

    /**
     * 统一结束入口：宿主在隐藏、禁用、卸载、焦点作用域切换、宿主关闭时调用。
     * 正常释放与同域焦点转移完成有效预览，取消原因回退到本次操作起点值。
     */
    public void endInteraction(UiInteractionSession.EndReason reason) {
        interaction.end(reason);
    }

    /** {@inheritDoc} */
    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        interaction.finish(reason);
    }

    /** {@inheritDoc} */
    @Override
    public UiFocusState focusState() {
        return shell.focusState();
    }

    /** {@inheritDoc} */
    @Override
    public boolean canFocus() {
        return shell.canFocus();
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
