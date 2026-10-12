package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListEditor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 字符串 / id 列表 ----

final class ListControl extends FormControl {

    private final UiListEditor editor;
    // 装载时的条目基线；用户未改动且条目未变时视为「未编辑」
    private List<String> loadedItems = List.of();

    ListControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        UiListEditor.Mode mode;
        switch (field.type()) {
            case TAG_LIST:
                mode = UiListEditor.Mode.TAG;
                break;
            case STRING_LIST:
                mode = UiListEditor.Mode.TEXT;
                break;
            default:
                mode = UiListEditor.Mode.ID;
                break;
        }
        this.editor = new UiListEditor(THEME, font, mode, field.registry());
        this.editor.setOnChanged(items -> {
            markEdited();
            onChanged.run();
        });
    }

    @Override
    List<String> inputText() {
        return List.of(editor.input().value(), Integer.toString(editor.editingIndex()));
    }

    @Override
    void restoreInputText(List<String> text) {
        if (text.size() == 2) editor.restorePendingInput(text.get(0), Integer.parseInt(text.get(1)));
        markEdited();
    }

    @Override
    void finishInput() {
        editor.commitPendingInput();
    }

    @Override
    boolean hasPendingInput() {
        return !editor.input().value().isBlank() || !editor.items().equals(loadedItems);
    }

    @Override
    int height() {
        return UiListEditor.preferredHeight(Math.max(1, editor.items().size()));
    }

    @Override
    void setBounds(int x, int y, int width) {
        editor.setBounds(x, y, width, layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        editor.render(graphics, font, mouseX, mouseY);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return editor.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return editor.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return editor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return editor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return editor.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        return editor.charTyped(codePoint, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(editor);
        out.add(editor.input());
    }

    @Override
    void setEnabled(boolean enabled) {
        editor.setEnabled(enabled);
    }

    @Override
    boolean isEditing() {
        return editor.input().isFocused();
    }

    @Override
    boolean isVisible() {
        return editor.isVisible();
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        List<String> items = new ArrayList<>();
        if (value != null && value.isList()) {
            for (RuleValue item : value.items()) {
                if (item != null && !item.isNull()) {
                    items.add(item.asText());
                }
            }
        }
        editor.setItems(items);
        this.loadedItems = List.copyOf(editor.items());
    }

    @Override
    @Nullable RuleValue store() {
        // 未编辑且条目未变：保持 JSON 原样（缺失键不会变成空数组）
        if (!isEdited() && editor.items().equals(loadedItems)) {
            return originalValue();
        }
        RuleValue.Sequence array = RuleValue.list();
        if (editor.size() == 0) {
            return field.nullable() ? null : array.build();
        }
        for (String item : editor.items()) {
            array.add(RuleValue.text(item));
        }
        return array.build();
    }

    @Override
    List<FormIssue> issues(String path) {
        List<FormIssue> list = new ArrayList<>();
        if (field.required() && editor.size() == 0) {
            list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
        }
        String pending = editor.input().value().trim();
        if (!pending.isEmpty() && !validEntry(pending)) {
            list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_id")));
        } else if (!pending.isEmpty() && editor.items().contains(pending)
                && (editor.editingIndex() < 0 || !pending.equals(editor.items().get(editor.editingIndex())))) {
            list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "duplicate")));
        }
        List<String> seen = new ArrayList<>();
        for (String item : editor.items()) {
            if (seen.contains(item)) {
                list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "duplicate")));
                break;
            }
            if (!validEntry(item)) {
                list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_id")));
                break;
            }
            seen.add(item);
        }
        return list;
    }

    private boolean validEntry(String value) {
        if (field.type() == EditorFieldType.STRING_LIST) return true;
        String id = value.startsWith("#") ? value.substring(1) : value;
        return net.minecraft.resources.ResourceLocation.tryParse(id) != null;
    }

    @Override
    void setValueFromSuggestion(String value) {
        markEdited();
        editor.addItem(value);
    }
}
