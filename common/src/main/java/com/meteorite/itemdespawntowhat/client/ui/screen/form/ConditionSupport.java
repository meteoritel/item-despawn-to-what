package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeEditor;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树控件所需的宿主能力：类型清单、新建叶子、编辑叶子、解码注册表。
 * <p>由屏幕提供实现，表单引擎只调用，不自己访问 core 注册表。
 */
public interface ConditionSupport {

    // 可添加的条件类型清单
    List<UiConditionTreeEditor.TypeOption> typeOptions();

    // 新建叶子的工厂
    UiConditionTreeEditor.LeafFactory leafFactory();

    // 打开叶子参数编辑页
    Consumer<ConditionNode.Leaf> onEditLeaf();

    // 问题定位可附带完整草稿字段路径；宿主可打开参数页并聚焦该字段。
    default void revealLeaf(ConditionNode.Leaf leaf, String fieldPath) {
        onEditLeaf().accept(leaf);
    }

    // 打开当前表达式的编辑作用域；默认宿主不提供叠加页。
    default boolean openTree(UiConditionTreeEditor tree, @Nullable String fieldPath) { return false; }

    // 条件表达式解码所用的类型注册表
    TypeRegistry<ConditionType<?>> registry();
}
