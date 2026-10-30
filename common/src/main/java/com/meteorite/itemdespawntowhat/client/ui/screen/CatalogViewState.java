package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 两个目录宿主共用的发布状态；按真实输入变化创建视图组和文案。 */
final class CatalogViewState {
    private List<String> selection;
    private UiCatalogGrid.Content content;
    private UiCatalogGrid.Navigation navigation;
    private UiCatalogGrid.Status status;
    private UiCatalogGrid.View view;
    private int total = Integer.MIN_VALUE;
    private String errorKey;

    CatalogViewState(List<String> selected) {
        selection = List.copyOf(selected);
        content = new UiCatalogGrid.Content(List.of(), selection);
    }

    List<String> selection() { return selection; }

    void select(List<String> selected) {
        selection = List.copyOf(selected);
        content = new UiCatalogGrid.Content(content.entries(), selection);
    }

    void entries(List<UiCatalogGrid.Entry> entries) {
        if (content.entries() != entries) content = new UiCatalogGrid.Content(entries, selection);
    }

    void navigation(UiCatalogGrid.Scope scope, int page, int count, @Nullable Component label) {
        if (navigation == null || navigation.scope() != scope || navigation.page() != page
                || navigation.pageCount() != count || navigation.label() != label) {
            navigation = new UiCatalogGrid.Navigation(scope, page, count, label);
        }
    }

    void status(boolean loading, boolean enabled, int nextTotal, @Nullable String nextErrorKey) {
        if (status == null || status.loading() != loading || status.enabled() != enabled
                || total != nextTotal || !Objects.equals(errorKey, nextErrorKey)) {
            total = nextTotal;
            errorKey = nextErrorKey;
            Component note = total < 0 ? null : Component.translatable(TypeLabels.UI_PREFIX + "pick.total", total);
            Component error = errorKey == null ? null : Component.translatable(TypeLabels.UI_PREFIX + errorKey);
            status = new UiCatalogGrid.Status(loading, enabled, note, error);
        }
    }

    UiCatalogGrid.View view() {
        if (view == null || view.content() != content || view.navigation() != navigation || view.status() != status) {
            view = new UiCatalogGrid.View(content, navigation, status);
        }
        return view;
    }
}
