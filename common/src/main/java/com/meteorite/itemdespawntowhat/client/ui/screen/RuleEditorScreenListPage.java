package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategories;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategorySkeleton;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.ConditionTreeTextSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeNode;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeView;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.debug.DebugProfile;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
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

        // 条目显示名（未命名时回退到本地化占位）
        String labelText(String id);

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
    private static final int FILTER_COUNT = 4;
    private static final int SEARCH_MAX_LENGTH = 128;
    private static final int MORE_MODAL_WIDTH = 300;
    private static final int TAG_MODAL_WIDTH = 280;
    private static final int TOOLTIP_RESERVED_LINES = 8;
    // i18n 前缀
    private static final String UI = "gui.itemdespawntowhat.edit.";

    private final Host host;
    private final RuleEditorScreenListActions actions;

    // ---- 列表控件与视图状态 ----
    private @Nullable UiTextInput searchField;
    private @Nullable UiSegmentedControl filterControl;
    private @Nullable UiTreeView<String> ruleList;
    private final List<String> visibleIds = new ArrayList<>();
    private final List<UiButton> listButtons = new ArrayList<>();
    private final RuleEditorScreenButtonBar listBar = new RuleEditorScreenButtonBar();
    private final RuleEditorScreenButtonBar globalBar = new RuleEditorScreenButtonBar();
    private final List<UiButton> selectedButtons = new ArrayList<>();
    private @Nullable UiButton toggleButton;
    private @Nullable UiButton applyButton;
    private final Set<String> expandedCategories = new LinkedHashSet<>();
    private final Map<String, RuleRecipeView> recipeViews = new LinkedHashMap<>();
    private final Map<String, Component> conditionTooltips = new LinkedHashMap<>();
    private String lastQuery = "";
    private String lastTreeQuery = "";
    private int filterIndex;
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
        filterControl = new UiSegmentedControl(THEME, font, filterOptions());
        filterControl.setSelected(filterOptions().get(filterIndex).value());
        filterControl.setOnChanged(value -> {
            filterIndex = indexOfFilter(value);
            refreshList();
        });
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

    private List<UiSegmentedControl.Option> filterOptions() {
        return List.of(
                new UiSegmentedControl.Option("all", Component.translatable(UI + "filter.all")),
                new UiSegmentedControl.Option("enabled", Component.translatable(UI + "filter.enabled")),
                new UiSegmentedControl.Option("disabled", Component.translatable(UI + "filter.disabled")),
                new UiSegmentedControl.Option("issue", Component.translatable(UI + "filter.issue")));
    }

    private int indexOfFilter(String value) {
        for (int i = 0; i < FILTER_COUNT; i++) {
            if (filterOptions().get(i).value().equals(value)) {
                return i;
            }
        }
        return 0;
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
        UiButton more = button(UI + "button.more", UiButtonVariant.SECONDARY, this::openMore);
        UiButton delete = button(UI + "button.delete", UiButtonVariant.DANGER, actions::deleteSelected);
        applyButton = button(UI + "button.apply", UiButtonVariant.PRIMARY, host::save);
        UiButton changes = button(UI + "button.changes", UiButtonVariant.SECONDARY, host::showChanges);
        selectedButtons.clear();
        selectedButtons.addAll(List.of(edit, toggleButton, copy, delete, more));
        globalBar.set(List.of(create, edit, toggleButton, copy, delete, more));
        listBar.set(List.of(applyButton, changes));
        listButtons.addAll(List.of(create, edit, toggleButton, copy, delete, more, applyButton, changes));
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(THEME, host.font(), Component.translatable(labelKey), variant, action::run).setChipStyle(true);
    }

    /*** 纵向菜单项，避免窄屏和英文长按钮挤出弹窗。 */
    private record MenuAction(String key, Runnable action) { }

    private void openMore() {
        if (selectedRuleId() == null) { actions.noSelection(); return; }
        UiModal modal = UiModal.create(THEME, host.font()).title(Component.translatable(UI + "button.more"));
        UiListView<MenuAction> list = new UiListView<>(THEME, host.font(),
                (graphics, rowFont, item, index, row, selected, hovered, focused) -> graphics.drawString(rowFont,
                        Component.translatable(UI + item.key()), row.x() + 4, row.y() + 4, THEME.palette().textPrimary(), false));
        String id = selectedRuleId();
        RuleValue body = id == null ? null : host.model().displayBody(id);
        boolean masked = body != null && body.flag(RuleFields.DELETE, false);
        list.setItems(List.of(new MenuAction(masked ? "button.unmask" : "button.mask", actions::maskSelected),
                new MenuAction("button.restore", actions::restoreSelected)));
        list.setRowHeight(20);
        list.setActivateOnSingleClick(true);
        list.setOnActivate(item -> { host.modals().close(modal); item.action().run(); });
        modal.contentWidget(list, 44).cancel(Component.translatable(UI + "button.cancel"));
        modal.preferredWidth(MORE_MODAL_WIDTH).layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    // 列表条目操作随选中状态同步；切换按钮只显示下一步动作，布局按当前文案计算。
    void updateButtons() {
        String id = selectedRuleId();
        for (UiButton button : selectedButtons) {
            button.setEnabled(host.workspace().active() && id != null);
        }
        if (toggleButton != null) {
            RuleValue body = id == null ? null : host.model().displayBody(id);
            boolean enabled = body != null && (!body.has(RuleFields.ENABLED) || body.flag(RuleFields.ENABLED, false));
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
        int searchWidth = Math.min(SEARCH_MAX_WIDTH, area.width() / 2);
        if (searchField != null) {
            searchField.setBounds(area.x(), searchY, searchWidth, ROW_H);
            searchField.render(graphics, renderFont, mouseX, mouseY);
        }
        if (filterControl != null) {
            filterControl.setBounds(area.x() + searchWidth + 2, searchY, area.width() - searchWidth - 2, ROW_H);
            filterControl.render(graphics, renderFont, mouseX, mouseY);
        }
        int listY = searchY + ROW_H + RuleEditorPageSupport.PAD;
        int available = Math.max(0, area.bottom() - listY);
        if (ruleList != null) {
            ruleList.setBounds(area.x(), listY, area.width(), available);
            ruleList.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    // 名称和状态放第一行；原注册名位置改为图标配方，不显示内部规则 ID。
    private void renderRuleRow(GuiGraphics graphics, Font rowFont, String id, UiRect row, boolean hovered) {
        RuleSnapshotEntry entry = host.model().entry(id);
        boolean active = isRuleActive(id, entry);
        int color = active ? THEME.palette().textPrimary() : THEME.palette().textDisabled();
        RuleRecipeView.renderRow(graphics, rowFont, row, host.labelText(id), ruleState(id), color,
                host.model().hasError(id) ? THEME.palette().danger() : THEME.palette().textSecondary(),
                recipeViews.get(id), hovered, active);
    }

    // 状态用明确文字，正常启用不占用配方行；草稿标志与问题提示不再使用符号。
    private Component ruleState(String id) {
        if (host.model().hasError(id)) return Component.translatable(UI + "recipe.status.error");
        EditSession session = host.model().session(id);
        if (session != null && (session.isDirty() || host.model().isRestoring(id)))
            return Component.translatable(UI + "recipe.status.pending");
        RuleSnapshotEntry entry = host.model().entry(id);
        if (entry != null && RuleSnapshotEntry.STATUS_MASKED.equals(entry.status()))
            return Component.translatable(UI + "recipe.status.masked");
        return isRuleActive(id, entry) ? Component.empty() : Component.translatable(UI + "recipe.status.disabled");
    }

    // ---- 列表维护 ----

    void refreshList() {
        if (ruleList == null) {
            return;
        }
        // 首载分段观测：一次刷新只读一次开关；关闭时除布尔判断外不取时间、不分配对象
        final boolean profile = DebugProfile.enabled();
        final long refreshStarted = profile ? DebugProfile.start() : 0L;
        long filterMicros = 0L;
        long bodyMicros = 0L;
        long previewMicros = 0L;
        long tooltipMicros = 0L;
        // 未收到首份快照不能判为空；有效空快照和筛选结果为空才使用空列表文案。
        ruleList.setEmptyMessage(Component.translatable(UI + (host.model().isLoaded() ? "list.empty" : "list.loading")));
        String selectedValue = ruleList.selectedNode() == null ? null : ruleList.selectedNode().value();
        int scrollOffset = ruleList.scrollOffset();
        visibleIds.clear();
        recipeViews.clear();
        conditionTooltips.clear();
        String query = lastQuery == null ? "" : lastQuery.toLowerCase(Locale.ROOT).trim();
        int summaryLines = Math.max(2, host.screenHeight() / host.font().lineHeight - TOOLTIP_RESERVED_LINES);
        for (String id : host.model().ruleIds()) {
            long filterStarted = profile ? DebugProfile.start() : 0L;
            RuleSnapshotEntry entry = host.model().entry(id);
            if (!matchesFilter(id, entry)) {
                filterMicros += profile ? DebugProfile.micros(filterStarted) : 0L;
                continue;
            }
            if (!query.isEmpty()
                    && !id.toLowerCase(Locale.ROOT).contains(query)
                    && !host.labelText(id).toLowerCase(Locale.ROOT).contains(query)
                    && !matchesBodyQuery(id, query)) {
                filterMicros += profile ? DebugProfile.micros(filterStarted) : 0L;
                continue;
            }
            filterMicros += profile ? DebugProfile.micros(filterStarted) : 0L;
            visibleIds.add(id);
            long bodyStarted = profile ? DebugProfile.start() : 0L;
            RuleValue body = host.model().displayBody(id);
            bodyMicros += profile ? DebugProfile.micros(bodyStarted) : 0L;
            long previewStarted = profile ? DebugProfile.start() : 0L;
            recipeViews.put(id, new RuleRecipeView(body));
            previewMicros += profile ? DebugProfile.micros(previewStarted) : 0L;
            long tooltipStarted = profile ? DebugProfile.start() : 0L;
            conditionTooltips.put(id, ConditionTreeTextSummary.format(
                    body == null ? null : body.child(RuleFields.CONDITIONS), summaryLines, NaturalSummary::conditionTreeLeaf));
            tooltipMicros += profile ? DebugProfile.micros(tooltipStarted) : 0L;
        }
        long treeStarted = profile ? DebugProfile.start() : 0L;
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
            // 快照下发的分类优先；未声明时回落到按产出的判定
            RuleSnapshotEntry entry = host.model().entry(id);
            long categoryBodyStarted = profile ? DebugProfile.start() : 0L;
            String category = RuleCategories.category(entry == null ? null : entry.category(),
                    id, host.model().displayBody(id));
            bodyMicros += profile ? DebugProfile.micros(categoryBodyStarted) : 0L;
            UiTreeNode<String> group = groups.get(category);
            if (group == null) {
                group = unknownCategoryNode(groups, category);
            }
            group.addChild(id, Component.literal(host.labelText(id)));
        }
        List<UiTreeNode<String>> nonempty = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            UiTreeNode<String> root = roots.get(index);
            RuleCategorySkeleton.Category definition = skeleton.get(index);
            if (definition.children().isEmpty()) {
                if (!root.children().isEmpty()) nonempty.add(counted(root));
                continue;
            }
            // 父分类只收下有内容的子分类；直接声明父分类的规则按叶子保留
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
        long treeMicros = profile ? DebugProfile.micros(treeStarted) : 0L;
        long rootsStarted = profile ? DebugProfile.start() : 0L;
        ruleList.setRoots(nonempty);
        ruleList.setSelectedNode(findNode(nonempty, selectedValue));
        ruleList.setScrollOffset(scrollOffset);
        // 首份快照后的首次刷新即「列表可用」，与开关状态无关地只认一次
        boolean firstReady = host.model().isLoaded() && !firstReadyLogged;
        if (firstReady) {
            firstReadyLogged = true;
        }
        if (profile) {
            logRefresh(refreshStarted, filterMicros, bodyMicros, previewMicros, tooltipMicros, treeMicros,
                    DebugProfile.micros(rootsStarted), firstReady);
        }
    }

    // 首载分段观测：一次刷新的分段耗时；first 表示首份快照后的首次刷新（列表可用时刻）
    private void logRefresh(long refreshStarted, long filterMicros, long bodyMicros, long previewMicros,
                            long tooltipMicros, long treeMicros, long rootsMicros, boolean firstReady) {
        JsonObject data = new JsonObject();
        data.addProperty("rules", host.model().ruleIds().size());
        data.addProperty("visible", visibleIds.size());
        data.addProperty("snapshot_version", host.model().snapshotVersion());
        data.addProperty("filter_index", filterIndex);
        data.addProperty("query_chars", lastQuery == null ? 0 : lastQuery.length());
        data.addProperty("first", firstReady);
        // 取体是本次刷新全部规则体读取的累计，包含建树阶段按分类读取的那一次
        data.addProperty("filter_ms", filterMicros / 1000.0);
        data.addProperty("body_ms", bodyMicros / 1000.0);
        data.addProperty("preview_ms", previewMicros / 1000.0);
        data.addProperty("tooltip_ms", tooltipMicros / 1000.0);
        data.addProperty("tree_ms", treeMicros / 1000.0);
        data.addProperty("set_roots_ms", rootsMicros / 1000.0);
        data.addProperty("total_ms", DebugProfile.micros(refreshStarted) / 1000.0);
        DebugProfile.event(DebugProfile.SCENE_FIRST_LOAD, "CLIENT_LIST_REFRESH", data);
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

    // 当前草稿中的资源 ID、备注与参数也参与搜索；不触发目录查询。
    private boolean matchesBodyQuery(String id, String query) {
        RuleValue body = host.model().displayBody(id);
        return body != null && body.toJson().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesFilter(String id, @Nullable RuleSnapshotEntry entry) {
        boolean active = isRuleActive(id, entry);
        return switch (filterIndex) {
            case 1 -> active;
            case 2 -> !active;
            case 3 -> entry != null && !entry.issues().isEmpty() || host.model().hasError(id);
            default -> true;
        };
    }

    // 尚未修改的条目以快照状态为准；改动、恢复与新建条目以当前草稿为准。
    private boolean isRuleActive(String id, @Nullable RuleSnapshotEntry entry) {
        EditSession session = host.model().session(id);
        if (session != null && (session.isDirty() || host.model().isCreated(id) || host.model().isRestoring(id))) {
            return !session.draft().isDeleted() && session.draft().getBoolean(RuleFields.ENABLED, true);
        }
        return entry == null || RuleSnapshotEntry.STATUS_ACTIVE.equals(entry.status());
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
        if (filterControl != null && filterControl.mouseClicked(mouseX, mouseY, button)) {
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
        host.modals().push(UiModal.create(THEME, host.font()).title(Component.translatable(UI + "tag.title", tag.id()))
                .contentWidget(new TagCarouselView(host.font(), tag), Math.clamp(host.screenHeight() - 70, 110, 220))
                .preferredWidth(TAG_MODAL_WIDTH).cancel(Component.translatable(UI + "button.close"))
                .layoutCentered(host.screenWidth(), host.screenHeight()));
        return true;
    }

    // 指针必须先通过树的内容门禁，再命中配方标签。
    private @Nullable TagPreviewIcons.Tag tagAt(double mouseX, double mouseY) {
        if (ruleList == null) return null;
        UiTreeNode<String> node = ruleList.nodeAt(mouseX, mouseY);
        if (node == null) return null;
        RuleRecipeView recipe = recipeViews.get(node.value());
        if (recipe == null) return null;
        UiRect row = ruleList.nodeContentBounds(node);
        return recipe.tagAt(host.font(), RuleRecipeView.recipeBounds(host.font(), row), mouseX, mouseY);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = ruleList != null && ruleList.mouseReleased(mouseX, mouseY, button);
        consumed |= searchField != null && searchField.mouseReleased(mouseX, mouseY, button);
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
        RuleRecipeView recipe = recipeViews.get(id);
        Component triggerTip = recipe == null ? null : recipe.triggerTooltipAt(host.font(),
                RuleRecipeView.recipeBounds(host.font(), ruleList.nodeContentBounds(node)), mouseX, mouseY);
        if (triggerTip != null) return triggerTip;
        TagPreviewIcons.Tag tag = tagAt(mouseX, mouseY);
        Component tip = tag == null ? Component.translatable(UI + "recipe.condition_tree").append("\n")
                .append(conditionTooltips.getOrDefault(id, Component.empty()))
                : tag.tooltip();
        Component state = ruleState(id);
        if (!state.getString().isBlank()) tip = tip.copy().append("\n").append(state);
        if (RulePreviewIcons.unavailable(host.model().displayBody(id))) tip = tip.copy()
                .append("\n").append(Component.translatable(UI + "preview.unavailable"));
        return tip;
    }

    // ---- 焦点与使能 ----

    void addFocusTargets(UiFocusManager focus) {
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
    }

    // 冻结时列表页动作按钮一并禁用（返回按钮不在其中）
    void setButtonsEnabled(boolean editable) {
        for (UiButton button : listButtons) {
            button.setEnabled(editable);
        }
    }
}
