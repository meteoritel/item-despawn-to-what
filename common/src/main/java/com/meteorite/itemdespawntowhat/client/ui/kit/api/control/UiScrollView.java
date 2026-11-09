package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;

import java.util.OptionalDouble;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 通用纵向滚动视口：持有视口矩形、内容高度与滚动偏移，负责裁剪、坐标换算以及滚动条的命中与绘制。
 *
 * <p>坐标口径：视口矩形使用宿主 GUI 逻辑坐标，与 {@link UiControl} 的矩形同坐标系。绘制内容前调用
 * {@link #push(GuiGraphics)} 进入内容坐标——原点在内容顶部左对齐（x 与视口左边界对齐）；绘制后调用
 * {@link #pop(GuiGraphics)} 返回宿主坐标，再由宿主调用 {@link #renderScrollbar(GuiGraphics, UiStyleSource)}。
 * 命中走同一套换算：{@link #toContentX(double)} / {@link #toContentY(double)} 把屏幕坐标换成内容坐标，
 * 交给 {@link UiControlGroup} 使用。</p>
 *
 * <p>偏移恒钳制在 [0, {@link #maxOffset()}]：视口尺寸变化、内容变短以及 {@link #setContentHeight(int)}
 * 之后都会重新钳制。内容高度不超过有效视口高度时偏移必为 0、{@link #maxOffset()} 为 0，滚动条不显示。</p>
 *
 * <p>滚动偏移、轨道几何及拖动会话由本视口持有；内容与滚轮消费政策由宿主按
 * {@link #contains(double, double)} 决定。{@link Policy} 可指定内容步长、轨道留宽及按下行为。</p>
 */
public final class UiScrollView implements UiInteractionSession.Target {
    /**
     * 滚动条占用的宽度；宿主可据此在内容溢出时让出内容宽度。
     */
    public static final int SCROLLBAR_WIDTH = 4;
    /**
     * 滑块最小高度：内容再长也要留出可抓取区域。
     */
    private static final int MIN_THUMB_HEIGHT = 8;

    /** 滚动政策；alignment 为内容步长，其他参数仅控制轨道交互与留宽。 */
    public record Policy(int alignment, int minimumThumb, int grabMargin,
                         boolean centerOnPress, boolean reserveScrollbar) {
        /** 尺寸统一规范为有效的几何输入。 */
        public Policy {
            alignment = Math.max(1, alignment);
            minimumThumb = Math.max(0, minimumThumb);
            grabMargin = Math.max(0, grabMargin);
        }
    }

    private Policy policy = new Policy(1, MIN_THUMB_HEIGHT, 0, false, false);

    /** 设置布局与交互政策；偏移随同一入口重新对齐。 */
    public void setPolicy(Policy policy) {
        this.policy = policy;
        clampOffset();
    }

    // 对齐视口按完整步长计范围；不足一步仍保留一个内容单元供裁剪显示。
    private int effectiveHeight() {
        return policy.alignment() == 1 ? viewport.height()
                : Math.max(policy.alignment(), viewport.height() / policy.alignment() * policy.alignment());
    }

    private UiRect viewport = new UiRect(0, 0, 1, 1);
    private int contentHeight;
    private int offset;
    private int step = 20;
    private int scrollbarWidth = SCROLLBAR_WIDTH;
    private boolean scrollbarVisible;
    private UiInteractionSession session;
    /**
     * 按下时指针相对滑块顶部的距离；拖动期间保持不变，滑块才会跟着指针平移而不是跳到指针处。
     */
    private double dragGrab;

    // ---------- 几何与偏移 ----------

    // 视口尺寸变化立即重新钳制：内容不足一屏时偏移必须回到 0。
    // 负宽高按 0 钳制——kit 的几何入口统一钳制，UiRect 只在构造期拒绝负尺寸（那是矩形自身的不变式）。
    public void setViewport(int x, int y, int width, int height) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        if (viewport.x() == x && viewport.y() == y && viewport.width() == w && viewport.height() == h) return;
        viewport = new UiRect(x, y, w, h);
        clampOffset();
    }

    public UiRect viewport() {
        return viewport;
    }

    /**
     * 返回当前内容可用宽度，按政策扣除实际可见或固定预留的滚动条占用。
     */
    public int contentWidth() {
        return Math.max(0, viewport.width() - (policy.reserveScrollbar() || isScrollbarVisible() ? scrollbarWidth : 0));
    }

    /** 设置实际轨道宽度，文本视图可保留其较窄的轨道。 */
    public void setScrollbarWidth(int width) {
        scrollbarWidth = Math.max(1, width);
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        clampOffset();
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int offset() {
        return offset;
    }

    public int maxOffset() {
        int range = Math.max(0, contentHeight - effectiveHeight());
        return (range + policy.alignment() - 1) / policy.alignment() * policy.alignment();
    }

    public void setOffset(int offset) {
        int bounded = Math.clamp(offset, 0, maxOffset());
        this.offset = Math.min(maxOffset(), (int) Math.round(bounded / (double) policy.alignment()) * policy.alignment());
    }

    public void setStep(int step) {
        this.step = Math.max(1, step);
    }

    public int step() {
        return step;
    }

    // 滚轮：正数朝内容顶部（偏移减小），与 GLFW / Screen 的纵向滚动符号一致；按 signum 走整步并钳制。
    public boolean scrollBy(double amount) {
        int direction = (int) Math.signum(amount);
        if (direction == 0) return false;
        return scrollByPixels(direction * (double) step);
    }

    /** 按像素量滚动；正数朝内容顶部，保留连续滚轮的实际量。 */
    public boolean scrollByPixels(double amount) {
        int previous = offset;
        setOffset(offset - (int) Math.round(amount));
        return offset != previous;
    }

    /**
     * 指针是否落在视口内；宿主据此只消费自身区域的滚轮与命中。
     */
    public boolean contains(double x, double y) {
        return viewport.contains(x, y);
    }

    /** 以最小位移露出目标；超高目标覆盖视口时保持位置，避免反复对齐两边。 */
    public void ensureVisible(UiRect contentRect) {
        int next = offset;
        if (contentRect.height() > effectiveHeight()) {
            if (contentRect.bottom() < next + effectiveHeight()) next = contentRect.bottom() - effectiveHeight();
            else if (contentRect.y() > next) next = contentRect.y();
        } else if (contentRect.y() < next) next = contentRect.y();
        else if (contentRect.bottom() > next + effectiveHeight()) next = contentRect.bottom() - effectiveHeight();
        if (next > offset) next = (int) Math.ceil(next / (double) policy.alignment()) * policy.alignment();
        else if (next < offset) next = (int) Math.floor(next / (double) policy.alignment()) * policy.alignment();
        setOffset(next);
    }

    /** GUI 点转内容点；纯换算不检查视口，捕获拖动可传越界点。 */
    public double toContentX(double screenX) {
        return screenX - viewport.x();
    }

    /** GUI 点转内容点，保留小数精度。 */
    public double toContentY(double screenY) {
        return screenY - viewport.y() + offset;
    }

    /** 内容纵坐标落到 GUI 逻辑像素，在绘制边界四舍五入。 */
    public int toScreenY(double contentY) {
        return (int) Math.round(viewport.y() + contentY - offset);
    }

    /** 内容矩形转 GUI 矩形；当前视口与偏移为整数平移，尺寸保持。 */
    public UiRect toScreen(UiRect contentRect) {
        return new UiRect(viewport.x() + contentRect.x(), toScreenY(contentRect.y()),
                contentRect.width(), contentRect.height());
    }

    // ---------- 变换与裁剪 ----------

    // 进入内容坐标：先 flush 已有顶点，再按当前 pose 设置视口裁剪，最后平移。
    public void push(GuiGraphics graphics) {
        push(graphics, viewport);
    }

    /** 使用独立裁剪范围进入内容坐标，偏移与坐标换算仍由同一视口持有。 */
    public void push(GuiGraphics graphics, UiRect clip) {
        graphics.flush();
        UiRenderLayers.enableScissor(graphics, clip);
        graphics.pose().pushPose();
        graphics.pose().translate(viewport.x(), viewport.y() - offset, 0.0F);
    }

    // 退出内容坐标：先弹回宿主 pose，再恢复上一层裁剪（原版 scissor 交集栈）。
    public void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
        UiRenderLayers.disableScissor(graphics);
    }

    // ---------- 滚动条 ----------

    // 宿主只在内容溢出时打开；内容变短或钳制后没有可滚距离时一律不显示。
    public void setScrollbarVisible(boolean visible) {
        scrollbarVisible = visible;
        if (!visible) endInteraction(UiInteractionSession.EndReason.CANCEL);
    }

    public boolean isScrollbarVisible() {
        return scrollbarVisible && maxOffset() > 0;
    }

    public boolean hitScrollbar(double x, double y) {
        if (!isScrollbarVisible()) return false;
        UiRect track = scrollbarTrack();
        return x >= track.x() - policy.grabMargin() && x <= track.right() + policy.grabMargin()
                && y >= track.y() && y < track.bottom();
    }

    /**
     * 指针是否落在当前滑块上：宿主据此决定悬停反馈，几何只在这里算一次。
     */
    public boolean hitThumb(double x, double y) {
        if (!isScrollbarVisible()) return false;
        UiRect track = scrollbarTrack();
        int top = thumbTop();
        return x >= track.x() && x < track.right() && y >= top && y < top + thumbHeight();
    }

    /**
     * 滑块拖动是否进行中：宿主据此在整个拖动期间消费输入，即使这一帧偏移没有变化。
     */
    public boolean isDragging() {
        return session != null && session.owns(this);
    }

    /** 内容区的纯几何命中；指针交互权限由 UiPointer 统一控制。 */
    public boolean containsContent(double x, double y) {
        return contains(x, y) && !hitScrollbar(x, y);
    }

    /**
     * 命中内容区后返回内容纵坐标；视口外及滚动条区域为空。
     * 纯几何换算仍使用 {@link #toContentY(double)}，不受命中门控影响。
     */
    public OptionalDouble hitContentY(double screenX, double screenY) {
        return containsContent(screenX, screenY)
                ? OptionalDouble.of(toContentY(screenY)) : OptionalDouble.empty();
    }

    public boolean mousePressed(UiInputContext context) {
        double x = context.pointerX();
        double y = context.pointerY();
        int button = context.button();
        if (button != 0 || !hitScrollbar(x, y)) return false;
        // 点在滑块之外（轨道空白）时先把滑块中心对到指针：否则拖动时滑块带着一个很大的抓取偏移，跟不上手。
        if (policy.centerOnPress() || y < thumbTop() || y >= thumbTop() + thumbHeight()) {
            int travel = thumbTravel();
            if (travel > 0) {
                double top = Math.clamp(y - viewport.y() - thumbHeight() / 2.0, 0.0, travel);
                setOffset((int) Math.round(top * maxOffset() / travel));
            }
        }

        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, button);
        // 抓取偏移必须在跳转之后取样，而且要用**未取整**的滑块位置：
        // 取跳转前的值会让下一次 mouseDragged 把滑块拉回原处，取跳转后的整数像素又会让第一个 drag 事件产生不足 1 像素的对齐跳动。
        dragGrab = policy.centerOnPress() ? thumbHeight() / 2.0
                : maxOffset() <= 0 ? 0.0 : y - viewport.y() - (double) thumbTravel() * offset / maxOffset();
        return true;
    }

    // 拖动滑块：指针位移按「滑块可走距离 : 内容可滚距离」映射为偏移，返回偏移是否变化。
    public boolean mouseDragged(double y) {
        if (!isDragging() || !isScrollbarVisible()) return false;
        int travel = thumbTravel();
        if (travel <= 0) return false;
        double top = Math.clamp(y - dragGrab - viewport.y(), 0.0, travel);
        int previous = offset;
        setOffset((int) Math.round(top * maxOffset() / travel));
        return offset != previous;
    }

    /**
     * 结束滚动条拖动，保留当前视图偏移。
     */
    public void endInteraction(UiInteractionSession.EndReason reason) {
        if (session != null) session.end(this, reason);
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        return session != null && session.end(this, UiInteractionSession.EndReason.RELEASE);
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dx, double dy) {
        if (!isDragging()) return false;
        mouseDragged(context.pointerY());
        return true;
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
    }

    /** 在宿主坐标绘制轨道与滑块，几何与主题 painter 均由共同入口管理。 */
    public void renderScrollbar(GuiGraphics graphics, UiStyleSource theme) {
        if (!isScrollbarVisible()) return;
        theme.drawScrollTrack(graphics, scrollbarTrack());
        theme.drawScrollThumb(graphics, scrollbarThumb());
    }

    // ---------- 内部几何 ----------

    private void clampOffset() {
        setOffset(offset);
    }

    // 滚动条始终贴着视口右边界；视口窄于滚动条时退化为整个视口宽。
    /** 当前轨道的屏幕矩形，供宿主使用自己的主题绘制。 */
    public UiRect scrollbarTrack() {
        return UiScrollbarGeometry.track(viewport, scrollbarWidth);
    }

    // 滑块高度按「视口/内容」比例缩放，并保底 MIN_THUMB_HEIGHT；没有可滚距离时占满轨道。
    private int thumbHeight() {
        return UiScrollbarGeometry.thumbHeight(viewport.height(), effectiveHeight(), contentHeight, policy.minimumThumb());
    }

    /** 当前滑块矩形，命中与主题绘制共用同一几何。 */
    public UiRect scrollbarThumb() {
        UiRect track = scrollbarTrack();
        return new UiRect(track.x(), thumbTop(), track.width(), thumbHeight());
    }

    private int thumbTravel() {
        return Math.max(0, viewport.height() - thumbHeight());
    }

    // 滑块位置按偏移占可滚距离的比例映射；无位移空间时停在轨道顶部。
    private int thumbTop() {
        return viewport.y() + (maxOffset() == 0 ? 0
                : (int) Math.round((double) thumbTravel() * offset / maxOffset()));
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        return hitScrollbar(x, y) ? UiPointerHit.leaf(this, x, y).preservingFocus() : null;
    }

}
