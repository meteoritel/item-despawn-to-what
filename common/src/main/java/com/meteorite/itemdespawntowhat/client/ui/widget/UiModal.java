package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * 模态弹窗。
 * <p>结构固定为「标题栏 + 内容区 + 按钮栏」。内容可以是纯文本消息、自定义渲染回调，
 * 也可以是任意 {@link UiWidget}（例如列表、树、输入框），由弹窗负责把鼠标、滚轮与键盘事件转发给它。
 * <p>遮罩与淡入淡出由 {@link UiModalStack} 处理，输入独占与焦点归本模态的作用域，
 * 弹窗本身只关心自身矩形内的绘制与命中。
 */
public final class UiModal implements UiWidget {
    private final UiInputScope inputScope = new UiInputScope(this);

    /**
     * 模态实例的稳定输入作用域。
     */
    public UiInputScope inputScope() {
        return inputScope;
    }


    /**
     * 自定义内容渲染回调：只负责绘制内容，内容区矩形与鼠标坐标由弹窗给出；{@code content} 为绝对屏幕坐标。
     */
    @FunctionalInterface
    public interface ContentRenderer {
        void renderContent(GuiGraphics graphics, Font font, UiRect content, int mouseX, int mouseY);
    }

    // 弹窗宽度
    public static final int DEFAULT_WIDTH = 220;
    public static final int MIN_WIDTH = 120;
    // 按钮行
    public static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_MIN_WIDTH = 40;
    private static final int BUTTON_SPACING = 4;
    // 按钮行与同排内容之间的间距
    private static final int INLINE_FOOTER_GAP = 6;

    private final UiHostTheme theme;
    private final Font font;
    private Component title = Component.empty();
    private Component message = Component.empty();
    private int contentHeight;
    private ContentRenderer contentRenderer;
    private UiWidget contentWidget;
    private final List<UiButton> buttons = new ArrayList<>();
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 绝对屏幕坐标
    private UiRect contentRect = new UiRect(0, 0, 0, 0);
    // 上次布局的按钮行高度与按钮宽度签名：内容控件布局较重，两者都未变时才允许早退
    private int appliedFooterHeight = -1;
    private int appliedButtonWidthSignature = -1;
    private int preferredWidth = DEFAULT_WIDTH;
    // 消息区滚动偏移，单位为像素
    private int messageScrollOffset;
    private boolean closeOnBackdrop;
    private boolean inlineFooter;
    // 由弹窗栈注入
    private Consumer<UiModal> closeHandler;
    private boolean visible = true;
    private Consumer<UiHistoryShortcut> onHistoryChanged = shortcut -> {
    };
    private Runnable onClosed = () -> {
    };

    /**
     * 设置关闭回调。各关闭入口统一通知宿主保存输入缓冲并释放交互。
     *
     * @param callback 关闭时执行的回调
     */
    public UiModal onClosed(Runnable callback) {
        onClosed = callback;
        return this;
    }

    void closed() {
        onClosed.run();
    }

    /**
     * 设置撤销 / 重做的历史变化回调。控件只转交快捷键，不派发底层键盘事件。
     *
     * @param callback 历史变化回调，参数为触发的快捷键
     */
    public void onHistoryChanged(Consumer<UiHistoryShortcut> callback) {
        onHistoryChanged = callback;
    }

    void historyChanged(UiHistoryShortcut shortcut) {
        onHistoryChanged.accept(shortcut);
    }

