package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 枚举字段控件：单个轮询按钮，点一下切到下一个候选。
 *
 * <p>候选文案各自使用本地化文案原样显示，不会因为列宽不足被压成省略号；按钮按胶囊绘制并带右箭头，
 * 用户因此能看出点击会轮换取值。草稿写入、必填校验与回填都与其他字段走同一条路径。</p>
 */
final class EnumControl extends FormControl {

    // 轮换按钮：标签始终是当前取值
    private final UiButton button;
    private final List<String> values;
    // 枚举组名：决定每项文案的 i18n key
    private final String group;
    private String selected;

    EnumControl(UiHostTheme theme, Font font, EditorField field, Runnable onChanged) {
        super(theme, field);
        this.values = field.enumValues();
        this.group = field.enumGroup() == null || field.enumGroup().isBlank() ? field.name() : field.enumGroup();
        this.selected = values.isEmpty() ? "" : values.getFirst();
        this.button = new UiButton(THEME, font, valueLabel(selected), UiButtonVariant.SECONDARY, () -> {
            cycle();
            onChanged.run();
        }).setCycleStyle(true).setAccessibleName(valueLabel(selected));
    }

    // 候选项文案：枚举组名 + wire 取值
    private Component valueLabel(String value) {
        return Component.translatable(TypeLabels.UI_PREFIX + "enum." + group + "." + value);
    }

    // 轮询到下一个候选：到末尾后回到第一个
    private void cycle() {
        if (values.isEmpty()) {
            return;
        }
        int index = values.indexOf(selected);
        selected = values.get(Math.floorMod(index + 1, values.size()));
        button.setLabel(valueLabel(selected));
        button.setAccessibleName(valueLabel(selected));
        markEdited();
    }

    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
        button.setBounds(x, y, width, layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        button.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        return button.mousePressed(context);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 轮询按钮沿用按钮的激活语义：聚焦后 Enter / Space 切到下一个取值。
        // FormView 走自己的键路由，因此这里不能只依赖按钮自身的按键处理。
        if (!button.isFocused()) {
            return false;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                cycle();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(button);
    }

    @Override
    void setEnabled(boolean enabled) {
        button.setEnabled(enabled);
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        String loaded = value != null && value.isScalar() ? value.asText() : "";
        selected = values.contains(loaded) ? loaded : values.isEmpty() ? "" : values.getFirst();
        button.setLabel(valueLabel(selected));
        button.setAccessibleName(valueLabel(selected));
    }

    @Override
    @Nullable RuleValue store() {
        // 用户没点过枚举：保持 JSON 原样（省略 shape/pickup 时不会写入或写成空串）
        if (!isEdited()) {
            return originalValue();
        }
        return selected == null || selected.isEmpty() ? null : RuleValue.text(selected);
    }

    @Override
    List<FormIssue> issues(String path) {
        if (field.required() && selected.isBlank()) {
            return List.of(FormIssue.required(path, label()));
        }
        return List.of();
    }
}
