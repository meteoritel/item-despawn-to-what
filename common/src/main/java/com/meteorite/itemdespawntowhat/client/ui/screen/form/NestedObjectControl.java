package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 单个嵌套对象（固定子表单，不提供增删） ----

/**
 * 单个嵌套对象的固定子表单：对象内部字段沿用声明的子字段描述。
 * <p>与子列表不同，这里不提供添加或删除：对象存在时展示为一个子表单，缺失时展示为空白子表单；
 * 用户编辑任一子字段后才由子表单写入草稿，未编辑时父字段保持原有的省略状态。</p>
 */
final class NestedObjectControl extends FormControl {

    private final FormView child;
    private int measuredHeight;

    NestedObjectControl(Font font, FormView owner, EditorField field, Runnable onChanged) {
        super(owner.theme(), field);
        this.child = owner.newChildForm(field.name(), field.subFields());
        this.child.setLabelWidth(Math.min(88, field.subFields().stream()
                .mapToInt(nested -> font.width(Component.translatable(nested.labelKey())) + 4).max().orElse(0)));
        this.child.setOnChanged(() -> {
            markEdited();
            onChanged.run();
        });
        // 子字段高度变化（如条件树摘要）要传给外层表单重新测量整行
        this.child.setOnGeometryChanged(this::notifyGeometryChanged);
        // 空对象至少保留一行高度，避免子表单尚未布局时行高塌陷
        this.measuredHeight = Math.max(DEFAULT_HEIGHT, font.lineHeight);
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

    // 子表单占满控件宽度；高度随内部字段内容变化
    private void layoutAt(int x, int y, int width) {
        int used = child.layoutUnbounded(x, y, Math.max(16, width));
        measuredHeight = Math.max(DEFAULT_HEIGHT, used);
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        child.render(graphics, font, mouseX, mouseY);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return child.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return child.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return child.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return child.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return child.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return child.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        return child.charTyped(codePoint, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        child.addFocusTargets(out);
    }

    @Override
    void setEnabled(boolean enabled) {
        child.setEnabled(enabled);
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        child.retainPendingInputs();
        child.reloadWith(value != null && value.isMapping() ? value : RuleValue.mapping().build());
        layoutAt(0, 0, 1);
    }

    @Override
    @Nullable RuleValue store() {
        // 未编辑时原样返回装载值（含缺失或显式 null），不把空白子表单写成空对象
        if (!isEdited()) {
            return originalValue();
        }
        child.applyToDraft();
        return child.storeObject();
    }

    @Override
    List<FormIssue> issues(String path) {
        return child.issues();
    }

    @Override
    boolean isEditing() {
        return child.isEditing();
    }

    @Override
    void finishInput() {
        child.commitPendingInputs();
    }

    @Override
    void retainChildInputs() {
        child.retainPendingInputs();
    }

    @Override
    void endInteractions(UiInputCapture.EndReason reason) {
        child.endInteractions(reason);
    }

    @Override
    @Nullable UiFocusTarget revealPath(String path, String basePath) {
        return child.revealPath(path);
    }

    // 子表单把输入保留在自己的 FormView 里，本控件不再贡献文本
    @Override
    void appendInputText(List<String> out) {
    }
}
