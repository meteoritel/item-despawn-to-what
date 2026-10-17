package com.meteorite.itemdespawntowhat.client.edit;

import java.util.List;

/**
 * 规则分类的静态骨架：已知分类的标识、层级、顺序与本地化显示名。
 *
 * <p>列表页按 {@link #roots()} 建树；快照下发的分类不在骨架内时按原文标识动态成组，
 * 挂在 {@link #EXTENSION} 分组下，不中断列表刷新。</p>
 */
public final class RuleCategorySkeleton {

    // 分类显示名的本地化 key 前缀
    public static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.category.";

    // 扩展分组：无产出、含未知产出与未知分类都归入这组语义
    public static final String EXTENSION = "extension";

    // 已知分类的层级与顺序：实体父节点下按产出子类细分，其余为顶层分类
    private static final List<Category> ROOTS = List.of(
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
            new Category(DebugTestRules.CATEGORY));

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
