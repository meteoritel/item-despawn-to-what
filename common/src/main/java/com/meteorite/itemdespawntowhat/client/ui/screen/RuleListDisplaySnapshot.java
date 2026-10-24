package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionTreeDescription;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
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
    private static final String UNNAMED_KEY = TypeLabels.UI_PREFIX + "recipe.unnamed";

    private final @Nullable RuleValue body;
    private final String title;
    private final @Nullable String category;
    private final String origin;
    private final boolean unfinished;
    private final boolean exportable;
    private final boolean active;
    private final boolean error;
    private final boolean enabled;
    private final boolean issues;
    private final boolean previewUnavailable;
    private final Component state;
    private final boolean stateVisible;

    // 按需构建的展示产物：三者互不依赖，各自只建一次
    private @Nullable RuleRecipeView preview;
    private @Nullable List<DescriptionLine> conditionLines;
    private @Nullable String searchText;

    // 行首文本的度量缓存：状态宽与标题裁剪只随字体/语言与行宽变化，静止帧不再逐帧测文本
    private @Nullable Font metricsFont;
    private @Nullable Object metricsLanguage;
    private int stateWidth;
    private int clippedWidth = -1;
    private String clippedTitle = "";

    RuleListDisplaySnapshot(@Nullable RuleValue body, String title, @Nullable String category,
                            String origin, boolean unfinished, boolean exportable,
                            boolean active, boolean error, boolean enabled, boolean issues,
                            boolean previewUnavailable,
                            Component state, boolean stateVisible) {
        this.body = body;
        this.title = title;
        this.category = category;
        this.origin = origin;
        this.unfinished = unfinished;
        this.exportable = exportable;
        this.active = active;
        this.error = error;
        this.enabled = enabled;
        this.issues = issues;
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

    /**
     * 状态文字在给定字体下的宽度；按字体与语言令牌缓存，绘制不再逐帧测量。
     *
     * @param font 当前绘制字体
     * @return 状态文字宽度（状态为空时为 0）
     */
    int stateWidth(Font font) {
        ensureMetrics(font);
        return stateWidth;
    }

    /**
     * 标题按可用宽度裁剪后的绘制文本；同一快照在同一字体/语言与行宽下只裁剪一次。
     *
     * <p>裁剪口径与 {@link TextScroll#trimToWidth} 完全一致，列表页不再另写截断算法。</p>
     *
     * @param font  当前绘制字体
     * @param width 去掉状态文字与间距后的可用宽度
     * @return 可直接绘制的标题文本
     */
    String clippedTitle(Font font, int width) {
        ensureMetrics(font);
        if (width != clippedWidth) {
            clippedWidth = width;
            clippedTitle = TextScroll.trimToWidth(font, title, width);
        }
        return clippedTitle;
    }

    // 字体或语言变化后状态宽与裁剪结果都会变：整份作废，下次绘制重算一次
    private void ensureMetrics(Font font) {
        Object language = Language.getInstance();
        if (font == metricsFont && language == metricsLanguage) {
            return;
        }
        metricsFont = font;
        metricsLanguage = language;
        stateWidth = font.width(state);
        clippedWidth = -1;
    }

    // 快照下发的分类；未声明分类时为 null
    @Nullable String category() {
        return category;
    }

    // 条目来源：overlay / datapack / mixed；本次新建的草稿按覆盖层来源处理
    String origin() {
        return origin;
    }

    // 是否属于「未完成」草稿分组：本次新建或有未保存改动
    boolean unfinished() {
        return unfinished;
    }

    // 是否可导出为数据包：有可读规则体且不是屏蔽/删除/非法条目
    boolean exportable() {
        return exportable;
    }

    // 当前是否按「启用」显示（草稿改动与新建的条目以草稿为准）
    boolean active() {
        return active;
    }

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

    // 条件树说明行：结构固定，首次悬停时构建一次，行数裁剪交给渲染方
    List<DescriptionLine> conditionLines() {
        if (conditionLines == null) {
            conditionLines = ConditionTreeDescription.lines(
                    body == null ? null : body.child(RuleFields.CONDITIONS));
        }
        return conditionLines;
    }

    // 搜索用的小写规则体文本（覆盖当前草稿的资源 ID、备注与参数）；首次搜索时构建一次
    String searchText() {
        if (searchText == null) {
            searchText = body == null ? "" : body.toJson().toLowerCase(Locale.ROOT);
        }
        return searchText;
    }
}
