package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 选择目录类型在客户端的名称、预览、标签与默认可见策略。 */
public enum CatalogTypePresentation {
    ITEM("item", illustrated(CatalogTypePresentation::itemLabel, CatalogPreviewIcons::itemIcon),
            tags("items", () -> BuiltInRegistries.ITEM, TagPreviewIcons::itemMembers), CatalogTypePresentation::hiddenItem),
    BLOCK("block", illustrated(CatalogTypePresentation::blockLabel, CatalogPreviewIcons::blockIcon),
            tags("blocks", () -> BuiltInRegistries.BLOCK, TagPreviewIcons::blockMembers), CatalogTypePresentation::visible),
    ENTITY("entity", illustrated(CatalogTypePresentation::entityLabel, CatalogPreviewIcons::entityIcon),
            tags("entities", () -> BuiltInRegistries.ENTITY_TYPE, TagPreviewIcons::entityMembers), CatalogTypePresentation::visible),
    LOOT_TABLE("loot_table", text(CatalogTypePresentation::noLabel), noTags(), CatalogTypePresentation::visible),
    BIOME("biome", text(id -> translatedKey("biome." + id.getNamespace() + "." + id.getPath(), id)),
            biomeTags(), CatalogTypePresentation::visible),
    DIMENSION("dimension", text(CatalogTypePresentation::dimensionLabel), noTags(), CatalogTypePresentation::visible),
    TAG("tag", text(CatalogTypePresentation::noLabel), noTags(), CatalogTypePresentation::visible),
    FLUID("fluid", illustrated(FluidPreviewIcons::label, CatalogPreviewIcons::fluidIcon),
            tags("fluids", () -> BuiltInRegistries.FLUID, TagPreviewIcons::fluidMembers), CatalogTypePresentation::hiddenFluid),
    MOB_EFFECT("mob_effect", illustrated(CatalogTypePresentation::mobEffectLabel, CatalogPreviewIcons::mobEffectIcon),
            noTags(), CatalogTypePresentation::visible);

