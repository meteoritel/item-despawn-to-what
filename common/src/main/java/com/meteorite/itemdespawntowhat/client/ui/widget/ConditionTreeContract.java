package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树控件家族共享的宿主注入契约与路径常量。
 * <p>两类使用方都在本包内：{@link UiConditionTreeSummary}（缩进只读摘要）与
 * {@link UiConditionGraphEditor}（画布编辑器）。摘要类不再承载这些类型；画布按契约注入条件类型选项、
 * 叶工厂与变更监听；两者都实现 {@link Navigation}，让宿主在两种渲染之间以同一组语义读写选中项。</p>
 * <p>本类型只依赖条件树数据模型、{@link ResourceLocation} 与 {@link Component}，不依赖界面层其它子包。</p>
 */
public final class ConditionTreeContract {

    // 根节点的路径（与条件树 JSON 的字段名一致）
    public static final String ROOT_PATH = RuleFields.CONDITIONS;

    // 条件类型选项：由宿主注入，id 用于工厂与序列化，label 用于展示
    public record TypeOption(ResourceLocation id, Component label) {
    }

    // 新条件叶工厂：宿主按类型 id 生成默认参数；返回 null 表示该类型不支持可视化新增
    @FunctionalInterface
    public interface LeafFactory {
        @Nullable ConditionNode create(ResourceLocation type);
    }

    // 表达式变更监听：画布编辑器完成结构改动后回调
    public interface Listener {
        void onExpressionChanged(ConditionExpression expression);
    }

    // 选中项导航：摘要与画布共用的选中路径语义
    public interface Navigation {

        @Nullable String selectedPath();

        @Nullable ConditionNode selectedNode();

        void selectPath(@Nullable String path);
    }

    private ConditionTreeContract() {
    }
}
