package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
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
    public record Tag(RuleCatalogType type, TaggedId reference, List<Member> members, UiCarousel carousel) {
        public Component label() { return RuleDisplayLabels.tagLabel(reference); }
        public Component tooltip() {
            var tip = CatalogTypePresentation.of(type).tags()
                    .map(support -> Component.translatable(UI + "supports." + support.description(), reference.serialized()))
                    .orElseGet(() -> label().copy());
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

    /** 展开已解析标签，名称与成员共用同一引用。 */
    public static Tag resolve(RuleCatalogType type, TaggedId reference) {
        List<Member> members = CatalogTypePresentation.of(type).tags().map(support -> support.members(reference.id())).orElseGet(List::of);
        return snapshot(type, reference, members);
    }

    // 方块物品按方块模型渲染：物品渲染在 GUI 中叠加方向光，交叉平面的两面会被点亮成不同亮度，
    // 且放大绘制时的非等比缩放会改变法线朝向，使同一模型在不同尺寸下打光不同；方块模型预览不做方向光。
    private static UiIcon previewIcon(Item item) {
        if (item instanceof BlockItem blockItem) {
            return PreviewIcons.block(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
        }
        return PreviewIcons.item(new ItemStack(item));
    }

    /** 将源物品标签中的方块成员映射成方块预览。 */
    public static Tag sourceBlocks(TaggedId reference) {
        List<Member> members = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, reference.id()))
                .map(set -> set.stream().map(Holder::value).filter(item -> item instanceof BlockItem)
                        .map(item -> ((BlockItem) item).getBlock())
                        .map(block -> new Member(block.getName(), PreviewIcons.block(BuiltInRegistries.BLOCK.getKey(block).toString()))).toList())
                .orElse(List.of());
        return snapshot(RuleCatalogType.ITEM, reference, members);
    }

    private static Tag snapshot(RuleCatalogType type, TaggedId reference, List<Member> members) {
        return new Tag(type, new TaggedId(reference.id(), true), members,
                new UiCarousel(members.stream().map(Member::icon).toList()));
    }

    static List<Member> itemMembers(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                .map(set -> set.stream().filter(holder -> holder.value() != Items.AIR)
                        .map(holder -> new Member(holder.value().getDescription(), previewIcon(holder.value()))).toList())
                .orElse(List.of());
    }

    static List<Member> blockMembers(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id))
                .map(set -> set.stream().map(holder -> new Member(holder.value().getName(),
                        CatalogTypePresentation.BLOCK.icon(BuiltInRegistries.BLOCK.getKey(holder.value())))).toList())
                .orElse(List.of());
    }

    static List<Member> entityMembers(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, id))
                .map(set -> set.stream().map(holder -> new Member(holder.value().getDescription(),
                        PreviewIcons.entity(BuiltInRegistries.ENTITY_TYPE.getKey(holder.value()).toString(), 0))).toList())
                .orElse(List.of());
    }

    static List<Member> fluidMembers(ResourceLocation id) {
        return BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id))
                .map(set -> set.stream().filter(holder -> !holder.value().defaultFluidState().isEmpty()
                                && holder.value().defaultFluidState().isSource())
                        .map(holder -> new Member(FluidPreviewIcons.label(BuiltInRegistries.FLUID.getKey(holder.value())),
                                PreviewIcons.fluid(BuiltInRegistries.FLUID.getKey(holder.value()).toString()))).toList())
                .orElse(List.of());
    }

}
