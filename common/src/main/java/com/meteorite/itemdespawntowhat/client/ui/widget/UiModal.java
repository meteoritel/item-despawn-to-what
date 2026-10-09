package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/***
 * 模态弹窗。
 * <p>结构固定为「标题栏 + 内容区 + 按钮栏」。内容可以是纯文本消息、自定义渲染回调，
 * 也可以是任意 {@link UiWidget}（例如列表、树、输入框），由弹窗负责把鼠标、滚轮与键盘事件转发给它。
 * <p>遮罩、淡入淡出、焦点切换与点击穿透由 {@link UiModalStack} 统一处理，
 * 弹窗本身只关心自身矩形内的绘制与命中。
 */
public final class UiModal implements UiWidget {

    // 内容渲染回调
    @FunctionalInterface
    public interface ContentRenderer {
        // 在内容区内渲染；content 为绝对屏幕坐标矩形
        void renderContent(GuiGraphics graphics, Font font, UiRect content, int mouseX, int mouseY);
    }

    // 默认宽度
    public static final int DEFAULT_WIDTH = 220;
    // 最小宽度
    public static final int MIN_WIDTH = 120;
    // 按钮高度
    public static final int BUTTON_HEIGHT = 16;
    // 按钮最小宽度
    private static final int BUTTON_MIN_WIDTH = 40;
    // 按钮间距
    private static final int BUTTON_SPACING = 4;

    // 字体
    private final Font font;
    // 标题
    private Component title = Component.empty();
    // 纯文本消息
    private Component message = Component.empty();
    // 内容高度
    private int contentHeight;
    // 自定义内容渲染
    private ContentRenderer contentRenderer;
    // 自定义内容控件
    private UiWidget contentWidget;
    // 按钮
    private final List<UiButton> buttons = new ArrayList<>();
    // 弹窗矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 内容区矩形（绝对坐标）
    private UiRect contentRect = new UiRect(0, 0, 0, 0);
    // 期望宽度
    private int preferredWidth = DEFAULT_WIDTH;
    private int messageOffset;
    // 点击遮罩是否关闭
    private boolean closeOnBackdrop;
    // 确认后是否保留弹窗
    private boolean retainOnConfirm;
    // 取消回调
    private UiAction onCancel;
    // 关闭回调（由弹窗栈注入）
    private Consumer<UiModal> closeHandler;
    // 是否可见
    private boolean visible = true;
    private Runnable onHistoryChanged = () -> {};
    private Runnable onClosed = () -> {};

    /** 各关闭入口统一通知宿主保存输入缓冲并释放交互。 */
    public UiModal onClosed(Runnable callback) {
        onClosed = callback;
        return this;
    }

    void closed() {
        onClosed.run();
    }

    /** 宿主配置历史恢复后，重新装载当前内容；不派发底层键盘事件。 */
    public UiModal onHistoryChanged(Runnable callback) {
        onHistoryChanged = callback;
        return this;
    }

    void historyChanged() {
        onHistoryChanged.run();
    }

    private UiModal(Font font) {
        this.font = font;
    }

    // 新建弹窗
    public static UiModal create(Font font) {
        return new UiModal(font);
    }

    // ---- 配置 ----

    // 设置标题
    public UiModal title(Component title) {
        this.title = title;
        return this;
    }

    // 设置纯文本消息
    public UiModal message(Component message) {
        this.message = message;
        this.contentHeight = 0;
        return this;
    }

    // 设置自定义内容渲染
    public UiModal content(ContentRenderer renderer, int height) {
        this.contentRenderer = renderer;
        this.contentWidget = null;
        this.contentHeight = Math.max(0, height);
        return this;
    }

    // 设置自定义内容控件
    public UiModal contentWidget(UiWidget widget, int height) {
        this.contentWidget = widget;
        this.contentRenderer = null;
        this.contentHeight = Math.max(0, height);
        return this;
    }

    // 设置期望宽度
    public UiModal preferredWidth(int width) {
        this.preferredWidth = Math.max(MIN_WIDTH, width);
        return this;
    }

    // 追加自定义按钮
    public UiModal addButton(UiButton button) {
        buttons.add(button);
        return this;
    }

    // 追加确认按钮
    public UiModal confirm(Component label, UiAction action) {
        UiButton button = new UiButton(font, label, UiButtonVariant.PRIMARY, () -> {
            if (!retainOnConfirm) {
                requestClose();
            }
            if (action != null) {
                action.run();
            }
        });
        return addButton(button);
    }

    // 追加取消按钮
    public UiModal cancel(Component label) {
        return addButton(new UiButton(font, label, UiButtonVariant.SECONDARY, this::doCancel));
    }

