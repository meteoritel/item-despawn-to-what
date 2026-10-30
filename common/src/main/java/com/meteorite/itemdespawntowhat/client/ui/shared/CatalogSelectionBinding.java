package com.meteorite.itemdespawntowhat.client.ui.shared;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 一次目录选择的接收契约：接收端声明模式与初始值，确认交付完整引用快照。 */
public record CatalogSelectionBinding(Mode mode, List<String> initialSelection, Consumer<List<String>> onConfirmed) {
    /** 按同一模式冻结初始选择，避免显示与确认采用不同的选择基数。 */
    public CatalogSelectionBinding {
        Objects.requireNonNull(mode);
        initialSelection = mode.snapshot(initialSelection);
        Objects.requireNonNull(onConfirmed);
    }

    /** 接收端按本次编辑操作声明选择基数。 */
    public enum Mode {
        /** 替换一个引用，空选择表示清空。 */
        SINGLE,
        /** 协调整段引用集合。 */
        MULTIPLE;

        /**
         * 冻结有序、去重的选择；空白文本留在接收端的原始数据中。
         * @throws IllegalArgumentException 单选接收端提供多项引用
         */
        public List<String> snapshot(List<String> selected) {
            if (this == SINGLE && selected.size() > 1) {
                throw new IllegalArgumentException("Single selection received multiple references");
            }
            LinkedHashSet<String> references = new LinkedHashSet<>();
            for (String id : selected) {
                if (!id.isBlank()) references.add(id);
            }
            return List.copyOf(references);
        }
    }

    /** 交付确认快照；取消由关闭表达，空快照属于有效确认。 */
    public void confirm(List<String> selection) {
        onConfirmed.accept(mode.snapshot(selection));
    }
}
