package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftConflict;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.CatalogSuggestions;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConsumptionSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormIssue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.RuleTemplateHooks;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.Suggestion;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 规则编辑器主屏（契约 §5、docs/plan/plan-frontend-rewrite-forms.md）。
 * <p>两种模式：规则管理页（列表/搜索/筛选/新建/复制/停用/删除/恢复原始/模板）与规则编辑页
 * （基本信息 / 源物品 / 触发条件 / 效果 四个页签）。所有字段编辑走描述符驱动的
 * {@link FormView}，条件树走 {@link UiConditionTreeEditor}，界面任何位置都不出现可编辑 JSON 文本框。
 * <p>只有工作区处于 ACTIVE 时可编辑；APPLYING 冻结；工作区进入 FREE 时自行关屏。
 */
public final class RuleEditorScreen extends Screen {

    // 布局常量
    private static final int PAD = 4;
    private static final int HEADER_H = 14;
    private static final int TAB_H = 14;
    private static final int FOOTER_H = 16;
    private static final int ROW_H = 12;
    private static final int NOTICE_H = 10;
    // 小尺寸（320x240）单列阈值
    private static final int NARROW_WIDTH = 430;
    // 效果类型选择浮层
    private static final int PICKER_HEADER = 14;
    private static final int PICKER_FOOTER = 12;
    private static final int PICKER_MAX_ROWS = 8;
    // 自然语言摘要 / 消耗语义提示行高
    private static final int SUMMARY_H = 10;
    // i18n 前缀
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final String ISSUE = UI + "issue.";

    // 模式
    private enum Mode { LIST, EDIT }

    // 编辑页签
    private enum Tab { INFO, SOURCE, CONDITIONS, EFFECTS }

    private final EditorWorkspaceView workspace;
    private final RuleEditorModel model;
    private final UiFocusManager focus = new UiFocusManager();
    private final UiModalStack modals = new UiModalStack();

    // 当前模式与页签
    private Mode mode = Mode.LIST;
    private Tab tab = Tab.INFO;
    // 状态提示（保存回执/校验失败等）
    private @Nullable Component notice;
    private int noticeColor = UiPalette.TEXT_SECONDARY;
    // 已消费的回执序号
    private long seenResultSeq;

    // ---- 管理页 ----
    private @Nullable UiTextInput searchField;
    private @Nullable UiSegmentedControl filterControl;
    private @Nullable UiListView<String> ruleList;
    private final List<String> visibleIds = new ArrayList<>();
    private final List<UiButton> listButtons = new ArrayList<>();
    private final ButtonBar listBar = new ButtonBar();
    private String lastQuery = "";
    private int filterIndex;

    // ---- 编辑页 ----
    private @Nullable String editingId;
    private @Nullable FormView infoForm;
    private @Nullable FormView sourceForm;
    private @Nullable FormView conditionsForm;
    private @Nullable FormView effectForm;
    private @Nullable UiListView<Integer> effectList;
    private @Nullable UiSegmentedControl tabControl;
    private final List<Integer> effectIndexes = new ArrayList<>();
    private int effectSelection = -1;
    private final List<UiButton> footerButtons = new ArrayList<>();
    private final ButtonBar footerBar = new ButtonBar();
    private final List<UiButton> effectButtons = new ArrayList<>();
    private final ButtonBar effectBar = new ButtonBar();

    // ---- 效果类型选择浮层 ----
    private boolean pickerOpen;
    private int pickerIndex;
    private int pickerOffset;
    private UiRect pickerRect = new UiRect(0, 0, 0, 0);
    private final List<TypeEditorDescriptor> pickerTypes = new ArrayList<>();

    // ---- 叶子参数弹窗 ----
    private @Nullable UiModal leafModal;
    private @Nullable FormView leafForm;

    // ---- P6：草稿落盘与冲突 ----
    // 「应用全部」按钮（无改动或会话冻结时禁用）
    private @Nullable UiButton applyButton;
    // 撤销 / 重做按钮（显示下一次操作名，无历史时禁用）
    private @Nullable UiButton undoButton;
    private @Nullable UiButton redoButton;
    // 已弹过冲突窗的目标 id（避免每帧重复弹；解决后移除）
    private final Set<String> promptedConflicts = new LinkedHashSet<>();
    // 上一帧的可编辑状态（冻结切换时刷新表单使能）
    private boolean lastEditable = true;
    // 冻结提示只提示一次
    private boolean frozenNotified;

    // 草稿被修改后需要在下一帧刷新列表
    private boolean listNeedsRefresh;
    // 注册表候选建议（惰性创建；创建失败时退化为无建议）
    private @Nullable SuggestionProvider catalogSuggestions;

    // 内容区（每帧重算，供命中测试复用）
    private UiRect contentArea = new UiRect(0, 0, 0, 0);

    // 构造：由 {@link RuleEditorOpener} 在服务端授权成功后调用
    public RuleEditorScreen(EditorWorkspaceView workspace) {
        super(Component.translatable(UI + "title"));
        BuiltinEditorDescriptors.bootstrap();
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.model = new RuleEditorModel(workspace);
        this.seenResultSeq = workspace.resultSeq();
        this.model.refresh();
    }

    // 编辑器不做游戏内暂停
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 初始化 ----

    @Override
    protected void init() {
        super.init();
        searchField = new UiTextInput(font, Component.translatable(UI + "list.search"));
        searchField.setMaxLength(128);
        filterControl = new UiSegmentedControl(font, filterOptions());
        filterControl.setOnChanged(value -> {
            filterIndex = indexOfFilter(value);
            refreshList();
        });
        ruleList = new UiListView<>(font, this::renderRuleRow);
        ruleList.setRowHeight(ROW_H);
        ruleList.setEmptyMessage(Component.translatable(UI + "list.empty"));
        ruleList.setOnActivate(this::openEditor);
        tabControl = new UiSegmentedControl(font, tabOptions());
        tabControl.setOnChanged(value -> switchTab(value));
        effectList = new UiListView<>(font, this::renderEffectRow);
        effectList.setRowHeight(ROW_H);
        effectList.setEmptyMessage(Component.translatable(UI + "effect.empty"));
        effectList.setOnSelectionChanged(index -> selectEffect(index));
        buildListButtons();
        buildFooterButtons();
        buildEffectButtons();
        refreshList();
        rebuildEdit();
        rebuildFocus();
    }

    private List<UiSegmentedControl.Option> filterOptions() {
        return List.of(
                new UiSegmentedControl.Option("all", Component.translatable(UI + "filter.all")),
                new UiSegmentedControl.Option("enabled", Component.translatable(UI + "filter.enabled")),
                new UiSegmentedControl.Option("disabled", Component.translatable(UI + "filter.disabled")),
                new UiSegmentedControl.Option("issue", Component.translatable(UI + "filter.issue")));
    }

