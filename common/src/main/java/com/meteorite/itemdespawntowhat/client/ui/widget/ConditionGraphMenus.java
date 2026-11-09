package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.Hotspot;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphRenderer.drawBorder;

/**
 * 画布局部菜单：拥有类型插入目标、确认状态与屏幕几何，结构动作由画布执行。
 */
final class ConditionGraphMenus {
    // ---- 分组方式小菜单 ----

    // 菜单与锚点之间的屏幕像素间距
    private static final int KIND_MENU_GAP = 4;
    private static final int KIND_MENU_ROW_HEIGHT = 18;
    // 菜单行文本左右内边距
    private static final int KIND_MENU_PADDING = 4;
    // 菜单最小宽度
    private static final int KIND_MENU_MIN_WIDTH = 56;

    // 分组方式：∧ 全部满足 / ∨ 任一满足
    enum GroupKind {
        ALL(TypeLabels.UI_PREFIX + "tree.operator.all", TypeLabels.UI_PREFIX + "tree.node.all"),
        ANY(TypeLabels.UI_PREFIX + "tree.operator.any", TypeLabels.UI_PREFIX + "tree.node.any");

        final String operatorKey;
        final String labelKey;

        GroupKind(String operatorKey, String labelKey) {
            this.operatorKey = operatorKey;
            this.labelKey = labelKey;
        }

        // 菜单行同时给出运算符与它展开后的说法，只看到单字「且 / 或」时也能判断含义
        Component menuLabel() {
            return Component.translatable(operatorKey).append(" ").append(Component.translatable(labelKey));
        }
    }

    // 弹层确认后的动作：分组方式与删除范围共用同一套弹出菜单
    enum KindMenuAction {INSERT_ALL, INSERT_ANY, WRAP_ALL, WRAP_ANY, DELETE_KEEP_CHILDREN, DELETE_SUBTREE}

    // 弹层的一行：显示文本与确认动作
    record KindMenuEntry(Component label, KindMenuAction action) {
    }

    // 弹层用途：决定锚点位置与工具栏选中态
    enum KindMenuPurpose {INSERT_GROUP, WRAP_SELECTION, DELETE_CONFIRM}

    final UiTypePicker picker;
    boolean pickerAppend;
    @Nullable UiTreePath pickerParent;
    int pickerIndex;
    boolean kindMenuOpen;
    KindMenuPurpose kindMenuPurpose = KindMenuPurpose.INSERT_GROUP;
    private List<KindMenuEntry> kindMenuEntries = List.of();
    private @Nullable UiRect kindMenuAnchor;
    private int kindMenuIndex;
    // 指针位置：指针在列表内时高亮跟随鼠标，指针静止则不覆盖键盘选择
    private boolean kindMenuPointerKnown;
    private int kindMenuLastMouseX;
    private int kindMenuLastMouseY;
    private UiRect kindMenuRect = new UiRect(0, 0, 0, 0);
    private UiRect kindMenuListRect = new UiRect(0, 0, 0, 0);

    private final Font font;
    private final UiHostTheme theme;
    private final Runnable changed;
    private final Consumer<KindMenuAction> confirmed;

    ConditionGraphMenus(UiHostTheme theme, Font font, Consumer<ResourceLocation> typeConfirmed,
                        Consumer<KindMenuAction> confirmed, Runnable changed) {
        this.theme = theme;
        this.font = font;
        this.confirmed = confirmed;
        this.changed = changed;
        picker = new UiTypePicker(theme, font, Component.translatable(TypeLabels.UI_PREFIX + "picker.title"), typeConfirmed);
        picker.setOnOpenChanged(changed);
    }

    void open(KindMenuPurpose purpose, List<KindMenuEntry> entries, @Nullable UiRect anchor) {
        kindMenuPurpose = purpose;
        kindMenuEntries =List.copyOf(entries);
        kindMenuAnchor =anchor;
        kindMenuIndex =0;
        kindMenuPointerKnown =false;
        kindMenuOpen = true;
        changed.run();
    }

    void openPicker(Hotspot hotspot, List<UiTypePicker.Option> options, UiRect bounds) {
        close();
        pickerAppend = false;
        pickerParent = hotspot.parent();
        pickerIndex = hotspot.index();
        picker.open(options, bounds);
    }

    void closePicker() {
        picker.close();
        pickerParent = null;
    }

    void scroll(double amount) {
        if (!kindMenuEntries.isEmpty()) {
            kindMenuIndex =Math.clamp(kindMenuIndex + (amount > 0.0D ? -1 : 1), 0, kindMenuEntries.size() - 1);
        }
    }

    private void confirm() {
        if (!kindMenuOpen || kindMenuEntries.isEmpty()) return;
        KindMenuAction action = kindMenuEntries.get(Math.clamp(kindMenuIndex, 0, kindMenuEntries.size() - 1)).action();
        close();
        confirmed.accept(action);
    }

