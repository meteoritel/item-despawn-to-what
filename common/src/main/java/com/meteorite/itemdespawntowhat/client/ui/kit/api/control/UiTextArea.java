package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.UiTextValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.UiTextEditSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.UiTextHistory;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import java.util.Objects;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
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
public final class UiTextArea implements UiWidget, UiTextValue, UiLayoutSource {

    // 左右内边距
    private static final int PADDING_X = TextInputSupport.PADDING_X;
    // 上下内边距
    private static final int PADDING_Y = TextInputSupport.PADDING_Y;
    // 最小宽度
    private static final int MIN_WIDTH = TextInputSupport.MIN_WIDTH;
    // 默认可见行数
    private static final int DEFAULT_ROWS = 3;
    // 内容超出可视高度时的滚动条宽度
    private static final int SCROLLBAR_WIDTH = 3;
    // 光标闪烁周期（毫秒）
    private static final long BLINK_INTERVAL = 300L;

    private final Font font;
    // 文本模型：宽度为 final，宽度变化时按当前值重建
    private AccessibleTextField textField;
    private final UiTextEditSession editSession;
    // 控件矩形
    private UiRect bounds;
    // 已同步到文本模型的换行宽度
    private int textWidth = -1;
    private @Nullable String displayedText;
    private int displayedWidth = -1;
    private List<int[]> displayedLines = List.of();
    // 可见行数
    private int rows = DEFAULT_ROWS;
    // 垂直滚动偏移（像素）
    private final UiScrollView scroll = new UiScrollView();
    // 占位提示
    private @Nullable Component hint;
    // 取消回调（Esc）
    private @Nullable Runnable onCancel;
    // 无障碍名称
    private @Nullable Component accessibleName;
    private boolean visible = true;
    private boolean editable = true;
    private boolean selectableWhenReadOnly;
    private final UiFocusState focusState = new UiFocusState();
    // 获得焦点的时刻，用于光标闪烁
    private long focusedTime;

    // 注入的宿主主题
    private final UiStyleSource theme;

    /** 使用原版默认主题创建多行输入。 */
    public UiTextArea(Font font, @Nullable Component hint) { this(UiMinecraftTheme.INSTANCE, font, hint); }

    /** 注入主题并复用原版多行文本模型与 kit 编辑会话。 */
    public UiTextArea(UiStyleSource theme, Font font, @Nullable Component hint) {
        this.theme = Objects.requireNonNull(theme, "theme");
        scroll.setScrollbarWidth(SCROLLBAR_WIDTH);
        this.font = font;
        this.hint = hint;
        this.accessibleName = hint;
        this.textField = createField(MIN_WIDTH - PADDING_X * 2 - SCROLLBAR_WIDTH);
        this.bounds = new UiRect(0, 0, MIN_WIDTH, height());
        editSession = new UiTextEditSession(new UiTextEditSession.Model() {
            @Override
            public String value() {
                return textField.value();
            }

            @Override
            public void write(String text) {
                textField.setValue(text);
            }

            @Override
            public void restoreRejected(String text) {
                int cursor = textField.cursor();
                textField.setValue(text);
                textField.seekCursor(Whence.ABSOLUTE, Math.min(cursor, text.length()));
            }

            @Override
            public int cursor() {
                return textField.cursor();
            }

            @Override
            public int anchor() {
                return textField.anchor();
            }

            @Override
            public void seek(double pointerX, double pointerY) {
                textField.setSelecting(false);
                seekCursor(pointerX, pointerY);
            }

            @Override
            public void select(int anchor, int cursor) {
                textField.setSelecting(false);
                textField.seekCursor(Whence.ABSOLUTE, anchor);
                textField.setSelecting(true);
                textField.seekCursor(Whence.ABSOLUTE, cursor);
                textField.setSelecting(false);
            }
        }, 1024);
    }