    // 设置点击遮罩是否关闭
    public UiModal closeOnBackdrop(boolean closeOnBackdrop) {
        this.closeOnBackdrop = closeOnBackdrop;
        return this;
    }

    // 设置确认后是否保留弹窗（用于多步操作）
    public UiModal retainOnConfirm(boolean retainOnConfirm) {
        this.retainOnConfirm = retainOnConfirm;
        return this;
    }

    // 设置取消回调
    public UiModal onCancel(UiAction action) {
        this.onCancel = action;
        return this;
    }

    // ---- 查询 ----

    // 标题
    public Component title() {
        return title;
    }

    // 按钮列表（只读）
    public List<UiButton> buttons() {
        return Collections.unmodifiableList(buttons);
    }

    // 点击遮罩是否关闭
    public boolean closeOnBackdrop() {
        return closeOnBackdrop;
    }

    // 确认后是否保留
    public boolean retainOnConfirm() {
        return retainOnConfirm;
    }

    // 期望宽度
    public int preferredWidth() {
        return preferredWidth;
    }

    // 内容区矩形
    public UiRect contentRect() {
        return contentRect;
    }

    // 设置关闭回调
    void setCloseHandler(Consumer<UiModal> closeHandler) {
        this.closeHandler = closeHandler;
    }

    // 设置是否可见
    public UiModal setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    // ---- 布局 ----

    // 弹窗期望高度
    public int preferredHeight() {
        return UiTheme.HEADER_HEIGHT + UiTheme.PADDING + bodyHeight() + UiTheme.PADDING
                + footerHeight(preferredWidth) + UiTheme.PADDING;
    }

    // 内容区高度
    private int bodyHeight() {
        if (contentWidget != null || contentRenderer != null) {
            return contentHeight;
        }
        return messageLines(Math.max(1, preferredWidth - UiTheme.PADDING * 4)).size() * (font.lineHeight + 2) + UiTheme.PADDING * 2;
    }

    // 按可用宽度换行后的消息行
    private List<FormattedCharSequence> messageLines(int width) {
        if (message.getString().isEmpty()) {
            return List.of();
        }
        return font.split(message, Math.max(1, width));
    }

