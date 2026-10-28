package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeViewModel;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import org.jetbrains.annotations.Nullable;

/**
 * 一次条件编辑的局部目标上下文：绑定实际条件字段在草稿中的绝对路径与其所属对象，
 * 并把条件树内部的相对节点坐标转换为草稿绝对路径。
 *
 * <p>上下文由实际条件控件的字段路径创建，因此条件树摘要与画布只面对当前这一个目标；
 * 树内坐标以 {@link ConditionTreeContract#ROOT_PATH} 为根，转换只做相对到绝对的映射，
 * 不判断入参是否「像绝对路径」。需要按绝对草稿路径定位的问题入口由发起方先转成树内坐标，
 * 不把绝对坐标混进本上下文。</p>
 */
public interface ConditionEditContext {

    /**
     * 条件树在草稿中的绝对作用域，即实际条件字段的字段路径。
     *
     * @return 条件字段的绝对草稿路径
     */
    String scope();

    /**
     * 条件字段所属对象在草稿中的绝对路径；规则级条件为空串。
     *
     * @return 所属对象的绝对草稿路径，规则级为空串
     */
    String ownerPath();

    /**
     * 把条件树内部坐标转换为本上下文的草稿绝对路径。
     *
     * <p>合法输入只有两种：恰好等于 {@link ConditionTreeContract#ROOT_PATH}，或以
     * {@code ROOT_PATH + "."} 开头。判定按点分隔的完整段边界，不接受 {@code conditionsExtra}
     * 这类仅字符串前缀相同的坐标；其余输入一律返回 null，不退回作用域根。</p>
     *
     * @param treePath 摘要给出的树内相对坐标，以 {@link ConditionTreeContract#ROOT_PATH} 为根
     * @return 本上下文下对应的绝对草稿路径；坐标不合法时返回 null
     */
    @Nullable
    String absolutePath(String treePath);

    /**
     * 打开本上下文对应的条件画布。
     *
     * @param treePath 需要定位的树内相对坐标；null 表示只打开作用域不定位节点
     * @param viewModel 本字段的展示协调器，不持有绝对路径
     * @return 是否已打开画布
     */
    boolean open(@Nullable String treePath, ConditionTreeViewModel viewModel);
}
