package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meteorite.itemdespawntowhat.client.ui.form.FormFieldContext;
import com.meteorite.itemdespawntowhat.client.ui.form.FormRenderer;
import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.client.ui.view.EditMessage;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleEditorSession;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleEntry;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleForm;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleFormBuilder;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleTemplate;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleView;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 单效果规则编辑界面（视图模型版）。
 * 保存与删除都提交「变更集」，不再回写整份快照，因此不会误删未参与编辑的规则。
 * 多效果规则或来自只读来源的规则超出最小实现边界，只读展示并提示手改 JSON。
 */
public final class RuleEditScreen extends Screen {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final int STATUS_DISPLAY_DURATION = 200;

    private final Screen parent;
    private final RuleView draft;
    private final boolean entryEditable;
    private final RuleEditorSession session = RuleEditorSession.get();

    private FormListPanel formList;
    private FormRenderer<RuleView> formRenderer;
    private RuleForm ruleForm;
    private Button saveButton;
    private Button deleteButton;
    private Button listButton;
    private Component statusMessage;
    private int statusTicks;
    private String lastSeenResult;

    // 模板新建（entry 为 null）或编辑既有条目（template 为 null）
    public RuleEditScreen(Screen parent, @Nullable RuleTemplate template, @Nullable RuleEntry entry) {
        super(buildTitle(template, entry));
        this.parent = parent;
        if (entry != null) {
            this.draft = entry.rule().copy();
            this.entryEditable = entry.editable();
        } else if (template != null) {
            this.draft = template.createRule();
            this.entryEditable = true;
        } else {
            throw new IllegalArgumentException("模板与既有规则不能同时为空");
        }
        session.install();
    }

    // 标题：新建用模板名，编辑用规则 id
    private static Component buildTitle(@Nullable RuleTemplate template, @Nullable RuleEntry entry) {
        String name = entry != null ? entry.rule().id() : (template != null ? template.uiId().getPath() : "");
        return Component.translatable("gui.itemdespawntowhat.edit.title", name);
    }

    @Override
    protected void init() {
        super.init();
        if (!isReadOnly()) {
            formList = new FormListPanel(minecraft, width, height - 80, 36, height - 215);
            addRenderableWidget(formList);
            ruleForm = RuleFormBuilder.build(new FormFieldContext(font), draft.primaryEffectType());
            formRenderer = new FormRenderer<>(font, formList, ruleForm.definition());
            formRenderer.initialize();
            for (RuleForm.ConditionBinding binding : ruleForm.conditions()) {
                binding.input().setBeforeOpen(this::captureDraft);
                binding.input().setOnEdited(value -> binding.writer().accept(draft, value));
            }
            formRenderer.readFrom(draft);
        }
        initButtons();
    }

