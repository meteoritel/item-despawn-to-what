package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTextSelection;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTextHistory;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 单行文本输入框。
 * <p>封装原版 {@link EditBox}：关闭原版边框，改由主题绘制内凹像素框；
 * 支持占位提示、长度限制、输入过滤、提交（Enter）与取消（Esc）。
 * <p>点击输入框可定位光标并拖动选择；焦点顺序由宿主的 {@code UiFocusManager} 统一维护。
 */
public final class UiTextInput implements UiWidget, UiFocusTarget, UiLayoutSource {

    // 左右内边距
    private static final int PADDING_X = 3;
    // 上下内边距
    private static final int PADDING_Y = 2;
    // 最小宽度
    private static final int MIN_WIDTH = 24;

    // 原版输入框
    private final TrackedEditBox editBox;
    private final UiTextHistory textHistory = new UiTextHistory();
    private final UiTextSelection selection;
    private boolean programmatic;
    private final Font font;
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 提交回调（Enter）
    private Consumer<String> onCommit;
    // 取消回调（Esc）
    private Runnable onCancel;
    // 无障碍名称
    private Component accessibleName;
    private boolean visible = true;
    // 是否可编辑（原版 EditBox 未暴露读取方法，这里自行记录）
    private boolean editable = true;
    // 码点上限：原版 EditBox 的上限按 UTF-16 单元截断，不能直接用，这里自己按 Unicode 码点判定
    private int maxLength = 256;
    // 最近一次被接受的文本（构造函数里初始化），超限时整体回退到它
    private String lastAccepted;
    // 业务过滤规则
    private Predicate<String> filter = text -> true;
    // 输入因超上限被拒绝时的回调
    private @Nullable Runnable onOverflow;
    // 文本被接受发生变化时的回调（宿主据此清除超限提示）
    private @Nullable Runnable onValueChanged;

    // 注入的宿主主题
    private final UiHostTheme theme;

