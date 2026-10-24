package com.meteorite.itemdespawntowhat.client.edit;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 规则分类的静态骨架：已知分类的标识、层级、顺序与本地化显示名。
 *
 * <p>列表页按 {@link #roots()} 建树；快照下发的分类不在骨架内时按原文标识动态成组，
 * 挂在 {@link #EXTENSION} 分组下，不中断列表刷新。{@link #UNFINISHED} 与 {@link #ISSUES}
 * 只由客户端按草稿状态与校验问题使用，服务端分类器不产出这两个值；{@link #TEMPLATE}
 * 为随发布提供的模板规则预留。</p>
 */
public final class RuleCategorySkeleton {

    // 分类显示名的本地化 key 前缀
    public static final String LABEL_PREFIX = TypeLabels.UI_PREFIX + "category.";

    // 扩展分组：无产出、含未知产出与未知分类都归入这组语义
    public static final String EXTENSION = "extension";
    // 有问题分组：列表页把带校验问题或解码失败的条目归入这里，服务端分类器不产出该值
    public static final String ISSUES = "issues";
    // 未完成分组：列表页把有未保存改动的草稿条目归入这里，服务端分类器不产出该值
    public static final String UNFINISHED = "unfinished";
    // 模板分组：为随发布提供的模板规则预留，当前不产生该分类
    public static final String TEMPLATE = "template";

    // 已知分类的层级与顺序：实体父节点下按产出子类细分，其余为顶层分类
    private static final List<Category> ROOTS = List.of(
            new Category(ISSUES),
            new Category("entity", List.of(
                    new Category("entity.item"),
                    new Category("entity.entity"),
                    new Category("entity.experience"),
                    new Category("entity.mixed"))),
            new Category("block"),
            new Category("loot"),
            new Category("world"),
            new Category("mixed"),
            new Category(EXTENSION),
            new Category(UNFINISHED),
            new Category(DebugTestRules.CATEGORY),
            new Category(TEMPLATE));

    private RuleCategorySkeleton() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 已知分类的根节点，按列表展示顺序返回。
     *
     * @return 不可变的根分类列表，子分类层级与顺序随之确定
     */
    public static List<Category> roots() {
        return ROOTS;
    }

    /**
     * 已知分类的显示名本地化 key。
     *
     * @param id 分类标识，允许为 null
     * @return 已知分类的 key；未知分类返回 null，调用方按原文标识显示
     */
    public static @Nullable String knownLabelKey(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Category root : ROOTS) {
            if (root.id().equals(id)) {
                return root.labelKey();
            }
            for (Category child : root.children()) {
                if (child.id().equals(id)) {
                    return child.labelKey();
                }
            }
        }
        return null;
    }

    /**
     * 一条已知分类：稳定标识、子分类与本地化显示名。
     *
     * @param id 分类标识，同时也是快照下发的分类值
     * @param children 子分类；叶子分类为空列表
     */
    public record Category(String id, List<Category> children) {

        public Category {
            children = children == null ? List.of() : List.copyOf(children);
        }

        /**
         * 只有标识的叶子分类。
         *
         * @param id 分类标识
         */
        public Category(String id) {
            this(id, List.of());
        }

        /**
         * 分类显示名的本地化 key。
         *
         * @return 形如 {@code gui.itemdespawntowhat.edit.category.block} 的 key
         */
        public String labelKey() {
            return LABEL_PREFIX + id;
        }
    }
}