    void close() {
        boolean wasOpen = kindMenuOpen;
        kindMenuOpen = false;
        kindMenuAnchor =null;
        kindMenuEntries =List.of();
        kindMenuIndex =0;
        kindMenuPointerKnown =false;
        if (wasOpen) changed.run();
    }

    void prepare(UiRect bounds, @Nullable UiRect selectedAnchor) {
        if (!kindMenuOpen || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        UiRect anchor = kindMenuAnchor != null ? kindMenuAnchor : selectedAnchor;
        if (anchor == null) {
            close();
            kindMenuRect =new UiRect(0, 0, 0, 0);
            return;
        }
        if (kindMenuEntries.isEmpty()) {
            close();
            kindMenuRect =new UiRect(0, 0, 0, 0);
            return;
        }
        int rowCount = kindMenuEntries.size();
        int width = KIND_MENU_MIN_WIDTH;
        for (KindMenuEntry entry : kindMenuEntries) {
            width = Math.max(width, font.width(entry.label().getString()) + KIND_MENU_PADDING * 2 + BORDER * 2);
        }
        int maxWidth = Math.max(1, bounds.width() - KIND_MENU_PADDING);
        width = Math.min(width, maxWidth);
        int height = Math.min(rowCount * KIND_MENU_ROW_HEIGHT + BORDER * 2, bounds.height());
        int x = anchor.right() + KIND_MENU_GAP;
        if (x + width > bounds.right()) {
            x = anchor.x() - KIND_MENU_GAP - width;
        }
        x = Math.clamp(x, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
        int y = Math.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
        kindMenuRect =new UiRect(x, y, width, height);
        // 视口过矮时按可用高度裁掉列表区：行高不变，只显示画得下的行
        int availableListHeight = height - BORDER * 2;
        int listHeight = availableListHeight <= 0 ? 0
                : Math.min(rowCount * KIND_MENU_ROW_HEIGHT, availableListHeight);
        kindMenuListRect =new UiRect(x + BORDER, y + BORDER, Math.max(0, width - BORDER * 2), listHeight);
    }

    void render(GuiGraphics graphics, Font renderFont, UiPointer pointer) {
        if (pointer.allowed()) updateKindMenuHover((int) pointer.x(), (int) pointer.y());
        if (!kindMenuOpen || kindMenuRect.width() <= 0 || kindMenuRect.height() <= 0) {
            return;
        }
        graphics.fill(kindMenuRect.x(), kindMenuRect.y(), kindMenuRect.right(), kindMenuRect.bottom(),
                theme.palette().canvas().canvasPickerFill());
        drawBorder(graphics, kindMenuRect, theme.palette().canvas().canvasPickerBorder());
        for (int index = 0; index < kindMenuEntries.size(); index++) {
            int rowY = kindMenuRect.y() + BORDER + index * KIND_MENU_ROW_HEIGHT;
            boolean current = index == Math.clamp(kindMenuIndex, 0, kindMenuEntries.size() - 1);
            if (current) {
                graphics.fill(kindMenuRect.x() + BORDER, rowY, kindMenuRect.right() - BORDER,
                        rowY + KIND_MENU_ROW_HEIGHT, theme.palette().canvas().canvasPickerRowSelected());
            }
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                            kindMenuEntries.get(index).label().getString(),
                            Math.max(0, kindMenuRect.width() - KIND_MENU_PADDING * 2)),
                    kindMenuRect.x() + KIND_MENU_PADDING,
                    rowY + (KIND_MENU_ROW_HEIGHT - renderFont.lineHeight) / 2,
                    current ? theme.palette().canvas().canvasPickerText() : theme.palette().canvas().canvasPickerTextMuted(), false);
        }
    }

    void mousePressed(double mouseX, double mouseY) {
        int index = kindMenuIndexAt(mouseX, mouseY);
        if (index >= 0) {
            kindMenuIndex =index;
            confirm();
            return;
        }
        if (!kindMenuRect.contains(mouseX, mouseY)) {
            close();
        }
    }

    private int kindMenuIndexAt(double mouseX, double mouseY) {
        if (!kindMenuListRect.contains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - kindMenuListRect.y()) / Math.max(1, KIND_MENU_ROW_HEIGHT));
        return row >= 0 && row < kindMenuEntries.size() ? row : -1;
    }

    private void updateKindMenuHover(int mouseX, int mouseY) {
        boolean moved = !kindMenuPointerKnown || mouseX != kindMenuLastMouseX || mouseY != kindMenuLastMouseY;
        kindMenuPointerKnown =true;
        kindMenuLastMouseX =mouseX;
        kindMenuLastMouseY =mouseY;
        if (!moved) {
            return;
        }
        int index = kindMenuIndexAt(mouseX, mouseY);
        if (index >= 0) {
            kindMenuIndex =index;
        }
    }

    void keyPressed(int keyCode) {
        if (kindMenuEntries.isEmpty()) {
            close();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        } else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            kindMenuIndex =Math.max(0, kindMenuIndex - 1);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            kindMenuIndex =Math.min(kindMenuEntries.size() - 1, kindMenuIndex + 1);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
        }
    }
}
