package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogSelectionBinding;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 通用目录的宿主装配：表单注入一次，选择接收端继续拥有结果与草稿写入。 */
final class CatalogSelectionOpener implements FormView.SelectionCatalogOpener {
    private final EditorShell shell;
    private final Font font;
    private final EditSession session;

    CatalogSelectionOpener(EditorShell shell, Font font, EditSession session) {
        this.shell = shell;
        this.font = font;
        this.session = session;
    }

    @Override
    public void open(EditorField field, String path, boolean tags, @Nullable Predicate<String> filter,
                     CatalogSelectionBinding binding) {
        if (!shell.editable() || shell.editingSession() != session) return;
        RuleCatalogType type = tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field);
        if (type == null) return;
        CatalogSelectionBinding target = new CatalogSelectionBinding(binding.mode(), binding.initialSelection(), ids -> {
            if (!shell.editable() || shell.editingSession() != session) {
                shell.setNotice(Component.translatable(TypeLabels.UI_PREFIX + "pick.target_changed"),
                        EditorShell.NoticeLevel.WARNING);
                return;
            }
            binding.confirm(ids);
        });
        shell.modals().push(RuleEditorPanelFactory.fieldCatalogModal(font, shell, shell.workspace(), type,
                target, Component.translatable(field.labelKey()), shell.screenWidth(), shell.screenHeight(),
                field, session.root(), path, filter));
    }
}
