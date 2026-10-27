package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import java.util.function.Supplier;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** 屏幕独占输入作用域；控件内部捕获与焦点派发仍由各页和 kit 负责。 */
final class EditorInputRouter {
    private enum Scope { SAVING, MODAL, OVERLAY, PAGE }
    private final EditorShell shell;
    private final Supplier<RuleSaveOverlay> savingOverlay;
    private final Supplier<UiRect> contentArea;
    private @Nullable Object activeScope;
    private @Nullable Object pressedScope;
    private int pressedButton = -1;
    private EditorPage page;
    private @Nullable UiTypePicker picker;
    private Object scopeIdentity;

    EditorInputRouter(EditorShell shell, Supplier<RuleSaveOverlay> savingOverlay, Supplier<UiRect> contentArea) {
        this.shell = shell;
        this.savingOverlay = savingOverlay;
        this.contentArea = contentArea;
        this.page = shell.activePage();
        this.scopeIdentity = page;
    }

    private Scope select() {
        page = shell.activePage();
        picker = page.overlay();
        Scope scope;
        if (shell.saving()) {
            scope = Scope.SAVING;
            scopeIdentity = savingOverlay.get();
        } else if (shell.modals().isVisible()) {
            scope = Scope.MODAL;
            scopeIdentity = shell.modals().top();
        } else if (picker != null) {
            scope = Scope.OVERLAY;
            scopeIdentity = picker;
        } else {
            scope = Scope.PAGE;
            scopeIdentity = page;
        }
        if (activeScope != scopeIdentity) {
            if (activeScope != null) shell.leaveInteractions();
            activeScope = scopeIdentity;
            pressedScope = null;
            pressedButton = -1;
        }
        return scope;
    }

    private UiTypePicker overlay() {
        return Objects.requireNonNull(picker, "OVERLAY scope requires a type picker");
    }

    boolean mouseClicked(double x, double y, int button) {
        Scope scope = select();
        pressedScope = scopeIdentity;
        pressedButton = button;
        return switch (scope) {
            case SAVING -> { savingOverlay.get().mouseClicked(x, y, button); yield true; }
            case MODAL -> { shell.modals().mouseClicked(x, y, button); yield true; }
            case OVERLAY -> { overlay().mouseClicked(x, y, button); yield true; }
            case PAGE -> {
                if (button == 0) focusClicked(x, y);
                yield page.mouseClicked(x, y, button);
            }
        };
    }

    boolean mouseReleased(double x, double y, int button) {
        Scope scope = select();
        if (pressedScope != scopeIdentity || pressedButton != button) return true;
        pressedScope = null;
        pressedButton = -1;
        return switch (scope) {
            case SAVING -> { savingOverlay.get().mouseReleased(x, y, button); yield true; }
            case MODAL -> { shell.modals().mouseReleased(x, y, button); yield true; }
            case OVERLAY -> { overlay().mouseReleased(x, y, button); yield true; }
            case PAGE -> page.mouseReleased(x, y, button);
        };
    }

    boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        Scope scope = select();
        if (pressedScope != scopeIdentity || pressedButton != button) return true;
        return switch (scope) {
            case SAVING -> true;
            case MODAL -> { shell.modals().mouseDragged(x, y, button, dx, dy); yield true; }
            case OVERLAY -> { overlay().mouseDragged(x, y, button, dx, dy); yield true; }
            case PAGE -> page.mouseDragged(x, y, button, dx, dy);
        };
    }

    boolean mouseScrolled(double x, double y, double sx, double sy) {
        return switch (select()) {
            case SAVING -> true;
            case MODAL -> { shell.modals().mouseScrolled(x, y, sx, sy); yield true; }
            case OVERLAY -> { overlay().mouseScrolled(x, y, sx, sy); yield true; }
            case PAGE -> page.mouseScrolled(x, y, sx, sy);
        };
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_F8 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && shell.canEnterDebug()) {
            shell.enterDebug();
            return false;
        }
        // 原版功能键需要保持放行，覆盖保存遮罩与所有弹层。
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return false;
        return switch (select()) {
            case SAVING -> { savingOverlay.get().keyPressed(key, scan, modifiers); yield true; }
            case MODAL -> { shell.modals().keyPressed(key, scan, modifiers); yield true; }
            case OVERLAY -> { overlay().keyPressed(key, scan, modifiers); yield true; }
            case PAGE -> pageKeyPressed(key, scan, modifiers);
        };
    }

    private boolean pageKeyPressed(int key, int scan, int modifiers) {
        if (shell.dispatchHistory(key, scan, modifiers, (k, s, m) ->
                page.keyPressed(k, s, m) || shell.focus().keyPressed(k, s, m))) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (shell.focus().keyPressed(key, scan, modifiers) || page.keyPressed(key, scan, modifiers)) return true;
            shell.escape();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && shell.focus().keyPressed(key, scan, modifiers)) return true;
        return page.keyPressed(key, scan, modifiers) || shell.focus().keyPressed(key, scan, modifiers);
    }

    boolean keyReleased(int key, int scan, int modifiers) {
        return switch (select()) {
            case SAVING -> true;
            case MODAL -> { shell.modals().keyReleased(key, scan, modifiers); yield true; }
            case OVERLAY -> overlay().keyReleased(key, scan, modifiers);
            case PAGE -> page.keyReleased(key, scan, modifiers);
        };
    }

    boolean charTyped(char value, int modifiers) {
        return switch (select()) {
            case SAVING -> true;
            case MODAL -> { shell.modals().charTyped(value, modifiers); yield true; }
            case OVERLAY -> { overlay().charTyped(value, modifiers); yield true; }
            case PAGE -> page.charTyped(value, modifiers);
        };
    }

    // 鼠标和键盘共用唯一焦点，裁剪区域外的控件不能夺取输入。
    private void focusClicked(double mouseX, double mouseY) {
        for (UiFocusTarget target : shell.focus().targets()) {
            if (!target.canFocus() || !target.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            if (target instanceof UiListView<?> list && !list.acceptsFocusAt(mouseX, mouseY)) {
                continue;
            }
            if (shell.activePage().ownsFocusTarget(target) && !contentArea.get().contains(mouseX, mouseY)) {
                continue;
            }
            shell.focus().focusOn(target);
            return;
        }
        shell.focus().clearFocus();
    }

}
