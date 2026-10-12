package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/**
 * 表单输入分发：把指针 / 键盘 / 滚轮事件按「选择框 → 行尾按钮 → 字段控件 → 表单滚动」的顺序派发。
 *
 * <p>按下归属（pressedWidget / pressedControl）由本类记录，释放与拖动只发给同一目标；
 * 事件只消费一次。几何与滚动状态仍由 {@link FormView} 持有。</p>
 */
final class FormInputRouter {

    // 滚动步长
    private static final int SCROLL_STEP = 24;

    private final FormView view;

    FormInputRouter(FormView view) {
        this.view = view;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!view.visible) {
            return false;
        }
        if (view.picker != null) {
            return view.picker.mouseClicked(mouseX, mouseY, button);
        }
        if (!view.enabled || !view.bounds().contains(mouseX, mouseY)) {
            return false;
        }
        view.syncBounds();
        for (FormRow row : view.rows) {
            if (!row.visible) continue;
            for (var pickerButton : row.pickers) {
                if (pickerButton.mouseClicked(mouseX, mouseY, button)) {
                    return rememberPress(pickerButton);
                }
            }
            for (com.meteorite.itemdespawntowhat.client.ui.widget.UiButton action : row.actions) {
                if (action.mouseClicked(mouseX, mouseY, button)) {
                    return rememberPress(action);
                }
            }
            if (row.control.mouseClicked(mouseX, mouseY, button)) {
                return rememberPress(row.control);
            }
        }
        return false;
    }

    // 记录本次按下的归属：只允许这个控件接收随后的释放与拖动
    private boolean rememberPress(com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget widget) {
        view.pressedWidget = widget;
        view.pressedControl = null;
        return true;
    }

    private boolean rememberPress(FormControl control) {
        view.pressedWidget = null;
        view.pressedControl = control;
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (view.picker != null) {
            return view.picker.mouseReleased(mouseX, mouseY, button);
        }
        com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget widget = view.pressedWidget;
        FormControl control = view.pressedControl;
        view.pressedWidget = null;
        view.pressedControl = null;
        if (widget != null) {
            return widget.mouseReleased(mouseX, mouseY, button);
        }
        return control != null && control.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (view.picker != null) {
            return view.picker.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (view.pressedWidget != null) {
            return view.pressedWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return view.pressedControl != null && view.pressedControl.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (view.picker != null) {
            return view.picker.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (!view.visible || !view.bounds().contains(mouseX, mouseY)) {
            return false;
        }
        for (FormRow row : view.rows) {
            if (row.visible && row.control.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        if (view.maxScroll() <= 0) {
            return false;
        }
        int delta = (int) Math.round(scrollY * SCROLL_STEP);
        view.scrollOffset = Math.clamp(view.scrollOffset - delta, 0, view.maxScroll());
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (view.picker != null) {
            return view.picker.keyPressed(keyCode, scanCode, modifiers);
        }
        // 顺序：聚焦控件优先；方向键与 PageUp/PageDown 都不允许同时改值和滚动
        for (FormRow row : view.rows) {
            if (row.visible && handlesInput(row.control) && row.control.keyPressed(keyCode, scanCode, modifiers)) {
                view.retainPendingInputs();
                if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                        && row.field.type() != EditorFieldType.LONG_TEXT
                        && row.control.issues(row.path).stream().noneMatch(FormIssue::blocking)) {
                    view.writeField(row, row.control.store());
                    view.acceptInput(row);
                }
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            view.scrollOffset = Math.max(0, view.scrollOffset - view.viewportHeight);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            view.scrollOffset = Math.min(view.maxScroll(), view.scrollOffset + view.viewportHeight);
            return true;
        }
        return false;
    }

    // 按键抬起：交给聚焦控件收尾（方向键重复在抬起时合并为一次提交）
    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (FormRow row : view.rows) {
            if (row.visible && handlesInput(row.control) && row.control.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (view.picker != null) {
            return view.picker.charTyped(codePoint, modifiers);
        }
        for (FormRow row : view.rows) {
            if (row.visible && handlesInput(row.control) && row.control.charTyped(codePoint, modifiers)) {
                view.retainPendingInputs();
                return true;
            }
        }
        return false;
    }

    // 该控件当前是否持有焦点（键盘输入只发给持有焦点的控件）
    private boolean handlesInput(FormControl control) {
        List<UiFocusTarget> targets = new ArrayList<>();
        control.addFocusTargets(targets);
        for (UiFocusTarget target : targets) {
            if (target.isFocused()) {
                return true;
            }
        }
        return false;
    }
}
