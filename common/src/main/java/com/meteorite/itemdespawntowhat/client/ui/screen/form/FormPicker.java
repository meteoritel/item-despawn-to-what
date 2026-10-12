package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 候选值选择框：过滤输入框 + 可选列表，由 {@link FormView} 在目录/建议入口打开。
 *
 * <p>视图状态（可见行数、偏移、选中项）由本类自持；几何所需的视口来自 {@link Host}，
 * 关闭动作也回调宿主，因此本类不持有表单的行与草稿状态。</p>
 */
final class FormPicker {

    /** 选择框覆盖绘制所需的宿主视口与关闭入口。 */
    interface Host {
        int viewportX();

        int viewportY();

        int viewportWidth();

        int viewportHeight();

        void closePicker();
    }

    // 选择框最多显示的行数
    private static final int PICKER_ROWS = 8;
    // 选择框头部/底部/输入框高度
    private static final int PICKER_HEADER = 14;
    private static final int PICKER_FOOTER = 12;
    private static final int PICKER_INPUT = 12;
    // 列表行的最小高度与滚动条宽度无关，行高取主题行高
    private static final int MIN_ROW_HEIGHT = 12;

    private final UiHostTheme theme;
    private final Host host;
    private final List<Suggestion> all;
    private final List<Suggestion> filtered = new ArrayList<>();
    private final UiTextInput filterInput;
    private final Consumer<String> onPicked;
    private final List<String> chosen;
    private int selectedIndex;
    private int offset;
    private UiRect rect = new UiRect(0, 0, 0, 0);
    private UiRect listRect = new UiRect(0, 0, 0, 0);

    FormPicker(Font font, UiHostTheme theme, List<Suggestion> suggestions, List<String> chosen,
               Consumer<String> onPicked, Host host) {
        this.theme = theme;
        this.host = host;
        this.all = suggestions;
        this.onPicked = onPicked;
        this.chosen = List.copyOf(chosen);
        this.filterInput = new UiTextInput(theme, font,
                Component.translatable("gui.itemdespawntowhat.edit.picker.filter"));
        this.filterInput.setOnCommit(text -> confirm());
        applyFilter("");
    }

    private int rowHeight() {
        return Math.max(MIN_ROW_HEIGHT, theme.rowHeight());
    }

    private void applyFilter(@Nullable String text) {
        filtered.clear();
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (Suggestion suggestion : all) {
            if (lower.isEmpty() || suggestion.value().toLowerCase(Locale.ROOT).contains(lower)) {
                filtered.add(suggestion);
            }
        }
        this.selectedIndex = 0;
        this.offset = 0;
        for (int i = 0; i < filtered.size(); i++) {
            if (chosen.contains(filtered.get(i).value())) {
                selectedIndex = i;
                offset = Math.clamp(i, 0, Math.max(0, filtered.size() - PICKER_ROWS));
                break;
            }
        }
    }

