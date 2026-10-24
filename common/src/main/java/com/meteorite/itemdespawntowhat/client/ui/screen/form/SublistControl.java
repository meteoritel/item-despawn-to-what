package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 子列表（对象数组，例如 arrow_rain.potion_effects） ----

final class SublistControl extends FormControl {

    private final Font font;
    private final FormView owner;
    private final List<EditorField> subFields;
    private final List<FormView> children = new ArrayList<>();
    private final List<UiButton> removeButtons = new ArrayList<>();
    private final UiButton addButton;
    private int measuredHeight = DEFAULT_HEIGHT;

    SublistControl(Font font, FormView owner, EditorField field, Runnable onChanged) {
        super(field);
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
        removeButtons.add(createRemoveButton(child));
    }

    // 每个子项右侧的删除按钮
    private UiButton createRemoveButton(FormView child) {
        return new UiButton(THEME, font, Component.translatable(TypeLabels.UI_PREFIX + "list.remove"),
                UiButtonVariant.DANGER, () -> {
                    int index = children.indexOf(child);
                    if (index >= 0) {
                        owner.changeChildStructure(field.name(), () -> {
                            markEdited();
                            children.get(index).retainPendingInputs();
                            owner.clearChildInputs(childPath(index));
                            children.remove(index);
                            removeButtons.remove(index);
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
        addButton.setBounds(x, cursor, Math.max(40, addButton.preferredWidth(6)), DEFAULT_HEIGHT);
        cursor += DEFAULT_HEIGHT + 2;
        for (int i = 0; i < children.size(); i++) {
            UiButton remove = removeButtons.get(i);
            int removeWidth = Math.max(28, remove.preferredWidth(4));
            remove.setBounds(x + Math.max(0, width - removeWidth), cursor, removeWidth, DEFAULT_HEIGHT);
            cursor += DEFAULT_HEIGHT + 2;
            int used = children.get(i).layoutUnbounded(x + 4, cursor, Math.max(16, width - 8));
            cursor += used + 2;
        }
        this.measuredHeight = Math.max(DEFAULT_HEIGHT, cursor - y);
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        addButton.render(graphics, font, mouseX, mouseY);
        for (int i = 0; i < children.size(); i++) {
            removeButtons.get(i).render(graphics, font, mouseX, mouseY);
            children.get(i).render(graphics, font, mouseX, mouseY);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (addButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = 0; i < children.size(); i++) {
            if (removeButtons.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (children.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = addButton.mouseReleased(mouseX, mouseY, button);
        for (int i = 0; i < children.size(); i++) {
            consumed |= removeButtons.get(i).mouseReleased(mouseX, mouseY, button);
            consumed |= children.get(i).mouseReleased(mouseX, mouseY, button);
        }
        return consumed;
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean consumed = false;
        for (FormView child : children) {
            consumed |= child.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return consumed;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean consumed = false;
        for (FormView child : children) {
            consumed |= child.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return consumed;
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean consumed = false;
        for (FormView child : children) {
            consumed |= child.keyPressed(keyCode, scanCode, modifiers);
        }
        return consumed;
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        boolean consumed = false;
        for (FormView child : children) {
            consumed |= child.charTyped(codePoint, modifiers);
        }
        return consumed;
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(addButton);
        for (FormView child : children) {
            child.addFocusTargets(out);
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

    // 子表单把输入保留在自己的 FormView 里，本控件不再贡献文本
    @Override
    void appendInputText(List<String> out) { }

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
        children.clear();
        removeButtons.clear();
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
}
