package com.meteorite.itemdespawntowhat.client.ui.screen;

import net.minecraft.client.gui.Font;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormField;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;

/**
 * 触发与条件页：触发来源、触发时间、条件树摘要与画布页入口，以及条件叶参数弹窗。
 */
final class RuleEditorTriggerSection {

    final EditorSectionContent content;
    private final EditorShell shell;
    private final Font font;
    private final EditorFormFactory forms;
    private final EditorSectionActions commands;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditSections.THEME;

    RuleEditorTriggerSection(EditorShell shell, Font font, EditorFormFactory forms, EditorSectionActions actions) {
        this.shell = shell;
        this.font = font;
        this.forms = forms;
        this.commands = actions;
        this.content = new EditorSectionContent(font);
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        triggerBoxes.clear();
        triggerNoteRect = null;
        delayForm = null;
        delayWindow = null;
        delayHintRect = null;
        conditionsForm = null;
    }

    final List<UiCheckBox> triggerBoxes = new ArrayList<>();

    @Nullable UiRect triggerNoteRect;

    @Nullable FormView delayForm;
    private @Nullable FormField delayWindow;

    @Nullable UiRect delayHintRect;

    @Nullable FormView conditionsForm;

    void buildTrigger(EditSession session) {
        shell.workspace().requestReferenceLifespan();
        RuleValue rule = session.root();
        List<String> effective = RuleTriggers.effective(rule);
        for (String value : RuleTriggers.known()) {
            UiCheckBox box = new UiCheckBox(RuleEditorEditSections.THEME, font, Component.translatable(RuleEditorPageSupport.UI + "trigger." + value), effective.contains(value));
            box.setOnChanged(checked -> onTriggerToggled());
            triggerBoxes.add(box);
            content.widgets.add(box);
        }
        if (RuleTriggers.allowsDelay(rule)) {
            delayForm = forms.create(session, "", BuiltinEditorDescriptors.delayDescriptor());
            delayWindow = delayForm.field(RuleFields.TRIGGER_AFTER_SECONDS);
            content.forms.add(delayForm);
            content.widgets.add(delayForm);
        }
        TypeEditorDescriptor conditions = BuiltinEditorDescriptors.conditionsDescriptor();
        conditionsForm = forms.create(session, "", conditions);
        conditionsForm.setLabelWidth(90);
        content.forms.add(conditionsForm);
        content.widgets.add(conditionsForm);
    }

    void updateLifespanWindow() {
        EditSession session = shell.editingSession();
        if (delayWindow == null || session == null) return;
        int ticks = shell.workspace().session().referenceLifespanTicks();
        // 整数秒向上取整保证不足一秒的寿命也可拖动；常用窗口不钳制既有触发值。
        int seconds = ticks <= 0 ? BuiltinEditorDescriptors.DEFAULT_TRIGGER_WINDOW_SECONDS
                : (int) Math.ceil((double) ticks / RuleEditSessionLimits.TICKS_PER_SECOND);
        delayWindow.sliderWindow(0, Math.max(1, seconds));
    }

    void onTriggerToggled() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < triggerBoxes.size(); i++) {
            if (triggerBoxes.get(i).isChecked()) {
                values.add(RuleTriggers.known().get(i));
            }
        }
        for (String value : RuleTriggers.effective(draft)) {
            if (!RuleTriggers.known().contains(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        commands.apply(EditSession.OP_SET_FIELD, edits -> edits.setAt(RuleFields.TRIGGERS, RuleTriggers.toArray(values)));
    }

    int layoutTrigger(UiRect area) {
        int x = area.x() + RuleEditorPageSupport.CARD_PAD;
        int width = Math.max(0, area.width() - RuleEditorPageSupport.CARD_PAD * 2);
        int top = area.y();
        int y = top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H;
        int cursorX = x;
        for (UiCheckBox box : triggerBoxes) {
            int boxWidth = Math.min(width, box.preferredWidth(4));
            if (cursorX > x && cursorX + boxWidth > x + width) {
                cursorX = x;
                y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
            }
            box.setBounds(cursorX, y, boxWidth, RuleEditorPageSupport.ROW_H);
            cursorX += boxWidth + RuleEditorPageSupport.CARD_GAP;
        }
        y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        triggerNoteRect = null;
        delayHintRect = null;
        if (RuleTriggers.isExplicitlyEmpty(shell.editingValue())) {
            triggerNoteRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        y = EditorSectionContent.layoutForm(delayForm, x, y, width);
        if (delayForm != null) {
            delayHintRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        top = content.finishCard(area.x(), top, area.width(), y, "card.trigger");
        y = EditorSectionContent.layoutForm(conditionsForm, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        return content.finishCard(area.x(), top, area.width(), y, "card.conditions");
    }

    void renderTrigger(GuiGraphics graphics) {
        RuleValue view = shell.editingValue();
        // 提示只在「显式选择空触发」这一确实缺失的状态出现，有效触发不再恒显
        if (RuleTriggers.isExplicitlyEmpty(view)) {
            content.drawText(graphics, triggerNoteRect, Component.translatable(RuleEditorPageSupport.UI + "trigger.note"), THEME.palette().warning());
        }
        // 等待时长只对自然触发生效，其他触发组合下不显示该说明
        if (RuleTriggers.allowsDelay(view)) {
            content.drawText(graphics, delayHintRect, Component.translatable(RuleEditorPageSupport.UI + "trigger.delay_hint"), THEME.palette().textDisabled());
        }
    }
    void applyEnabled(boolean editable) {
        triggerBoxes.forEach(box -> box.setEnabled(editable));
    }

    void restoreSwitches() {
        List<String> triggers = RuleTriggers.effective(shell.editingValue());
        for (int i = 0; i < triggerBoxes.size(); i++) {
            triggerBoxes.get(i).setChecked(triggers.contains(RuleTriggers.known().get(i)));
        }
    }
}