    private void confirm() {
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            onPicked.accept(filtered.get(selectedIndex).value());
        }
        host.closePicker();
    }

    private int width() {
        return Math.clamp(host.viewportWidth() - 8, 120, 240);
    }

    private int height() {
        int rows = Math.clamp(filtered.size(), 1, PICKER_ROWS);
        return PICKER_HEADER + PICKER_INPUT + rows * rowHeight() + PICKER_FOOTER;
    }

    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        int viewportX = host.viewportX();
        int viewportY = host.viewportY();
        int viewportWidth = host.viewportWidth();
        int viewportHeight = host.viewportHeight();
        int w = width();
        int h = height();
        int x = viewportX + Math.max(0, (viewportWidth - w) / 2);
        int y = viewportY + Math.max(0, (viewportHeight - h) / 2);
        this.rect = new UiRect(x, y, w, h);
        this.listRect = new UiRect(x + 1, y + PICKER_HEADER + PICKER_INPUT, w - 2,
                Math.max(rowHeight(), h - PICKER_HEADER - PICKER_INPUT - PICKER_FOOTER));
        graphics.fill(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight,
                theme.palette().modalDim());
        theme.drawWindow(graphics, rect);
        theme.drawHeader(graphics, new UiRect(rect.x(), rect.y(), rect.width(), PICKER_HEADER));
        String title = TextScroll.trimToWidth(renderFont,
                Component.translatable("gui.itemdespawntowhat.edit.picker.title").getString(),
                Math.max(4, rect.width() - 4));
        graphics.drawString(renderFont, title, rect.x() + 2, rect.y() + 4, theme.palette().headerText(), false);
        filterInput.setBounds(x + 2, y + PICKER_HEADER, w - 4, PICKER_INPUT - 2);
        filterInput.render(graphics, renderFont, mouseX, mouseY);
        int visibleRows = Math.max(1, listRect.height() / rowHeight());
        for (int i = 0; i < visibleRows; i++) {
            int index = offset + i;
            if (index >= filtered.size()) {
                break;
            }
            int rowY = listRect.y() + i * rowHeight();
            UiRect rowRect = new UiRect(listRect.x(), rowY, listRect.width(), rowHeight());
            if (index == selectedIndex || chosen.contains(filtered.get(index).value())) {
                theme.drawSelection(graphics, rowRect);
            } else if (rowRect.contains(mouseX, mouseY)) {
                graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), theme.palette().controlHover());
            }
            Component label = filtered.get(index).label();
            String text = TextScroll.trimToWidth(renderFont, label.getString(), rowRect.width() - 2);
            graphics.drawString(renderFont, text, rowRect.x() + 1, rowRect.y() + 2, theme.palette().textPrimary(), false);
        }
        if (filtered.isEmpty()) {
            String text = TextScroll.trimToWidth(renderFont,
                    Component.translatable("gui.itemdespawntowhat.edit.picker.empty").getString(),
                    Math.max(4, listRect.width() - 2));
            graphics.drawString(renderFont, text, listRect.x() + 1, listRect.y() + 2,
                    theme.palette().textDisabled(), false);
        }
        String footer = TextScroll.trimToWidth(renderFont,
                Component.translatable("gui.itemdespawntowhat.edit.picker.hint").getString(),
                Math.max(4, w - 4));
        graphics.drawString(renderFont, footer, x + 2, rect.bottom() - PICKER_FOOTER + 2,
                theme.palette().textSecondary(), false);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (filterInput.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!listRect.contains(mouseX, mouseY)) {
            if (!rect.contains(mouseX, mouseY)) {
                host.closePicker();
            }
            return true;
        }
        int index = offset + (int) ((mouseY - listRect.y()) / rowHeight());
        if (index >= 0 && index < filtered.size()) {
            this.selectedIndex = index;
            confirm();
        }
        return true;
    }

    void endInteraction(UiInputCapture.EndReason reason) {
        filterInput.endInteraction(reason);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return filterInput.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return filterInput.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = Math.max(1, listRect.height() / rowHeight());
        int maxOffset = Math.max(0, filtered.size() - visibleRows);
        this.offset = Math.max(0, Math.min(offset - (int) Math.signum(scrollY), maxOffset));
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            host.closePicker();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.selectedIndex = Math.max(0, selectedIndex - 1);
            ensureVisible();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.selectedIndex = Math.min(Math.max(0, filtered.size() - 1), selectedIndex + 1);
            ensureVisible();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            this.selectedIndex = 0;
            ensureVisible();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            this.selectedIndex = Math.max(0, filtered.size() - 1);
            ensureVisible();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (filterInput.keyPressed(keyCode, scanCode, modifiers)) {
            applyFilter(filterInput.value());
            return true;
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (filterInput.charTyped(codePoint, modifiers)) {
            applyFilter(filterInput.value());
            return true;
        }
        return false;
    }

    private void ensureVisible() {
        int visibleRows = Math.max(1, listRect.height() / rowHeight());
        if (selectedIndex < offset) {
            this.offset = selectedIndex;
        } else if (selectedIndex >= offset + visibleRows) {
            this.offset = selectedIndex - visibleRows + 1;
        }
    }
}
