package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.EditorPreset;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextArea;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 单行文本控件（含数值、id、百分比等全部文本型字段） ----

final class TextControl extends FormControl {

    // 备注的行数：2–3 行内直接读完，不再依赖单行横向滚动
    private static final int NOTES_ROWS = 3;

    private final Font font;
    private final UiTextInput input;
    // 备注专用多行编辑框；单行字段为 null
    private final @Nullable UiTextArea area;
    private final List<UiButton> presetButtons = new ArrayList<>();
    private final Function<String, RuleValue> parse;
    private final Function<RuleValue, String> format;
    private final String invalidKey;
    private @Nullable RuleValue loaded;
    // 装载时写入输入框的文本；用户未改动且未提交时视为「未编辑」
    private String loadedText = "";
    // 本次输入因超出码点上限被拒绝（下一次被接受的输入变化时清除）
    private boolean rejectedOverflow;

    TextControl(Font font, EditorField field, Predicate<String> filter,
            Function<String, RuleValue> parse, Function<RuleValue, String> format,
            Runnable onChanged, String invalidKey) {
        super(field);
        this.font = font;
        this.parse = parse;
        this.format = format;
        this.invalidKey = invalidKey;
        Component hint = hint();
        this.input = new UiTextInput(THEME, font, hint == null ? Component.empty() : hint);
        this.input.setMaxLength(textLimit());
        this.input.setFilter(filter);
        this.input.setOnCommit(text -> {
            markEdited();
            onChanged.run();
        });
        this.input.setOnOverflow(this::notifyTooLong);
        // 下一次被接受的输入变化即清除超限提示
        this.input.setOnValueChanged(() -> this.rejectedOverflow = false);
        if (field.type() == EditorFieldType.LONG_TEXT) {
            // 备注使用真正的多行编辑框：支持换行、选择与滚动；单行输入框隐藏但保留装载基线
            this.area = new UiTextArea(THEME, font, hint);
            this.area.setRows(NOTES_ROWS);
            this.area.setOnChanged(() -> {
                this.rejectedOverflow = false;
                markEdited();
                onChanged.run();
            });
            this.area.setOnOverflow(this::notifyTooLong);
            this.input.setVisible(false);
        } else {
            this.area = null;
        }
        for (EditorPreset preset : field.presets()) {
            UiButton button = new UiButton(THEME, font, Component.translatable(preset.labelKey()), UiButtonVariant.SECONDARY,
                    () -> {
                        markEdited();
                        setText(preset.value());
                        onChanged.run();
                    }).setChipStyle(true);
            presetButtons.add(button);
        }
    }

    @Override
    int height() {
        return area != null ? area.height() : DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
        if (area != null) {
            // 多行备注不支持预设按钮：直接铺满整行（presetButtons 恒为空，加预设需另行布局）
            area.setBounds(x, y, width, layoutHeight());
            return;
        }
        int reserved = 0;
        for (UiButton button : presetButtons) {
            reserved += buttonWidth(button) + 2;
        }
        int inputWidth = Math.max(16, width - reserved);
        input.setBounds(x, y, inputWidth, layoutHeight());
        int cursor = x + inputWidth + 2;
        for (UiButton button : presetButtons) {
            int buttonWidth = buttonWidth(button);
            button.setBounds(cursor, y, buttonWidth, layoutHeight());
            cursor += buttonWidth + 2;
        }
    }

