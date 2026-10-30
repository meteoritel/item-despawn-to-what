package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 枚举字段控件：默认把候选铺成一行分段，声明为轮询形态时改用单个按钮。
 *
 * <p>分段选择适合候选少、文案短的枚举；轮询按钮只显示当前取值，占满控件列宽并在点击时切到下一个候选，
 * 因此候选文案再长也不会被压成省略号。两种形态的配置数据完全一致，草稿写入与校验走同一路径。</p>
 */
final class EnumControl extends FormControl {

    // 轮询形态的分段控件与按钮只存在其一
    private final @Nullable UiSegmentedControl segments;
    private final @Nullable UiButton cycleButton;
    private final List<String> values;
    // 枚举组名：决定每项文案的 i18n key
    private final String group;
    private String selected = "";

    EnumControl(UiHostTheme theme, Font font, EditorField field, Runnable onChanged) {
        super(theme, field);
        this.values = field.enumValues();
        this.group = field.enumGroup() == null || field.enumGroup().isBlank() ? field.name() : field.enumGroup();
        if (field.enumCycling() && !values.isEmpty()) {
            this.segments = null;
            this.selected = values.getFirst();
            this.cycleButton = new UiButton(THEME, font, valueLabel(selected), UiButtonVariant.SECONDARY, () -> {
                cycle();
                onChanged.run();
            });
            this.cycleButton.setAccessibleName(valueLabel(selected));
            return;
        }
        this.cycleButton = null;
        List<UiSegmentedControl.Option> options = new ArrayList<>();
        for (String value : values) {
            options.add(new UiSegmentedControl.Option(value, valueLabel(value)));
        }
        this.segments = new UiSegmentedControl(THEME, font, options);
        this.segments.setOnChanged(value -> {
            selected = value;
            markEdited();
            onChanged.run();
        });
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
        if (cycleButton != null) {
            cycleButton.setLabel(valueLabel(selected));
            cycleButton.setAccessibleName(valueLabel(selected));
        }
        markEdited();
    }

    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
        if (cycleButton != null) {
            cycleButton.setBounds(x, y, width, layoutHeight());
        } else if (segments != null) {
            segments.setBounds(x, y, width, layoutHeight());
        }
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (cycleButton != null) {
            cycleButton.render(graphics, font, mouseX, mouseY);
            drawCycleHint(graphics);
        } else if (segments != null) {
            segments.render(graphics, font, mouseX, mouseY);
        }
    }

    // 轮询按钮右缘留一个箭头：整行长条不加提示会被读成输入框，看不出点一下会换取值
    private void drawCycleHint(GuiGraphics graphics) {
        UiRect rect = cycleButton.bounds();
        UiMarkIcons.Mark mark = UiMarkIcons.Mark.NEXT;
        int x = Math.max(rect.x(), rect.right() - 4 - mark.width());
        int y = rect.y() + Math.max(0, (rect.height() - mark.height()) / 2);
        UiMarkIcons.icon(mark, THEME.palette().textSecondary()).render(graphics, x, y);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (cycleButton != null) {
            return cycleButton.mousePressed(context);
        }
        return segments != null && segments.mousePressed(context);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (cycleButton != null) {
            // 轮询按钮沿用按钮的激活语义：聚焦后 Enter / Space 切到下一个取值
            if (!cycleButton.isFocused()) {
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
        return segments != null && segments.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        if (cycleButton != null) {
            out.add(cycleButton);
        } else if (segments != null) {
            out.add(segments);
        }
    }

    @Override
    void setEnabled(boolean enabled) {
        if (cycleButton != null) {
            cycleButton.setEnabled(enabled);
        } else if (segments != null) {
            segments.setEnabled(enabled);
        }
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        String loaded = value != null && value.isScalar() ? value.asText() : "";
        selected = values.contains(loaded) ? loaded : values.isEmpty() ? "" : values.getFirst();
        if (cycleButton != null) {
            cycleButton.setLabel(valueLabel(selected));
            cycleButton.setAccessibleName(valueLabel(selected));
        } else if (segments != null) {
            segments.setSelected(selected);
        }
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
