package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/***
 * 目录候选提供器：把表单字段（注册表提示串 / 字段名 / 字段类型）映射到服务端来源目录，
 * 让玩家按名字挑选物品、方块、实体、流体、战利品表、群系、维度与标签，而不必手打技术 id。
 * <p>数据来自 {@link RuleEditClientWorkspace} 已缓存的目录页；某类型首次被查询且会话处于
 * ACTIVE 时按需请求第一页，之后的帧直接复用缓存（界面会自行按输入过滤）。
 * <p>只读 protocol DTO 与客户端工作区，不引用任何网络载荷类型。
 */
public final class CatalogSuggestions implements SuggestionProvider {

    // 首次请求的页大小
    private static final int FIRST_PAGE_SIZE = 64;

    // 单个字段最多返回的候选数（界面仍会按输入过滤）
    private static final int MAX_SUGGESTIONS = 256;

    // 已发起过首次请求的目录类型，避免每帧重复请求
    private final Set<RuleCatalogType> requested = EnumSet.noneOf(RuleCatalogType.class);

    // 无状态构造：候选按需从工作区缓存读取
    public CatalogSuggestions() {
    }

    // 返回该字段的候选值；无法判定目录类型时返回空列表
    @Override
    public List<Suggestion> suggestions(EditorField field) {
        if (field == null) {
            return List.of();
        }
        // 标签类字段既要标签（# 前缀）也要其底层注册表条目
        boolean tagField = field.type() == EditorFieldType.TAG || field.type() == EditorFieldType.TAG_LIST;
        RuleCatalogType base = resolveType(field);
        List<RuleCatalogType> types = new ArrayList<>();
        if (base != null) {
            types.add(base);
        }
        if (tagField && base != RuleCatalogType.TAG) {
            types.add(RuleCatalogType.TAG);
        }
        if (types.isEmpty()) {
            return List.of();
        }
        Map<String, Suggestion> candidates = new LinkedHashMap<>();
        for (RuleCatalogType type : types) {
            for (RuleCatalogEntry entry : entriesOf(type)) {
                if (candidates.size() >= MAX_SUGGESTIONS) {
                    break;
                }
                add(candidates, entry.id(), entry.label(), tagField);
            }
        }
        return List.copyOf(candidates.values());
    }

    // 加入候选：标签字段同时给出带 # 与不带 # 的两种写法，按 value 去重
    private static void add(Map<String, Suggestion> candidates, @Nullable String id, @Nullable String label, boolean tagField) {
        if (id == null || id.isBlank()) {
            return;
        }
        candidates.putIfAbsent(id, new Suggestion(id, labelOf(id, label)));
        if (!tagField) {
            return;
        }
        String tagged = id.startsWith("#") ? id.substring(1) : "#" + id;
        if (!tagged.isBlank()) {
            candidates.putIfAbsent(tagged, new Suggestion(tagged, labelOf(tagged, label)));
        }
    }

    // 取某类型的目录页；首次查询且会话 ACTIVE 时按需请求第一页，返回当前已缓存内容（可能为空）
    private List<RuleCatalogEntry> entriesOf(RuleCatalogType type) {
        RuleEditClientWorkspace workspace = RuleEditClientWorkspace.instance();
        RuleCatalog catalog = workspace.catalog(type);
        if (catalog == null && requested.add(type) && workspace.state() == RuleEditClientState.ACTIVE) {
            workspace.requestCatalog(type, null, 0, FIRST_PAGE_SIZE);
            catalog = workspace.catalog(type);
        }
        return catalog == null ? List.of() : catalog.entries();
    }

    // 字段 -> 目录类型：先看注册表提示串，再按字段名兜底
    private static @Nullable RuleCatalogType resolveType(EditorField field) {
        RuleCatalogType byRegistry = fromRegistry(field.registry());
        return byRegistry != null ? byRegistry : fromName(field.name());
    }

    // 注册表提示串 -> 目录类型（原版注册表键；覆盖目录九类，未覆盖的返回 null）
    private static @Nullable RuleCatalogType fromRegistry(@Nullable String registry) {
        if (registry == null || registry.isBlank()) {
            return null;
        }
        String key = registry.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("#")) {
            key = key.substring(1);
        }
        return switch (key) {
            case "minecraft:item", "item", "minecraft:items" -> RuleCatalogType.ITEM;
            case "minecraft:block", "block", "minecraft:blocks" -> RuleCatalogType.BLOCK;
            case "minecraft:entity_type", "entity_type", "minecraft:entity_types" -> RuleCatalogType.ENTITY;
            case "minecraft:fluid", "fluid", "minecraft:fluids" -> RuleCatalogType.FLUID;
            case "minecraft:loot_table", "loot_table", "minecraft:loot_tables" -> RuleCatalogType.LOOT_TABLE;
            case "minecraft:worldgen/biome", "biome", "minecraft:biomes" -> RuleCatalogType.BIOME;
            case "minecraft:dimension", "dimension", "minecraft:dimensions", "minecraft:level" -> RuleCatalogType.DIMENSION;
            case "minecraft:mob_effect", "mob_effect", "minecraft:mob_effects" -> RuleCatalogType.MOB_EFFECT;
            default -> null;
        };
    }

    // 字段名关键词兜底（如 loot_table 字段只有 RESOURCE_LOCATION 类型、没有注册表提示）
    private static @Nullable RuleCatalogType fromName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (key.contains("loot")) {
            return RuleCatalogType.LOOT_TABLE;
        }
        if (key.contains("biome")) {
            return RuleCatalogType.BIOME;
        }
        if (key.contains("dimension") || key.contains("level")) {
            return RuleCatalogType.DIMENSION;
        }
        if (key.contains("fluid")) {
            return RuleCatalogType.FLUID;
        }
        if (key.contains("entity")) {
            return RuleCatalogType.ENTITY;
        }
        if (key.contains("block")) {
            return RuleCatalogType.BLOCK;
        }
        if (key.contains("item")) {
            return RuleCatalogType.ITEM;
        }
        return null;
    }

    // 展示名：目录 label 是本地化键时交给翻译系统，否则原样显示
    private static Component labelOf(String value, @Nullable String label) {
        if (label == null || label.isBlank()) {
            return Component.literal(value);
        }
        return looksLikeTranslationKey(label) ? Component.translatable(label) : Component.literal(label);
    }

    // 形如 item.minecraft.chicken / biome.minecraft.plains 的键直接走翻译；含空格或命名空间分隔符的按原文
    private static boolean looksLikeTranslationKey(String label) {
        return label.indexOf(' ') < 0 && label.indexOf(':') < 0 && label.indexOf('.') > 0;
    }
}