    private UiModal(UiHostTheme theme, Font font) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
        inputScope.setFocusTargets(() -> addFocusTargets(inputScope.focus()));
    }

    /**
     * 创建模态弹窗。
     *
     * @param theme 宿主主题，不可为 null
     * @param font  渲染字体
     */
    public static UiModal create(UiHostTheme theme, Font font) {
        return new UiModal(theme, font);
    }

    // ---- 配置 ----

    public UiModal title(Component title) {
        this.title = title;
        return this;
    }

    public UiModal message(Component message) {
        this.message = message;
        this.contentHeight = 0;
        return this;
    }

    /**
     * 使用渲染回调填充内容区，替换此前的控件或消息。
     *
     * @param renderer 内容渲染回调
     * @param height   内容区高度，负值按 0 处理
     */
    public UiModal content(ContentRenderer renderer, int height) {
        this.contentRenderer = renderer;
        this.contentWidget = null;
        this.contentHeight = Math.max(0, height);
        return this;
    }

    /**
     * 使用控件填充内容区，替换此前的渲染回调或消息。
     *
     * @param widget 内容控件
     * @param height 内容区高度，负值按 0 处理
     */
    public UiModal contentWidget(UiWidget widget, int height) {
        this.contentWidget = widget;
        this.contentRenderer = null;
        this.contentHeight = Math.max(0, height);
        return this;
    }

    /**
     * 设置期望宽度。低于 {@link #MIN_WIDTH} 的值按 {@link #MIN_WIDTH} 处理。
     *
     * @param width 期望宽度
     */
    public UiModal preferredWidth(int width) {
        this.preferredWidth = Math.max(MIN_WIDTH, width);
        return this;
    }

    public UiModal addButton(UiButton button) {
        buttons.add(button);
        return this;
    }

    /**
     * 追加确认按钮：点击后关闭弹窗，再执行回调。
     *
     * @param label  按钮文案
     * @param action 点击回调；null 表示只关闭
     */
    public UiModal confirm(Component label, UiAction action) {
        UiButton button = new UiButton(theme, font, label, UiButtonVariant.PRIMARY, () -> {
            requestClose();
            if (action != null) {
                action.run();
            }
        });
        return addButton(button);
    }

    /**
     * 追加取消按钮：点击后关闭弹窗。
     *
     * @param label 按钮文案
     */
    public UiModal cancel(Component label) {
        return addButton(new UiButton(theme, font, label, UiButtonVariant.SECONDARY, this::doCancel));
    }

    /**
     * 让按钮行与内容同排：内容区向下延伸一行，{@link UiFooterHost} 内容控件把翻页排到按钮左边。
     */
    public UiModal inlineFooter() {
        this.inlineFooter = true;
        return this;
    }

    /**
     * 设置点击遮罩是否关闭弹窗。
     *
     * @param closeOnBackdrop true 表示点击弹窗外部时关闭
     */
    @SuppressWarnings("unused") // 公共配置入口，当前界面尚未启用
    public UiModal closeOnBackdrop(boolean closeOnBackdrop) {
        this.closeOnBackdrop = closeOnBackdrop;
        return this;
    }

    // ---- 查询 ----

    public Component title() {
        return title;
    }

    /**
     * 按钮列表。
     *
     * @return 只读按钮列表
     */
    public List<UiButton> buttons() {
        return Collections.unmodifiableList(buttons);
    }

    /**
     * 导出弹窗的排版快照：MODAL 节点覆盖整窗外框，子节点为内容控件与页脚按钮。
     *
     * <p>内容控件实现 {@link UiLayoutSource} 时才递归展开；文本内容与自绘内容渲染器不在控件树里，
     * 此时只报一个覆盖内容区的 FRAME 节点，让探针仍能读到内容区几何。文案取标题，带宽按标题栏
     * 可用宽度算，标题过长时探针可据此发现。</p>
     */
    List<UiLayoutNode> layoutNodes() {
        if (!visible) {
            return List.of();
        }
        List<UiLayoutNode> children = new ArrayList<>();
        if (contentWidget instanceof UiLayoutSource source) {
            children.addAll(source.layoutNodes());
        } else if (contentWidget != null) {
            children.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, contentRect, Component.empty(), 0, 0,
                    true, true, List.of()));
        }
        for (UiButton button : buttons) {
            children.addAll(button.layoutNodes());
        }
        int headerBudget = Math.max(0, bounds.width() - theme.padding() * 2);
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.MODAL, bounds, title, font.width(title),
                headerBudget, true, true, List.copyOf(children)));
    }

    public boolean closeOnBackdrop() {
        return closeOnBackdrop;
    }

    public int preferredWidth() {
        return preferredWidth;
    }

    void setCloseHandler(Consumer<UiModal> closeHandler) {
        this.closeHandler = closeHandler;
    }

    public UiModal setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    // ---- 布局 ----

    public int preferredHeight() {
        return theme.headerHeight() + theme.padding() + bodyHeight() + theme.padding()
                + footerHeight(preferredWidth) + theme.padding();
    }

    private int bodyHeight() {
        if (contentWidget != null || contentRenderer != null) {
            return contentHeight;
        }
        return messageLines(Math.max(1, preferredWidth - theme.padding() * 4)).size() * (font.lineHeight + 2) + theme.padding() * 2;
    }

    private List<FormattedCharSequence> messageLines(int width) {
        if (message.getString().isEmpty()) {
            return List.of();
        }
        return font.split(message, Math.max(1, width));
    }

    /**
     * 在屏幕内居中布局。宽高按屏幕尺寸收缩，并同步更新期望宽度。
     *
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public UiModal layoutCentered(int screenWidth, int screenHeight) {
        int maxWidth = Math.max(1, screenWidth - 8);
        int width = maxWidth < MIN_WIDTH ? maxWidth : Math.clamp(preferredWidth, MIN_WIDTH, maxWidth);
        preferredWidth = width;
        int height = Math.clamp(preferredHeight(), 1, Math.max(1, screenHeight - 8));
        return setBoundsInternal((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    /**
     * 按给定矩形布局。
     *
     * @param x      左上角横坐标，负值按 0 处理
     * @param y      左上角纵坐标，负值按 0 处理
     * @param width  宽度，小于 1 时按 1 处理
     * @param height 高度，小于 {@link #BUTTON_HEIGHT} 时按 {@link #BUTTON_HEIGHT} 处理
     */
    public void layoutAt(int x, int y, int width, int height) {
        setBoundsInternal(x, y, width, height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        setBoundsInternal(x, y, width, height);
    }

    // 目标矩形与按钮行几何都没变时直接返回：弹窗静止帧不重建 bounds/contentRect，也不重排按钮与内容控件。
    // 按钮可见性或标签宽度会改变按钮行几何，因此把 footerHeight 与按钮宽度签名一起纳入比较，
    // 避免公开的 buttons() 在运行期变动后仍沿用旧矩形。
    private UiModal setBoundsInternal(int x, int y, int width, int height) {
        int nextX = Math.max(0, x);
        int nextY = Math.max(0, y);
        int nextWidth = Math.max(1, width);
        int nextHeight = Math.max(BUTTON_HEIGHT, height);
        int nextFooterHeight = footerHeight(nextWidth);
        int nextButtonWidthSignature = buttonWidthSignature(theme.padding());
        if (bounds.x() == nextX && bounds.y() == nextY && bounds.width() == nextWidth
                && bounds.height() == nextHeight && appliedFooterHeight == nextFooterHeight
                && appliedButtonWidthSignature == nextButtonWidthSignature) {
            return this;
        }
        this.bounds = new UiRect(nextX, nextY, nextWidth, nextHeight);
        appliedFooterHeight = nextFooterHeight;
        appliedButtonWidthSignature = nextButtonWidthSignature;
        // 与按钮同排时把按钮行也算进内容区高度，由内容控件自己让出右侧
        int inlineExtra = inlineFooter && contentWidget != null ? nextFooterHeight + theme.padding() : 0;
        this.contentRect = new UiRect(bounds.x() + theme.padding(), bounds.y() + theme.headerHeight() + theme.padding(),
                Math.max(0, bounds.width() - theme.padding() * 2),
                Math.max(0, bounds.height() - theme.headerHeight() - theme.padding() * 3 - nextFooterHeight) + inlineExtra);
        if (contentWidget != null) {
            contentWidget.setBounds(contentRect.x(), contentRect.y(), contentRect.width(), contentRect.height());
        }
        layoutButtons();
        if (inlineFooter && contentWidget instanceof UiFooterHost host) {
            host.setFooterReserved(inlineFooterReserved(), nextFooterHeight);
        }
        return this;
    }

    // 按钮行占用的右侧宽度：最左按钮到弹窗内边距再加间距；没有可见按钮时为 0
    private int inlineFooterReserved() {
        int leftmost = bounds.right() - theme.padding();
        boolean any = false;
        for (UiButton button : buttons) {
            if (!button.isVisible()) {
                continue;
            }
            any = true;
            leftmost = Math.min(leftmost, button.bounds().x());
        }
        return any ? Math.max(0, bounds.right() - leftmost + INLINE_FOOTER_GAP) : 0;
    }

    // 可用宽度不足时缩到可用宽度，避免按钮越出弹窗
    private int buttonWidth(UiButton button, int available) {
        return available < BUTTON_MIN_WIDTH
                ? available
                : Math.clamp(button.preferredWidth(theme.padding()), BUTTON_MIN_WIDTH, available);
    }

    // 长标签按实际宽度换行，正文为按钮预留全部行高。
    private int footerHeight(int width) {
        int available = Math.max(1, width - theme.padding() * 2);
        int used = 0;
        int rows = 0;
        for (UiButton button : buttons) {
            if (!button.isVisible()) continue;
            int next = buttonWidth(button, available);
            if (rows == 0) rows = 1;
            if (used > 0 && used + BUTTON_SPACING + next > available) {
                rows++;
                used = 0;
            }
            used += (used == 0 ? 0 : BUTTON_SPACING) + next;
        }
        return rows == 0 ? 0 : rows * BUTTON_HEIGHT + (rows - 1) * BUTTON_SPACING;
    }

    // 可见按钮的宽度签名：按钮可见性与标签宽度共同决定按钮行换行与宽度，任一变化都必须重排。
    // preferredWidth 的标签宽度内部已按语言缓存，这里只做整数乘加，不产生分配。
    private int buttonWidthSignature(int padding) {
        int signature = 1;
        for (UiButton button : buttons) {
            if (!button.isVisible()) {
                continue;
            }
            signature = signature * 31 + button.preferredWidth(padding);
        }
        return signature;
    }

    // 每行右对齐，按钮不越过弹窗边缘。
    private void layoutButtons() {
        int available = Math.max(1, bounds.width() - theme.padding() * 2);
        List<List<UiButton>> rows = new ArrayList<>();
        List<UiButton> row = new ArrayList<>();
        int used = 0;
        for (UiButton button : buttons) {
            if (!button.isVisible()) continue;
            int next = buttonWidth(button, available);
            if (!row.isEmpty() && used + BUTTON_SPACING + next > available) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            used += (row.isEmpty() ? 0 : BUTTON_SPACING) + next;
            row.add(button);
        }
        if (!row.isEmpty()) rows.add(row);
        int y = bounds.bottom() - theme.padding() - footerHeight(bounds.width());
        for (List<UiButton> line : rows) {
            int total = (line.size() - 1) * BUTTON_SPACING;
            for (UiButton button : line) total += buttonWidth(button, available);
            int x = bounds.right() - theme.padding() - total;
            for (UiButton button : line) {
                int width = buttonWidth(button, available);
                button.setBounds(x, y, width, BUTTON_HEIGHT);
                x += width + BUTTON_SPACING;
            }
            y += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    // ---- 渲染 ----

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        theme.drawWindow(graphics, bounds);
        if (!title.getString().isEmpty()) {
            UiRect header = new UiRect(bounds.x() + theme.border(), bounds.y() + theme.border(),
                    Math.max(0, bounds.width() - theme.border() * 2), theme.headerHeight());
            theme.drawHeader(graphics, header);
            graphics.drawString(font, title, header.x() + theme.padding(),
                    header.y() + Math.max(0, (header.height() - font.lineHeight) / 2), theme.palette().headerText(), false);
            theme.drawDivider(graphics, header.x(), header.bottom(), header.width());
        }
        if (contentWidget != null) {
            // 内容控件自带边框，这里不再重复画内凹底
            contentWidget.render(graphics, font, mouseX, mouseY);
        } else if (contentRenderer != null) {
            contentRenderer.renderContent(graphics, font, contentRect, mouseX, mouseY);
        } else {
            theme.drawInset(graphics, contentRect);
            List<FormattedCharSequence> lines = messageLines(contentRect.width() - theme.padding() * 2);
            int textY = contentRect.y() + theme.padding() - messageScrollOffset;
            graphics.enableScissor(contentRect.x(), contentRect.y(), contentRect.right(), contentRect.bottom());
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, contentRect.x() + theme.padding(), textY, theme.palette().textPrimary(), false);
                textY += font.lineHeight + 2;
            }
            graphics.disableScissor();
        }
        for (UiButton button : buttons) {
            if (button.isVisible()) {
                button.render(graphics, font, mouseX, mouseY);
            }
        }
    }

    // ---- 输入 ----

    @Override
    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible) {
            return false;
        }
        if (!bounds.contains(mouseX, mouseY)) {
            context.scope().pointerHit(null);
            if (closeOnBackdrop()) requestClose();
            return true;
        }
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiButton candidate = buttons.get(i);
            if (candidate.isVisible() && candidate.mousePressed(context)) {
                return true;
            }
        }
        if (contentWidget != null) {
            return contentWidget.mousePressed(context);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        if (!visible) {
            return false;
        }
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiButton candidate = buttons.get(i);
            if (candidate.isVisible()) {
                candidate.mouseReleased(context);
            }
        }
        if (contentWidget != null) {
            contentWidget.mouseReleased(context);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (contentWidget != null) {
            contentWidget.mouseDragged(context, dragX, dragY);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (contentWidget != null) {
            contentWidget.mouseScrolled(context, scrollX, scrollY);
        } else if (contentRect.contains(mouseX, mouseY)) {
            int maximum = Math.max(0, messageLines(contentRect.width() - theme.padding() * 2).size()
                    * (font.lineHeight + 2) + theme.padding() * 2 - contentRect.height());
            messageScrollOffset = Math.clamp(messageScrollOffset - (int) (scrollY * 20), 0, maximum);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) {
            return false;
        }
        // 逐级取消：先给内容控件（聚焦控件/活跃捕获）机会，未消费才关闭模态
        if (contentWidget != null && contentWidget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            doCancel();
            return true;
        }
        return false;
    }

    /**
     * 按键释放只交给本层内容，完成方向键数值交互。
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return visible && contentWidget != null && contentWidget.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return visible && contentWidget != null && contentWidget.charTyped(codePoint, modifiers);
    }

    @Override
    public void scopeExited(UiInteractionSession.EndReason reason) {
        if (contentWidget != null) contentWidget.scopeExited(reason);
    }

    // ---- 焦点 ----

    // 把自身可聚焦控件登记到焦点管理器
    public void addFocusTargets(UiFocusManager manager) {
        manager.beginUpdate();
        if (contentWidget instanceof UiFocusTarget target && target.canFocus()) {
            manager.add(target);
        }
        for (UiButton button : buttons) {
            if (button.canFocus()) {
                manager.add(button);
            }
        }
        manager.endUpdate();
    }

    // ---- 关闭 ----

    private void doCancel() {
        requestClose();
    }

    // 请求关闭自身
    private void requestClose() {
        if (closeHandler != null) {
            closeHandler.accept(this);
        }
    }
}
