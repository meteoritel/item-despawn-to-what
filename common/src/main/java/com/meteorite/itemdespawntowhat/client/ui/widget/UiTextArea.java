package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTextHistory;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 多行文本编辑框：换行、选择、滚动与剪贴板由原版 {@link MultilineTextField} 承担，外观按 kit 主题自绘。
 * <p>与 {@link UiTextInput} 的分工：单行短文本仍走原版 {@code EditBox}；需要同时看到多行或允许换行的字段用本控件。
 * <p>长度上限按 Unicode 码点计数，超限的这一次输入整体回退，不会把半个代理对或超过上限的整段文本写进草稿。
 */
public final class UiTextArea implements UiWidget, UiFocusTarget {

    // 左右内边距
    private static final int PADDING_X = 3;
    // 上下内边距
    private static final int PADDING_Y = 2;
    // 最小宽度
    private static final int MIN_WIDTH = 24;
    // 默认可见行数
    private static final int DEFAULT_ROWS = 3;
    // 内容超出可视高度时的滚动条宽度
    private static final int SCROLLBAR_WIDTH = 3;
    // 光标闪烁周期（毫秒）
    private static final long BLINK_INTERVAL = 300L;

    private final Font font;
    // 文本模型：宽度为 final，宽度变化时按当前值重建
    private AccessibleTextField textField;
    private final UiTextHistory textHistory = new UiTextHistory();
    // 控件矩形
    private UiRect bounds;
    // 已同步到文本模型的换行宽度
    private int textWidth = -1;
    // 可见行数
    private int rows = DEFAULT_ROWS;
    // 长度上限（Unicode 码点）
    private int maxLength = 1024;
    // 最近一次被接受的文本，超限时整体回退到它
    private String lastAccepted = "";
    // 垂直滚动偏移（像素）
    private int scrollOffset;
    // 占位提示
    private @Nullable Component hint;
    // 文本变化回调
    private @Nullable Runnable onChanged;
    // 输入因超出码点上限被拒绝时的回调（表单据此即时提示）
    private @Nullable Runnable onOverflow;
    // 取消回调（Esc）
    private @Nullable Runnable onCancel;
    // 无障碍名称
    private @Nullable Component accessibleName;
    // 是否可见
    private boolean visible = true;
    // 是否可编辑
    private boolean editable = true;
    private boolean selectableWhenReadOnly;
    // 是否持有焦点
    private boolean focused;
    // 本次按下是否由本控件承接
    private boolean pressed;
    // 程序化写入期间不触发变化回调与长度回退
    private boolean programmatic;
    // 获得焦点的时刻，用于光标闪烁
    private long focusedTime;

    public UiTextArea(Font font, @Nullable Component hint) {
        this.font = font;
        this.hint = hint;
        this.accessibleName = hint;
        this.textField = createField(MIN_WIDTH - PADDING_X * 2 - SCROLLBAR_WIDTH);
        this.bounds = new UiRect(0, 0, MIN_WIDTH, height());
    }

    // 建立文本模型并挂接回调
    private AccessibleTextField createField(int width) {
        AccessibleTextField field = new AccessibleTextField(font, Math.max(8, width));
        field.setValueListener(this::onTextChanged);
        field.setCursorListener(this::scrollToCursor);
        return field;
    }

    // 当前文本
    public String value() {
        return textField.value();
    }

    // 程序化设置文本：光标留在末尾，回填路径随后调用 moveCursorToStart()
    public void setValue(String value) {
        String next = value == null ? "" : value;
        boolean changed = !next.equals(textField.value());
        programmatic = true;
        try {
            textField.setValue(next);
        } finally {
            programmatic = false;
        }
        this.lastAccepted = textField.value();
        if (changed) {
            textHistory.clear();
        }
        scrollToCursor();
    }

