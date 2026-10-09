package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 列表编辑器：上方是条目列表，下方是一行内嵌文本输入。
 * <p>Enter 把输入内容追加为新条目（或覆盖当前选中项），Delete 删除选中项，Alt+上下移动顺序。
 * <p>条目唯一，重复输入会被拒绝；ID / TAG 模式下只允许资源 ID 字符集。
 */
public final class UiListEditor implements UiWidget, UiFocusTarget {

    /*** 条目文本模式 */
    public enum Mode {
        // 资源 ID（命名空间:路径）
        ID,
        // 资源 ID 或 #标签
        TAG,
        // 自由文本
        TEXT
    }

    // 单行高度
    public static final int ROW_HEIGHT = 12;
    // 内嵌输入行高度
    public static final int INPUT_HEIGHT = 12;
    // 列表区最多显示的行数
    public static final int VISIBLE_ROWS = 4;
    // 单条最大长度
    public static final int MAX_ENTRY_LENGTH = 128;

    private final Mode mode;
    private final @Nullable String registryHint;
    private final UiTextInput input;
    private List<String> items = new ArrayList<>();
    private int selected = -1;
    // 只有显式进入编辑才覆盖；普通输入始终追加。
    private int editingIndex = -1;
    private int offset;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private @Nullable Consumer<List<String>> onChanged;

    public UiListEditor(Font font, Mode mode, @Nullable String registryHint) {
        Objects.requireNonNull(font, "font");
        this.mode = mode == null ? Mode.ID : mode;
        this.registryHint = registryHint;
        this.input = new UiTextInput(font, Component.translatable("gui.itemdespawntowhat.edit.list.input_hint"));
        this.input.setMaxLength(MAX_ENTRY_LENGTH);
        this.input.setFilter(this::acceptText);
        this.input.setOnCommit(text -> commitInput());
    }

    // 建议高度：rows 行列表 + 输入行 + 边框
    public static int preferredHeight(int rows) {
        int visible = Math.clamp(rows, 1, VISIBLE_ROWS);
        return visible * ROW_HEIGHT + INPUT_HEIGHT + 2;
    }

    public Mode mode() {
        return mode;
    }

    public @Nullable String registryHint() {
        return registryHint;
    }

    // 内嵌输入框（表单引擎把它一并注册进焦点顺序）
    public UiTextInput input() {
        return input;
    }

    public List<String> items() {
        return List.copyOf(items);
    }

    // 直接设置条目（不回调）
    public void setItems(List<String> next) {
        List<String> copy = new ArrayList<>();
        if (next != null) {
            for (String value : next) {
                if (value != null && !value.isBlank()) {
                    copy.add(value);
                }
            }
        }
        this.items = copy;
        this.editingIndex = -1;
        this.input.clear();
        this.selected = copy.isEmpty() ? -1 : Math.clamp(selected, 0, copy.size() - 1);
        this.offset = 0;
    }

    public int editingIndex() { return editingIndex; }

    // 装载会话输入时保持追加/覆盖语义，不提交列表。
    public void restorePendingInput(String text, int index) {
        input.setValue(text);
        editingIndex = index >= 0 && index < items.size() ? index : -1;
    }

    public int size() {
        return items.size();
    }

