package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayers;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** 编辑器的显示选择：一份清单同时约束输入、绘制和布局导出。 */
final class EditorLayers {
    private final EditorShell shell;
    private final Supplier<RuleSaveOverlay> savingOverlay;
    private final Map<EditorPage, UiLayer> pages = new IdentityHashMap<>();
    private final UiLayers presentation;
    private UiLayer singleOverlay;
    private List<UiLayer> singlePresentation = List.of();

    EditorLayers(EditorShell shell, Supplier<RuleSaveOverlay> savingOverlay,
                 UiLayer.Painter pagePainter, UiLayoutSource pageLayout) {
        this.shell = shell;
        this.savingOverlay = savingOverlay;
        for (EditorPage page : List.of(shell.listPage(), shell.editPage(), shell.debugPage())) {
            pages.put(page, new UiLayer(page.inputScope(), pagePainter, pageLayout));
        }
        presentation = new UiLayers(pages.get(shell.activePage()));
    }

    UiLayers select() {
        presentation.setBase(pages.get(shell.activePage()));
        if (shell.saving()) {
            selectOverlay(savingOverlay.get().layer());
        } else if (shell.modals().isVisible()) {
            presentation.setOverlays(shell.modals().layers());
        } else {
            UiTypePicker picker = shell.activePage().overlay();
            selectOverlay(picker == null ? null : picker.layer());
        }
        return presentation;
    }

    private void selectOverlay(UiLayer layer) {
        if (singleOverlay != layer) {
            singleOverlay = layer;
            singlePresentation = layer == null ? List.of() : List.of(layer);
        }
        presentation.setOverlays(singlePresentation);
    }
}
