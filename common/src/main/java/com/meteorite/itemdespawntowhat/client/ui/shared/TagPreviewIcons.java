package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiCarousel;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;

/** 从已同步注册表取得标签成员；展示完整标签名，悬停说明匹配范围与成员数量。 */
public final class TagPreviewIcons {
    private static final String UI = TypeLabels.UI_PREFIX + "tag.";
    private TagPreviewIcons() { }

    /** 可展开的标签快照，不将单个示例物品解释为唯一匹配对象。 */
    public record Tag(RuleCatalogType type, String id, List<Member> members, UiCarousel carousel) {
        public Component label() { return RuleDisplayLabels.tagLabel(id); }
        public Component tooltip() {
            var tip = Component.translatable(UI + "supports." + kind(type), id);
            return tip.append("\n").append(Component.translatable(UI + "expand", members.size()));
        }
        public UiIcon icon() {
            if (members.isEmpty()) return PreviewIcons.item(new ItemStack(Items.NAME_TAG));
            return new UiIcon.Rendered(18, 18, (graphics, box) -> carousel.render(graphics, box, Util.getMillis()),
                    () -> carousel.prepare(Util.getMillis()));
        }
    }

    /** 保留成员的实际名称与模型，供轮播和展开列表共用。 */
    public record Member(Component name, UiIcon icon) { }

    public static Tag resolve(RuleCatalogType type, String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
        List<Member> members = id == null ? List.of() : switch (type) {
            case ITEM -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                    .map(set -> set.stream().filter(holder -> holder.value() != Items.AIR)
                            .map(holder -> new Member(holder.value().getDescription(), previewIcon(holder.value()))).toList())
                    .orElse(List.of());
            case BLOCK -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id))
                    .map(set -> set.stream().map(holder -> new Member(holder.value().getName(),
                            CatalogPreviewIcons.iconFor(RuleCatalogType.BLOCK, BuiltInRegistries.BLOCK.getKey(holder.value()).toString()))).toList())
                    .orElse(List.of());
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, id))
                    .map(set -> set.stream().map(holder -> new Member(holder.value().getDescription(),
                            PreviewIcons.entity(BuiltInRegistries.ENTITY_TYPE.getKey(holder.value()).toString(), 0))).toList())
                    .orElse(List.of());
            case FLUID -> BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id))
                    .map(set -> set.stream().filter(holder -> !holder.value().defaultFluidState().isEmpty()
                                    && holder.value().defaultFluidState().isSource())
                            .map(holder -> new Member(FluidPreviewIcons.label(BuiltInRegistries.FLUID.getKey(holder.value())),
                                    PreviewIcons.fluid(BuiltInRegistries.FLUID.getKey(holder.value()).toString()))).toList())
                    .orElse(List.of());
            default -> List.of();
        };
        members = members.stream().filter(member -> member.icon() != null).toList();
        return snapshot(type, raw, members);
    }

    // 方块物品按方块模型渲染：物品渲染在 GUI 中叠加方向光，交叉平面的两面会被点亮成不同亮度，
    // 且放大绘制时的非等比缩放会改变法线朝向，使同一模型在不同尺寸下打光不同；方块模型预览不做方向光。
    private static UiIcon previewIcon(Item item) {
        if (item instanceof BlockItem blockItem) {
            return PreviewIcons.block(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
        }
        return PreviewIcons.item(new ItemStack(item));
    }

    // 使用源物品对应方块时，将物品标签映射成真正的方块模型，而不是名称牌或空气物品。
    public static Tag sourceBlocks(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
        List<Member> members = id == null ? List.of() : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                .map(set -> set.stream().map(Holder::value).filter(item -> item instanceof BlockItem)
                        .map(item -> ((BlockItem) item).getBlock())
                        .map(block -> new Member(block.getName(), PreviewIcons.block(BuiltInRegistries.BLOCK.getKey(block).toString()))).toList())
                .orElse(List.of());
        return snapshot(RuleCatalogType.ITEM, raw, members);
    }

    private static Tag snapshot(RuleCatalogType type, String raw, List<Member> members) {
        return new Tag(type, raw.startsWith("#") ? raw : "#" + raw, members,
                new UiCarousel(members.stream().map(Member::icon).toList()));
    }

    private static String kind(RuleCatalogType type) {
        return switch (type) {
            case BLOCK -> "blocks";
            case ENTITY -> "entities";
            case FLUID -> "fluids";
            case BIOME -> "biomes";
            default -> "items";
        };
    }
}
