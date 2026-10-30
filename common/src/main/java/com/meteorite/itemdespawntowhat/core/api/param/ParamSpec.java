package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * 单个类型参数的唯一事实来源：字段名、wire presence、取值域、默认值、新建初值与编辑器投影信息。
 * <p>codec、语义校验、编辑器描述符与新建初值四个读点都从同一份声明取值，避免同一事实写多遍后漂移。</p>
 * <p>声明只覆盖单字段事实：跨字段关系（区间顺序、模式相关必填）、注册表引用存在性与服务端运行期上限
 * 仍由各类型的 validateParams / validateRuntimeLimits 负责。</p>
 */
public sealed interface ParamSpec<V> permits IntSpec, FloatSpec, DoubleSpec, BoolSpec, EnumSpec, RefSpec,
        RefListSpec, ResLocSpec, ResLocListSpec, MapSpec, NestedSpec, NestedListSpec {

    // 声明元数据
    ParamMeta meta();

    // JSON 字段名
    default String name() {
        return meta().name();
    }

    // JSON 出现形态
    default ParamPresence presence() {
        return meta().presence();
    }

    // 语义单位
    default ParamUnit unit() {
        return meta().unit();
    }

    // 编辑器特殊形态
    default ParamShape shape() {
        return meta().shape();
    }

    // label key 片段：非 null 表示完整后缀，null 表示按「类型片段.字段名」推导
    default @Nullable String labelSuffix() {
        return meta().labelSuffix();
    }

    // 提示文案 key 片段；null 表示无提示
    default @Nullable String hintSuffix() {
        return meta().hintSuffix();
    }

    // 新建规则的初值；null 表示不预写该字段
    default @Nullable Object initialValue() {
        return meta().initialValue();
    }

    // 编辑器是否强制填写
    default boolean editorRequired() {
        return meta().editorRequired();
    }

    /** 该参数是否在编辑器中展示 */
    default boolean editorVisible() {
        return meta().editorVisible();
    }

    /** 参数在编辑器中的展示层级。 */
    default ParamTier tier() {
        return meta().tier();
    }

    default SelectionIntent selectionIntent() {
        return meta().selectionIntent();
    }

    // 数值字段的常用取值按钮
    default List<ParamPreset> presets() {
        return meta().presets();
    }

    // 是否必填
    default boolean required() {
        return presence() == ParamPresence.REQUIRED;
    }

    // 是否可空
    default boolean nullable() {
        return presence() == ParamPresence.NULLABLE;
    }

    // 省略该字段时后端采用的默认值；必填与可空字段没有默认值
    @Nullable V defaultValue();

    /**
     * 参数 wire 值的编解码器（不含字段包装与 presence）。
     * <p>新建初值与省略字段默认值的编辑器投影都从这里编码，避免编辑器再维护一套 Java 对象到 JSON 的转换规则；
     * 声明因此同时约束 wire 形态与预填形态。</p>
     */
    Codec<V> valueCodec();

    /**
     * 单字段语义校验：值域与必填；跨字段关系由类型自己校验。
     *
     * @param value 参数值；可空字段可能为 null
     * @param issues 问题收集器
     * @param fieldPath 该参数对象在规则中的字段路径
     * @return 是否合法
     */
    boolean check(@Nullable V value, IssueCollector issues, String fieldPath);

    /**
     * 将声明与所属参数对象的取值绑定；codec 字段和批量校验共用该 getter。
     *
     * @param getter 从参数对象读取本字段的函数，可空字段直接返回 null
     * @return 不可变字段绑定
     */
    default <P> ParamBinding<P, V> bind(Function<? super P, ? extends V> getter) {
        return new ParamBinding<>(this, getter);
    }

}