    // 设置长度上限（Unicode 码点）
    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
    }

    // 设置是否可编辑
    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    /** 允许只读文本获得焦点、选择和复制；不启用修改或文本局部历史。 */
    public void setSelectableWhenReadOnly(boolean selectable) {
        this.selectableWhenReadOnly = selectable;
    }

    // 设置占位提示
    public void setHint(@Nullable Component hint) {
        this.hint = hint;
    }

    // 设置文本变化回调
    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    // 设置「输入超限被拒绝」回调
    public void setOnOverflow(@Nullable Runnable onOverflow) {
        this.onOverflow = onOverflow;
    }

    // 设置取消回调（Esc）
    public void setOnCancel(@Nullable Runnable onCancel) {
        this.onCancel = onCancel;
    }

    // 设置可见行数
    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
    }

    // 设置无障碍名称
    public void setAccessibleName(@Nullable Component accessibleName) {
        this.accessibleName = accessibleName;
    }

    // 设置是否可见
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // 回填后把光标与垂直滚动复位到开头；编辑期间不调用，避免抢走用户光标
    public void moveCursorToStart() {
        textField.seekCursor(Whence.ABSOLUTE, 0);
        this.scrollOffset = 0;
        clampScroll();
    }

    // 控件高度：可见行数加内边距
    public int height() {
        return rows * font.lineHeight + PADDING_Y * 2;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        ensureTextWidth();
        clampScroll();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        ensureTextWidth();
        syncBounds();
        UiTheme.drawInset(graphics, bounds);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, UiPalette.SLOT_INNER);
        int innerX = bounds.x() + PADDING_X;
        int innerY = bounds.y() + PADDING_Y;
        int innerWidth = innerWidth();
        int innerHeight = innerHeight();
        graphics.enableScissor(innerX, innerY, innerX + innerWidth + SCROLLBAR_WIDTH, innerY + innerHeight);
        try {
            if (textField.value().isEmpty() && !focused && hint != null) {
                graphics.drawWordWrap(renderFont, hint, innerX, innerY, innerWidth, UiPalette.TEXT_DISABLED);
            } else {
                renderLines(graphics, renderFont, innerX, innerY, innerHeight);
            }
        } finally {
            graphics.disableScissor();
        }
        renderScrollbar(graphics, innerY, innerHeight);
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
    }

    // 逐行绘制文本、选中高亮与光标；行高必须与原版文本模型一致（9 像素）
    private void renderLines(GuiGraphics graphics, Font renderFont, int innerX, int innerY, int innerHeight) {
        String text = textField.value();
        int cursor = textField.cursor();
        int[] selection = selectionRange();
        boolean blink = focused && (Util.getMillis() - focusedTime) / BLINK_INTERVAL % 2L == 0L;
        int lineHeight = font.lineHeight;
        List<int[]> lines = displayLines();
        for (int index = 0; index < lines.size(); index++) {
            int lineY = innerY + index * lineHeight - scrollOffset;
            if (lineY + lineHeight < innerY) {
                continue;
            }
            if (lineY > innerY + innerHeight) {
                break;
            }
            int begin = lines.get(index)[0];
            int end = lines.get(index)[1];
            if (selection.length == 2) {
                int from = Math.max(selection[0], begin);
                int to = Math.min(selection[1], end);
                if (from < to) {
                    int highlightX = innerX + renderFont.width(text.substring(begin, from));
                    int highlightWidth = renderFont.width(text.substring(from, to));
                    UiTheme.drawSelection(graphics, new UiRect(highlightX, lineY - 1, Math.max(1, highlightWidth), lineHeight));
                }
            }
            graphics.drawString(renderFont, text.substring(begin, end), innerX, lineY, UiPalette.TEXT_ON_DARK, false);
            if (blink && cursor >= begin && cursor <= end) {
                int cursorX = innerX + renderFont.width(text.substring(begin, cursor));
                graphics.fill(cursorX - 1, lineY - 1, cursorX + 1, lineY + lineHeight - 1, UiPalette.TEXT_ON_DARK);
            }
        }
    }

    // 当前选中区间 [起, 止]；原版行视图类型跨包不可命名，这里由选中文本与光标位置推导
    private int[] selectionRange() {
        if (!textField.hasSelection()) {
            return new int[0];
        }
        String text = textField.value();
        String selected = textField.getSelectedText();
        if (selected.isEmpty()) {
            return new int[0];
        }
        int cursor = textField.cursor();
        int start = cursor >= selected.length() && text.startsWith(selected, cursor - selected.length())
                ? cursor - selected.length() : cursor;
        return new int[]{start, start + selected.length()};
    }

    // 自行折行：与原版文本模型同一套拆分器，保证光标行号与行边界一致
    private List<int[]> displayLines() {
        String text = textField.value();
        List<int[]> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add(new int[]{0, 0});
            return lines;
        }
        font.getSplitter().splitLines(text, innerWidth(), Style.EMPTY, false,
                (style, begin, end) -> lines.add(new int[]{begin, end}));
        if (text.charAt(text.length() - 1) == '\n') {
            lines.add(new int[]{text.length(), text.length()});
        }
        return lines;
    }

    // 内容超出可视高度时在右侧绘制滚动条
    private void renderScrollbar(GuiGraphics graphics, int innerY, int innerHeight) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int x = bounds.right() - SCROLLBAR_WIDTH - 1;
        graphics.fill(x, innerY, x + SCROLLBAR_WIDTH, innerY + innerHeight, UiPalette.SCROLL_TRACK);
        int thumbHeight = Math.max(8, innerHeight * innerHeight / Math.max(1, textField.getLineCount() * font.lineHeight));
        int thumbY = innerY + (innerHeight - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, UiPalette.SCROLL_THUMB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!canFocus() || button != 0 || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        this.pressed = true;
        setFocused(true);
        textField.setSelecting(Screen.hasShiftDown());
        seekCursor(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed) {
            return false;
        }
        this.pressed = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!pressed || !canFocus()) {
            return false;
        }
        textField.setSelecting(true);
        seekCursor(mouseX, mouseY);
        textField.setSelecting(Screen.hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        this.scrollOffset = Math.clamp(scrollOffset - (int) Math.round(scrollY * font.lineHeight * 2), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !focused || (!editable && !selectableWhenReadOnly)) {
            return false;
        }
        UiHistoryShortcut shortcut = UiHistoryShortcut.fromKey(keyCode, modifiers);
        if (shortcut != null && editable) {
            restore(shortcut == UiHistoryShortcut.UNDO ? textHistory.undo() : textHistory.redo());
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (onCancel != null) {
                onCancel.run();
                return true;
            }
            return false;
        }
        if (!editable && !isReadOnlyNavigation(keyCode, modifiers)) return false;
        UiTextHistory.Snapshot before = snapshot();
        boolean handled = textField.keyPressed(keyCode);
        textHistory.record(before, snapshot());
        if (handled) {
            scrollToCursor();
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !editable || !focused) {
            return false;
        }
        if (!StringUtil.isAllowedChatCharacter(codePoint)) {
            return false;
        }
        UiTextHistory.Snapshot before = snapshot();
        textField.insertText(Character.toString(codePoint));
        textHistory.record(before, snapshot());
        return true;
    }

    @Override
    public boolean canFocus() {
        return visible && (editable || selectableWhenReadOnly);
    }

    private static boolean isReadOnlyNavigation(int keyCode, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0
                && (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_C)) return true;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
                    GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> true;
            default -> false;
        };
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            this.focusedTime = Util.getMillis();
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        // Enter 属于换行而不是激活：返回 false 让按键继续交给文本模型
        return false;
    }

    @Override
    public @Nullable Component accessibleName() {
        return accessibleName;
    }

    // 文本变化：超限时整体回退，否则记录并上报
    private void onTextChanged(String text) {
        if (programmatic || text.equals(lastAccepted)) {
            return;
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            int cursor = textField.cursor();
            programmatic = true;
            try {
                textField.setValue(lastAccepted);
                textField.seekCursor(Whence.ABSOLUTE, Math.min(cursor, lastAccepted.length()));
            } finally {
                programmatic = false;
            }
            scrollToCursor();
            reportOverflow();
            return;
        }
        this.lastAccepted = text;
        scrollToCursor();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private UiTextHistory.Snapshot snapshot() {
        return new UiTextHistory.Snapshot(textField.value(), textField.cursor(), textField.anchor());
    }

    private void restore(@Nullable UiTextHistory.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        programmatic = true;
        try {
            textField.setValue(snapshot.text());
            textField.setSelecting(false);
            textField.seekCursor(Whence.ABSOLUTE, snapshot.anchor());
            textField.setSelecting(true);
            textField.seekCursor(Whence.ABSOLUTE, snapshot.cursor());
            textField.setSelecting(false);
        } finally {
            programmatic = false;
        }
        lastAccepted = textField.value();
        scrollToCursor();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    // 在子类中读取原版受保护的选择区间，准确保留选择方向。
    private static final class AccessibleTextField extends MultilineTextField {
        private AccessibleTextField(Font font, int width) {
            super(font, width);
        }

        private int anchor() {
            StringView selected = getSelected();
            return cursor() == selected.beginIndex() ? selected.endIndex() : selected.beginIndex();
        }
    }

    // 上报一次「输入超出码点上限被拒绝」
    private void reportOverflow() {
        if (onOverflow != null) {
            onOverflow.run();
        }
    }

    // 按点击位置定位光标
    private void seekCursor(double mouseX, double mouseY) {
        textField.seekCursorToPoint(mouseX - (bounds.x() + PADDING_X), mouseY - (bounds.y() + PADDING_Y) + scrollOffset);
        scrollToCursor();
    }

    // 让光标所在行保持在可视区内
    private void scrollToCursor() {
        int line = Math.max(0, textField.getLineAtCursor()) * font.lineHeight;
        int viewHeight = innerHeight();
        if (line < scrollOffset) {
            this.scrollOffset = line;
        } else if (line + font.lineHeight > scrollOffset + viewHeight) {
            this.scrollOffset = line + font.lineHeight - viewHeight;
        }
        clampScroll();
    }

    // 文本模型宽度为 final，宽度变化时按当前文本与光标重建
    private void ensureTextWidth() {
        int width = innerWidth();
        if (width == textWidth) {
            return;
        }
        UiTextHistory.Snapshot snapshot = snapshot();
        AccessibleTextField next = createField(width);
        this.textWidth = width;
        programmatic = true;
        try {
            next.setValue(snapshot.text());
            next.setSelecting(false);
            next.seekCursor(Whence.ABSOLUTE, snapshot.anchor());
            next.setSelecting(true);
            next.seekCursor(Whence.ABSOLUTE, snapshot.cursor());
            next.setSelecting(false);
        } finally {
            programmatic = false;
        }
        this.textField = next;
        scrollToCursor();
    }

    // 摆放内凹框时把模型坐标贴合矩形
    private void syncBounds() {
        ensureTextWidth();
    }

    // 文本可用宽度（右侧预留滚动条）
    private int innerWidth() {
        return Math.max(8, bounds.width() - PADDING_X * 2 - SCROLLBAR_WIDTH);
    }

    // 文本可视高度
    private int innerHeight() {
        return Math.max(1, bounds.height() - PADDING_Y * 2);
    }

    private void clampScroll() {
        this.scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, textField.getLineCount() * font.lineHeight - innerHeight());
    }
}