    // 预设按钮宽度：文本宽度 + 内边距
    private int buttonWidth(UiButton button) {
        return Math.max(16, font.width(button.label()) + 8);
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (area != null) {
            area.render(graphics, font, mouseX, mouseY);
            return;
        }
        input.render(graphics, font, mouseX, mouseY);
        for (UiButton button : presetButtons) {
            button.render(graphics, font, mouseX, mouseY);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (UiButton preset : presetButtons) {
            if (preset.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return area != null ? area.mouseClicked(mouseX, mouseY, button) : input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return area != null ? area.mouseReleased(mouseX, mouseY, button) : input.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    void endInteractions(UiInputCapture.EndReason reason) {
        input.endInteraction(reason);
        if (area != null) area.endInteraction(reason);
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return area != null ? area.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                : input.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return area != null && area.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return area != null ? area.keyPressed(keyCode, scanCode, modifiers) : input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        return area != null ? area.charTyped(codePoint, modifiers) : input.charTyped(codePoint, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        if (area != null) {
            out.add(area);
            return;
        }
        out.add(input);
    }

    @Override
    void setEnabled(boolean enabled) {
        if (area != null) {
            area.setEditable(enabled);
        }
        input.setEditable(enabled);
        for (UiButton preset : presetButtons) {
            preset.setEnabled(enabled);
        }
    }

    @Override
    boolean hasPendingInput() {
        return !text().equals(loadedText);
    }

    @Override
    @Nullable Component fullText() {
        return text().isEmpty() ? null : Component.literal(text());
    }

    @Override
    boolean isEditing() {
        return area != null ? area.isFocused() : input.isFocused();
    }

    @Override
    boolean isVisible() {
        return area != null ? area.isVisible() : input.isVisible();
    }

    // 当前文本：备注取自多行编辑框
    private String text() {
        return area != null ? area.value() : input.value();
    }

    // 程序化写入文本（预设值与建议值）
    private void setText(String value) {
        if (area != null) {
            area.setValue(value);
        } else {
            input.setValue(value);
        }
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        this.loaded = value == null || value.isNull() ? null : value.deepCopy();
        this.loadedText = loaded == null ? "" : format.apply(loaded);
        this.rejectedOverflow = false;
        if (area != null) {
            // 原值超出编辑长度也必须完整回填；上限按码点，避免多字节字符被算成多个
            area.setMaxLength(Math.max(textLimit(), loadedText.codePointCount(0, loadedText.length())));
            area.setValue(loadedText);
            // 回填后光标与滚动停在开头：先回填再按窄宽度布局也不会把显示推到末尾
            area.moveCursorToStart();
            return;
        }
        // 原值超长也必须完整回填，浏览或修改其他字段不能截短它；上限按码点抬高（既有超长原文只提示不改写）
        input.setMaxLength(Math.max(textLimit(), loadedText.codePointCount(0, loadedText.length())));
        input.setValue(loadedText);
        // 单行同样从开头展示，避免窄宽度下只看到末尾几个字符
        input.moveCursorToStart();
    }

    // 普通文本和备注分别使用声明的编辑长度，资源 ID 沿用原输入范围。
    private int textLimit() {
        return field.type() == EditorFieldType.LONG_TEXT ? 1024
                : field.type() == EditorFieldType.TEXT ? 128 : 256;
    }

    @Override
    @Nullable RuleValue store() {
        String value = text();
        if (value.equals(loadedText)) {
            return originalValue();
        }
        String text = field.type() == EditorFieldType.LONG_TEXT ? value : value.trim();
        // 未编辑且文本仍等于装载基线：原样回写原始元素（缺失即 null），applyToDraft 跳过该字段
        if (field.type() != EditorFieldType.LONG_TEXT && !isEdited() && text.equals(loadedText.trim())) {
            return originalValue();
        }
        if (text.isEmpty()) {
            // 必填/不可空字段清空时不删除既有值，交由 issues() 提示（阻止应用）
            boolean keepExisting = loaded != null && (field.required() || !field.nullable());
            return keepExisting ? loaded : null;
        }
        RuleValue parsed = parse.apply(text);
        // 输入非法时保留原值，交由 issues() 提示玩家修正
        return parsed != null ? parsed : loaded;
    }

    @Override
    List<FormIssue> issues(String path) {
        // 长度超限做行内提示（warning）：被拒时缓冲值本身仍在合法范围，
        // 既有原文超限由规则级校验给出阻塞错误
        String raw = text();
        if (rejectedOverflow || raw.codePointCount(0, raw.length()) > textLimit()) {
            String key = tooLongKey();
            if (key != null) {
                return List.of(FormIssue.warning(path, label(), Component.translatable(key)));
            }
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            if (field.required()) {
                return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
            }
            return List.of();
        }
        if (parse.apply(text) == null) {
            return List.of(FormIssue.error(path, label(), Component.translatable(invalidKey)));
        }
        return List.of();
    }

    // 超限提示 key：备注与显示名各有专属文案；其它文本字段没有专属文案，不提示
    private @Nullable String tooLongKey() {
        if (field.type() == EditorFieldType.LONG_TEXT) {
            return ISSUE_PREFIX + "notes_too_long";
        }
        return RuleFields.DISPLAY_NAME.equals(field.name()) ? ISSUE_PREFIX + "display_name_too_long" : null;
    }

    // 输入超限被拒：置瞬时标记并给出一条即时提示
    private void notifyTooLong() {
        this.rejectedOverflow = true;
        reportRejected(tooLongKey());
    }

    @Override
    void setValueFromSuggestion(String value) {
        markEdited();
        setText(value);
    }
}