    // 在给定屏幕区域内居中布局
    public UiModal layoutCentered(int screenWidth, int screenHeight) {
        int width = Math.min(Math.max(MIN_WIDTH, preferredWidth), Math.max(1, screenWidth - 8));
        preferredWidth = width;
        int height = Math.min(preferredHeight(), Math.max(1, screenHeight - 8));
        return setBoundsInternal((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    // 直接指定弹窗矩形
    public UiModal layoutAt(int x, int y, int width, int height) {
        return setBoundsInternal(x, y, width, height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        setBoundsInternal(x, y, width, height);
    }

    private UiModal setBoundsInternal(int x, int y, int width, int height) {
        this.bounds = new UiRect(Math.max(0, x), Math.max(0, y), Math.max(1, width), Math.max(BUTTON_HEIGHT, height));
        this.contentRect = new UiRect(bounds.x() + UiTheme.PADDING, bounds.y() + UiTheme.HEADER_HEIGHT + UiTheme.PADDING,
                Math.max(0, bounds.width() - UiTheme.PADDING * 2),
                Math.max(0, bounds.height() - UiTheme.HEADER_HEIGHT - UiTheme.PADDING * 3 - footerHeight(bounds.width())));
        if (contentWidget != null) {
            contentWidget.setBounds(contentRect.x(), contentRect.y(), contentRect.width(), contentRect.height());
        }
        layoutButtons();
        return this;
    }

    // 长标签按实际宽度换行，正文为按钮预留全部行高。
    private int footerHeight(int width) {
        int available = Math.max(1, width - UiTheme.PADDING * 2);
        int used = 0;
        int rows = 0;
        for (UiButton button : buttons) {
            if (!button.isVisible()) continue;
            int next = Math.min(available, Math.max(BUTTON_MIN_WIDTH, button.preferredWidth(UiTheme.PADDING)));
            if (rows == 0) rows = 1;
            if (used > 0 && used + BUTTON_SPACING + next > available) { rows++; used = 0; }
            used += (used == 0 ? 0 : BUTTON_SPACING) + next;
        }
        return rows == 0 ? 0 : rows * BUTTON_HEIGHT + (rows - 1) * BUTTON_SPACING;
    }

    // 每行右对齐，按钮不越过弹窗边缘。
    private void layoutButtons() {
        int available = Math.max(1, bounds.width() - UiTheme.PADDING * 2);
        List<List<UiButton>> rows = new ArrayList<>();
        List<UiButton> row = new ArrayList<>();
        int used = 0;
        for (UiButton button : buttons) {
            if (!button.isVisible()) continue;
            int next = Math.min(available, Math.max(BUTTON_MIN_WIDTH, button.preferredWidth(UiTheme.PADDING)));
            if (!row.isEmpty() && used + BUTTON_SPACING + next > available) {
                rows.add(row); row = new ArrayList<>(); used = 0;
            }
            used += (row.isEmpty() ? 0 : BUTTON_SPACING) + next;
            row.add(button);
        }
        if (!row.isEmpty()) rows.add(row);
        int y = bounds.bottom() - UiTheme.PADDING - footerHeight(bounds.width());
        for (List<UiButton> line : rows) {
            int total = (line.size() - 1) * BUTTON_SPACING;
            for (UiButton button : line) total += Math.min(available, Math.max(BUTTON_MIN_WIDTH, button.preferredWidth(UiTheme.PADDING)));
            int x = bounds.right() - UiTheme.PADDING - total;
            for (UiButton button : line) {
                int buttonWidth = Math.min(available, Math.max(BUTTON_MIN_WIDTH, button.preferredWidth(UiTheme.PADDING)));
                button.setBounds(x, y, buttonWidth, BUTTON_HEIGHT);
                x += buttonWidth + BUTTON_SPACING;
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
        UiTheme.drawWindow(graphics, bounds);
        if (!title.getString().isEmpty()) {
            UiRect header = new UiRect(bounds.x() + UiTheme.BORDER, bounds.y() + UiTheme.BORDER,
                    Math.max(0, bounds.width() - UiTheme.BORDER * 2), UiTheme.HEADER_HEIGHT);
            UiTheme.drawHeader(graphics, header);
            graphics.drawString(font, title, header.x() + UiTheme.PADDING,
                    header.y() + Math.max(0, (header.height() - font.lineHeight) / 2), UiPalette.HEADER_TEXT, false);
            UiTheme.drawDivider(graphics, header.x(), header.bottom(), header.width());
        }
        if (contentWidget != null) {
            // 内容控件自带边框，这里不再重复画内凹底
            contentWidget.render(graphics, font, mouseX, mouseY);
        } else if (contentRenderer != null) {
            contentRenderer.renderContent(graphics, font, contentRect, mouseX, mouseY);
        } else {
            UiTheme.drawInset(graphics, contentRect);
            List<FormattedCharSequence> lines = messageLines(contentRect.width() - UiTheme.PADDING * 2);
            int textY = contentRect.y() + UiTheme.PADDING - messageOffset;
            graphics.enableScissor(contentRect.x(), contentRect.y(), contentRect.right(), contentRect.bottom());
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, contentRect.x() + UiTheme.PADDING, textY, UiPalette.TEXT_PRIMARY, false);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) {
            return false;
        }
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiButton candidate = buttons.get(i);
            if (candidate.isVisible() && candidate.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (contentWidget != null) {
            return contentWidget.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible) {
            return false;
        }
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiButton candidate = buttons.get(i);
            if (candidate.isVisible()) {
                candidate.mouseReleased(mouseX, mouseY, button);
            }
        }
        if (contentWidget != null) {
            contentWidget.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (contentWidget != null && contentWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (contentWidget != null) {
            contentWidget.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } else if (contentRect.contains(mouseX, mouseY)) {
            int maximum = Math.max(0, messageLines(contentRect.width() - UiTheme.PADDING * 2).size()
                    * (font.lineHeight + 2) + UiTheme.PADDING * 2 - contentRect.height());
            messageOffset = Math.clamp(messageOffset - (int) (scrollY * 20), 0, maximum);
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

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return visible && contentWidget != null && contentWidget.charTyped(codePoint, modifiers);
    }

    // ---- 焦点 ----

    // 把自身可聚焦控件登记到焦点管理器
    public void addFocusTargets(UiFocusManager manager) {
        manager.clear();
        if (contentWidget instanceof UiFocusTarget target && target.canFocus()) {
            manager.add(target);
        }
        for (UiButton button : buttons) {
            if (button.canFocus()) {
                manager.add(button);
            }
        }
    }

    // ---- 关闭 ----

    // 触发取消
    private void doCancel() {
        if (onCancel != null) {
            onCancel.run();
        }
        requestClose();
    }

    // 请求关闭自身
    private void requestClose() {
        if (closeHandler != null) {
            closeHandler.accept(this);
        }
    }
}
