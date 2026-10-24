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

    /** 条件类型选项：宿主注入的可选条件类型，{@code id} 用于生成默认叶与 JSON 序列化，{@code label} 只用于界面展示。 */
    public record TypeOption(ResourceLocation id, Component label) {
    }

    /**
     * 新条件叶工厂：宿主注入，画布在类型选择确认后按类型 id 生成默认参数的新叶。
     * 返回 {@code null} 表示该类型不支持可视化新增，画布放弃插入。
     */
    @FunctionalInterface
    public interface LeafFactory {
        @Nullable ConditionNode create(ResourceLocation type);
    }

    /** 表达式变更监听：画布完成一次结构改动后回调新表达式，宿主据此同步草稿与展示。 */
    public interface Listener {
        void onExpressionChanged(ConditionExpression expression);
    }

    /**
     * 选中项导航：缩进摘要与画布共同实现，让宿主以同一组语义在两种渲染间同步选中项。
     * 路径为条件树 JSON 路径；{@code selectPath} 接受节点路径或其下参数字段路径，{@code null} 表示清除选中。
     */
    public interface Navigation {

        @Nullable String selectedPath();

        @Nullable ConditionNode selectedNode();

        void selectPath(@Nullable String path);
    }

    private ConditionTreeContract() {
    }
}
