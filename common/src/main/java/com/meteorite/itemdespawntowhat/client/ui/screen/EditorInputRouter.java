package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;

import java.util.Objects;
import java.util.function.Supplier;


/**
 * 宿主只选择保存、模态、类型选择器或页面，通用派发由 kit 持有。
 */
final class EditorInputRouter {
    private final EditorShell shell;
    private final Supplier<RuleSaveOverlay> savingOverlay;
    private final UiInputRouter router;

    EditorInputRouter(EditorShell shell, Supplier<RuleSaveOverlay> savingOverlay, UiInputRouter router, Runnable synchronize) {
        this.router = router;
        this.shell = shell;
        this.savingOverlay = savingOverlay;
        shell.modals().setOnScopeChanged(synchronize);
        shell.setInputChanged(synchronize);
        shell.setCloseInput(router::close);
    }

    UiInputScope activeScope() {
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
        return next;
    }

    void cancelPointerPreview() {
        if (router.active() != null && router.active().interaction().pointerTarget() != null)
            router.active().interaction().end(UiInteractionSession.EndReason.CANCEL);
    }

    void close() {
        router.close();
    }
}
