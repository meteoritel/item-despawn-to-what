package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategorySkeleton;
import com.meteorite.itemdespawntowhat.client.edit.RulePackExporter;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Tone;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeNode;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeView;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑器「规则管理页」（LIST 模式）：搜索、筛选、分类规则树与配方预览，以及列表底排按钮的状态同步。
 *
 * <p>本类只持有管理页控件与视图状态（选中项、展开分类、每个条目的配方视图），
 * 规则会话与工作区、弹窗栈、提示条、跨模式动作都通过 {@link Host} 暴露的屏幕能力完成；
 * 新建 / 复制 / 启停 / 删除等条目动作在 {@link RuleEditorListActions}，
 * 模式切换、保存入口与编辑页由 {@link RuleEditorScreen} 与 {@link RuleEditorEditPage} 负责。</p>
 */
final class RuleEditorListPage {

    /** 宿主能力：管理页只通过这些入口读写屏幕状态与跨模式动作。 */
    interface Host {

        Font font();

        // 弹窗栈
        UiModalStack modals();

        // 规则模型（快照、草稿会话与脏规则）
        RuleEditorModel model();

        // 编辑工作区只读视图
        EditorWorkspaceView workspace();

        // 屏幕宽高
        int screenWidth();

        int screenHeight();

        // 冻结（会话失效 / 只读）时拒绝写入
        boolean rejectWhenFrozen();

        // 底部提示条
        void setNotice(@Nullable Component message, int color);

        // 列表展示快照缓存（标题、状态、分类、预览与提示都从同一份快照读）
        RuleListDisplayCache display();

        void openEditor(String id);

        // 新建规则后直接切到「输入」页签
        void selectInputTab();

        // 草稿改动清单弹窗
        void showChanges();

        // 提交当前草稿（与编辑页保存按钮同一入口）
        void save();

        // 确认弹窗
        void confirm(String titleKey, String messageKey, Runnable onConfirm);

        // 重建规则树（条目动作改动草稿后的统一刷新入口）
        void refreshList();
    }

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    // 布局常量
    private static final int ROW_H = 12;
    private static final int SEARCH_MAX_WIDTH = 160;
    private static final int SEARCH_MIN_WIDTH = 80;
    private static final int SEARCH_MAX_LENGTH = 128;
    private static final int TAG_MODAL_WIDTH = 280;
    private static final int EXPORT_MODAL_WIDTH = 280;
    private static final int EXPORT_ROW_H = 20;
    // 规则摘要面板的行数预算：条件树展开叶节点详情后行数变多，超出仍由面板高度兜底
    private static final int TOOLTIP_MAX_LINES = 16;
    // 状态筛选的轮询顺序：全部 → 已启用 → 已停用
    private static final String[] STATUS_VALUES = {"all", "enabled", "disabled"};
    // 来源筛选的轮询顺序：全部 → 覆盖层 → 数据包
    private static final String[] SOURCE_VALUES = {"all", "overlay", "datapack"};
    // i18n 前缀
    private static final String UI = TypeLabels.UI_PREFIX;

    private final Host host;
    private final RuleEditorListActions actions;

    // ---- 列表控件与视图状态 ----
    private @Nullable UiTextInput searchField;
    private @Nullable UiButton statusButton;
    private @Nullable UiButton sourceButton;
    private @Nullable UiTreeView<String> ruleList;
    private final List<String> visibleIds = new ArrayList<>();
    // 分类行标题裁剪缓存：键为节点身份（每次刷新重建节点后整体清空），值记录测量时的行宽与结果，静止帧不再测文本
    private final Map<UiTreeNode<String>, CategoryTitle> categoryTitles = new IdentityHashMap<>();
    private @Nullable Font categoryFont;
    private @Nullable Object categoryLanguage;
    private final List<UiButton> listButtons = new ArrayList<>();
    private final List<UiButton> filterButtons = new ArrayList<>();
    private final RuleEditorScreenButtonBar listBar = new RuleEditorScreenButtonBar();
    private final RuleEditorScreenButtonBar globalBar = new RuleEditorScreenButtonBar();
    private final RuleEditorScreenButtonBar filterBar = new RuleEditorScreenButtonBar(ROW_H, 2, true);
    private final List<UiButton> selectedButtons = new ArrayList<>();
    private @Nullable UiButton editButton;
    private @Nullable UiButton toggleButton;
    private @Nullable UiButton deleteButton;
    private @Nullable UiButton applyButton;
    private @Nullable UiButton exportButton;
    // 是否有后台导出在途：在途期间导出按钮禁用，避免重复点击叠加写盘
    private boolean exporting;
    private final Set<String> expandedCategories = new LinkedHashSet<>();
    private String lastQuery = "";
    private String lastTreeQuery = "";
    // 状态筛选下标（0 全部 / 1 已启用 / 2 已停用）与来源筛选下标（0 全部 / 1 覆盖层 / 2 数据包）
    private int statusFilter;
    private int sourceFilter;
    // 首载分段观测：首份快照后的首次刷新只记录一次，作为「列表可用」的终点
    private boolean firstReadyLogged;

