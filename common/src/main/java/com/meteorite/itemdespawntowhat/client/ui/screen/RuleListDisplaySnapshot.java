package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.shared.ConditionTreeTextSummary;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则列表条目的一份展示快照：管理页显示、搜索与悬停提示需要的全部内容。
 *
 * <p>规则体、标题、分类、启用状态、状态文字与错误标记在构建时一次确定；配方预览在行首次可见时
 * 构建，条件提示在悬停时构建，搜索文本在首次搜索时构建，构建后各自保留一份。同一份快照供多次
 * 刷新与多帧渲染复用，因此渲染与搜索逐键刷新都不再重复读取规则体或做深拷贝。</p>
 *
 * <p>快照只表达「构建那一刻」的展示结果，不订阅后续改动；失效与重建由
 * {@link RuleListDisplayCache} 按快照版本与草稿修订负责。</p>
 */
final class RuleListDisplaySnapshot {

    // 未命名规则的回退文案
    private static final String UNNAMED_KEY = "gui.itemdespawntowhat.edit.recipe.unnamed";

    private final @Nullable RuleValue body;
    private final String title;
    private final @Nullable String category;
    private final boolean active;
    private final boolean error;
    private final boolean enabled;
    private final boolean issues;
    private final boolean deleteMarked;
    private final boolean previewUnavailable;
    private final Component state;
    private final boolean stateVisible;

    // 按需构建的展示产物：三者互不依赖，各自只建一次
    private @Nullable RuleRecipeView preview;
    private @Nullable Component conditionTooltip;
    private int conditionTooltipLines = -1;
    private @Nullable String searchText;

    RuleListDisplaySnapshot(@Nullable RuleValue body, String title, @Nullable String category,
                            boolean active, boolean error, boolean enabled, boolean issues,
                            boolean deleteMarked, boolean previewUnavailable,
                            Component state, boolean stateVisible) {
        this.body = body;
        this.title = title;
        this.category = category;
        this.active = active;
        this.error = error;
        this.enabled = enabled;
        this.issues = issues;
        this.deleteMarked = deleteMarked;
        this.previewUnavailable = previewUnavailable;
        this.state = state;
        this.stateVisible = stateVisible;
    }

    /**
     * 规则标题：显式显示名优先，未命名时回退到本地化占位。
     *
     * @param id   规则 id，用于识别「标题退化为 id 原文」的未命名条目
     * @param body 规则体，允许为 null（条目不存在或没有可读的生效视图）
     * @return 列表与页头共用的标题文本
     */
    static String titleOf(String id, @Nullable RuleValue body) {
        String label = body == null ? "" : RuleNaming.ruleTitle(body, RuleDisplayLabels::label).getString();
        return label.isBlank() || label.equals(id) ? Component.translatable(UNNAMED_KEY).getString() : label;
    }

    // 展示标题
    String title() {
        return title;
    }

    // 快照下发的分类；未声明分类时为 null
    @Nullable String category() {
        return category;
    }

    // 当前是否按「启用」显示（草稿改动、新建与恢复中的条目以草稿为准）
    boolean active() {
        return active;
    }

    // 是否存在错误级问题
    boolean error() {
        return error;
    }

    // 规则体自身是否声明为启用（列表切换按钮据此显示下一步动作）
    boolean enabled() {
        return enabled;
    }

    // 条目是否带服务端问题清单（筛选「问题」用）
    boolean issues() {
        return issues;
    }

    // 是否带覆盖层删除标记
    boolean deleteMarked() {
        return deleteMarked;
    }

    // 首帧预览是否不可用（悬停提示追加说明用）
    boolean previewUnavailable() {
        return previewUnavailable;
    }

    // 渲染用状态文字：没有状态时为空文本，避免调用方判空
    Component state() {
        return state;
    }

    // 状态文字是否为空
    boolean stateVisible() {
        return stateVisible;
    }

    // 配方预览：随首次可见构建，分帧绘制与标签命中测试共用同一份
    RuleRecipeView preview() {
        if (preview == null) {
            preview = new RuleRecipeView(body);
        }
        return preview;
    }

    // 条件树提示文本：行数预算随窗口高度变化，预算变化时重建一次
    Component conditionTooltip(int maxLines) {
        if (conditionTooltip == null || conditionTooltipLines != maxLines) {
            conditionTooltip = ConditionTreeTextSummary.format(
                    body == null ? null : body.child(RuleFields.CONDITIONS), maxLines,
                    NaturalSummary::conditionTreeLeaf);
            conditionTooltipLines = maxLines;
        }
        return conditionTooltip;
    }

    // 搜索用的小写规则体文本（覆盖当前草稿的资源 ID、备注与参数）；首次搜索时构建一次
    String searchText() {
        if (searchText == null) {
            searchText = body == null ? "" : body.toJson().toLowerCase(Locale.ROOT);
        }
        return searchText;
    }
}