    public UiTextInput(UiHostTheme theme, Font font, Component hint) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
        this.editBox = new TrackedEditBox(font, theme.rowHeight());
        this.editBox.setBordered(false);
        this.editBox.setHint(placeholder(hint));
        this.editBox.setTextColor(theme.palette().textOnDark());
        this.editBox.setTextColorUneditable(theme.palette().textDisabled());
        // 长度由码点过滤器控制；程序回填不经过原版截断。
        this.editBox.setMaxLength(Integer.MAX_VALUE);
        installFilter();
        this.editBox.setCanLoseFocus(true);
        this.editBox.setResponder(text -> onValueAccepted());
        this.lastAccepted = editBox.getValue();
        this.accessibleName = hint;
        selection = new UiTextSelection(new UiTextSelection.Target() {
            @Override
            public int cursor() { return editBox.getCursorPosition(); }
            @Override
            public int anchor() { return editBox.anchor(); }
            @Override
            public void seek(double pointerX, double pointerY) { seekCursor(pointerX, pointerY); }
            @Override
            public void select(int anchor, int cursor) {
                editBox.setHighlightPos(anchor);
                editBox.setCursorPosition(cursor);
            }
        });
    }

    public String value() {
        return editBox.getValue();
    }

    public UiTextInput setValue(String value) {
        backfill(value == null ? "" : value);
        return this;
    }

    public UiTextInput clear() {
        backfill("");
        return this;
    }

    // 设置长度上限（Unicode 码点）：超出上限的输入整体被拒绝，不截断，并触发 onOverflow
    public UiTextInput setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        installFilter();
        return this;
    }

    // 设置输入过滤规则：与码点上限共同构成 EditBox 的过滤器
    public UiTextInput setFilter(Predicate<String> filter) {
        this.filter = filter == null ? text -> true : filter;
        installFilter();
        return this;
    }

    // 安装过滤器：先判码点上限（超限整体拒绝并上报），再判业务规则
    private void installFilter() {
        editBox.setFilter(candidate -> {
            if (programmatic) {
                return true;
            }
            if (codePoints(candidate) > maxLength) {
                reportOverflow();
                return false;
            }
            return filter.test(candidate);
        });
    }

    // 文本被接受：更新回退基线并清除超限提示；超限兜底回退（正常已被过滤器拦住）
    private void onValueAccepted() {
        String value = editBox.getValue();
        if (programmatic || value.equals(lastAccepted)) {
            return;
        }
        if (codePoints(value) > maxLength) {
            editBox.setValue(lastAccepted);
            return;
        }
        this.lastAccepted = value;
        if (onValueChanged != null) {
            onValueChanged.run();
        }
    }

    // Unicode 码点计数：补充平面字符（emoji）按 1 个码点计
    private static int codePoints(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    // 上报一次「输入超出码点上限被拒绝」
    private void reportOverflow() {
        if (onOverflow != null) {
            onOverflow.run();
        }
    }

    public UiTextInput setOnOverflow(@Nullable Runnable onOverflow) {
        this.onOverflow = onOverflow;
        return this;
    }

    public UiTextInput setOnValueChanged(@Nullable Runnable onValueChanged) {
        this.onValueChanged = onValueChanged;
        return this;
    }

    public UiTextInput setEditable(boolean editable) {
        if (!editable) endInteraction(UiInputCapture.EndReason.DISABLED);
        this.editable = editable;
        editBox.setEditable(editable);
        return this;
    }

    public UiTextInput setHint(Component hint) {
        editBox.setHint(placeholder(hint));
        return this;
    }

    /**
     * 把占位提示染成禁用文本色，与多行输入框的占位提示保持一致。
     *
     * <p>原版 {@link EditBox} 绘制提示时复用正文的 {@code textColor}，无法单独指定颜色；
     * 但组件自带的 {@link net.minecraft.network.chat.Style} 颜色优先于绘制参数，
     * 因此在这里给提示挂上样式色即可只影响提示，不影响用户实际输入的正文。</p>
     */
    private Component placeholder(Component hint) {
        // 提示为 null 或空时无需着色，避免无意义地构造副本
        if (hint == null || hint.getString().isEmpty()) {
            return hint;
        }
        return hint.copy().withStyle(hint.getStyle().withColor(TextColor.fromRgb(theme.palette().textDisabled())));
    }

    public UiTextInput setOnCommit(Consumer<String> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    public UiTextInput setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    public UiTextInput setVisible(boolean visible) {
        selection.setVisible(visible);
        this.visible = visible;
        editBox.setVisible(visible);
        return this;
    }

    // 全选当前文本
    public UiTextInput selectAll() {
        editBox.setHighlightPos(0);
        editBox.moveCursorToEnd(true);
        return this;
    }

    // 把光标与水平滚动复位到开头；回填后调用一次，编辑期间不调用
    public UiTextInput moveCursorToStart() {
        editBox.moveCursorToStart(false);
        editBox.setHighlightPos(0);
        return this;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    /**
     * 更新布局；文本宽度变化时重算水平视口，保留光标与选择锚点。
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        editBox.setX(bounds.x() + PADDING_X);
        editBox.setY(bounds.y() + Math.max(PADDING_Y, (bounds.height() - font.lineHeight) / 2));
        int textWidth = Math.max(1, bounds.width() - PADDING_X * 2);
        if (textWidth != editBox.getWidth()) {
            int cursor = editBox.getCursorPosition();
            int anchor = editBox.anchor();
            editBox.setWidth(textWidth);
            // EditBox 扩宽不会清理旧滚动偏移；先归零视口再恢复编辑位置，避免能放下的文本仍缺首字。
            editBox.moveCursorToStart(false);
            editBox.setCursorPosition(cursor);
            editBox.setHighlightPos(anchor);
        }
        editBox.setHeight(Math.max(1, bounds.height() - PADDING_Y * 2));
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    /**
     * 导出输入框的排版快照。
     *
     * <p>文案取当前输入值；输入为空时退回占位提示，与屏幕上的可见内容一致。
     * 带宽扣除左右内边距，探针据此判断长文本是否被横向滚动裁切。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!visible || bounds.width() <= 0 || bounds.height() <= 0) {
            return List.of();
        }
        String text = value();
        Component content = text.isEmpty() ? accessibleName : Component.literal(text);
        int budget = Math.max(0, bounds.width() - PADDING_X * 2);
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.TEXT_INPUT, bounds, content, font.width(content),
                budget, editable, true, List.of()));
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        theme.drawInset(graphics, bounds);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, theme.palette().slotInner());
        UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND,
                () -> editBox.renderWidget(graphics, mouseX, mouseY, 0.0F));
        if (editBox.isFocused()) {
            theme.drawFocusOutline(graphics, bounds);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        selection.setBounds(bounds);
        selection.setEnabled(canFocus());
        if (!selection.mousePressed(pointerContext(mouseX, mouseY, button))) return false;
        setFocused(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return selection.mouseReleased(pointerContext(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return selection.mouseDragged(pointerContext(mouseX, mouseY, button));
    }

    public void endInteraction(UiInputCapture.EndReason reason) {
        selection.endInteraction(reason);
    }

    private static UiInputContext pointerContext(double mouseX, double mouseY, int button) {
        return UiInputContext.of(mouseX, mouseY, button,
                Screen.hasShiftDown(), Screen.hasControlDown(), Screen.hasAltDown());
    }

    private void seekCursor(double mouseX, double mouseY) {
        editBox.onClick(mouseX, mouseY);
        if (!selection.isSelecting()) return;
        int cursor = editBox.getCursorPosition();
        // 越界拖动越过可见文本的端点，推动水平视口；按码点移动，避免拆开代理对。
        if (mouseX < editBox.getX() && cursor > 0) {
            editBox.setCursorPosition(value().offsetByCodePoints(cursor, -1));
        } else if (mouseX >= editBox.getX() + editBox.getWidth() && cursor < value().length()) {
            editBox.setCursorPosition(value().offsetByCodePoints(cursor, 1));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        endInteraction(UiInputCapture.EndReason.RELEASE);
        if (!visible || !editBox.isFocused()) {
            return false;
        }
        UiHistoryShortcut shortcut = UiHistoryShortcut.fromKey(keyCode, modifiers);
        if (shortcut != null && editable) {
            restore(shortcut == UiHistoryShortcut.UNDO ? textHistory.undo() : textHistory.redo());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (onCommit != null) {
                onCommit.accept(editBox.getValue());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (onCancel != null) {
                onCancel.run();
                return true;
            }
            return false;
        }
        UiTextHistory.Snapshot snapshot = snapshot();
        boolean handled = editBox.keyPressed(keyCode, scanCode, modifiers);
        textHistory.record(snapshot, snapshot());
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        endInteraction(UiInputCapture.EndReason.RELEASE);
        if (!visible) {
            return false;
        }
        UiTextHistory.Snapshot snapshot = snapshot();
        boolean handled = editBox.charTyped(codePoint, modifiers);
        textHistory.record(snapshot, snapshot());
        return handled;
    }

    @Override
    public boolean canFocus() {
        return visible && editable;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        editBox.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return editBox.isFocused();
    }

    @Override
    public boolean activate() {
        if (!visible || onCommit == null) {
            return false;
        }
        onCommit.accept(editBox.getValue());
        return true;
    }

    @Override
    public Component accessibleName() {
        return accessibleName;
    }

    // 程序回填不通知宿主，不产生局部历史；不同内容成为新的装载基线。
    private void backfill(String value) {
        endInteraction(UiInputCapture.EndReason.UNMOUNTED);
        boolean changed = !value.equals(editBox.getValue());
        programmatic = true;
        try {
            editBox.setValue(value);
        } finally {
            programmatic = false;
        }
        lastAccepted = editBox.getValue();
        if (changed) {
            textHistory.clear();
        }
    }

    private UiTextHistory.Snapshot snapshot() {
        return new UiTextHistory.Snapshot(editBox.getValue(), editBox.getCursorPosition(), editBox.anchor());
    }

    private void restore(@Nullable UiTextHistory.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        programmatic = true;
        try {
            editBox.setValue(snapshot.text());
            editBox.setCursorPosition(snapshot.cursor());
            editBox.setHighlightPos(snapshot.anchor());
        } finally {
            programmatic = false;
        }
        lastAccepted = editBox.getValue();
        if (onValueChanged != null) {
            onValueChanged.run();
        }
    }

    // 原版未暴露选择锚点；记录公开设置入口，避免用重复文本猜测选择方向。
    private static final class TrackedEditBox extends EditBox {
        private int anchor;

        private TrackedEditBox(Font font, int rowHeight) {
            super(font, 0, 0, MIN_WIDTH, rowHeight, Component.empty());
        }

        @Override
        public void setHighlightPos(int position) {
            super.setHighlightPos(position);
            anchor = Math.clamp(position, 0, getValue().length());
        }

        private int anchor() {
            return anchor;
        }
    }

    public UiTextInput setAccessibleName(Component name) {
        this.accessibleName = name;
        return this;
    }
}