    public int selectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int index) {
        this.selected = index >= 0 && index < items.size() ? index : -1;
    }

    public @Nullable String selectedItem() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public void setOnChanged(@Nullable Consumer<List<String>> listener) {
        this.onChanged = listener;
    }

    public void setEnabled(boolean next) {
        this.enabled = next;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVisible(boolean next) {
        this.visible = next;
        this.input.setVisible(next);
    }

    // 追加条目
    public boolean addItem(String value) {
        if (value == null || value.isBlank() || items.size() >= 512) {
            return false;
        }
        List<String> next = new ArrayList<>(items);
        next.add(value);
        selected = next.size() - 1;
        applyItems(next);
        return true;
    }

    // 删除当前选中条目
    public boolean removeSelected() {
        editingIndex = -1;
        if (selected < 0 || selected >= items.size()) {
            return false;
        }
        List<String> next = new ArrayList<>(items);
        next.remove(selected);
        selected = next.isEmpty() ? -1 : Math.min(selected, next.size() - 1);
        applyItems(next);
        return true;
    }

    // 移动当前选中条目（delta = -1 上移 / +1 下移）
    public boolean moveSelected(int delta) {
        if (selected < 0 || selected >= items.size()) {
            return false;
        }
        int target = selected + delta;
        if (target < 0 || target >= items.size()) {
            return false;
        }
        List<String> next = new ArrayList<>(items);
        String value = next.remove(selected);
        next.add(target, value);
        selected = target;
        if (editingIndex >= 0) editingIndex = target;
        applyItems(next);
        return true;
    }

    // 页面切换前使用同一提交入口保留正在输入的条目。
    public void commitPendingInput() {
        if (enabled) {
            commitInput();
        }
    }

    // 输入内容提交：追加或覆盖选中项
    private void commitInput() {
        String text = input.value().trim();
        String id = mode == Mode.TAG && text.startsWith("#") ? text.substring(1) : text;
        if (mode != Mode.TEXT && net.minecraft.resources.ResourceLocation.tryParse(id) == null) return;
        if (text.isEmpty()) {
            return;
        }
        List<String> next = new ArrayList<>(items);
        if (editingIndex >= 0 && editingIndex < next.size()) {
            if (text.equals(next.get(editingIndex))) {
                input.clear();
                editingIndex = -1;
                return;
            }
            if (next.contains(text) && !text.equals(next.get(editingIndex))) return;
            next.set(editingIndex, text);
        } else {
            if (next.contains(text)) {
                return;
            }
            next.add(text);
            selected = next.size() - 1;
        }
        input.clear();
        editingIndex = -1;
        applyItems(next);
    }

    // 条目变更的统一落点
    private void applyItems(List<String> next) {
        this.items = new ArrayList<>(next);
        if (selected >= items.size()) {
            selected = items.isEmpty() ? -1 : items.size() - 1;
        }
        if (onChanged != null) {
            onChanged.accept(List.copyOf(items));
        }
    }

    // 字符过滤：ID / TAG 模式只允许资源 ID 字符集
    private boolean acceptText(String text) {
        if (text == null || text.length() > MAX_ENTRY_LENGTH) {
            return false;
        }
        if (mode == Mode.TEXT) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == ':' || c == '/'
                    || (mode == Mode.TAG && c == '#');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int inputHeight = Math.min(INPUT_HEIGHT, bounds.height());
        this.input.setBounds(bounds.x() + 1, Math.max(bounds.y(), bounds.bottom() - inputHeight - 1),
                Math.max(0, bounds.width() - 2), Math.max(0, inputHeight));
    }

    // 列表区矩形（去掉内嵌输入行）
    private UiRect listArea() {
        int height = Math.max(0, bounds.height() - Math.min(INPUT_HEIGHT, bounds.height()) - 2);
        return new UiRect(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.width() - 2), height);
    }

    private int visibleRowCount() {
        return Math.max(1, listArea().height() / ROW_HEIGHT);
    }

    // 让选中项保持在可见范围内
    private void clampOffset(int visibleRows) {
        int maxOffset = Math.max(0, items.size() - visibleRows);
        if (selected >= 0) {
            if (selected < offset) {
                offset = selected;
            } else if (selected >= offset + visibleRows) {
                offset = selected - visibleRows + 1;
            }
        }
        offset = Math.clamp(offset, 0, maxOffset);
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        UiTheme.drawInset(graphics, bounds);
        UiRect area = listArea();
        int visibleRows = visibleRowCount();
        if (items.isEmpty()) {
            String empty = Component.translatable("gui.itemdespawntowhat.edit.list.empty").getString();
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, empty, Math.max(0, area.width() - 2)),
                    area.x() + 1, area.y() + 1, UiPalette.TEXT_DISABLED, false);
        } else {
            clampOffset(visibleRows);
            for (int row = 0; row < visibleRows; row++) {
                int index = offset + row;
                if (index >= items.size()) {
                    break;
                }
                UiRect rowRect = new UiRect(area.x(), area.y() + row * ROW_HEIGHT, area.width(), ROW_HEIGHT);
                boolean isSelected = index == selected;
                boolean hovered = enabled && rowRect.contains(mouseX, mouseY);
                if (isSelected) {
                    UiTheme.drawSelection(graphics, rowRect);
                    UiTheme.drawSelectMarker(graphics, rowRect);
                } else if (hovered) {
                    graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), UiPalette.CONTROL_HOVER);
                }
                int textX = rowRect.x() + UiTheme.SELECT_MARKER_WIDTH;
                String text = TextScroll.trimToWidth(renderFont, items.get(index),
                        Math.max(0, rowRect.right() - textX - 2));
                graphics.drawString(renderFont, text, textX, rowRect.y() + 2,
                        enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
            }
        }
        if (enabled) {
            input.render(graphics, renderFont, mouseX, mouseY);
        }
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) {
            return false;
        }
        if (input.bounds().contains(mouseX, mouseY)) {
            if (input.value().isBlank()) editingIndex = -1;
            input.setFocused(true);
            input.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        UiRect area = listArea();
        if (!area.contains(mouseX, mouseY)) {
            return false;
        }
        int index = offset + (int) ((mouseY - area.y()) / ROW_HEIGHT);
        if (index >= 0 && index < items.size()) {
            selected = index;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return input.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return input.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (input.isFocused()) {
            if (input.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                if (alt) {
                    return moveSelected(-1);
                }
                return selectRelative(-1);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (alt) {
                    return moveSelected(1);
                }
                return selectRelative(1);
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                return selectRelative(-visibleRowCount());
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                return selectRelative(visibleRowCount());
            }
            case GLFW.GLFW_KEY_HOME -> {
                selected = items.isEmpty() ? -1 : 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (!items.isEmpty()) {
                    selected = items.size() - 1;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                return removeSelected();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String current = selectedItem();
                if (current != null) {
                    editingIndex = selected;
                    input.setValue(current);
                    input.setFocused(true);
                    input.selectAll();
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean selectRelative(int delta) {
        if (items.isEmpty()) {
            return false;
        }
        int from = Math.max(0, selected);
        selected = Math.max(0, Math.min(items.size() - 1, from + delta));
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        return input.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean next) {
        this.focused = next;
        if (!next) {
            input.setFocused(false);
        }
    }

    @Override
    public boolean isFocused() {
        return focused || input.isFocused();
    }

    @Override
    public boolean activate() {
        String current = selectedItem();
        if (current == null) {
            input.setFocused(true);
            return true;
        }
        input.setValue(current);
        input.setFocused(true);
        input.selectAll();
        return true;
    }

    @Override
    public Component accessibleName() {
        return Component.translatable("gui.itemdespawntowhat.edit.list.accessible_name");
    }
}
