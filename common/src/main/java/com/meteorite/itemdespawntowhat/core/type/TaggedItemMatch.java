package com.meteorite.itemdespawntowhat.core.type;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物品引用匹配器：判定物品栈是否命中 TaggedId 引用（非 tag 比注册表物品，tag 比标签）。
 * 全仓唯一的物品与 tag 匹配实现：效果式消耗、催化剂固定成本、催化剂条件求值都编译本类后复用，
 * 任何口径调整只在这里发生，调用方不得复刻判定。
 * 语义：null 引用与未注册的物品 id 解析为「永不命中」，不存在的标签因标签成员为空而不命中；
 * 标签键在编译时按引用预建，逐实体判定只做标签成员查找，不在热点循环内重复创建 TagKey。
 */
public final class TaggedItemMatch {

    // 空匹配器：无有效引用时共用同一实例，matches 恒为 false
    private static final TaggedItemMatch NEVER = new TaggedItemMatch(List.of(), List.of());

    // 已解析的具体物品引用；未注册的 id 不入表（等价于永不命中）
    private final List<Item> items;
    // 预建的标签键；标签不存在时 is 判定自然为 false
    private final List<TagKey<Item>> tags;

    private TaggedItemMatch(List<Item> items, List<TagKey<Item>> tags) {
        this.items = items;
        this.tags = tags;
    }

    /**
     * 编译一组引用，热点循环只编译一次后重复使用。
     * @param references 待编译的引用列表，可为 null；null 元素直接跳过
     * @return 可复用的匹配器，无有效引用时返回空匹配器
     */
    public static TaggedItemMatch compileAll(@Nullable List<TaggedId> references) {
        if (references == null || references.isEmpty()) {
            return NEVER;
        }
        List<Item> items = new ArrayList<>(references.size());
        List<TagKey<Item>> tags = new ArrayList<>(references.size());
        for (TaggedId reference : references) {
            if (reference == null) {
                continue;
            }
            if (reference.tag()) {
                tags.add(TagKey.create(Registries.ITEM, reference.id()));
            } else if (BuiltInRegistries.ITEM.containsKey(reference.id())) {
                // 必须用 containsKey：DefaultedRegistry（物品注册表）对未知 id 返回默认值而非 null
                items.add(BuiltInRegistries.ITEM.get(reference.id()));
            }
        }
        return items.isEmpty() && tags.isEmpty() ? NEVER : new TaggedItemMatch(List.copyOf(items), List.copyOf(tags));
    }

    /**
     * 编译单个引用。
     * @param reference 单个引用，可为 null（null 解析为永不命中）
     * @return 该引用的匹配器
     */
    public static TaggedItemMatch compile(@Nullable TaggedId reference) {
        // singletonList 允许 null 元素，null 由 compileAll 统一跳过
        return compileAll(Collections.singletonList(reference));
    }

    /**
     * 判定物品栈是否命中已编译的任一引用。
     * @param stack 待判定的物品栈
     * @return true 表示命中至少一个引用
     */
    public boolean matches(ItemStack stack) {
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        for (Item item : items) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }
}