    private int indexOfFilter(String value) {
        for (int i = 0; i < 4; i++) {
            if (filterOptions().get(i).value().equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private List<UiSegmentedControl.Option> tabOptions() {
        List<UiSegmentedControl.Option> options = new ArrayList<>();
        for (Tab value : Tab.values()) {
            options.add(new UiSegmentedControl.Option(value.name().toLowerCase(Locale.ROOT),
                    Component.translatable(UI + "tab." + value.name().toLowerCase(Locale.ROOT))));
        }
        return options;
    }

    private void switchTab(String value) {
        Tab target = tab;
        for (Tab candidate : Tab.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                target = candidate;
            }
        }
        if (target == tab) {
            return;
        }
        applyAllForms();
        tab = target;
        rebuildEdit();
        rebuildFocus();
    }

    // ---- 按钮 ----

    private void buildListButtons() {
        listButtons.clear();
        listButtons.add(button(UI + "button.new", UiButtonVariant.PRIMARY, this::promptNewRule));
        listButtons.add(button(UI + "button.template", UiButtonVariant.SECONDARY, this::openTemplatePicker));
        listButtons.add(button(UI + "button.duplicate", UiButtonVariant.SECONDARY, this::promptDuplicate));
        listButtons.add(button(UI + "button.edit", UiButtonVariant.PRIMARY, () -> {
            String id = selectedRuleId();
            if (id != null) {
                openEditor(id);
            }
        }));
        listButtons.add(button(UI + "button.toggle", UiButtonVariant.SECONDARY, this::toggleSelectedEnabled));
        listButtons.add(button(UI + "button.mask", UiButtonVariant.SECONDARY, this::maskSelected));
        listButtons.add(button(UI + "button.restore", UiButtonVariant.SECONDARY, this::restoreSelected));
        listButtons.add(button(UI + "button.delete", UiButtonVariant.DANGER, this::deleteSelected));
        this.applyButton = button(UI + "button.apply", UiButtonVariant.PRIMARY, this::save);
        listButtons.add(this.applyButton);
        listBar.set(listButtons);
    }

    private void buildFooterButtons() {
        footerButtons.clear();
        footerButtons.add(button(UI + "button.save", UiButtonVariant.PRIMARY, this::save));
        this.undoButton = button(UI + "button.undo", UiButtonVariant.SECONDARY, this::undo);
        this.redoButton = button(UI + "button.redo", UiButtonVariant.SECONDARY, this::redo);
        footerButtons.add(this.undoButton);
        footerButtons.add(this.redoButton);
        footerButtons.add(button(UI + "button.back", UiButtonVariant.SECONDARY, this::backToList));
        footerBar.set(footerButtons);
    }

    private void buildEffectButtons() {
        effectButtons.clear();
        effectButtons.add(button(UI + "button.effect_add", UiButtonVariant.PRIMARY, this::openEffectPicker));
        effectButtons.add(button(UI + "button.effect_remove", UiButtonVariant.DANGER, this::removeEffect));
        effectButtons.add(button(UI + "button.effect_up", UiButtonVariant.SECONDARY, () -> moveEffect(-1)));
        effectButtons.add(button(UI + "button.effect_down", UiButtonVariant.SECONDARY, () -> moveEffect(1)));
        effectBar.set(effectButtons);
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(font, Component.translatable(labelKey), variant, action::run);
    }

    // ---- 渲染 ----

    // 每帧重算布局并绘制
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int w = this.width;
        int h = this.height;
        modals.setBounds(0, 0, w, h);
        UiTheme.drawWindow(graphics, new UiRect(0, 0, w, h));
        UiTheme.drawDivider(graphics, 0, HEADER_H, w);
        drawHeaderText(graphics);
        int y = HEADER_H + 2;
        if (mode == Mode.EDIT && tabControl != null) {
            tabControl.setBounds(PAD, y, Math.min(Math.max(0, w - PAD * 2), 240), TAB_H);
            tabControl.render(graphics, font, mouseX, mouseY);
            y += TAB_H + 2;
        }
        int reserved = NOTICE_H + (mode == Mode.EDIT ? SUMMARY_H : 0);
        int noticeY = y;
        int bottomLimit = h - PAD - FOOTER_H - 1;
        if (bottomLimit - reserved > y) {
            noticeY = bottomLimit - NOTICE_H;
        }
        int summaryY = noticeY - SUMMARY_H;
        contentArea = new UiRect(PAD, y, Math.max(0, w - PAD * 2), Math.max(0, summaryY - y - 1));
        if (mode == Mode.LIST) {
            renderListMode(graphics, mouseX, mouseY);
        } else {
            renderEditMode(graphics, mouseX, mouseY);
            drawSummary(graphics, summaryY, w);
        }
        drawNotice(graphics, noticeY, w);
        int footerY = h - PAD - FOOTER_H;
        if (mode == Mode.LIST) {
            listBar.layout(PAD, footerY, Math.max(0, w - PAD * 2), FOOTER_H);
            listBar.render(graphics, font, mouseX, mouseY);
        } else {
            if (!footerButtons.isEmpty()) {
                footerButtons.get(0).setEnabled(workspace.active());
            }
            footerBar.layout(PAD, footerY, Math.max(0, w - PAD * 2), FOOTER_H);
            footerBar.render(graphics, font, mouseX, mouseY);
        }
        modals.render(graphics, font, mouseX, mouseY);
        if (pickerOpen) {
            renderPicker(graphics, mouseX, mouseY);
        }
        // 字段说明提示：没有弹窗或选择器时才显示，避免盖住上层内容
        if (modals.isEmpty() && !pickerOpen && mode == Mode.EDIT) {
            Component tip = activeFormTooltip(mouseX, mouseY);
            if (tip != null) {
                graphics.renderTooltip(font, tip, mouseX, mouseY);
            }
        }
    }

    // 当前可见表单中鼠标所指字段的说明（无则返回 null）
    private @Nullable Component activeFormTooltip(double mouseX, double mouseY) {
        FormView[] candidates = {infoForm, sourceForm, conditionsForm, effectForm};
        for (FormView candidate : candidates) {
            if (candidate == null || !candidate.isVisible()) {
                continue;
            }
            Component tip = candidate.tooltipAt(mouseX, mouseY);
            if (tip != null) {
                return tip;
            }
        }
        return null;
    }

    // 顶部标题与状态
    private void drawHeaderText(GuiGraphics graphics) {
        String title = this.getTitle().getString();
        if (mode == Mode.EDIT && editingId != null) {
            title = title + " - " + editingId;
        }
        title = TextScroll.trimToWidth(font, title, Math.max(0, this.width - 96));
        graphics.drawString(font, title, PAD, 3, UiPalette.HEADER_TEXT, false);
        String state = Component.translatable(workspace.active() ? UI + "state.active" : UI + "state.freezing")
                .getString();
        graphics.drawString(font, state, this.width - PAD - font.width(state), 3, UiPalette.HEADER_TEXT, false);
    }

    // 底部状态提示
    private void drawNotice(GuiGraphics graphics, int y, int w) {
        if (notice == null) {
            return;
        }
        String text = TextScroll.trimToWidth(font, notice.getString(), Math.max(0, w - PAD * 2));
        graphics.drawString(font, text, PAD, y + 1, noticeColor, false);
    }

    // 自然语言摘要（条件/效果页签的一行摘要）与消耗语义提示（效果页签）
    private void drawSummary(GuiGraphics graphics, int y, int w) {
        EditSession session = editingSession();
        if (session == null) {
            return;
        }
        Component text = tab == Tab.EFFECTS
                ? ConsumptionSummary.hint(session.draft().view().get(RuleFields.EFFECTS))
                : NaturalSummary.rule(session.draft().view());
        if (text == null || text.getString().isEmpty()) {
            return;
        }
        String trimmed = TextScroll.trimToWidth(font, text.getString(), Math.max(0, w - PAD * 2));
        graphics.drawString(font, trimmed, PAD, y + 1, UiPalette.TEXT_SECONDARY, false);
    }

    // 管理页布局
    private void renderListMode(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect area = contentArea;
        int searchWidth = Math.min(160, area.width());
        if (searchField != null) {
            searchField.setBounds(area.x(), area.y(), searchWidth, ROW_H);
            searchField.render(graphics, font, mouseX, mouseY);
        }
        if (filterControl != null) {
            filterControl.setBounds(area.x() + searchWidth + 2, area.y(),
                    Math.max(0, area.width() - searchWidth - 2), ROW_H);
            filterControl.render(graphics, font, mouseX, mouseY);
        }
        int barHeight = listBar.preferredHeight(area.width());
        int listY = area.y() + ROW_H + 2;
        int listHeight = Math.max(0, area.height() - ROW_H - 2 - barHeight - 2);
        if (ruleList != null) {
            ruleList.setBounds(area.x(), listY, area.width(), listHeight);
            ruleList.render(graphics, font, mouseX, mouseY);
        }
        listBar.layout(area.x(), listY + listHeight + 2, area.width(), barHeight);
    }

    // 编辑页布局：效果页签宽屏为左列表 + 右表单，窄屏上下堆叠；其余页签表单独占
    private void renderEditMode(GuiGraphics graphics, int mouseX, int mouseY) {
        int w = contentArea.width();
        int h = contentArea.height();
        if (tab == Tab.EFFECTS) {
            boolean wide = w >= NARROW_WIDTH && w - Math.min(150, w / 3) - 6 >= 200;
            int listWidth = wide ? Math.min(150, w / 3) : w;
            int listHeight = wide ? Math.max(0, h - FOOTER_H - 2) : Math.min(Math.max(0, h / 3), 60);
            if (effectList != null) {
                effectList.setBounds(contentArea.x(), contentArea.y(), listWidth, listHeight);
                effectList.render(graphics, font, mouseX, mouseY);
            }
            effectBar.layout(contentArea.x(), contentArea.y() + listHeight + 2, listWidth, FOOTER_H);
            effectBar.render(graphics, font, mouseX, mouseY);
            if (effectForm != null) {
                int formX = wide ? contentArea.x() + listWidth + 4 : contentArea.x();
                int formY = wide ? contentArea.y() : contentArea.y() + listHeight + FOOTER_H + 5;
                int formWidth = wide ? Math.max(0, w - listWidth - 4) : w;
                int formHeight = Math.max(0, contentArea.y() + h - formY);
                effectForm.setBounds(formX, formY, formWidth, formHeight);
                effectForm.render(graphics, font, mouseX, mouseY);
            }
        } else {
            FormView form = activeForm();
            if (form != null) {
                form.setBounds(contentArea.x(), contentArea.y(), w, h);
                form.render(graphics, font, mouseX, mouseY);
            }
        }
    }

    // 当前页签对应的表单
    private @Nullable FormView activeForm() {
        return switch (tab) {
            case INFO -> infoForm;
            case SOURCE -> sourceForm;
            case CONDITIONS -> conditionsForm;
            case EFFECTS -> effectForm;
        };
    }

    // 规则列表行
    private void renderRuleRow(GuiGraphics graphics, Font rowFont, String id, int index, UiRect row,
            boolean selected, boolean hovered, boolean focused) {
        RuleSnapshotEntry entry = model.entry(id);
        String marker = statusMark(entry);
        int color = UiPalette.TEXT_PRIMARY;
        if (entry != null) {
            if (RuleSnapshotEntry.STATUS_DISABLED.equals(entry.status())
                    || RuleSnapshotEntry.STATUS_MASKED.equals(entry.status())) {
                color = UiPalette.TEXT_DISABLED;
            } else if (!entry.editable()) {
                color = UiPalette.TEXT_SECONDARY;
            }
        }
        EditSession session = model.session(id);
        if (session != null && session.isDirty()) {
            marker = marker + Component.translatable(UI + "list.dirty").getString();
        }
        String text = TextScroll.trimToWidth(rowFont, marker + id, Math.max(0, row.width() - 4));
        graphics.drawString(rowFont, text, row.x() + 2, row.y() + 2, color, false);
        String label = labelText(entry);
        if (!label.isEmpty()) {
            String trimmed = TextScroll.trimToWidth(rowFont, label, Math.max(0, row.width() / 2));
            graphics.drawString(rowFont, trimmed, row.x() + 4 + rowFont.width(text), row.y() + 2,
                    UiPalette.TEXT_SECONDARY, false);
        }
        if (model.hasError(id)) {
            String tag = Component.translatable(UI + "list.error").getString();
            graphics.drawString(rowFont, tag, row.right() - 2 - rowFont.width(tag), row.y() + 2,
                    UiPalette.DANGER, false);
        }
    }

    // 状态文字标记（颜色必须配文字）
    private String statusMark(@Nullable RuleSnapshotEntry entry) {
        if (entry == null) {
            return "[*] ";
        }
        return switch (entry.status()) {
            case RuleSnapshotEntry.STATUS_ACTIVE -> "[+] ";
            case RuleSnapshotEntry.STATUS_DISABLED -> "[-] ";
            case RuleSnapshotEntry.STATUS_MASKED -> "[x] ";
            case RuleSnapshotEntry.STATUS_INVALID -> "[!] ";
            default -> "[?] ";
        };
    }

    // 效果列表行
    private void renderEffectRow(GuiGraphics graphics, Font rowFont, Integer effectIndex, int index, UiRect row,
            boolean selected, boolean hovered, boolean focused) {
        int position = effectIndexes.indexOf(effectIndex);
        String prefix = (position >= 0 ? position + 1 : index + 1) + ". ";
        JsonObject effect = draftEffect(effectIndex);
        Component typeLabel = Component.translatable(UI + "effect.unknown");
        String extra = "";
        if (effect != null) {
            JsonElement typeElement = effect.get(RuleFields.TYPE);
            if (typeElement != null && typeElement.isJsonPrimitive()) {
                ResourceLocation typeId = ResourceLocation.tryParse(typeElement.getAsString());
                typeLabel = typeId == null
                        ? Component.literal(typeElement.getAsString())
                        : TypeLabels.effectLabel(typeId);
            }
            extra = effectSummary(effect);
        }
        String text = TextScroll.trimToWidth(rowFont, prefix + typeLabel.getString() + extra,
                Math.max(0, row.width() - 4));
        graphics.drawString(rowFont, text, row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false);
    }

    // 效果摘要：非 100% 概率与延迟
    private String effectSummary(JsonObject effect) {
        StringBuilder builder = new StringBuilder();
        JsonElement chance = effect.get(RuleFields.CHANCE);
        if (chance != null && chance.isJsonPrimitive() && chance.getAsJsonPrimitive().isNumber()) {
            double value = chance.getAsDouble();
            if (Math.abs(value - 1.0D) > 1.0E-6D) {
                builder.append("  ").append(String.format(Locale.ROOT, "%.0f%%", value * 100.0D));
            }
        }
        JsonElement delay = effect.get(RuleFields.DELAY_TICKS);
        if (delay != null && delay.isJsonPrimitive() && delay.getAsJsonPrimitive().isNumber()) {
            int ticks = delay.getAsInt();
            if (ticks > 0) {
                builder.append("  ").append(Component.translatable(UI + "effect.delay_summary", ticks).getString());
            }
        }
        return builder.toString();
    }

    // 效果类型选择浮层
    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = pickerRows();
        int width = Math.min(Math.max(160, this.width / 2), Math.max(120, this.width - PAD * 2));
        int height = PICKER_HEADER + rows * ROW_H + PICKER_FOOTER;
        int x = Math.max(0, (this.width - width) / 2);
        int y = Math.max(0, (this.height - height) / 2);
        pickerRect = new UiRect(x, y, width, height);
        graphics.fill(0, 0, this.width, this.height, UiPalette.MODAL_DIM);
        UiTheme.drawWindow(graphics, pickerRect);
        UiTheme.drawDivider(graphics, x + 1, y + PICKER_HEADER - 1, width - 2);
        String title = Component.translatable(UI + "pick.type_title").getString();
        graphics.drawString(font, TextScroll.trimToWidth(font, title, width - PAD * 2), x + PAD, y + 2,
                UiPalette.TEXT_PRIMARY, false);
        UiRect list = pickerListRect();
        for (int i = 0; i < rows; i++) {
            int itemIndex = pickerOffset + i;
            if (itemIndex >= pickerTypes.size()) {
                break;
            }
            UiRect row = new UiRect(list.x(), list.y() + i * ROW_H, list.width(), ROW_H);
            if (itemIndex == pickerIndex) {
                UiTheme.drawSelection(graphics, row);
            } else if (row.contains(mouseX, mouseY)) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), UiPalette.CONTROL_HOVER);
            }
            String label = TextScroll.trimToWidth(font, pickerTypes.get(itemIndex).label().getString(),
                    Math.max(0, row.width() - 4));
            graphics.drawString(font, label, row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false);
        }
        String hint = Component.translatable(UI + "pick.type_hint").getString();
        graphics.drawString(font, TextScroll.trimToWidth(font, hint, width - PAD * 2), x + PAD,
                y + height - PICKER_FOOTER + 1, UiPalette.TEXT_SECONDARY, false);
    }

    private int pickerRows() {
        return Math.max(1, Math.min(pickerTypes.size(), PICKER_MAX_ROWS));
    }

    private UiRect pickerListRect() {
        return new UiRect(pickerRect.x() + 2, pickerRect.y() + PICKER_HEADER, Math.max(0, pickerRect.width() - 4),
                pickerRows() * ROW_H);
    }

    private void closePicker() {
        pickerOpen = false;
        pickerOffset = 0;
    }

    private void confirmPicker() {
        if (!pickerOpen || pickerTypes.isEmpty()) {
            closePicker();
            return;
        }
        int index = Math.max(0, Math.min(pickerIndex, pickerTypes.size() - 1));
        ResourceLocation typeId = pickerTypes.get(index).id();
        closePicker();
        addEffect(typeId);
    }

    // 浮层点击：列表项确认、外部关闭
    private boolean handlePickerClick(double mouseX, double mouseY) {
        UiRect list = pickerListRect();
        if (list.contains(mouseX, mouseY)) {
            int row = (int) ((mouseY - list.y()) / ROW_H);
            int itemIndex = pickerOffset + row;
            if (itemIndex >= 0 && itemIndex < pickerTypes.size()) {
                pickerIndex = itemIndex;
                confirmPicker();
            }
            return true;
        }
        if (!pickerRect.contains(mouseX, mouseY)) {
            closePicker();
        }
        return true;
    }

    // 浮层键盘
    private boolean handlePickerKey(int keyCode) {
        int rows = pickerRows();
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> pickerIndex = Math.max(0, pickerIndex - 1);
            case GLFW.GLFW_KEY_DOWN -> pickerIndex = Math.min(pickerTypes.size() - 1, pickerIndex + 1);
            case GLFW.GLFW_KEY_PAGE_UP -> pickerIndex = Math.max(0, pickerIndex - rows);
            case GLFW.GLFW_KEY_PAGE_DOWN -> pickerIndex = Math.min(pickerTypes.size() - 1, pickerIndex + rows);
            case GLFW.GLFW_KEY_HOME -> pickerIndex = 0;
            case GLFW.GLFW_KEY_END -> pickerIndex = Math.max(0, pickerTypes.size() - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                confirmPicker();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                closePicker();
                return true;
            }
            default -> {
                return true;
            }
        }
        scrollPickerIntoView(rows);
        return true;
    }

    private void scrollPickerIntoView(int rows) {
        if (pickerIndex < pickerOffset) {
            pickerOffset = pickerIndex;
        } else if (pickerIndex >= pickerOffset + rows) {
            pickerOffset = pickerIndex - rows + 1;
        }
        pickerOffset = Math.max(0, Math.min(pickerOffset, Math.max(0, pickerTypes.size() - rows)));
    }

    // ---- 列表维护 ----

    private void refreshList() {
        if (ruleList == null) {
            return;
        }
        visibleIds.clear();
        String query = lastQuery == null ? "" : lastQuery.toLowerCase(Locale.ROOT).trim();
        for (String id : model.ruleIds()) {
            RuleSnapshotEntry entry = model.entry(id);
            if (entry != null && !matchesFilter(entry)) {
                continue;
            }
            if (!query.isEmpty()
                    && !id.toLowerCase(Locale.ROOT).contains(query)
                    && !labelText(entry).toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            visibleIds.add(id);
        }
        for (String id : model.dirtyRuleIds()) {
            if (model.isCreated(id) && !visibleIds.contains(id)) {
                visibleIds.add(id);
            }
        }
        ruleList.setItems(new ArrayList<>(visibleIds));
        if (ruleList.size() > 0) {
            int selected = ruleList.selectedIndex();
            if (selected < 0 || selected >= ruleList.size()) {
                ruleList.setSelectedIndex(0);
            }
        }
    }

    private boolean matchesFilter(RuleSnapshotEntry entry) {
        return switch (filterIndex) {
            case 1 -> RuleSnapshotEntry.STATUS_ACTIVE.equals(entry.status());
            case 2 -> !RuleSnapshotEntry.STATUS_ACTIVE.equals(entry.status());
            case 3 -> !entry.issues().isEmpty() || model.hasError(entry.id().toString());
            default -> true;
        };
    }

    private String labelText(@Nullable RuleSnapshotEntry entry) {
        if (entry == null || entry.effective() == null) {
            return "";
        }
        JsonElement name = entry.effective().get(RuleFields.DISPLAY_NAME);
        return name != null && name.isJsonPrimitive() ? name.getAsString() : "";
    }

    private @Nullable String selectedRuleId() {
        return ruleList == null ? null : ruleList.selectedItem();
    }

    // 搜索框内容变化后重新过滤
    private void syncSearch() {
        if (searchField == null) {
            return;
        }
        String value = searchField.value();
        if (!value.equals(lastQuery)) {
            lastQuery = value;
            refreshList();
        }
    }

    // ---- 草稿与表单 ----

    private @Nullable EditSession editingSession() {
        return editingId == null ? null : model.session(editingId);
    }

    private @Nullable JsonObject draftEffect(int index) {
        EditSession session = editingSession();
        if (session == null) {
            return null;
        }
        List<JsonObject> effects = session.draft().effects();
        if (index < 0 || index >= effects.size()) {
            return null;
        }
        return effects.get(index);
    }

    private void rebuildEdit() {
        EditSession session = editingSession();
        if (mode != Mode.EDIT || session == null) {
            infoForm = null;
            sourceForm = null;
            conditionsForm = null;
            effectForm = null;
            effectIndexes.clear();
            effectSelection = -1;
            return;
        }
        infoForm = new FormView(font, session, "");
        infoForm.setDescriptor(BuiltinEditorDescriptors.ruleDescriptor());
        infoForm.setOnChanged(this::onDraftChanged);
        infoForm.reload();
        sourceForm = new FormView(font, session, RuleFields.SOURCE);
        sourceForm.setDescriptor(BuiltinEditorDescriptors.sourceDescriptor());
        sourceForm.setSuggestionProvider(suggestionProvider());
        sourceForm.setOnChanged(this::onDraftChanged);
        sourceForm.reload();
        conditionsForm = new FormView(font, session, "");
        conditionsForm.setDescriptor(conditionDescriptor());
        conditionsForm.setSuggestionProvider(suggestionProvider());
        conditionsForm.setConditionSupport(pageConditionSupport());
        conditionsForm.setOnChanged(this::onDraftChanged);
        conditionsForm.reload();
        refreshEffectList();
        selectEffect(effectSelection >= 0 ? effectSelection : (effectIndexes.isEmpty() ? -1 : 0));
        // 表单是刚重建的，按当前工作区状态同步一次使能（APPLYING 期间必须冻结）
        lastEditable = workspace.active();
        applyEditableStateNow(lastEditable);
    }

    // 只含条件树一个字段的页签描述符
    private TypeEditorDescriptor conditionDescriptor() {
        return TypeEditorDescriptor.of(ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "conditions_page"),
                Component.translatable(UI + "tab.conditions"),
                List.of(EditorField.conditionTree(RuleFields.CONDITIONS, UI + "rule.conditions")));
    }

    private void refreshEffectList() {
        EditSession session = editingSession();
        effectIndexes.clear();
        if (session != null) {
            int count = session.draft().effectCount();
            for (int i = 0; i < count; i++) {
                effectIndexes.add(i);
            }
        }
        if (effectSelection >= effectIndexes.size()) {
            effectSelection = effectIndexes.size() - 1;
        }
        if (effectList != null) {
            effectList.setItems(new ArrayList<>(effectIndexes));
            if (effectList.size() > 0) {
                effectList.setSelectedIndex(Math.max(0, effectSelection));
            }
        }
    }

    // 选中某个效果并为其构建表单（含未注册类型的只读回退）
    private void selectEffect(int index) {
        effectSelection = index;
        EditSession session = editingSession();
        if (session == null || index < 0) {
            effectForm = null;
            return;
        }
        String type = effectType(session.draft(), index);
        if (type == null) {
            effectForm = null;
            return;
        }
        ResourceLocation typeId = ResourceLocation.tryParse(type);
        if (typeId == null) {
            typeId = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "unknown");
        }
        TypeEditorDescriptor descriptor = EffectEditorRegistry.descriptorFor(typeId);
        FormView form = new FormView(font, session, "effects[" + index + "]");
        form.setDescriptor(descriptor);
        form.setSuggestionProvider(suggestionProvider());
        form.setConditionSupport(pageConditionSupport());
        form.setOnChanged(this::onDraftChanged);
        form.reload();
        effectForm = form;
    }

    private @Nullable String effectType(RuleDraft draft, int index) {
        List<JsonObject> effects = draft.effects();
        if (index < 0 || index >= effects.size()) {
            return null;
        }
        JsonElement element = effects.get(index).get(RuleFields.TYPE);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private void applyAllForms() {
        if (infoForm != null) {
            infoForm.applyToDraft();
        }
        if (sourceForm != null) {
            sourceForm.applyToDraft();
        }
        if (conditionsForm != null) {
            conditionsForm.applyToDraft();
        }
        if (effectForm != null) {
            effectForm.applyToDraft();
        }
    }

    private void onDraftChanged() {
        listNeedsRefresh = true;
    }

    // 建议提供者（注册表候选），构造失败时降级为无建议
    private SuggestionProvider suggestionProvider() {
        if (catalogSuggestions == null) {
            try {
                catalogSuggestions = new CatalogSuggestions();
            } catch (RuntimeException | LinkageError error) {
                catalogSuggestions = null;
            }
        }
        return catalogSuggestions == null ? field -> List.of() : catalogSuggestions;
    }

    // 条件树宿主能力（页签与效果级条件共用）
    private ConditionSupport pageConditionSupport() {
        return new ConditionSupport() {
            @Override
            public List<UiConditionTreeEditor.TypeOption> typeOptions() {
                List<UiConditionTreeEditor.TypeOption> options = new ArrayList<>();
                for (TypeEditorDescriptor descriptor : ConditionEditorRegistry.all()) {
                    options.add(new UiConditionTreeEditor.TypeOption(descriptor.id(), descriptor.label()));
                }
                return options;
            }

            @Override
            public UiConditionTreeEditor.LeafFactory leafFactory() {
                return ClientTypeRegistries::leafNode;
            }

            @Override
            public Consumer<ConditionNode.Leaf> onEditLeaf() {
                return RuleEditorScreen.this::openLeafEditor;
            }

            @Override
            public TypeRegistry<ConditionType<?>> registry() {
                return ClientTypeRegistries.conditions();
            }
        };
    }

    // ---- 模式切换 ----

    private void openEditor(String id) {
        if (!canEdit()) {
            return;
        }
        if (id == null) {
            return;
        }
        applyAllForms();
        editingId = id;
        mode = Mode.EDIT;
        tab = Tab.INFO;
        effectSelection = -1;
        closePicker();
        if (tabControl != null) {
            tabControl.setSelected("info");
        }
        rebuildEdit();
        rebuildFocus();
    }

    private void backToList() {
        applyAllForms();
        mode = Mode.LIST;
        editingId = null;
        infoForm = null;
        sourceForm = null;
        conditionsForm = null;
        effectForm = null;
        effectIndexes.clear();
        effectSelection = -1;
        closePicker();
        refreshList();
        rebuildFocus();
    }

    // ---- 管理页动作 ----

    private void promptNewRule() {
        promptNewRule(null);
    }

    private void promptNewRule(@Nullable String templateId) {
        if (!canEdit()) {
            return;
        }
        promptText(UI + "prompt.new_title", UI + "prompt.id_hint", "itemdespawntowhat:new_rule",
                value -> createRule(value, templateId));
    }

    private void createRule(String value, @Nullable String templateId) {
        if (!canEdit()) {
            return;
        }
        ResourceLocation id = normalizeId(value);
        if (id == null) {
            setNotice(Component.translatable(UI + "notice.invalid_id"), UiPalette.DANGER);
            return;
        }
        String key = id.toString();
        if (model.entry(key) != null || model.isCreated(key)) {
            setNotice(Component.translatable(UI + "notice.duplicate_id"), UiPalette.DANGER);
            return;
        }
        JsonObject body;
        if (templateId == null) {
            body = BuiltinEditorDefaults.ruleBody(key);
        } else {
            JsonObject loaded = RuleTemplateHooks.load(ResourceLocation.tryParse(templateId));
            if (loaded == null) {
                setNotice(Component.translatable(UI + "notice.template_failed"), UiPalette.DANGER);
                return;
            }
            body = BuiltinEditorDefaults.copyOf(key, loaded);
        }
        model.createSession(key, body);
        refreshList();
        openEditor(key);
        setNotice(Component.translatable(UI + "notice.created"), UiPalette.SUCCESS);
    }

    private void promptDuplicate() {
        if (!canEdit()) {
            return;
        }
        String sourceId = selectedRuleId();
        if (sourceId == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(sourceId);
        if (session == null) {
            noSelection();
            return;
        }
        JsonObject body = session.draft().toJson();
        String candidate = sourceId + "_copy";
        int suffix = 2;
        while (model.entry(candidate) != null || model.isCreated(candidate)) {
            candidate = sourceId + "_copy" + suffix;
            suffix++;
        }
        promptText(UI + "prompt.duplicate_title", UI + "prompt.id_hint", candidate, value -> {
            // 弹窗期间可能已被冻结（应用在途 / 会话结束），提交前再确认一次
            if (!canEdit()) {
                return;
            }
            ResourceLocation id = normalizeId(value);
            if (id == null) {
                setNotice(Component.translatable(UI + "notice.invalid_id"), UiPalette.DANGER);
                return;
            }
            String key = id.toString();
            if (model.entry(key) != null || model.isCreated(key)) {
                setNotice(Component.translatable(UI + "notice.duplicate_id"), UiPalette.DANGER);
                return;
            }
            model.createSession(key, BuiltinEditorDefaults.copyOf(key, body));
            refreshList();
            openEditor(key);
            setNotice(Component.translatable(UI + "notice.created"), UiPalette.SUCCESS);
        });
    }

    // 从内置模板新建
    private void openTemplatePicker() {
        if (!canEdit()) {
            return;
        }
        List<Suggestion> templates = RuleTemplateHooks.list();
        if (templates.isEmpty()) {
            setNotice(Component.translatable(UI + "notice.template_empty"), UiPalette.WARNING);
            return;
        }
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "prompt.template_title"));
        UiListView<Suggestion> view = new UiListView<>(font, this::renderTemplateRow);
        view.setItems(templates);
        view.setRowHeight(ROW_H);
        view.setEmptyMessage(Component.translatable(UI + "list.empty"));
        view.setOnActivate(suggestion -> {
            modals.closeTop();
            promptNewRule(suggestion.value());
        });
        int height = Math.min(140, Math.max(ROW_H, templates.size() * ROW_H));
        modal.contentWidget(view, height);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    private void renderTemplateRow(GuiGraphics graphics, Font rowFont, Suggestion item, int index, UiRect row,
            boolean selected, boolean hovered, boolean focused) {
        String label = TextScroll.trimToWidth(rowFont, item.label().getString(), Math.max(0, row.width() - 4));
        graphics.drawString(rowFont, label, row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false);
    }

    private void toggleSelectedEnabled() {
        if (!canEdit()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        RuleDraft draft = session.draft();
        boolean enabled = !draft.getBoolean(RuleFields.ENABLED, true);
        session.apply(EditSession.OP_SET_FIELD, () -> draft.setBoolean(RuleFields.ENABLED, enabled));
        refreshList();
        setNotice(Component.translatable(enabled ? UI + "notice.enabled" : UI + "notice.disabled"), UiPalette.SUCCESS);
    }

    private void maskSelected() {
        if (!canEdit()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        RuleDraft draft = session.draft();
        boolean masked = !draft.getBoolean(RuleFields.DELETE, false);
        session.apply(EditSession.OP_SET_FIELD, () -> draft.setBoolean(RuleFields.DELETE, masked));
        refreshList();
        setNotice(Component.translatable(masked ? UI + "notice.masked" : UI + "notice.unmasked"), UiPalette.SUCCESS);
    }

    private void restoreSelected() {
        if (!canEdit()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        // 走统一门面：「恢复原始版本」也要能撤销
        session.apply(EditSession.OP_RESTORE_ORIGINAL, () -> model.markRestore(id));
        refreshList();
        setNotice(Component.translatable(UI + "notice.restored"), UiPalette.SUCCESS);
    }

    private void deleteSelected() {
        if (!canEdit()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        if (model.isCreated(id)) {
            confirm(UI + "confirm.delete_title", UI + "confirm.delete_draft_message", () -> {
                if (!canEdit()) {
                    return;
                }
                model.dropSession(id);
                refreshList();
                setNotice(Component.translatable(UI + "notice.deleted"), UiPalette.SUCCESS);
            });
            return;
        }
        confirm(UI + "confirm.delete_title", UI + "confirm.delete_message", () -> {
            if (!canEdit()) {
                return;
            }
            model.openSession(id);
            model.markDeleted(id, true);
            refreshList();
            setNotice(Component.translatable(UI + "notice.deleted"), UiPalette.SUCCESS);
        });
    }

    // ---- 效果动作 ----

    private void openEffectPicker() {
        if (!canEdit()) {
            return;
        }
        if (editingSession() == null) {
            return;
        }
        applyAllForms();
        pickerTypes.clear();
        pickerTypes.addAll(EffectEditorRegistry.all());
        if (pickerTypes.isEmpty()) {
            return;
        }
        pickerIndex = Math.max(0, Math.min(pickerIndex, pickerTypes.size() - 1));
        pickerOffset = 0;
        pickerOpen = true;
    }

    private void addEffect(ResourceLocation type) {
        if (!canEdit()) {
            return;
        }
        EditSession session = editingSession();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        JsonObject body = BuiltinEditorDefaults.effectBody(type);
        session.apply(EditSession.OP_ADD_EFFECT, () -> draft.addEffect(body));
        effectSelection = draft.effectCount() - 1;
        refreshEffectList();
        if (effectList != null && effectSelection >= 0) {
            effectList.ensureVisible(effectSelection);
        }
        selectEffect(effectSelection);
        onDraftChanged();
    }

    private void removeEffect() {
        if (!canEdit()) {
            return;
        }
        EditSession session = editingSession();
        if (session == null || effectSelection < 0) {
            setNotice(Component.translatable(UI + "notice.no_effect"), UiPalette.WARNING);
            return;
        }
        int index = effectSelection;
        RuleDraft draft = session.draft();
        session.apply(EditSession.OP_REMOVE_EFFECT, () -> draft.removeEffect(index));
        effectSelection = Math.min(index, draft.effectCount() - 1);
        refreshEffectList();
        selectEffect(effectSelection);
        onDraftChanged();
    }

    private void moveEffect(int delta) {
        if (!canEdit()) {
            return;
        }
        EditSession session = editingSession();
        if (session == null || effectSelection < 0) {
            return;
        }
        int from = effectSelection;
        int to = from + delta;
        RuleDraft draft = session.draft();
        if (to < 0 || to >= draft.effectCount()) {
            return;
        }
        session.apply(EditSession.OP_MOVE_EFFECT, () -> draft.moveEffect(from, to));
        effectSelection = to;
        refreshEffectList();
        selectEffect(to);
        onDraftChanged();
    }

    // ---- 条件叶参数弹窗 ----

    private void openLeafEditor(ConditionNode.Leaf leaf) {
        EditSession session = editingSession();
        FormView owner = activeForm();
        if (session == null || owner == null) {
            return;
        }
        UiConditionTreeEditor tree = findTree(owner, leaf);
        String base = tree != null && tree.selectedPath() != null ? tree.selectedPath() : RuleFields.CONDITIONS;
        String path = base.endsWith("." + RuleFields.CONDITION) ? base : base + "." + RuleFields.CONDITION;
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.descriptorFor(leaf.condition().type());
        FormView form = new FormView(font, session, path);
        form.setDescriptor(descriptor);
        form.setSuggestionProvider(suggestionProvider());
        form.setOnChanged(this::onDraftChanged);
        form.reload();
        UiModal modal = UiModal.create(font);
        modal.title(descriptor.label());
        modal.contentWidget(form, Math.min(170, Math.max(36, descriptor.fields().size() * ROW_H + 8)));
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
            form.applyToDraft();
            owner.reload();
            onDraftChanged();
        });
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
        leafModal = modal;
        leafForm = form;
    }

    private @Nullable UiConditionTreeEditor findTree(FormView owner, ConditionNode.Leaf leaf) {
        for (UiFocusTarget target : owner.focusTargets()) {
            if (target instanceof UiConditionTreeEditor tree && tree.selectedNode() == leaf) {
                return tree;
            }
        }
        for (UiFocusTarget target : owner.focusTargets()) {
            if (target instanceof UiConditionTreeEditor tree) {
                return tree;
            }
        }
        return null;
    }

    // 把第一条问题对应的字段切到正确页签、滚到可见并聚焦
    private void revealIssue(FormIssue issue) {
        String path = issue.path();
        if (path == null || path.isBlank()) {
            return;
        }
        FormView target;
        if (path.startsWith(RuleFields.EFFECTS)) {
            tab = Tab.EFFECTS;
            rebuildEdit();
            int index = effectIndexFromPath(path);
            if (index >= 0) {
                selectEffect(index);
            }
            target = effectForm;
        } else if (path.startsWith(RuleFields.SOURCE)) {
            tab = Tab.SOURCE;
            rebuildEdit();
            target = sourceForm;
        } else if (path.startsWith(RuleFields.CONDITIONS)) {
            tab = Tab.CONDITIONS;
            rebuildEdit();
            target = conditionsForm;
        } else {
            tab = Tab.INFO;
            rebuildEdit();
            target = infoForm;
        }
        if (tabControl != null) {
            tabControl.setSelected(tab.name().toLowerCase(Locale.ROOT));
        }
        rebuildFocus();
        if (target == null) {
            return;
        }
        UiFocusTarget focusTarget = target.revealPath(path);
        if (focusTarget != null) {
            focus.focusOn(focusTarget);
        }
    }

    // 从 effects[i].xxx 路径解析效果下标；解析失败返回 -1
    private static int effectIndexFromPath(String path) {
        int open = path.indexOf('[');
        int close = path.indexOf(']', open + 1);
        if (open < 0 || close < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(open + 1, close));
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    // ---- 保存 / 撤销 ----

    private void save() {
        // F-I：先把当前草稿落盘（写盘本身有 2 秒节流），保证崩溃窗口内的最近编辑不丢
        model.persistNow();
        applyAllForms();
        List<FormIssue> issues = collectIssues();
        // 只有「本地可证非法」才阻塞保存；未注册第三方类型等提醒项不阻塞（服务端才是权威校验方）
        FormIssue blocker = null;
        for (FormIssue issue : issues) {
            if (issue.blocking()) {
                blocker = issue;
                break;
            }
        }
        if (blocker != null) {
            // 字段级问题带上字段名，玩家才知道该改哪里（label 与 message 同一实例时说明它本身就是说明文案）
            Component notice = blocker.label() == blocker.message()
                    ? blocker.message()
                    : Component.translatable(UI + "notice.issue", blocker.label(), blocker.message());
            setNotice(notice, UiPalette.DANGER);
            revealIssue(blocker);
            return;
        }
        String json = model.buildChangeSetJson();
        if (json == null) {
            setNotice(Component.translatable(UI + "notice.no_changes"), UiPalette.TEXT_SECONDARY);
            return;
        }
        if (!workspace.active()) {
            setNotice(Component.translatable(UI + "notice.frozen"), UiPalette.WARNING);
            return;
        }
        if (workspace.save(workspace.newOperationId(), json)) {
            setNotice(Component.translatable(UI + "notice.applying"), UiPalette.TEXT_SECONDARY);
            if (!issues.isEmpty()) {
                // 提醒项不阻塞保存，但把玩家带到第一条上（例如未注册的第三方条件类型）
                revealIssue(issues.get(0));
            }
        } else {
            setNotice(Component.translatable(UI + "notice.save_rejected"), UiPalette.DANGER);
        }
    }

    private void undo() {
        if (!canEdit()) {
            return;
        }
        EditSession session = editingSession();
        if (session != null && session.canUndo()) {
            session.undo();
            rebuildEdit();
            rebuildFocus();
        }
    }

    private void redo() {
        if (!canEdit()) {
            return;
        }
        EditSession session = editingSession();
        if (session != null && session.canRedo()) {
            session.redo();
            rebuildEdit();
            rebuildFocus();
        }
    }

    private List<FormIssue> collectIssues() {
        List<FormIssue> issues = new ArrayList<>();
        EditSession session = editingSession();
        if (session != null) {
            issues.addAll(localIssues(session.draft()));
        }
        for (String id : model.dirtyRuleIds()) {
            if (id.equals(editingId)) {
                continue;
            }
            EditSession other = model.session(id);
            if (other != null) {
                issues.addAll(localIssues(other.draft()));
            }
        }
        if (infoForm != null) {
            issues.addAll(infoForm.issues());
        }
        if (sourceForm != null) {
            issues.addAll(sourceForm.issues());
        }
        if (conditionsForm != null) {
            issues.addAll(conditionsForm.issues());
        }
        if (effectForm != null) {
            issues.addAll(effectForm.issues());
        }
        return issues;
    }

    // 本地保存前拦截（forms.md §9 的可本地判定项）
    private List<FormIssue> localIssues(RuleDraft draft) {
        List<FormIssue> issues = new ArrayList<>();
        JsonArray effects = new JsonArray();
        JsonElement rawEffects = draft.view().get(RuleFields.EFFECTS);
        if (rawEffects != null && rawEffects.isJsonArray()) {
            effects = rawEffects.getAsJsonArray();
        }
        if (effects.isEmpty()) {
            issues.add(issue(RuleFields.EFFECTS, UI + "issue.effects_empty"));
        } else if (effects.size() > 32) {
            issues.add(issue(RuleFields.EFFECTS, UI + "issue.effects_limit"));
        }
        // 规则级条件树
        validateConditionTree(draft, "", RuleFields.CONDITIONS, issues);
        // 效果级条件树：切到别的效果页签时也要能拦住，否则只在服务端报 VALIDATION_FAILED
        for (int i = 0; i < effects.size(); i++) {
            JsonElement element = effects.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            // rawConditionsAt 需要指向 conditions 元素本身
            String path = RuleFields.EFFECTS + "[" + i + "]." + RuleFields.CONDITIONS;
            validateConditionTree(draft, path, path, issues);
        }
        JsonElement name = draft.view().get(RuleFields.DISPLAY_NAME);
        if (name != null && name.isJsonPrimitive()
                && name.getAsString().codePointCount(0, name.getAsString().length()) > 128) {
            issues.add(issue(RuleFields.DISPLAY_NAME, UI + "issue.display_name_too_long"));
        }
        JsonElement seconds = draft.view().get(RuleFields.TRIGGER_AFTER_SECONDS);
        if (seconds != null && seconds.isJsonPrimitive() && seconds.getAsJsonPrimitive().isNumber()
                && seconds.getAsInt() < 0) {
            issues.add(issue(RuleFields.TRIGGER_AFTER_SECONDS, UI + "issue.trigger_negative"));
        }
        Map<String, Integer> consumption = new LinkedHashMap<>();
        for (JsonElement element : effects) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement typeElement = element.getAsJsonObject().get(RuleFields.TYPE);
            if (typeElement == null || !typeElement.isJsonPrimitive()) {
                continue;
            }
            String type = typeElement.getAsString();
            if (type.startsWith(TypeLabels.OWN_NAMESPACE + ":consume_")) {
                consumption.merge(type, 1, Integer::sum);
            }
        }
        for (Integer count : consumption.values()) {
            if (count != null && count > 1) {
                issues.add(issue(RuleFields.EFFECTS, UI + "issue.duplicate_consumption"));
            }
        }
        return issues;
    }

    // 校验某条路径下的条件树（规则级传 ""，效果级传 "effects[i]"），问题路径指向 conditions 字段
    private void validateConditionTree(RuleDraft draft, String draftPath, String issuePath, List<FormIssue> issues) {
        if (draft.rawConditionsAt(draftPath) == null) {
            return;
        }
        ConditionExpression expression = draft.conditionsAt(draftPath, ClientTypeRegistries.conditions());
        if (expression == null) {
            // 只有本地可证结构损坏（不是对象 / 缺 op / op 不是字符串）才阻塞；
            // 未注册或第三方类型只提醒：原数据在草稿里原样保留，服务端才是权威校验方
            if (isStructurallyDamaged(draft.rawConditionsAt(draftPath))) {
                issues.add(issue(issuePath, UI + "issue.conditions_decode"));
            } else {
                issues.add(warning(issuePath, UI + "issue.conditions_unregistered"));
            }
            return;
        }
        if (!expression.isStructurallyValid()) {
            issues.add(issue(issuePath, ISSUE + "incomplete_group"));
        }
        if (expression.leafCount() > ConditionLimits.MAX_LEAVES) {
            issues.add(issue(issuePath, ISSUE + "too_many_leaves"));
        }
        if (expression.nodeCount() > ConditionLimits.MAX_NODES) {
            issues.add(issue(issuePath, ISSUE + "too_many_nodes"));
        }
        if (expression.depth() > ConditionLimits.MAX_DEPTH) {
            issues.add(issue(issuePath, ISSUE + "too_deep", expression.depth(), ConditionLimits.MAX_DEPTH));
        }
    }

    private FormIssue issue(String path, String messageKey) {
        return issue(path, messageKey, new Object[0]);
    }

    // 带占位符参数的校验项（阻塞保存）
    private FormIssue issue(String path, String messageKey, Object... args) {
        Component message = Component.translatable(messageKey, args);
        return FormIssue.error(path, message, message);
    }

    // 带占位符参数的提醒项（不阻塞保存）
    private FormIssue warning(String path, String messageKey, Object... args) {
        Component message = Component.translatable(messageKey, args);
        return FormIssue.warning(path, message, message);
    }

    // 条件树 JSON 是否本地可证损坏：不是对象、缺 op 或 op 不是原始值
    private static boolean isStructurallyDamaged(@Nullable JsonElement raw) {
        if (raw == null || raw.isJsonNull() || !raw.isJsonObject()) {
            return true;
        }
        JsonElement op = raw.getAsJsonObject().get(RuleFields.OP);
        return op == null || !op.isJsonPrimitive();
    }

    // ---- 焦点 / 事件 / 生命周期 ----

    private void rebuildFocus() {
        focus.clear();
        focus.beginUpdate();
        if (mode == Mode.LIST) {
            if (searchField != null) {
                focus.add(searchField);
            }
            if (filterControl != null) {
                focus.add(filterControl);
            }
            if (ruleList != null) {
                focus.add(ruleList);
            }
            for (UiButton button : listButtons) {
                focus.add(button);
            }
        } else {
            if (tabControl != null) {
                focus.add(tabControl);
            }
            if (tab == Tab.EFFECTS) {
                if (effectList != null) {
                    focus.add(effectList);
                }
                for (UiButton button : effectButtons) {
                    focus.add(button);
                }
            }
            FormView form = activeForm();
            if (form != null) {
                for (UiFocusTarget target : form.focusTargets()) {
                    focus.add(target);
                }
            }
            for (UiButton button : footerButtons) {
                focus.add(button);
            }
        }
        focus.endUpdate();
        if (focus.focused() == null) {
            focus.focusFirst();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modals.isVisible()) {
            return modals.mouseClicked(mouseX, mouseY, button);
        }
        if (pickerOpen) {
            return handlePickerClick(mouseX, mouseY);
        }
        if (mode == Mode.LIST) {
            if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (filterControl != null && filterControl.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (ruleList != null && ruleList.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return listBar.mouseClicked(mouseX, mouseY, button);
        }
        if (tabControl != null && tabControl.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (tab == Tab.EFFECTS) {
            if (effectList != null && effectList.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (effectBar.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (effectForm != null && effectForm.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (footerBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modals.isVisible()) {
            return modals.mouseReleased(mouseX, mouseY, button);
        }
        boolean consumed = false;
        if (infoForm != null) {
            consumed |= infoForm.mouseReleased(mouseX, mouseY, button);
        }
        if (sourceForm != null) {
            consumed |= sourceForm.mouseReleased(mouseX, mouseY, button);
        }
        if (conditionsForm != null) {
            consumed |= conditionsForm.mouseReleased(mouseX, mouseY, button);
        }
        if (effectForm != null) {
            consumed |= effectForm.mouseReleased(mouseX, mouseY, button);
        }
        consumed |= listBar.mouseReleased(mouseX, mouseY, button);
        consumed |= effectBar.mouseReleased(mouseX, mouseY, button);
        consumed |= footerBar.mouseReleased(mouseX, mouseY, button);
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modals.isVisible()) {
            return modals.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        FormView form = activeForm();
        if (form != null && form.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (ruleList != null && ruleList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pickerOpen) {
            pickerIndex = Math.max(0, Math.min(pickerTypes.size() - 1, pickerIndex - (int) Math.signum(scrollY)));
            scrollPickerIntoView(pickerRows());
            return true;
        }
        if (modals.isVisible()) {
            return modals.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (mode == Mode.LIST) {
            if (ruleList != null && ruleList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        } else {
            if (tab == Tab.EFFECTS && effectList != null
                    && effectList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
            FormView form = activeForm();
            if (form != null && form.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modals.isVisible()) {
            return modals.keyPressed(keyCode, scanCode, modifiers);
        }
        if (pickerOpen) {
            return handlePickerKey(keyCode);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (mode == Mode.EDIT) {
                backToList();
            } else {
                closeByUser();
            }
            return true;
        }
        if (focus.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (mode == Mode.LIST) {
            if (searchField != null && searchField.isFocused()
                    && searchField.keyPressed(keyCode, scanCode, modifiers)) {
                syncSearch();
                return true;
            }
            if (ruleList != null && ruleList.isFocused() && ruleList.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } else {
            FormView form = activeForm();
            if (form != null && form.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (tab == Tab.EFFECTS && effectList != null && effectList.isFocused()
                    && effectList.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modals.isVisible()) {
            return modals.charTyped(codePoint, modifiers);
        }
        if (pickerOpen) {
            return false;
        }
        if (searchField != null && searchField.isFocused() && searchField.charTyped(codePoint, modifiers)) {
            syncSearch();
            return true;
        }
        FormView form = activeForm();
        if (form != null && form.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        modals.tick();
        // F-A：快照是打开屏幕之后才到的（打开时只发出了请求），每帧消费一次；
        // refresh() 内部按快照实例去重，重复调用无副作用
        if (model.refresh()) {
            refreshList();
            rebuildEdit();
            rebuildFocus();
        }
        if (listNeedsRefresh) {
            listNeedsRefresh = false;
            refreshList();
        }
        if (mode == Mode.EDIT) {
            FormView form = activeForm();
            if (form != null) {
                form.tick();
            }
        }
        pollWorkspace();
        // P6：节流落盘、恢复提示、按钮状态、冲突弹窗与冻结
        model.tickPersistence();
        if (model.consumeDraftFailures() > 0) {
            setNotice(Component.translatable(UI + "notice.draft_save_failed"), UiPalette.WARNING);
        }
        int restored = model.consumeRestoredCount();
        if (restored > 0) {
            setNotice(Component.translatable(UI + "notice.restored_draft", restored), UiPalette.WARNING);
        }
        updateActionButtons();
        promptConflicts();
        boolean editable = workspace.active();
        if (!editable && !frozenNotified) {
            frozenNotified = true;
            setNotice(Component.translatable(UI + "notice.frozen"), UiPalette.WARNING);
        } else if (editable) {
            frozenNotified = false;
        }
        applyEditableState(editable);
    }

    // 应用全部按钮与撤销/重做按钮的可用状态与标签
    private void updateActionButtons() {
        boolean editable = workspace.active();
        if (applyButton != null) {
            applyButton.setEnabled(editable && model.hasDirty());
        }
        EditSession session = editingSession();
        boolean canUndo = editable && session != null && session.canUndo();
        boolean canRedo = editable && session != null && session.canRedo();
        if (undoButton != null) {
            undoButton.setEnabled(canUndo);
            String opKey = canUndo && session != null ? session.undoOpKey() : null;
            undoButton.setLabel(opKey == null
                    ? Component.translatable(UI + "button.undo")
                    : Component.translatable(UI + "button.undo_named", Component.translatable(opKey)));
        }
        if (redoButton != null) {
            redoButton.setEnabled(canRedo);
            String opKey = canRedo && session != null ? session.redoOpKey() : null;
            redoButton.setLabel(opKey == null
                    ? Component.translatable(UI + "button.redo")
                    : Component.translatable(UI + "button.redo_named", Component.translatable(opKey)));
        }
    }

    // 冻结（APPLYING / 非 ACTIVE）时禁用表单控件；状态切换时才真正遍历
    private void applyEditableState(boolean editable) {
        if (editable == lastEditable) {
            return;
        }
        lastEditable = editable;
        applyEditableStateNow(editable);
    }

    // 立即同步四个表单的使能状态（表单是新建出来的，默认可用，必须显式同步一次）
    private void applyEditableStateNow(boolean editable) {
        for (FormView form : List.of(infoForm, sourceForm, conditionsForm, effectForm)) {
            if (form != null) {
                form.setEnabled(editable);
            }
        }
        // 列表页与效果条的动作按钮同属可编辑面：冻结时一并禁用，避免「点了没反应」
        // （返回按钮不禁用，保证冻结期间玩家仍能退出屏幕）
        for (UiButton button : this.listButtons) {
            button.setEnabled(editable);
        }
        for (UiButton button : this.effectButtons) {
            button.setEnabled(editable);
        }
        // 应用/撤销/重做再按脏标记与历史精确修正
        updateActionButtons();
        if (!editable) {
            closePicker();
        }
    }

    // 冻结（APPLYING / 非 ACTIVE）时禁止一切会改动草稿的入口，并给出明确提示而不是静默丢弃
    private boolean canEdit() {
        if (workspace.active()) {
            return true;
        }
        setNotice(Component.translatable(UI + "notice.frozen"), UiPalette.WARNING);
        return false;
    }

    // 有冲突就弹窗（一次弹一个，已提示过的不重复弹）
    private void promptConflicts() {
        for (DraftConflict conflict : model.conflicts()) {
            if (promptedConflicts.add(conflict.targetId())) {
                openConflictModal(conflict);
                return;
            }
        }
    }

    // 冲突弹窗：保留草稿 / 丢弃草稿用服务端版本 / 稍后决定
    private void openConflictModal(DraftConflict conflict) {
        String id = conflict.targetId();
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "conflict.title"));
        modal.message(Component.translatable(UI + "conflict.message." + conflict.messageSuffix(), id));
        modal.confirm(Component.translatable(UI + "conflict.keep"), () -> {
            model.resolveConflict(id, true);
            promptedConflicts.remove(id);
            refreshList();
            rebuildEdit();
            rebuildFocus();
            setNotice(Component.translatable(UI + "notice.conflict_kept"), UiPalette.TEXT_SECONDARY);
        });
        modal.addButton(new UiButton(font, Component.translatable(UI + "conflict.use_server"),
                UiButtonVariant.DANGER, () -> {
                    model.resolveConflict(id, false);
                    promptedConflicts.remove(id);
                    refreshList();
                    rebuildEdit();
                    rebuildFocus();
                    setNotice(Component.translatable(UI + "notice.conflict_discarded"), UiPalette.WARNING);
                }));
        modal.cancel(Component.translatable(UI + "conflict.later"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    // 外部重新打开时刷新界面（此时工作区已进入 ACTIVE 并发出快照请求）
    public void refreshFromWorkspace() {
        model.refresh();
        refreshList();
        rebuildEdit();
        rebuildFocus();
    }

    // 保存回执与工作区状态轮询
    private void pollWorkspace() {
        long sequence = workspace.resultSeq();
        if (sequence != seenResultSeq) {
            seenResultSeq = sequence;
            RuleSaveStatus status = workspace.lastStatus();
            model.refresh();
            if (status == RuleSaveStatus.SUCCESS || status == RuleSaveStatus.NO_CHANGES) {
                model.acceptSaved();
                setNotice(Component.translatable(status.messageKey()), UiPalette.SUCCESS);
            } else if (status == RuleSaveStatus.SAVED_NOT_RELOADED) {
                model.acceptSaved();
                setNotice(Component.translatable(status.messageKey()), UiPalette.WARNING);
            } else if (status != null) {
                setNotice(Component.translatable(status.messageKey(), workspace.lastMessageArgs().toArray()),
                        UiPalette.DANGER);
            }
            refreshList();
            rebuildEdit();
            rebuildFocus();
        }
        if (workspace.state() == RuleEditClientState.FREE && workspace.openRequest() == null) {
            this.onClose();
        }
    }

    // 用户主动关闭：有未保存改动先确认
    private void closeByUser() {
        if (model.hasDirty()) {
            confirm(UI + "confirm.discard_title", UI + "confirm.discard_message", () -> {
                // 玩家确认放弃：连落盘草稿一起删掉，避免下次打开又「恢复」回来
                model.discardAllDrafts();
                workspace.close("screen_closed");
                this.onClose();
            });
            return;
        }
        workspace.close("screen_closed");
        this.onClose();
    }

    // 只关屏，不动工作区会话（工作区由服务端租约与协议驱动）
    @Override
    public void onClose() {
        applyAllForms();
        // P6：关屏前把未应用草稿写盘（断线 / 关游戏 / 关界面都能恢复）
        model.persistNow();
        super.onClose();
    }

    // ---- 小工具 ----

    private void setNotice(@Nullable Component message, int color) {
        this.notice = message;
        this.noticeColor = color;
    }

    private void noSelection() {
        setNotice(Component.translatable(UI + "notice.no_selection"), UiPalette.WARNING);
    }

    private @Nullable ResourceLocation normalizeId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation parsed = trimmed.indexOf(':') >= 0
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse(TypeLabels.OWN_NAMESPACE + ":" + trimmed);
        if (parsed == null || !TypeLabels.OWN_NAMESPACE.equals(parsed.getNamespace())) {
            return null;
        }
        return parsed;
    }

    // 文本输入弹窗
    private void promptText(String titleKey, String hintKey, String initial, Consumer<String> onValue) {
        UiTextInput input = new UiTextInput(font, Component.translatable(hintKey));
        input.setMaxLength(128);
        input.setValue(initial);
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(titleKey));
        modal.contentWidget(input, ROW_H + 4);
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> onValue.accept(input.value()));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
        input.setFocused(true);
    }

    // 确认弹窗
    private void confirm(String titleKey, String messageKey, Runnable onConfirm) {
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(titleKey));
        modal.message(Component.translatable(messageKey));
        modal.confirm(Component.translatable(UI + "button.confirm"), onConfirm::run);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    // 底排按钮：自动换行、左对齐
    private final class ButtonBar {
        private static final int BUTTON_H = 16;
        private static final int GAP = 2;

        private final List<UiButton> buttons = new ArrayList<>();

        private void set(List<UiButton> next) {
            buttons.clear();
            buttons.addAll(next);
        }

        private int preferredHeight(int width) {
            int rows = rows(width);
            return rows == 0 ? 0 : rows * BUTTON_H + (rows - 1) * GAP;
        }

        private int rows(int width) {
            if (buttons.isEmpty()) {
                return 0;
            }
            if (width <= 0) {
                return 1;
            }
            int perRow = 0;
            int used = 0;
            for (UiButton button : buttons) {
                int buttonWidth = Math.max(24, button.preferredWidth(6));
                if (perRow > 0 && used + buttonWidth > width) {
                    break;
                }
                used += buttonWidth + GAP;
                perRow++;
            }
            if (perRow <= 0) {
                perRow = 1;
            }
            return (int) Math.ceil(buttons.size() / (double) perRow);
        }

        private void layout(int x, int y, int width, int height) {
            if (buttons.isEmpty()) {
                return;
            }
            int rows = Math.max(1, rows(width));
            int cursor = 0;
            for (int rowIndex = 0; rowIndex < rows && cursor < buttons.size(); rowIndex++) {
                int rowY = y + rowIndex * (BUTTON_H + GAP);
                int cursorX = x;
                while (cursor < buttons.size()) {
                    UiButton button = buttons.get(cursor);
                    int buttonWidth = Math.min(Math.max(24, button.preferredWidth(6)), Math.max(1, width));
                    if (cursorX > x && cursorX + buttonWidth > x + width) {
                        break;
                    }
                    button.setBounds(cursorX, rowY, buttonWidth, BUTTON_H);
                    button.setVisible(true);
                    cursorX += buttonWidth + GAP;
                    cursor++;
                }
            }
        }

        private void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
            for (UiButton button : buttons) {
                if (button.isVisible()) {
                    button.render(graphics, renderFont, mouseX, mouseY);
                }
            }
        }

        private boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (UiButton candidate : buttons) {
                if (candidate.isVisible() && candidate.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }

        private boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean consumed = false;
            for (UiButton candidate : buttons) {
                if (candidate.isVisible()) {
                    consumed |= candidate.mouseReleased(mouseX, mouseY, button);
                }
            }
            return consumed;
        }
    }
}