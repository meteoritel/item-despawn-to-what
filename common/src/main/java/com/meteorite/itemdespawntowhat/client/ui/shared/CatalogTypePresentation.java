package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 选择目录类型在客户端的名称、预览、标签与默认可见策略。 */
public enum CatalogTypePresentation {
    ITEM("item", "items", true, CatalogTypePresentation::itemLabel,
            CatalogPreviewIcons::itemIcon, TagPreviewIcons::itemMembers, () -> BuiltInRegistries.ITEM,
            CatalogTypePresentation::hiddenItem),
    BLOCK("block", "blocks", true, CatalogTypePresentation::blockLabel,
            CatalogPreviewIcons::blockIcon, TagPreviewIcons::blockMembers, () -> BuiltInRegistries.BLOCK,
            CatalogTypePresentation::visible),
    ENTITY("entity", "entities", true, CatalogTypePresentation::entityLabel,
            CatalogPreviewIcons::entityIcon, TagPreviewIcons::entityMembers, () -> BuiltInRegistries.ENTITY_TYPE,
            CatalogTypePresentation::visible),
    LOOT_TABLE("loot_table", "items", false, CatalogTypePresentation::noLabel,
            null, null, () -> null, CatalogTypePresentation::visible),
    BIOME("biome", "biomes", true, id -> translatedLabel("biome", id),
            null, null, CatalogTypePresentation::biomeRegistry,
            CatalogTypePresentation::visible),
    DIMENSION("dimension", "items", false, id -> translatedLabel("dimension", id),
            null, null, () -> null, CatalogTypePresentation::visible),
    TAG("tag", "items", false, CatalogTypePresentation::noLabel,
            null, null, () -> null, CatalogTypePresentation::visible),
    FLUID("fluid", "fluids", true, FluidPreviewIcons::label,
            CatalogPreviewIcons::fluidIcon, TagPreviewIcons::fluidMembers, () -> BuiltInRegistries.FLUID,
            CatalogTypePresentation::hiddenFluid),
    MOB_EFFECT("mob_effect", "items", false, CatalogTypePresentation::mobEffectLabel,
            CatalogPreviewIcons::mobEffectIcon, null, () -> null,
            CatalogTypePresentation::visible);

    private static final Set<String> HIDDEN_ITEM_IDS = Set.of(
            "minecraft:air", "minecraft:light", "minecraft:barrier", "minecraft:structure_void",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:debug_stick", "minecraft:petrified_oak_slab");

    private final String scopeLabel;
    private final String tagDescription;
    private final boolean supportsTags;
    private final LabelResolver labelResolver;
    private final @Nullable IconResolver iconResolver;
    private final @Nullable TagMembersResolver tagMembersResolver;
    private final Supplier<@Nullable Registry<?>> tagRegistry;
    private final Predicate<String> hidden;

    CatalogTypePresentation(String scopeLabel, String tagDescription, boolean supportsTags, LabelResolver labelResolver,
                            @Nullable IconResolver iconResolver, @Nullable TagMembersResolver tagMembersResolver,
                            Supplier<@Nullable Registry<?>> tagRegistry, Predicate<String> hidden) {
        this.scopeLabel = scopeLabel;
        this.tagDescription = tagDescription;
        this.supportsTags = supportsTags;
        this.labelResolver = labelResolver;
        this.iconResolver = iconResolver;
        this.tagMembersResolver = tagMembersResolver;
        this.tagRegistry = tagRegistry;
        this.hidden = hidden;
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

    /** 返回标签预览提示使用的成员类别文案后缀。 */
    public String tagDescription() {
        return tagDescription;
    }

    /** 按该类目录对应的注册表解析资源名称；无专用名称时返回 null。 */
    public @Nullable Component label(ResourceLocation id) {
        return labelResolver.resolve(id);
    }

    /** 按该类目录解析图标；文字目录返回 null。 */
    public @Nullable UiIcon icon(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId.startsWith("#") ? rawId.substring(1) : rawId);
        return id == null || iconResolver == null ? null : iconResolver.resolve(id);
    }

    /** 该目录是否支持注册表标签引用。 */
    public boolean supportsTags() {
        return supportsTags;
    }

    /** 返回客户端当前可访问的标签注册表；世界目录在没有世界时返回 null。 */
    public @Nullable Registry<?> tagRegistry() {
        return tagRegistry.get();
    }

    /** 返回该类型标签的可见成员。 */
    public List<TagPreviewIcons.Member> tagMembers(ResourceLocation id) {
        return tagMembersResolver == null ? List.of() : tagMembersResolver.resolve(id);
    }

    /** 判断普通目录条目是否由默认策略隐藏。 */
    public boolean hidden(String id) {
        return hidden.test(id);
    }

    private static @Nullable Registry<?> biomeRegistry() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME);
    }

    private static Component itemLabel(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).map(net.minecraft.world.item.Item::getDescription).orElse(null);
    }

    private static Component blockLabel(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id).map(net.minecraft.world.level.block.Block::getName).orElse(null);
    }

    private static Component entityLabel(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .map(net.minecraft.world.entity.EntityType::getDescription).orElse(null);
    }

    private static Component mobEffectLabel(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT.getOptional(id)
                .map(net.minecraft.world.effect.MobEffect::getDisplayName).orElse(null);
    }

    private static Component translatedLabel(String type, ResourceLocation id) {
        String key = type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    private static @Nullable Component noLabel(ResourceLocation id) {
        return null;
    }

    private static boolean visible(String id) {
        return false;
    }

    private static boolean hiddenItem(String id) {
        return HIDDEN_ITEM_IDS.contains(id);
    }

    private static boolean hiddenFluid(String id) {
        return "minecraft:empty".equals(id) || FluidPreviewIcons.isExcluded(id);
    }

    @FunctionalInterface
    interface LabelResolver {
        @Nullable Component resolve(ResourceLocation id);
    }

    @FunctionalInterface
    interface IconResolver {
        @Nullable UiIcon resolve(ResourceLocation id);
    }

    @FunctionalInterface
    interface TagMembersResolver {
        List<TagPreviewIcons.Member> resolve(ResourceLocation id);
    }
}