    RuleEditorListPage(Host host) {
        this.host = host;
        this.actions = new RuleEditorListActions(host, this::selectedRuleId);
    }

    // ---- 初始化 ----

    // 控件在首次 init 时创建；resize 再次 init 时保留输入缓冲、选择与滚动。
    boolean initialized() {
        return ruleList != null;
    }

    void init() {
        Font font = host.font();
        searchField = new UiTextInput(THEME, font, Component.translatable(UI + "list.search"));
        searchField.setMaxLength(SEARCH_MAX_LENGTH);
        searchField.setValue(lastQuery);
        buildFilterButtons();
        ruleList = new UiTreeView<>(THEME, font, (graphics, rowFont, node, row, depth, selected, hovered, focused) -> {
            if (node.value().startsWith("@")) {
                drawCategoryRow(graphics, rowFont, node, row);
            } else renderRuleRow(graphics, rowFont, node.value(), row, hovered);
        });
        ruleList.setRowHeightProvider(node -> font.lineHeight + 4
                + (node.value().startsWith("@") ? 0 : RuleRecipeView.CONTENT_HEIGHT + 1));
        ruleList.setOnActivate(node -> { if (!node.value().startsWith("@")) host.openEditor(node.value()); });
        buildListButtons();
    }

    // 筛选按钮：状态与来源各一个轮询按钮，两者取交集
    private void buildFilterButtons() {
        statusButton = button(UI + "filter.status_label", UiButtonVariant.SECONDARY, this::cycleStatusFilter);
        sourceButton = button(UI + "filter.source_label", UiButtonVariant.SECONDARY, this::cycleSourceFilter);
        filterButtons.clear();
        filterButtons.addAll(List.of(statusButton, sourceButton));
        filterBar.set(filterButtons);
        refreshFilterLabels();
    }

    private void cycleStatusFilter() {
        statusFilter = (statusFilter + 1) % STATUS_VALUES.length;
        refreshFilterLabels();
        refreshList();
    }

    private void cycleSourceFilter() {
        sourceFilter = (sourceFilter + 1) % SOURCE_VALUES.length;
        refreshFilterLabels();
        refreshList();
    }

    // 轮询按钮只改显示文案；当前取值直接写进标签，玩家不用展开菜单
    private void refreshFilterLabels() {
        if (statusButton != null) {
            statusButton.setLabel(Component.translatable(UI + "filter.status_label",
                    Component.translatable(UI + "filter." + STATUS_VALUES[statusFilter])));
        }
        if (sourceButton != null) {
            sourceButton.setLabel(Component.translatable(UI + "filter.source_label",
                    Component.translatable(UI + "filter.source." + SOURCE_VALUES[sourceFilter])));
        }
    }

    // ---- 按钮 ----

    private void buildListButtons() {
        listButtons.clear();
        UiButton create = button(UI + "button.new", UiButtonVariant.PRIMARY, actions::promptNewRule);
        editButton = button(UI + "button.edit", UiButtonVariant.PRIMARY, () -> {
            String id = selectedRuleId(); if (id != null) host.openEditor(id);
        });
        toggleButton = button(UI + "button.enable", UiButtonVariant.SECONDARY, actions::toggleSelectedEnabled);
        UiButton copy = button(UI + "button.duplicate", UiButtonVariant.SECONDARY, actions::promptDuplicate);
        deleteButton = button(UI + "button.delete", UiButtonVariant.DANGER, actions::deleteSelected);
        exportButton = button(UI + "button.export", UiButtonVariant.SECONDARY, this::openExport);
        applyButton = button(UI + "button.apply", UiButtonVariant.PRIMARY, host::save);
        UiButton changes = button(UI + "button.changes", UiButtonVariant.SECONDARY, host::showChanges);
        selectedButtons.clear();
        selectedButtons.addAll(List.of(editButton, toggleButton, copy, deleteButton));
        globalBar.set(List.of(create, editButton, toggleButton, copy, deleteButton, exportButton));
        listBar.set(List.of(applyButton, changes));
        listButtons.addAll(List.of(create, editButton, toggleButton, copy, deleteButton, exportButton, applyButton, changes));
        // 重建按钮时不丢在途导出的禁用态（列表刷新可能在导出期间发生）
        if (exporting) {
            exportButton.setEnabled(false);
        }
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(THEME, host.font(), Component.translatable(labelKey), variant, action::run).setChipStyle(true);
    }

