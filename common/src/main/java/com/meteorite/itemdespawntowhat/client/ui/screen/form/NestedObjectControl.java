package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import java.util.function.Consumer;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.NumericDomain;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;

import java.util.List;

import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
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
final class NestedObjectControl extends FormControl implements UiInputContainer {
    private final UiChildren<UiInputTarget> members = new UiChildren<>();

    private final FormView child;
    private int measuredHeight;

    NestedObjectControl(UiHostTheme theme, Font font, FormControlContext context, EditorField field, Runnable onChanged) {
        super(theme, field);
        this.child = context.newChildForm(FieldPath.key(field.name()), field.subFields());
        members.add(child);
        this.child.setLabelWidth(Math.min(88, field.subFields().stream()
                .mapToInt(nested -> font.width(Component.translatable(nested.labelKey())) + 4).max().orElse(0)));
        // 带端点标签的数值（如整格偏移的三轴）由控件自述方向，子表单不再重复绘制轴名行
        for (FormField sub : this.child.fields()) {
            NumericDomain numbers = sub.descriptor().numbers();
            if (numbers != null && numbers.signedLabels() != null) {
                sub.hideLabel();
            }
        }
        this.child.setOnChanged(() -> {
            markEdited();
            onChanged.run();
        });
        // 子字段高度变化（如条件树摘要）要传给外层表单重新测量整行
        this.child.setOnGeometryChanged(this::notifyGeometryChanged);
        // 空对象至少保留一行高度，避免子表单尚未布局时行高塌陷
        this.measuredHeight = Math.max(defaultHeight(), font.lineHeight);
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
        measuredHeight = Math.max(defaultHeight(), used);
    }

    @Override
    void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        child.render(graphics, font, pointer);
    }

    @Override
    void setEnabled(boolean enabled) {
        child.setEnabled(enabled);
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        child.retainPendingInputs();
        child.reloadWith(value == null ? RuleValue.mapping().build() : value);
        layoutAt(0, 0, 1);
    }

    @Override
    @Nullable RuleValue draftValue() {
        // 未编辑时原样返回装载值（含缺失或显式 null），不把空白子表单写成空对象
        if (!isEdited() && !child.hasDraftChanges()) {
            return originalValue();
        }
        return child.draftObject();
    }

    @Override
    List<FormIssue> issues(FieldPath path) {
        return child.issues();
    }

    @Override
    boolean isEditing() {
        return child.isEditing();
    }

    @Override
    List<FormView> childForms() {
        return List.of(child);
    }

    @Override
    void finishInput() {
        child.finishInputs();
    }

    @Override
    void retainChildInputs() {
        child.retainPendingInputs();
    }

    @Override
    void endInteractions(UiInteractionSession.EndReason reason) {
        child.endInteractions(reason);
    }

    @Override
    @Nullable UiFocusTarget revealPath(FieldPath path, FieldPath basePath) {
        return child.revealPath(path);
    }

    // 子表单把输入保留在自己的 FormView 里，本控件不再贡献文本
    @Override
    void appendInputText(List<String> out) {
    }

    /** 实际成员同时提供命中和普通输入路径。 */
    @Override public UiChildren<UiInputTarget> inputChildren() { return members; }
    /** 基类的字段能力由真实子树提供。 */
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { members.addFocusTargets(out); }
    /** 释放只进入当前持焦分支。 */
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return members.keyReleased(key, scan, modifiers); }

}
