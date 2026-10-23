package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategorySkeleton;
import com.meteorite.itemdespawntowhat.client.edit.RulePackExporter;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeNode;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeView;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑器「规则管理页」（LIST 模式）：搜索、筛选、分类规则树与配方预览，以及列表底排按钮的状态同步。
 *
 * <p>本类只持有管理页控件与视图状态（选中项、展开分类、每个条目的配方视图），
 * 规则会话与工作区、弹窗栈、提示条、跨模式动作都通过 {@link Host} 暴露的屏幕能力完成；
 * 新建 / 复制 / 启停 / 删除等条目动作在 {@link RuleEditorScreenListActions}，
 * 模式切换、保存入口与编辑页由 {@link RuleEditorScreen} 与 {@link RuleEditorScreenEditPage} 负责。</p>
 */
final class RuleEditorScreenListPage {

    /*** 宿主能力：管理页只通过这些入口读写屏幕状态与跨模式动作。 */
    interface Host {

        // 渲染字体
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

        // 打开编辑页
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
    private static final int TOOLTIP_RESERVED_LINES = 8;
    // 状态筛选的轮询顺序：全部 → 已启用 → 已停用
    private static final String[] STATUS_VALUES = {"all", "enabled", "disabled"};
    // 来源筛选的轮询顺序：全部 → 覆盖层 → 数据包
    private static final String[] SOURCE_VALUES = {"all", "overlay", "datapack"};
    // i18n 前缀
    private static final String UI = "gui.itemdespawntowhat.edit.";

    private final Host host;
    private final RuleEditorScreenListActions actions;

    // ---- 列表控件与视图状态 ----
    private @Nullable UiTextInput searchField;
    private @Nullable UiButton statusButton;
    private @Nullable UiButton sourceButton;
    private @Nullable UiTreeView<String> ruleList;
    private final List<String> visibleIds = new ArrayList<>();
    private final List<UiButton> listButtons = new ArrayList<>();
    private final List<UiButton> filterButtons = new ArrayList<>();
    private final RuleEditorScreenButtonBar listBar = new RuleEditorScreenButtonBar();
    private final RuleEditorScreenButtonBar globalBar = new RuleEditorScreenButtonBar();
    private final RuleEditorScreenButtonBar filterBar = new RuleEditorScreenButtonBar(ROW_H, 2, true);
    private final List<UiButton> selectedButtons = new ArrayList<>();
    private @Nullable UiButton toggleButton;
    private @Nullable UiButton applyButton;
    private final Set<String> expandedCategories = new LinkedHashSet<>();
    private String lastQuery = "";
    private String lastTreeQuery = "";
    // 状态筛选下标（0 全部 / 1 已启用 / 2 已停用）与来源筛选下标（0 全部 / 1 覆盖层 / 2 数据包）
    private int statusFilter;
    private int sourceFilter;
    // 首载分段观测：首份快照后的首次刷新只记录一次，作为「列表可用」的终点
    private boolean firstReadyLogged;

    RuleEditorScreenListPage(Host host) {
        this.host = host;
        this.actions = new RuleEditorScreenListActions(host, this::selectedRuleId);
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
                graphics.drawString(rowFont, TextScroll.trimToWidth(rowFont, node.label().getString(), row.width() - 4),
                        row.x() + 2, row.y() + (row.height() - rowFont.lineHeight) / 2, THEME.palette().textSecondary(), false);
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
        UiButton edit = button(UI + "button.edit", UiButtonVariant.PRIMARY, () -> {
            String id = selectedRuleId(); if (id != null) host.openEditor(id);
        });
        toggleButton = button(UI + "button.enable", UiButtonVariant.SECONDARY, actions::toggleSelectedEnabled);
        UiButton copy = button(UI + "button.duplicate", UiButtonVariant.SECONDARY, actions::promptDuplicate);
        UiButton delete = button(UI + "button.delete", UiButtonVariant.DANGER, actions::deleteSelected);
        UiButton export = button(UI + "button.export", UiButtonVariant.SECONDARY, this::openExport);
        applyButton = button(UI + "button.apply", UiButtonVariant.PRIMARY, host::save);
        UiButton changes = button(UI + "button.changes", UiButtonVariant.SECONDARY, host::showChanges);
        selectedButtons.clear();
        selectedButtons.addAll(List.of(edit, toggleButton, copy, delete));
        globalBar.set(List.of(create, edit, toggleButton, copy, delete, export));
        listBar.set(List.of(applyButton, changes));
        listButtons.addAll(List.of(create, edit, toggleButton, copy, delete, export, applyButton, changes));
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(THEME, host.font(), Component.translatable(labelKey), variant, action::run).setChipStyle(true);
    }