    private void initButtons() {
        int centerX = width / 2;
        int y = height - 28;
        deleteButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemdespawntowhat.edit.list.delete"),
                        button -> requestDelete())
                .bounds(centerX - 160, y, 100, 20).build());
        deleteButton.active = entryEditable && draft.idLocation() != null;
        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemdespawntowhat.apply_to_file"),
                        button -> requestSave())
                .bounds(centerX - 50, y, 100, 20).build());
        saveButton.active = !isReadOnly();
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> back())
                .bounds(centerX + 60, y, 100, 20).build());
        listButton = addRenderableWidget(Button.builder(listLabel(), button -> openListScreen())
                .bounds(width - 110, 6, 100, 16).build());
    }

    // 只读条件：来源不可编辑，或规则含多个效果
    private boolean isReadOnly() {
        return !entryEditable || draft.isMultiEffect();
    }

    // 把表单当前值写回草稿
    private void captureDraft() {
        if (formRenderer != null) {
            formRenderer.writeTo(draft);
        }
    }

    private void requestSave() {
        captureDraft();
        if (!validateDraft()) {
            return;
        }
        session.submitUpsert(draft);
        statusMessage = Component.translatable("gui.itemdespawntowhat.rule.submitted");
        statusTicks = STATUS_DISPLAY_DURATION;
    }

    private void requestDelete() {
        if (draft.idLocation() == null) {
            fail("gui.itemdespawntowhat.rule.id_invalid");
            return;
        }
        session.submitDelete(draft.id());
        statusMessage = Component.translatable("gui.itemdespawntowhat.rule.submitted");
        statusTicks = STATUS_DISPLAY_DURATION;
    }

    // 保存前的本地校验：字段格式、必填项与效果存在性
    private boolean validateDraft() {
        if (formRenderer != null && !formRenderer.validateAll()) {
            fail("gui.itemdespawntowhat.edit.save_error");
            return false;
        }
        if (draft.idLocation() == null) {
            fail("gui.itemdespawntowhat.rule.id_invalid");
            return false;
        }
        if (!draft.source().hasItems()) {
            fail("gui.itemdespawntowhat.rule.source_required");
            return false;
        }
        if (!draft.hasEffect()) {
            fail("gui.itemdespawntowhat.rule.effect_required");
            return false;
        }
        return true;
    }

    private void fail(String key) {
        statusMessage = Component.translatable(key).withStyle(ChatFormatting.RED);
        statusTicks = STATUS_DISPLAY_DURATION;
    }

    private Component listLabel() {
        return Component.translatable("gui.itemdespawntowhat.edit.config_stat_1", session.rules().size());
    }

    private void openListScreen() {
        if (minecraft != null) {
            minecraft.setScreen(new RuleListScreen(this));
        }
    }

    private void back() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        back();
    }

    @Override
    public void tick() {
        super.tick();
        if (formRenderer != null) {
            formRenderer.refreshVisibility();
        }
        if (listButton != null) {
            listButton.setMessage(listLabel());
        }
        String result = session.lastResult();
        if (result != null && !result.equals(lastSeenResult)) {
            lastSeenResult = result;
            statusMessage = Component.translatable("gui.itemdespawntowhat.rule.result", EditMessage.translate(result));
            statusTicks = STATUS_DISPLAY_DURATION;
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        Component mode = minecraft != null && PlayerStateChecker.isSinglePlayerMode(minecraft)
                ? Component.translatable("gui.itemdespawntowhat.edit.mode.local_mode")
                : Component.translatable("gui.itemdespawntowhat.edit.mode.server_mode");
        graphics.drawString(font, mode, 10, 12, 0x808080, false);
        if (isReadOnly()) {
            renderReadOnly(graphics);
        }
        if (statusMessage != null && statusTicks > 0) {
            graphics.drawCenteredString(font, statusMessage, width / 2, 24, 0xFFFFFF);
        }
        if (formRenderer != null) {
            formRenderer.render(graphics, mouseX, mouseY);
        }
    }

    // 只读展示：规则 JSON 原文 + 手改 JSON 的提示（多效果只读边界）
    private void renderReadOnly(GuiGraphics graphics) {
        Component notice = draft.isMultiEffect()
                ? Component.translatable("gui.itemdespawntowhat.rule.multi_effect_readonly")
                : Component.translatable("gui.itemdespawntowhat.rule.not_editable");
        graphics.drawCenteredString(font, notice, width / 2, 34, 0xFFAA00);
        graphics.drawCenteredString(font, Component.translatable("gui.itemdespawntowhat.rule.json_hint"),
                width / 2, 46, 0xAAAAAA);
        String[] lines = PRETTY.toJson(draft.toJson()).split("\n");
        int y = 62;
        for (String line : lines) {
            if (y > height - 40) {
                break;
            }
            graphics.drawString(font, line, 12, y, 0xE0E0E0, false);
            y += 10;
        }
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        if (focused instanceof Button || focused instanceof CycleButton) {
            super.setFocused(null);
        } else {
            super.setFocused(focused);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (formRenderer != null && formRenderer.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (formRenderer != null && formRenderer.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return (formRenderer != null && formRenderer.keyPressed(keyCode, scanCode, modifiers))
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return (formRenderer != null && formRenderer.charTyped(codePoint, modifiers))
                || super.charTyped(codePoint, modifiers);
    }
}