    /** 导出范围选项：标签、是否含数据包条目与当前可导出的条数。 */
    private record ExportScope(String scope, String labelKey, String countKey, boolean includeDatapack, int count) { }

    /** 分类行标题的测量结果：行宽决定裁剪点，行宽变化时重测。 */
    private record CategoryTitle(int width, String text) { }

    // 导出弹窗：两个范围选项直接显示各自可导出的条数，避免导出后才发现选错范围
    private void openExport() {
        UiModal modal = UiModal.create(THEME, host.font()).title(Component.translatable(UI + "export.title"));
        UiListView<ExportScope> list = new UiListView<>(THEME, host.font(),
                (graphics, rowFont, item, index, row, selected, hovered, focused) -> {
                    graphics.drawString(rowFont, Component.translatable(UI + item.labelKey()),
                            row.x() + 4, row.y() + 6, THEME.palette().textPrimary(), false);
                    Component count = Component.translatable(UI + item.countKey(), item.count());
                    graphics.drawString(rowFont, count, row.right() - 4 - rowFont.width(count),
                            row.y() + 6, THEME.palette().textSecondary(), false);
                });
        list.setItems(List.of(
                new ExportScope(RulePackExporter.SCOPE_OVERLAY, "export.scope_overlay", "export.count", false,
                        exportCount(false)),
                new ExportScope(RulePackExporter.SCOPE_ALL, "export.scope_all", "export.count", true,
                        exportCount(true))));
        list.setRowHeight(EXPORT_ROW_H);
        list.setActivateOnSingleClick(true);
        list.setOnActivate(item -> {
            host.modals().close(modal);
            exportRules(item);
        });
        modal.message(Component.translatable(UI + "export.message"));
        modal.contentWidget(list, EXPORT_ROW_H * 2 + 4).cancel(Component.translatable(UI + "button.cancel"));
        modal.preferredWidth(EXPORT_MODAL_WIDTH).layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    // 统计某个范围可导出的条数：只算有可读规则体且可导出的条目
    private int exportCount(boolean includeDatapack) {
        int count = 0;
        for (String id : host.model().ruleIds()) {
            RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
            if (snapshot.exportable() && (includeDatapack || isOverlayOrigin(snapshot.origin()))) {
                count++;
            }
        }
        return count;
    }

    // 按范围收集当前显示体（含未保存草稿）后交给后台写盘；失败只提示，不改变任何规则状态
    private void exportRules(ExportScope option) {
        if (exporting) {
            return;
        }
        List<RulePackExporter.RuleEntry> entries = new ArrayList<>();
        for (String id : host.model().ruleIds()) {
            RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
            if (!snapshot.exportable() || (!option.includeDatapack() && !isOverlayOrigin(snapshot.origin()))) {
                continue;
            }
            RuleValue body = host.model().displayBody(id);
            if (body != null) {
                // 规则值只反映草稿实时内容，跨线程读取不安全：先在客户端线程取成不可变 JSON 文本快照
                entries.add(new RulePackExporter.RuleEntry(id, body.toJson()));
            }
        }
        if (entries.isEmpty()) {
            host.setNotice(Component.translatable(UI + "export.empty"), THEME.palette().warning());
            return;
        }
        exporting = true;
        if (exportButton != null) {
            exportButton.setEnabled(false);
        }
        RulePackExporter.exportAsync(option.scope(), entries)
                .thenAccept(result -> deliverExport(() -> host.setNotice(
                        Component.translatable(UI + "export.done", result.count(), result.displayPath()),
                        THEME.palette().success())))
                .exceptionally(error -> {
                    deliverExport(() -> reportExportFailure(error));
                    return null;
                });
    }

    // 后台结果回到客户端线程：先复位导出按钮再显示提示；期间工作区可能已冻结，按当前可编辑态复位
    private void deliverExport(Runnable notice) {
        Minecraft.getInstance().execute(() -> {
            exporting = false;
            if (exportButton != null) {
                exportButton.setEnabled(host.workspace().active());
            }
            notice.run();
        });
    }

    // 导出失败分两类提示：预检拒绝要列出无法写盘的规则 id，其余按意外错误报告原因
    private void reportExportFailure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof RulePackExporter.ExportRejectedException rejected) {
            host.setNotice(Component.translatable(UI + "export.invalid", rejected.ids().size(),
                    describeIds(rejected.ids())), THEME.palette().danger());
            return;
        }
        String reason = cause.getMessage() == null ? "" : cause.getMessage();
        host.setNotice(Component.translatable(UI + "export.failed", reason), THEME.palette().danger());
    }

    // 提示行空间有限：只列前几条，其余以省略号概括
    private static String describeIds(List<String> ids) {
        int shown = Math.min(ids.size(), 3);
        String joined = String.join(", ", ids.subList(0, shown));
        return ids.size() > shown ? joined + "…" : joined;
    }

    // 列表条目操作随选中状态同步；只读数据包条目只保留查看与复制，启停和删除禁用。
    // 切换按钮只显示下一步动作，布局按当前文案计算。
    void updateButtons() {
        String id = selectedRuleId();
        boolean active = host.workspace().active();
        boolean selected = id != null;
        boolean readOnly = selected && host.model().isReadOnly(id);
        for (UiButton button : selectedButtons) {
            button.setEnabled(active && selected);
        }
        // 只读条目没有写入面：启停与删除在列表层就不可用，避免点击后才在应用阶段失败
        if (toggleButton != null) {
            toggleButton.setEnabled(active && selected && !readOnly);
            boolean enabled = selected && host.display().snapshot(id).enabled();
            toggleButton.setLabel(Component.translatable(UI + (enabled ? "button.disable" : "button.enable")));
        }
        if (deleteButton != null) {
            deleteButton.setEnabled(active && selected && !readOnly);
        }
        if (editButton != null) {
            editButton.setLabel(Component.translatable(UI + (readOnly ? "button.view" : "button.edit")));
        }
    }

    // 「应用全部」按钮的可用状态与文案
    void updateApplyButton() {
        if (applyButton == null) {
            return;
        }
        applyButton.setEnabled(host.workspace().active() && host.model().hasDirty());
        applyButton.setLabel(Component.translatable(UI + "button.apply_changes", host.model().dirtyRuleIds().size()));
    }

    // ---- 渲染 ----

    int footerHeight(int width) {
        return listBar.preferredHeight(width);
    }

    void renderFooter(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, int y, int width) {
        listBar.layout(y, width);
        listBar.render(graphics, renderFont, mouseX, mouseY);
    }

    // 管理页布局：工具排、搜索框与筛选、规则树
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect area) {
        updateButtons();
        int headerHeight = globalBar.preferredHeight(area.width());
        globalBar.layout(area.y(), area.width());
        globalBar.render(graphics, renderFont, mouseX, mouseY);
        int searchY = area.y() + headerHeight + RuleEditorPageSupport.PAD;
        int searchWidth = Math.clamp(area.width() / 3, SEARCH_MIN_WIDTH, SEARCH_MAX_WIDTH);
        if (searchField != null) {
            searchField.setBounds(area.x(), searchY, searchWidth, ROW_H);
            searchField.render(graphics, renderFont, mouseX, mouseY);
        }
        int filterWidth = Math.max(0, area.width() - searchWidth - 2);
        filterBar.layout(area.x() + searchWidth + 2, searchY, filterWidth);
        filterBar.render(graphics, renderFont, mouseX, mouseY);
        int listY = searchY + Math.max(ROW_H, filterBar.preferredHeight(filterWidth)) + RuleEditorPageSupport.PAD;
        int available = Math.max(0, area.bottom() - listY);
        if (ruleList != null) {
            ruleList.setBounds(area.x(), listY, area.width(), available);
            ruleList.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    // 名称和状态放第一行；原注册名位置改为图标配方，不显示内部规则 ID。
    // 一帧内只读展示快照：不读规则体、不逐帧重算状态。
    private void renderRuleRow(GuiGraphics graphics, Font rowFont, String id, UiRect row, boolean hovered) {
        RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
        int color = snapshot.active() ? THEME.palette().textPrimary() : THEME.palette().textDisabled();
        // 标题/状态宽度与配方预览都缓存在快照上：绘制只取结果，静止帧不测文本
        RuleRecipeView.renderRow(graphics, rowFont, row, snapshot, color,
                snapshot.error() ? THEME.palette().danger() : THEME.palette().textSecondary(), hovered);
    }

    // 分类行标题只随节点、行宽与字体/语言变化：命中缓存时静止帧不再调用 font.width
    private void drawCategoryRow(GuiGraphics graphics, Font font, UiTreeNode<String> node, UiRect row) {
        Object language = Language.getInstance();
        if (font != categoryFont || language != categoryLanguage) {
            categoryTitles.clear();
            categoryFont = font;
            categoryLanguage = language;
        }
        int width = row.width() - 4;
        CategoryTitle cached = categoryTitles.get(node);
        if (cached == null || cached.width() != width) {
            cached = new CategoryTitle(width, TextScroll.trimToWidth(font, node.label().getString(), width));
            categoryTitles.put(node, cached);
        }
        graphics.drawString(font, cached.text(), row.x() + 2, row.y() + (row.height() - font.lineHeight) / 2,
                THEME.palette().textSecondary(), false);
    }

    // ---- 列表维护 ----

    void refreshList() {
        if (ruleList == null) {
            return;
        }
        // 节点在每次刷新时重建，按节点身份的裁剪缓存随旧树一起丢弃
        categoryTitles.clear();
        // 首载分段观测：一次刷新只读一次开关；关闭时除布尔判断外不取时间、不分配对象
        final boolean profile = DevelopmentDiagnostics.enabled();
        final long refreshStarted = profile ? DevelopmentDiagnostics.start() : 0L;
        long filterMicros = 0L;
        long bodyMicros = 0L;
        // 未收到首份快照不能判为空；有效空快照和筛选结果为空才使用空列表文案。
        ruleList.setEmptyMessage(Component.translatable(UI + (host.model().isLoaded() ? "list.empty" : "list.loading")));
        String selectedValue = ruleList.selectedNode() == null ? null : ruleList.selectedNode().value();
        int scrollOffset = ruleList.scrollOffset();
        List<String> ruleIds = host.model().ruleIds();
        // 只保留仍在列表里的条目，缓存随规则集合一起收敛
        host.display().retain(ruleIds);
        long bodyFetchesBefore = host.display().bodyFetches();
        visibleIds.clear();
        String query = lastQuery == null ? "" : lastQuery.toLowerCase(Locale.ROOT).trim();
        for (String id : ruleIds) {
            // 删除只写草稿、快照在应用前仍含该条目：本地标记删除后立即从列表退出，
            // 与新建草稿的删除视觉一致；应用成功后由新快照完成真正的移除
            if (host.model().isDeleteMarked(id)) {
                continue;
            }
            // 每条规则只在这里取一次体：展示快照同时给出标题、状态、分类与搜索文本；
            // 配方预览与条件提示不在这里构建，由首行可见与悬停时向快照惰性索取
            long snapshotStarted = profile ? DevelopmentDiagnostics.start() : 0L;
            RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
            bodyMicros += profile ? DevelopmentDiagnostics.micros(snapshotStarted) : 0L;
            long filterStarted = profile ? DevelopmentDiagnostics.start() : 0L;
            boolean matches = matchesFilter(snapshot) && matchesQuery(id, snapshot, query);
            filterMicros += profile ? DevelopmentDiagnostics.micros(filterStarted) : 0L;
            if (!matches) {
                continue;
            }
            visibleIds.add(id);
        }
        long treeStarted = profile ? DevelopmentDiagnostics.start() : 0L;
        if (lastTreeQuery.isBlank()) rememberCategoryState(ruleList.roots());
        lastTreeQuery = lastQuery;
        // 已知分类的层级、顺序与显示名来自静态骨架；未知分类按原文动态成组
        List<RuleCategorySkeleton.Category> skeleton = RuleCategorySkeleton.roots();
        List<UiTreeNode<String>> roots = new ArrayList<>();
        Map<String, UiTreeNode<String>> groups = new LinkedHashMap<>();
        for (RuleCategorySkeleton.Category root : skeleton) {
            UiTreeNode<String> node = categoryNode(root);
            roots.add(node);
            groups.put(root.id(), node);
            for (RuleCategorySkeleton.Category child : root.children()) {
                UiTreeNode<String> childNode = categoryNode(child);
                node.addChild(childNode);
                groups.put(child.id(), childNode);
            }
        }
        for (String id : visibleIds) {
            // 只消费展示快照里的分类与标题：问题优先归入「有问题」，其次未保存草稿归入「未完成」，
            // 未声明分类时归入扩展分组；三个分组都无成员时在树上自动隐藏
            RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
            String category = hasIssues(snapshot) ? RuleCategorySkeleton.ISSUES
                    : snapshot.unfinished() ? RuleCategorySkeleton.UNFINISHED
                    : snapshot.category() == null ? RuleCategorySkeleton.EXTENSION : snapshot.category();
            UiTreeNode<String> group = groups.get(category);
            if (group == null) {
                group = unknownCategoryNode(groups, category);
            }
            group.addChild(id, Component.literal(snapshot.title()));
        }
        List<UiTreeNode<String>> nonempty = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            UiTreeNode<String> root = roots.get(index);
            RuleCategorySkeleton.Category definition = skeleton.get(index);
            // 父分类只收下有内容的子分类；已知与未知的子分类统一带计数，直接声明父分类的规则按叶子保留
            UiTreeNode<String> parent = categoryNode(definition);
            for (UiTreeNode<String> child : root.children()) {
                if (child.value().startsWith("@")) {
                    if (!child.children().isEmpty()) parent.addChild(counted(child));
                } else {
                    parent.addChild(child);
                }
            }
            if (!parent.children().isEmpty()) nonempty.add(counted(parent));
        }
        long treeMicros = profile ? DevelopmentDiagnostics.micros(treeStarted) : 0L;
        long rootsStarted = profile ? DevelopmentDiagnostics.start() : 0L;
        ruleList.setRoots(nonempty);
        ruleList.setSelectedNode(findNode(nonempty, selectedValue));
        ruleList.setScrollOffset(scrollOffset);
        // 首份快照后的首次刷新即「列表可用」，与开关状态无关地只认一次
        boolean firstReady = host.model().isLoaded() && !firstReadyLogged;
        if (firstReady) {
            firstReadyLogged = true;
        }
        if (profile) {
            int rules = ruleIds.size();
            long bodyFetches = host.display().bodyFetches() - bodyFetchesBefore;
            logRefresh(refreshStarted, filterMicros, bodyMicros, treeMicros,
                    DevelopmentDiagnostics.micros(rootsStarted), rules, rules - (int) bodyFetches, bodyFetches, firstReady);
        }
    }

    // 首载分段观测：一次刷新的分段耗时；first 表示首份快照后的首次刷新（列表可用时刻）
    // body_fetches 是本次刷新实际取体次数，reused 是直接复用展示快照的条数
    private void logRefresh(long refreshStarted, long filterMicros, long bodyMicros, long treeMicros,
                            long rootsMicros, int rules, int reused, long bodyFetches, boolean firstReady) {
        JsonObject data = new JsonObject();
        data.addProperty("rules", rules);
        data.addProperty("visible", visibleIds.size());
        data.addProperty("snapshot_version", host.model().snapshotVersion());
        data.addProperty("status_filter", statusFilter);
        data.addProperty("source_filter", sourceFilter);
        data.addProperty("query_chars", lastQuery == null ? 0 : lastQuery.length());
        data.addProperty("first", firstReady);
        data.addProperty("body_fetches", bodyFetches);
        data.addProperty("reused", reused);
        data.addProperty("filter_ms", filterMicros / 1000.0);
        data.addProperty("body_ms", bodyMicros / 1000.0);
        data.addProperty("tree_ms", treeMicros / 1000.0);
        data.addProperty("set_roots_ms", rootsMicros / 1000.0);
        data.addProperty("total_ms", DevelopmentDiagnostics.micros(refreshStarted) / 1000.0);
        DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "CLIENT_LIST_REFRESH", data);
    }

    // 刷新时按稳定值恢复目录或规则选择，不依赖重建前的节点实例。
    private @Nullable UiTreeNode<String> findNode(List<UiTreeNode<String>> nodes, @Nullable String value) {
        if (value == null) return null;
        for (UiTreeNode<String> node : nodes) {
            if (value.equals(node.value())) return node;
            UiTreeNode<String> child = findNode(node.children(), value);
            if (child != null) return child;
        }
        return null;
    }

    private UiTreeNode<String> counted(UiTreeNode<String> node) {
        UiTreeNode<String> copy = new UiTreeNode<>(node.value(), node.label().copy().append(" (" + ruleCount(node) + ")"))
                .setExpanded(node.isExpanded());
        node.children().forEach(copy::addChild);
        return copy;
    }

    // 分类计数只统计规则叶；父分类汇总全部子分类，不受展开状态影响。
    private int ruleCount(UiTreeNode<String> node) {
        if (!node.value().startsWith("@")) return 1;
        return node.children().stream().mapToInt(this::ruleCount).sum();
    }

    private UiTreeNode<String> categoryNode(RuleCategorySkeleton.Category category) {
        return new UiTreeNode<>("@" + category.id(), Component.translatable(category.labelKey()))
                .setExpanded(!lastQuery.isBlank() || expandedCategories.contains(category.id()));
    }

    // 未知分类：用原文标识成组并挂在扩展分组下；同一分类只建一次
    private UiTreeNode<String> unknownCategoryNode(Map<String, UiTreeNode<String>> groups, String category) {
        UiTreeNode<String> node = new UiTreeNode<>("@" + category, Component.literal(category))
                .setExpanded(!lastQuery.isBlank() || expandedCategories.contains(category));
        groups.get(RuleCategorySkeleton.EXTENSION).addChild(node);
        groups.put(category, node);
        return node;
    }

    private void rememberCategoryState(List<UiTreeNode<String>> nodes) {
        for (UiTreeNode<String> node : nodes) {
            if (!node.value().startsWith("@")) continue;
            String category = node.value().substring(1);
            if (node.isExpanded()) expandedCategories.add(category); else expandedCategories.remove(category);
            rememberCategoryState(node.children());
        }
    }

    // 搜索：id、标题与当前草稿内容都参与匹配；规则体文本按展示快照缓存，不在逐键刷新时重建
    private boolean matchesQuery(String id, RuleListDisplaySnapshot snapshot, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return id.toLowerCase(Locale.ROOT).contains(query)
                || snapshot.title().toLowerCase(Locale.ROOT).contains(query)
                || snapshot.searchText().contains(query);
    }

    // 筛选只读展示快照，不重新读取规则体：状态与来源取交集
    private boolean matchesFilter(RuleListDisplaySnapshot snapshot) {
        if (statusFilter == 1 && !snapshot.active()) {
            return false;
        }
        if (statusFilter == 2 && snapshot.active()) {
            return false;
        }
        if (sourceFilter == 1 && !isOverlayOrigin(snapshot.origin())) {
            return false;
        }
        if (sourceFilter == 2 && !isDatapackOrigin(snapshot.origin())) {
            return false;
        }
        return true;
    }

    // 条目是否带问题：服务端校验问题或客户端解码失败，列表页据此归入「有问题」分组
    private static boolean hasIssues(RuleListDisplaySnapshot snapshot) {
        return snapshot.issues() || snapshot.error();
    }

    // 覆盖层筛选含「覆盖层 + 数据包」的混合条目：混合条目的生效视图来自覆盖层
    private static boolean isOverlayOrigin(String origin) {
        return RuleSnapshotEntry.ORIGIN_OVERLAY.equals(origin) || RuleSnapshotEntry.ORIGIN_MIXED.equals(origin);
    }

    // 数据包筛选同样含混合条目：混合条目在数据包层有基底
    private static boolean isDatapackOrigin(String origin) {
        return RuleSnapshotEntry.ORIGIN_DATAPACK.equals(origin) || RuleSnapshotEntry.ORIGIN_MIXED.equals(origin);
    }

    private @Nullable String selectedRuleId() {
        UiTreeNode<String> node = ruleList == null ? null : ruleList.selectedNode();
        return node == null || node.value().startsWith("@") ? null : node.value();
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

    // ---- 指针与键盘委托 ----

    void onScopeChanged() {
        if (searchField != null) searchField.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        if (ruleList != null) ruleList.mouseReleased(-1, -1, 0);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (filterBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (ruleList != null && ruleList.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return globalBar.mouseClicked(mouseX, mouseY, button)
                || listBar.mouseClicked(mouseX, mouseY, button);
    }

    // 点击配方标签：打开标签轮播弹窗；未命中标签时返回 false 交回屏幕继续命中测试。
    boolean openTagDetails(double mouseX, double mouseY) {
        TagPreviewIcons.Tag tag = tagAt(mouseX, mouseY);
        if (tag == null) {
            return false;
        }
        TagCarouselView view = new TagCarouselView(host.font(), tag);
        host.modals().push(UiModal.create(THEME, host.font()).title(Component.translatable(UI + "tag.title", tag.id()))
                .contentWidget(view, Math.clamp(host.screenHeight() - 70, 110, 220))
                .preferredWidth(TAG_MODAL_WIDTH).cancel(Component.translatable(UI + "button.close"))
                .onClosed(view::resumePolling)
                .layoutCentered(host.screenWidth(), host.screenHeight()));
        return true;
    }

    // 指针必须先通过树的内容门禁，再命中配方标签。
    private @Nullable TagPreviewIcons.Tag tagAt(double mouseX, double mouseY) {
        if (ruleList == null) return null;
        UiTreeNode<String> node = ruleList.nodeAt(mouseX, mouseY);
        // 分类节点没有配方预览；命中配方标签时才向展示快照惰性索取预览
        if (node == null || node.value().startsWith("@")) return null;
        UiRect row = ruleList.nodeContentBounds(node);
        // 命中直接复用绘制时的整行区域，配方区几何由 RuleRecipeView 内部同一份口径换算
        return host.display().snapshot(node.value()).preview().tagAt(host.font(), row, mouseX, mouseY);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = ruleList != null && ruleList.mouseReleased(mouseX, mouseY, button);
        consumed |= searchField != null && searchField.mouseReleased(mouseX, mouseY, button);
        consumed |= filterBar.mouseReleased(mouseX, mouseY, button);
        consumed |= globalBar.mouseReleased(mouseX, mouseY, button);
        consumed |= listBar.mouseReleased(mouseX, mouseY, button);
        return consumed;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (searchField != null && searchField.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return ruleList != null && ruleList.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return ruleList != null && ruleList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // 历史快捷键派发路径：搜索框优先消费，不触发列表刷新。
    boolean searchKeyPressed(int keyCode, int scanCode, int modifiers) {
        return searchField != null && searchField.isFocused() && searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    // 列表页普通按键：搜索框消费后同步过滤；否则交给规则树。
    boolean listKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField != null && searchField.isFocused()
                && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            syncSearch();
            return true;
        }
        return ruleList != null && ruleList.isFocused() && ruleList.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (searchField != null && searchField.isFocused() && searchField.charTyped(codePoint, modifiers)) {
            syncSearch();
            return true;
        }
        return false;
    }

    // 规则条目整行都进入条件提示的悬停范围；配方标签框优先，只有没命中标签时才展开条件树。
    // 条件正文、状态与不可用提示按语义分行，各自保持主题配色。
    void renderDescriptionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (ruleList == null || !ruleList.bounds().contains(mouseX, mouseY)) {
            return;
        }
        UiTreeNode<String> node = ruleList.nodeAt(mouseX, mouseY);
        if (node == null || node.value().startsWith("@")) {
            return;
        }
        String id = node.value();
        // 悬停时才向展示快照索取配方预览与条件提示：未悬停的条目不生成提示
        RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
        TagPreviewIcons.Tag tag = tagAt(mouseX, mouseY);
        List<DescriptionLine> lines = new ArrayList<>();
        if (tag != null) {
            lines.add(DescriptionLine.text(tag.tooltip(), Tone.VALUE, 0));
        } else {
            lines.add(DescriptionLine.section(Component.translatable(UI + "recipe.condition_tree"), 0));
            lines.addAll(snapshot.conditionLines());
            if (snapshot.previewUnavailable() || snapshot.stateVisible()) {
                lines.add(DescriptionLine.spacer());
            }
            if (snapshot.previewUnavailable()) {
                lines.add(DescriptionLine.note(Component.translatable(UI + "preview.unavailable"), 0));
            }
            if (snapshot.stateVisible()) {
                lines.add(DescriptionLine.note(snapshot.state(), 0));
            }
        }
        UiDescriptionTooltip tooltip = new UiDescriptionTooltip(THEME, host.font());
        if (tag == null) {
            tooltip.setMaxLines(TOOLTIP_MAX_LINES, Component.translatable(UI + "recipe.condition_tree_more"));
        }
        tooltip.render(graphics, new UiRect(0, 0, host.screenWidth(), host.screenHeight()), lines, mouseX, mouseY);
    }

    // ---- 焦点与使能 ----

    void addFocusTargets(UiFocusManager focus) {
        if (searchField != null) {
            focus.add(searchField);
        }
        for (UiButton button : filterButtons) {
            focus.add(button);
        }
        if (ruleList != null) {
            focus.add(ruleList);
        }
        for (UiButton button : listButtons) {
            focus.add(button);
        }
    }

    // 冻结时列表页动作按钮一并禁用（返回按钮不在其中）；在途导出保持禁用，避免写盘期间重新可点
    void setButtonsEnabled(boolean editable) {
        for (UiButton button : listButtons) {
            if (exporting && button == exportButton) {
                continue;
            }
            button.setEnabled(editable);
        }
        // 恢复可编辑态时仍要按当前选中项的来源收窄启停与删除，不能被整体放行覆盖
        updateButtons();
    }
}