    /*** 导出范围选项：标签、是否含数据包条目与当前可导出的条数。 */
    private record ExportScope(String scope, String labelKey, String countKey, boolean includeDatapack, int count) { }

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

    // 按范围收集当前显示体（含未保存草稿）并写盘；失败只提示，不改变任何规则状态
    private void exportRules(ExportScope option) {
        List<RulePackExporter.RuleEntry> entries = new ArrayList<>();
        for (String id : host.model().ruleIds()) {
            RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
            if (!snapshot.exportable() || (!option.includeDatapack() && !isOverlayOrigin(snapshot.origin()))) {
                continue;
            }
            RuleValue body = host.model().displayBody(id);
            if (body != null) {
                entries.add(new RulePackExporter.RuleEntry(id, body));
            }
        }
        if (entries.isEmpty()) {
            host.setNotice(Component.translatable(UI + "export.empty"), THEME.palette().warning());
            return;
        }
        try {
            RulePackExporter.Result result = RulePackExporter.export(option.scope(), entries);
            host.setNotice(Component.translatable(UI + "export.done", result.count(), result.displayPath()),
                    THEME.palette().success());
        } catch (IOException exception) {
            String reason = exception.getMessage() == null ? "" : exception.getMessage();
            host.setNotice(Component.translatable(UI + "export.failed", reason), THEME.palette().danger());
        }
    }

    // 列表条目操作随选中状态同步；切换按钮只显示下一步动作，布局按当前文案计算。
    void updateButtons() {
        String id = selectedRuleId();
        for (UiButton button : selectedButtons) {
            button.setEnabled(host.workspace().active() && id != null);
        }
        if (toggleButton != null) {
            boolean enabled = id != null && host.display().snapshot(id).enabled();
            toggleButton.setLabel(Component.translatable(UI + (enabled ? "button.disable" : "button.enable")));
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
        // 预览随行首见构建并缓存在展示快照上，滚动回来直接复用
        RuleRecipeView.renderRow(graphics, rowFont, row, snapshot.title(), snapshot.state(), color,
                snapshot.error() ? THEME.palette().danger() : THEME.palette().textSecondary(),
                snapshot.preview(), hovered, snapshot.active());
    }

    // ---- 列表维护 ----

    void refreshList() {
        if (ruleList == null) {
            return;
        }
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
        return host.display().snapshot(node.value()).preview().tagAt(host.font(),
                RuleRecipeView.recipeBounds(host.font(), row), mouseX, mouseY);
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

    // 悬停在规则条目上时的多行提示；未命中返回 null。
    @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (ruleList == null || !ruleList.bounds().contains(mouseX, mouseY)) {
            return null;
        }
        UiTreeNode<String> node = ruleList.nodeAt(mouseX, mouseY);
        if (node == null || node.value().startsWith("@")) {
            return null;
        }
        String id = node.value();
        // 悬停时才向展示快照索取配方预览与条件提示：未悬停的条目不生成提示
        RuleListDisplaySnapshot snapshot = host.display().snapshot(id);
        Component triggerTip = snapshot.preview().triggerTooltipAt(host.font(),
                RuleRecipeView.recipeBounds(host.font(), ruleList.nodeContentBounds(node)), mouseX, mouseY);
        if (triggerTip != null) return triggerTip;
        TagPreviewIcons.Tag tag = tagAt(mouseX, mouseY);
        Component tip = tag == null ? Component.translatable(UI + "recipe.condition_tree").append("\n")
                .append(snapshot.conditionTooltip(summaryLineBudget()))
                : tag.tooltip();
        if (snapshot.stateVisible()) tip = tip.copy().append("\n").append(snapshot.state());
        if (snapshot.previewUnavailable()) tip = tip.copy()
                .append("\n").append(Component.translatable(UI + "preview.unavailable"));
        return tip;
    }

    // 条件提示的行数预算随窗口高度变化；预算变化时展示快照重建一次提示文本
    private int summaryLineBudget() {
        return Math.max(2, host.screenHeight() / host.font().lineHeight - TOOLTIP_RESERVED_LINES);
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

    // 冻结时列表页动作按钮一并禁用（返回按钮不在其中）
    void setButtonsEnabled(boolean editable) {
        for (UiButton button : listButtons) {
            button.setEnabled(editable);
        }
    }
}
