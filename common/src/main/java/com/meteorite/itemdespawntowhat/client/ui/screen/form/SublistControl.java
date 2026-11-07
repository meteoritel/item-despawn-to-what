package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import java.util.function.Consumer;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// ---- 子列表（对象数组，例如 arrow_rain.potion_effects） ----

final class SublistControl extends FormControl implements UiInputContainer {
    private final UiChildren<UiInputTarget> members = new UiChildren<>();

    private final Font font;
    private final FormView owner;
    private final List<EditorField> subFields;
    private final List<FormView> children = new ArrayList<>();
    private final List<UiButton> removeButtons = new ArrayList<>();
    private final UiButton addButton;
    private int measuredHeight = defaultHeight();

    SublistControl(Font font, FormView owner, EditorField field, Runnable onChanged) {
        super(owner.theme(), field);
        this.font = font;
        this.owner = owner;
        this.subFields = field.subFields();
        this.addButton = new UiButton(THEME, font, Component.translatable(TypeLabels.UI_PREFIX + "list.add"),
                UiButtonVariant.SECONDARY, () -> {
            owner.changeChildStructure(field.name(), () -> {
                markEdited();
                appendChild(null);
            }, this::store);
            onChanged.run();
        }).setChipStyle(true);
        members.add(addButton);
    }

    // 追加一个子表单
    private void appendChild(@Nullable RuleValue value) {
        FormView child = owner.newChildForm(childPath(children.size()), subFields);
        child.reloadWith(value != null && value.isMapping() ? value : RuleValue.mapping().build());
        // 子表单里任何用户改动都要把整个子列表标记为已编辑
        child.setOnChanged(() -> {
            markEdited();
            owner.notifyChanged();
        });
        children.add(child);
        UiButton remove = createRemoveButton(child);
        removeButtons.add(remove);
        members.add(remove);
        members.add(child);
    }

    // 每个子项右侧的删除按钮
    private UiButton createRemoveButton(FormView child) {
        return new UiButton(THEME, font, Component.translatable(TypeLabels.UI_PREFIX + "list.remove"),
                UiButtonVariant.DANGER, () -> {
            int index = children.indexOf(child);
            if (index >= 0) {
                owner.changeChildStructure(field.name(), () -> {
                    markEdited();
                    child.unmount();
                    owner.clearChildInputs(childPath(index));
                    members.remove(child);
                    UiButton remove = removeButtons.remove(index);
                    remove.cancelPress();
                    members.remove(remove);
                    children.remove(index);
                    for (int next = index; next < children.size(); next++) {
                        children.get(next).rebase(owner.childPath(childPath(next)));
                    }
                }, this::store);
                owner.notifyChanged();
            }
        }).setChipStyle(true);
    }

    // 子项相对路径：字段名[下标]
    private String childPath(int index) {
        return DraftPaths.indexPath(field.name(), index);
    }

    @Override
    int height() {
        return measuredHeight;
    }

    @Override
    void measure(int width) {
        layoutAt(0, 0, width);
    }

    @Override
    void setBounds(int x, int y, int width) {
        layoutAt(x, y, width);
    }

    // 依次排列「添加按钮 + 每个子表单（含删除按钮）」，同时算出总高度
    private void layoutAt(int x, int y, int width) {
        int cursor = y;
        addButton.setBounds(x, cursor, Math.max(40, addButton.preferredWidth(6)), defaultHeight());
        cursor += defaultHeight() + 2;
        for (int i = 0; i < children.size(); i++) {
            UiButton remove = removeButtons.get(i);
            int removeWidth = Math.max(28, remove.preferredWidth(4));
            remove.setBounds(x + Math.max(0, width - removeWidth), cursor, removeWidth, defaultHeight());
            cursor += defaultHeight() + 2;
            int used = children.get(i).layoutUnbounded(x + 4, cursor, Math.max(16, width - 8));
            cursor += used + 2;
        }
        this.measuredHeight = Math.max(defaultHeight(), cursor - y);
    }

    @Override
    void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        addButton.render(graphics, font, pointer);
        for (int i = 0; i < children.size(); i++) {
            removeButtons.get(i).render(graphics, font, pointer);
            children.get(i).render(graphics, font, pointer);
        }
    }

    @Override
    void setEnabled(boolean enabled) {
        addButton.setEnabled(enabled);
        for (UiButton remove : removeButtons) {
            remove.setEnabled(enabled);
        }
        for (FormView child : children) {
            child.setEnabled(enabled);
        }
    }

    @Override
    void endInteractions(UiInteractionSession.EndReason reason) {
        addButton.cancelPress();
        cancelButtonPresses(removeButtons);
        for (FormView child : children) child.endInteractions(reason);
    }

    // 子表单把输入保留在自己的 FormView 里，本控件不再贡献文本
    @Override
    void appendInputText(List<String> out) {
    }

    @Override
    void retainChildInputs() {
        for (FormView child : children) child.retainPendingInputs();
    }

    @Override
    void finishInput() {
        for (FormView child : children) child.commitPendingInputs();
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        retainChildInputs();
        for (FormView child : children) child.unmount();
        cancelButtonPresses(removeButtons);
        children.clear();
        removeButtons.clear();
        members.clear();
        members.add(addButton);
        if (value != null && value.isList()) {
            for (RuleValue element : value.items()) {
                appendChild(element);
            }
        }
        layoutAt(0, 0, 1);
    }

    @Override
    @Nullable RuleValue store() {
        // 未编辑（含未增删子项、子表单未改动）：原样返回装载数组
        if (!isEdited()) {
            return originalValue();
        }
        RuleValue.Sequence array = RuleValue.list();
        for (FormView child : children) {
            child.applyToDraft();
            array.add(child.storeObject());
        }
        return array.build();
    }

    @Override
    List<FormIssue> issues(String path) {
        List<FormIssue> list = new ArrayList<>();
        for (FormView child : children) {
            list.addAll(child.issues());
        }
        return list;
    }

    @Override
    boolean isEditing() {
        for (FormView child : children) {
            if (child.isEditing()) {
                return true;
            }
        }
        return false;
    }

    /** 实际成员同时提供命中和普通输入路径。 */
    @Override public UiChildren<UiInputTarget> inputChildren() { return members; }
    /** 基类的字段能力由真实子树提供。 */
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { members.addFocusTargets(out); }
    /** 释放只进入当前持焦分支。 */
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return members.keyReleased(key, scan, modifiers); }

}
