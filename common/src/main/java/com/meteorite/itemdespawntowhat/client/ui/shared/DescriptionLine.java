package com.meteorite.itemdespawntowhat.client.ui.shared;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 悬停说明面板的一行语义内容：只描述「这一行是什么」，不携带具体颜色。
 *
 * <p><b>职责</b>：把条件与动作的说明拆成可排版的语义行，由渲染方
 * （{@code client/ui/widget/UiDescriptionTooltip}）按 {@link Kind} 排版、按 {@link Tone} 取主题色。
 * 条件画布、条件列表与效果列表因此共用同一套行模型，不各自拼字符串。</p>
 *
 * <p><b>排版</b>：缩进按层级计，渲染时换算成固定像素；{@link Kind#FIELD} 的标签参与整块面板的标签列对齐，
 * 其它类型的标签为 {@code null}；{@link Kind#ENTRY} 由渲染方补条目圆点，标签与正文都不带前缀。</p>
 *
 * @param kind 排版类型
 * @param tone 语义语气，渲染时映射为主题色
 * @param label 字段行左侧标签；非字段行为 {@code null}
 * @param text 行正文
 * @param indent 缩进层级，0 表示与面板文本左边距对齐
 */
public record DescriptionLine(Kind kind, Tone tone, @Nullable Component label, Component text, int indent) {

    /** 行的排版类型：决定缩进方式、是否参与标签列与条目圆点。 */
    public enum Kind {
        // 普通文本行
        TEXT,
        // 标签 + 取值行：标签与其它字段行左对齐
        FIELD,
        // 条目行：渲染时在行首补圆点
        ENTRY,
        // 分区标题：上方多留一段间距
        SECTION,
        // 纯间距行：不计入行数预算
        SPACER
    }

    /** 行的语义语气：渲染时按主题色表映射，条件与动作只表达含义、不挑颜色。 */
    public enum Tone {
        // 面板标题
        TITLE,
        // 分区标题
        SECTION,
        // 普通取值：枚举、开关、文本
        VALUE,
        // 可度量取值：数值、区间、时刻
        METRIC,
        // 注册表引用：维度、群系、方块、物品、流体
        REFERENCE,
        // 说明与未设置提示
        NOTE,
        // 问题与不可用提示
        DANGER,
        // 全部满足
        ALL,
        // 任一满足
        ANY,
        // 非
        NOT
    }

    public DescriptionLine {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tone, "tone");
        Objects.requireNonNull(text, "text");
        if (kind == Kind.FIELD && label == null) {
            throw new IllegalArgumentException("字段行必须带标签");
        }
        indent = Math.max(0, indent);
    }

    /**
     * 面板标题行。
     *
     * @param text 标题文本
     * @return 标题行
     */
    public static DescriptionLine title(Component text) {
        return new DescriptionLine(Kind.TEXT, Tone.TITLE, null, text, 0);
    }

    /**
     * 分区标题行。
     *
     * @param text 分区标题文本
     * @param indent 缩进层级
     * @return 分区标题行
     */
    public static DescriptionLine section(Component text, int indent) {
        return new DescriptionLine(Kind.SECTION, Tone.SECTION, null, text, indent);
    }

    /**
     * 标签 + 取值行；标签与同一面板内的其它字段标签对齐。
     *
     * @param label 字段标签
     * @param text 取值文本
     * @param tone 取值的语义语气
     * @param indent 缩进层级
     * @return 字段行
     */
    public static DescriptionLine field(Component label, Component text, Tone tone, int indent) {
        return new DescriptionLine(Kind.FIELD, tone, label, text, indent);
    }

    /**
     * 条目行，渲染时在行首补圆点。
     *
     * @param text 条目文本
     * @param tone 条目的语义语气
     * @param indent 缩进层级
     * @return 条目行
     */
    public static DescriptionLine entry(Component text, Tone tone, int indent) {
        return new DescriptionLine(Kind.ENTRY, tone, null, text, indent);
    }

    /**
     * 普通文本行。
     *
     * @param text 行文本
     * @param tone 语义语气
     * @param indent 缩进层级
     * @return 文本行
     */
    public static DescriptionLine text(Component text, Tone tone, int indent) {
        return new DescriptionLine(Kind.TEXT, tone, null, text, indent);
    }

    /**
     * 说明行：整行使用弱化语气。
     *
     * @param text 说明文本
     * @param indent 缩进层级
     * @return 说明行
     */
    public static DescriptionLine note(Component text, int indent) {
        return new DescriptionLine(Kind.TEXT, Tone.NOTE, null, text, indent);
    }

    /**
     * 纯间距行，用于分隔没有分区标题的段落。
     *
     * @return 间距行
     */
    public static DescriptionLine spacer() {
        return new DescriptionLine(Kind.SPACER, Tone.NOTE, null, Component.empty(), 0);
    }
}
