package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.List;

/**
 * 条件编辑的共享定义能力：可添加的条件类型清单、按场景创建新叶子的工厂、解码所用的类型注册表。
 *
 * <p>本能力只描述「条件有哪些」以及「如何按场景新建叶子」，不持有具体表单、条件树、选中节点或导航状态，
 * 因此可以脱离任何一次编辑会话单独装配。宿主侧的打开前提交、正常关闭刷新与失效丢弃由
 * {@link ConditionHostCoordination} 承担，两者互不依赖。</p>
 */
public interface ConditionEditingSupport {

    /**
     * 提供条件选择器与画布工具栏可添加的条件类型。
     *
     * @return 可添加的条件类型清单
     */
    List<ConditionTreeContract.TypeOption> typeOptions();

    /**
     * 提供符合当前编辑场景的新叶工厂；画布新建催化剂叶不预写门槛。
     *
     * @return 当前编辑场景使用的新叶工厂
     */
    ConditionTreeContract.LeafFactory leafFactory();

    /**
     * 提供条件表达式解码使用的类型注册表。
     *
     * @return 条件类型注册表
     */
    TypeRegistry<ConditionType<?>> registry();
}
