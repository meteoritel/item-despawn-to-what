package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

/**
 * 表单输入分发：把指针 / 键盘 / 滚轮事件按「选择框 → 行尾按钮 → 字段控件 → 表单滚动」的顺序派发。
 *
 * <p>持续交互由作用域路由到实际所有者；表单滚动由 UiScrollView 持有，字段输入只派发一次。</p>
 */
final class FormInputRouter {

    // 滚动步长
    private static final int SCROLL_STEP = 24;

    private final FormView view;

    FormInputRouter(FormView view) {
        this.view = view;
    }

    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!view.visible) {
            return false;
        }
        if (!view.enabled || !view.bounds().contains(mouseX, mouseY)) {
            return false;
        }
        view.syncBounds();
        if (context.hits(view.scroll) && view.scroll.mousePressed(context)) return true;
        for (FormRow row : view.rows) {
            if (!row.visible) continue;
            for (var pickerButton : row.pickers) {
                if (context.hits(pickerButton) && pickerButton.mousePressed(context)) {
                    return true;
                }
            }
            for (UiButton action : row.actions) {
                if (context.hits(action) && action.mousePressed(context)) {
                    return true;
                }
            }
            if (context.hits(row.control) && row.control.mousePressed(context)) {
                return true;
            }
        }
        return false;
    }


    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!view.visible || !view.bounds().contains(mouseX, mouseY)) {
            return false;
        }
        for (FormRow row : view.rows) {
            if (row.visible && row.control.mouseScrolled(context, scrollX, scrollY)) {
                return true;
            }
        }
        if (view.maxScroll() <= 0) {
            return false;
        }
        return view.scroll.scrollByPixels(scrollY * SCROLL_STEP);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 顺序：聚焦控件优先；方向键与 PageUp/PageDown 都不允许同时改值和滚动
        for (FormRow row : view.rows) {
            if (row.visible && handlesInput(row.control) && row.control.keyPressed(keyCode, scanCode, modifiers)) {
                view.retainPendingInputs();
                if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                        && !row.field.isLongText()
                        && row.control.issues(row.path).stream().noneMatch(FormIssue::blocking)) {
                    view.writeField(row, row.control.store());
                    view.acceptInput(row);
                }
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            view.scroll.setOffset(view.scroll.offset() - view.viewportHeight);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            view.scroll.setOffset(view.scroll.offset() + view.viewportHeight);
            return true;
        }
        return false;
    }

    // 按键抬起：交给聚焦控件收尾（方向键重复在抬起时合并为一次提交）
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (FormRow row : view.rows) {
            if (row.visible && handlesInput(row.control) && row.control.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
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
        control.addFocusTargets(targets::add);
        for (UiFocusTarget target : targets) {
            if (target.isFocused()) {
                return true;
            }
        }
        return false;
    }
}
