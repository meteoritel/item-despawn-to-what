package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * 宿主只选择保存、模态、类型选择器或页面，通用派发由 kit 持有。
 */
final class EditorInputRouter {
    private final EditorShell shell;
    private final Supplier<RuleSaveOverlay> savingOverlay;
    private final UiInputRouter router = new UiInputRouter();

    EditorInputRouter(EditorShell shell, Supplier<RuleSaveOverlay> savingOverlay) {
        this.shell = shell;
        this.savingOverlay = savingOverlay;
        shell.modals().setOnScopeChanged(this::synchronize);
        shell.setInputChanged(this::synchronize);
        shell.setCloseInput(router::close);
    }

    void synchronize() {
        UiInputScope next;
        if (shell.saving()) {
            RuleSaveOverlay current = savingOverlay.get();
            next = current.inputScope();
        } else if (shell.modals().isVisible()) {
            next = Objects.requireNonNull(shell.modals().top()).inputScope();
        } else {
            UiTypePicker current = shell.activePage().overlay();
            if (current != null) {
                next = current.inputScope();
            } else next = shell.activePage().inputScope();
        }
        next.refreshFocus();
        router.select(next);
    }

    boolean pageActive() {
        synchronize();
        return router.active() == shell.activePage().inputScope();
    }

    private UiInputContext pointer(double x, double y, int button) {
        synchronize();
        return UiInputContext.of(router.active(), x, y, button, Screen.hasShiftDown(), Screen.hasControlDown(), Screen.hasAltDown());
    }

    private boolean finish(boolean consumed, UiInputScope scope) {
        synchronize();
        return consumed || scope != shell.activePage().inputScope();
    }

    boolean mouseClicked(double x, double y, int button) {
        UiInputContext context = pointer(x, y, button);
        return finish(router.mousePressed(context), context.scope());
    }

    boolean mouseReleased(double x, double y, int button) {
        UiInputContext context = pointer(x, y, button);
        return finish(router.mouseReleased(context), context.scope());
    }

    boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        UiInputContext context = pointer(x, y, button);
        return finish(router.mouseDragged(context, dx, dy), context.scope());
    }

    boolean mouseScrolled(double x, double y, double sx, double sy) {
        UiInputContext context = pointer(x, y, -1);
        return finish(router.mouseScrolled(context, sx, sy), context.scope());
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return false;
        synchronize();
        UiInputScope scope = router.active();
        return finish(router.keyPressed(key, scan, modifiers), scope);
    }

    boolean keyReleased(int key, int scan, int modifiers) {
        synchronize();
        UiInputScope scope = router.active();
        return finish(router.keyReleased(key, scan, modifiers), scope);
    }

    boolean charTyped(char value, int modifiers) {
        synchronize();
        UiInputScope scope = router.active();
        return finish(router.charTyped(value, modifiers), scope);
    }

    void close() {
        router.close();
    }
}