    // 建立文本模型并挂接回调
    private AccessibleTextField createField(int width) {
        AccessibleTextField field = new AccessibleTextField(font, Math.max(8, width));
        field.setValueListener(this::onTextChanged);
        field.setCursorListener(this::scrollToCursor);
        return field;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value() {
        return textField.value();
    }

    // 程序化设置文本：光标留在末尾，回填路径随后调用 moveCursorToStart()

    /**
     * {@inheritDoc}
     */
    @Override
    public UiTextArea setValue(String value) {
        editSession.backfill(value == null ? "" : value);
        scrollToCursor();
        return this;
    }

    /**
     * 全选原版文本模型并将选择末端滚动进视口。
     */
    @Override
    public UiTextArea selectAll() {
        textField.setSelecting(false);
        textField.seekCursor(Whence.ABSOLUTE, 0);
        textField.setSelecting(true);
        textField.seekCursor(Whence.ABSOLUTE, value().length());
        textField.setSelecting(false);
        scrollToCursor();
        return this;
    }

    // 设置长度上限（Unicode 码点）

    /**
     * {@inheritDoc}
     */
    @Override
    public UiTextArea setMaxLength(int maxLength) {
        editSession.acceptance().setMaximum(maxLength);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiTextArea setEditable(boolean editable) {
        if (!editable && !selectableWhenReadOnly) endInteraction(UiInteractionSession.EndReason.DISABLED);
        this.editable = editable;
        return this;
    }

    /**
     * 允许只读文本获得焦点、选择和复制；不启用修改或文本局部历史。
     */
    public void setSelectableWhenReadOnly(boolean selectable) {
        if (!selectable && !editable) endInteraction(UiInteractionSession.EndReason.DISABLED);
        this.selectableWhenReadOnly = selectable;
    }

    public void setHint(@Nullable Component hint) {
        this.hint = hint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiTextArea setOnValueChanged(@Nullable Runnable onChanged) {
        editSession.acceptance().setOnChanged(onChanged);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiTextArea setOnOverflow(@Nullable Runnable onOverflow) {
        editSession.acceptance().setOnOverflow(onOverflow);
        return this;
    }

    // 设置取消回调（Esc）
    public void setOnCancel(@Nullable Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
    }

    public void setAccessibleName(@Nullable Component accessibleName) {
        this.accessibleName = accessibleName;
    }

    public void setVisible(boolean visible) {
        editSession.setVisible(visible);
        this.visible = visible;
        if (!visible) scroll.endInteraction(UiInteractionSession.EndReason.HIDDEN);
    }

    // 回填后把光标与垂直滚动复位到开头；编辑期间不调用，避免抢走用户光标

    /**
     * {@inheritDoc}
     */
    @Override
    public UiTextArea moveCursorToStart() {
        textField.seekCursor(Whence.ABSOLUTE, 0);
        scroll.setOffset(0);
        clampScroll();
        return this;
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

    /**
     * 导出多行输入框的排版快照。
     *
     * <p>多行内容不拆成子节点：节点文案只报首行（空内容时退回占位提示），
     * 宽度按控件内容宽计，探针仍能判断当前行是否横向被裁。纵向可见行数由矩形高度给出。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!visible || bounds.width() <= 0 || bounds.height() <= 0) {
            return List.of();
        }
        String text = value();
        String firstLine = text.isEmpty() ? "" : text.split("\n", 2)[0];
        Component content = firstLine.isEmpty() && hint != null ? hint : Component.literal(firstLine);
        int budget = Math.max(0, bounds.width() - PADDING_X * 2);
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.TEXT_INPUT, bounds, content, font.width(content),
                budget, editable, true, List.of()));
    }

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font renderFont, UiPointer incomingPointer) {
        if (!visible) {
            return;
        }
        ensureTextWidth();
        syncBounds();
        theme.drawInset(graphics, bounds);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, theme.surfaces().inputFill());
        int innerX = bounds.x() + PADDING_X;
        int innerY = bounds.y() + PADDING_Y;
        int innerWidth = innerWidth();
        int innerHeight = innerHeight();
        graphics.enableScissor(innerX, innerY, innerX + innerWidth + SCROLLBAR_WIDTH, innerY + innerHeight);
        try {
            if (textField.value().isEmpty() && !isFocused() && hint != null) {
                UiFlatText.drawWrapped(graphics, renderFont, hint, innerX, innerY, innerWidth, theme.surfaces().disabledText());
            } else {
                renderLines(graphics, renderFont, innerX, innerY, innerHeight);
            }
        } finally {
            graphics.disableScissor();
        }
        renderScrollbar(graphics);
        if (isFocusVisible()) {
            theme.drawFocusOutline(graphics, bounds);
        }
    }

    // 逐行绘制文本、选中高亮与光标；行高必须与原版文本模型一致（9 像素）
    private void renderLines(GuiGraphics graphics, Font renderFont, int innerX, int innerY, int innerHeight) {
        String text = textField.value();
        int cursor = textField.cursor();
        int[] selection = selectionRange();
        boolean blink = isFocused() && (Util.getMillis() - focusedTime) / BLINK_INTERVAL % 2L == 0L;
        int lineHeight = font.lineHeight;
        List<int[]> lines = displayLines();
        for (int index = 0; index < lines.size(); index++) {
            int lineY = innerY + index * lineHeight - scroll.offset();
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
                    theme.drawSelection(graphics, new UiRect(highlightX, lineY - 1, Math.max(1, highlightWidth), lineHeight));
                }
            }
            graphics.drawString(renderFont, text.substring(begin, end), innerX, lineY, theme.surfaces().inputText(), false);
            if (blink && cursor >= begin && cursor <= end) {
                int cursorX = innerX + renderFont.width(text.substring(begin, cursor));
                graphics.fill(cursorX - 1, lineY - 1, cursorX + 1, lineY + lineHeight - 1, theme.surfaces().inputText());
            }
        }
    }

    // 当前选中区间 [起, 止]，直接使用原版选择锚点，重复文本不影响高亮位置。
    private int[] selectionRange() {
        if (!textField.hasSelection()) {
            return new int[0];
        }
        int cursor = textField.cursor();
        int anchor = textField.anchor();
        return new int[]{Math.min(cursor, anchor), Math.max(cursor, anchor)};
    }

    // 自行折行：与原版文本模型同一套拆分器，保证光标行号与行边界一致
    private List<int[]> displayLines() {
        String text = textField.value();
        int width = innerWidth();
        if (text.equals(displayedText) && width == displayedWidth) return displayedLines;
        List<int[]> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add(new int[]{0, 0});
        } else {
            font.getSplitter().splitLines(text, width, Style.EMPTY, false,
                    (style, begin, end) -> lines.add(new int[]{begin, end}));
            if (text.charAt(text.length() - 1) == '\n') lines.add(new int[]{text.length(), text.length()});
        }
        displayedText = text;
        displayedWidth = width;
        displayedLines = List.copyOf(lines);
        return displayedLines;
    }

    // 内容超出可视高度时在右侧绘制滚动条
    private void renderScrollbar(GuiGraphics graphics) {
        if (!scroll.isScrollbarVisible()) return;
        theme.drawScrollTrack(graphics, scroll.scrollbarTrack());
        theme.drawScrollThumb(graphics, scroll.scrollbarThumb());
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (visible && scroll.mousePressed(context)) return true;
        return editSession.beginSelection(context, bounds, canFocus());
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        return editSession.mouseReleased(context);
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        return editSession.mouseDragged(context, dragX, dragY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endInteraction(UiInteractionSession.EndReason reason) {
        scroll.endInteraction(reason);
        editSession.endInteraction(reason);
    }


    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        return scroll.scrollByPixels(scrollY * font.lineHeight * 2);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        endInteraction(UiInteractionSession.EndReason.RELEASE);
        if (!visible || !isFocused() || (!editable && !selectableWhenReadOnly)) {
            return false;
        }
        UiHistoryShortcut shortcut = UiHistoryShortcut.fromKey(keyCode, modifiers);
        if (shortcut != null && editable) {
            editSession.applyHistory(shortcut);
            scrollToCursor();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (onCancel != null) {
                onCancel.run();
                return true;
            }
            return false;
        }
        if (!editable && !isReadOnlyNavigation(keyCode, modifiers)) return false;
        boolean handled = editSession.edit(() -> textField.keyPressed(keyCode));
        if (handled) {
            scrollToCursor();
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        endInteraction(UiInteractionSession.EndReason.RELEASE);
        if (!visible || !editable || !isFocused()) {
            return false;
        }
        if (!StringUtil.isAllowedChatCharacter(codePoint)) {
            return false;
        }
        return editSession.edit(() -> {
            textField.insertText(Character.toString(codePoint));
            return true;
        });
    }

    @Override
    public UiFocusState focusState() {
        return focusState;
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
    public void focusChanged(boolean focused) {
        if (focused) {
            this.focusedTime = Util.getMillis();
        }
    }

    @Override
    public boolean isFocused() {
        return focusState.isFocused();
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
        editSession.changed();
        scrollToCursor();
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

    // 按点击位置定位光标
    private void seekCursor(double mouseX, double mouseY) {
        textField.seekCursorToPoint(mouseX - (bounds.x() + PADDING_X), mouseY - (bounds.y() + PADDING_Y) + scroll.offset());
        scrollToCursor();
    }

    // 让光标所在行保持在可视区内
    private void scrollToCursor() {
        clampScroll();
        int line = Math.max(0, textField.getLineAtCursor()) * font.lineHeight;
        scroll.ensureVisible(new UiRect(0, line, innerWidth(), font.lineHeight));
    }

    // 文本模型宽度为 final，宽度变化时按当前文本与光标重建
    private void ensureTextWidth() {
        int width = innerWidth();
        if (width == textWidth) {
            return;
        }
        UiTextHistory.Snapshot snapshot = editSession.snapshot();
        this.textWidth = width;
        this.textField = createField(width);
        editSession.restore(snapshot);
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
        scroll.setViewport(bounds.x() + PADDING_X, bounds.y() + PADDING_Y,
                Math.max(0, bounds.width() - PADDING_X - 1), innerHeight());
        scroll.setContentHeight(textField.getLineCount() * font.lineHeight);
        scroll.setScrollbarVisible(scroll.maxOffset() > 0);
    }

    private int maxScroll() {
        return scroll.maxOffset();
    }
    /** 文本选择器与可见输入框共用指针归属。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (visible) {
            UiPointerHit scrollHit = scroll.hitTest(x, y);
            if (scrollHit != null) return scrollHit.within(this);
        }
        UiPointerHit hit = UiWidget.super.hitTest(x, y);
        return hit == null ? null : editSession.includeSelectionOwner(hit);
    }
}