    private static final Set<String> HIDDEN_ITEM_IDS = Set.of(
            "minecraft:air", "minecraft:light", "minecraft:barrier", "minecraft:structure_void",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:debug_stick", "minecraft:petrified_oak_slab");

    private final String scopeLabel;
    private final Appearance appearance;
    private final @Nullable TagSupport tags;
    private final Predicate<ResourceLocation> hidden;

    CatalogTypePresentation(String scopeLabel, Appearance appearance, @Nullable TagSupport tags, Predicate<ResourceLocation> hidden) {
        this.scopeLabel = scopeLabel;
        this.appearance = appearance;
        this.tags = tags;
        this.hidden = hidden;
    }

    /** 标签能力；没有世界时动态 registry 暂不可用，与类型没有标签能力分别表达。 */
    public record TagSupport(String description, Supplier<@Nullable Registry<?>> registry,
                             Optional<TagMembersResolver> preview) {
        /** 返回当前已同步的 registry，动态 registry 等待世界加载。 */
        public @Nullable Registry<?> currentRegistry() { return registry.get(); }

        /** 读取成员；无成员预览的类型沿用标签占位图标。 */
        public List<TagPreviewIcons.Member> members(ResourceLocation id) {
            return preview.map(resolver -> resolver.resolve(id)).orElseGet(List::of);
        }
    }

    private record Appearance(LabelResolver label, Optional<IconResolver> icon) { }

    private static Appearance text(LabelResolver label) { return new Appearance(label, Optional.empty()); }

    private static Appearance illustrated(LabelResolver label, IconResolver icon) {
        return new Appearance(label, Optional.of(icon));
    }

    private static @Nullable TagSupport noTags() { return null; }

    private static TagSupport tags(String description, Supplier<@Nullable Registry<?>> registry,
                                             TagMembersResolver members) {
        return new TagSupport(description, registry, Optional.of(members));
    }

    private static TagSupport biomeTags() {
        return new TagSupport("biomes", CatalogTypePresentation::biomeRegistry, Optional.empty());
    }

    /** 返回一个目录类型的展示定义。 */
    public static CatalogTypePresentation of(RuleCatalogType type) {
        return switch (type) {
            case ITEM -> ITEM;
            case BLOCK -> BLOCK;
            case ENTITY -> ENTITY;
            case LOOT_TABLE -> LOOT_TABLE;
            case BIOME -> BIOME;
            case DIMENSION -> DIMENSION;
            case TAG -> TAG;
            case FLUID -> FLUID;
            case MOB_EFFECT -> MOB_EFFECT;
        };
    }

    /** 返回此类目录范围按钮使用的本地化键后缀。 */
    public String scopeLabel() {
        return scopeLabel;
    }

    /** 已选引用和摘要使用同一名称策略；缺少本地名称时按资源路径给出可读名称。 */
    public Component label(ResourceLocation id) {
        Component name = appearance.label().resolve(id);
        return name != null ? name : this == LOOT_TABLE || this == TAG
                ? Component.literal(id.toString()) : RuleDisplayLabels.idFallback(id);
    }

    /** 按来源类型解释目录名称，翻译 key 和原文不依赖字符形状猜测。 */
    public Component label(TaggedId reference, String sourceLabel) {
        if (reference.tag()) return RuleDisplayLabels.tagLabel(reference);
        return switch (this) {
            case ITEM, BLOCK, ENTITY, MOB_EFFECT, BIOME -> translatedKey(sourceLabel, reference.id());
            case LOOT_TABLE -> Component.literal(sourceLabel);
            case DIMENSION, FLUID -> label(reference.id());
            case TAG -> RuleDisplayLabels.tagLabel(reference);
        };
    }

    /** 按该类目录解析图标；文字目录返回 null。 */
    public @Nullable UiIcon icon(ResourceLocation id) {
        return appearance.icon().map(resolver -> resolver.resolve(id)).orElse(null);
    }

    /** 返回标签能力；不支持标签的类型没有该能力。 */
    public Optional<TagSupport> tags() { return Optional.ofNullable(tags); }

    /** 判断普通目录条目是否由默认策略隐藏。 */
    public boolean hidden(ResourceLocation id) {
        return hidden.test(id);
    }

    private static @Nullable Registry<?> biomeRegistry() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME);
    }

    private static Component itemLabel(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).map(item -> translatedKey(item.getDescriptionId(), id)).orElse(null);
    }

    private static Component blockLabel(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id).map(block -> translatedKey(block.getDescriptionId(), id)).orElse(null);
    }

    private static Component entityLabel(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .map(entity -> translatedKey(entity.getDescriptionId(), id)).orElse(null);
    }

    private static Component mobEffectLabel(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT.getOptional(id)
                .map(effect -> translatedKey(effect.getDescriptionId(), id)).orElse(null);
    }

    private static Component dimensionLabel(ResourceLocation id) {
        String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return translatedKey(key, id);
    }

    private static Component translatedKey(String key, ResourceLocation id) {
        return Language.getInstance().has(key) ? Component.translatable(key) : RuleDisplayLabels.idFallback(id);
    }

    private static @Nullable Component noLabel(ResourceLocation id) {
        return null;
    }

    private static boolean visible(ResourceLocation id) {
        return false;
    }

    private static boolean hiddenItem(ResourceLocation id) {
        return HIDDEN_ITEM_IDS.contains(id.toString());
    }

    private static boolean hiddenFluid(ResourceLocation id) {
        return "minecraft:empty".equals(id.toString()) || FluidPreviewIcons.isExcluded(id);
    }

    @FunctionalInterface
    interface LabelResolver {
        @Nullable Component resolve(ResourceLocation id);
    }

    @FunctionalInterface
    interface IconResolver {
        @Nullable UiIcon resolve(ResourceLocation id);
    }

    /** 标签成员预览读取策略。 */
    @FunctionalInterface
    public interface TagMembersResolver {
        /** 返回指定标签的可见成员。 */
        List<TagPreviewIcons.Member> resolve(ResourceLocation id);
    }
}
