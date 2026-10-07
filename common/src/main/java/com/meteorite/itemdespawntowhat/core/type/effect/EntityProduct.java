package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.RefChecks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/*** 实体产出子类：专用参数独立建模，公共执行字段由外层效果承载。 */
public sealed interface EntityProduct permits EntityProduct.Item, EntityProduct.Generic, EntityProduct.Experience {
    MapCodec<EntityProduct> CODEC = strict(Codec.STRING.comapFlatMap(value -> switch (value) {
        case "item", "entity", "experience" -> DataResult.success(value);
        default -> DataResult.error(() -> "未知实体产出子类: " + value);
    }, value -> value).dispatchMap("variant", EntityProduct::variant, EntityProduct::codecFor));

    String variant();
    boolean validate(IssueCollector issues, String path);

    // DFU 分发的 keys 只有判别键，需提供所有子类字段并拒绝当前子类不适用的参数。
    private static MapCodec<EntityProduct> strict(MapCodec<EntityProduct> dispatch) {
        return new MapCodec<>() {
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.concat(Stream.of(ops.createString("variant")),
                        Stream.of(Item.CODEC, Generic.CODEC, Experience.CODEC).flatMap(codec -> codec.keys(ops))).distinct();
            }
            @Override public <T> DataResult<EntityProduct> decode(DynamicOps<T> ops, MapLike<T> input) {
                return dispatch.decode(ops, input).flatMap(product -> {
                    Set<T> allowed = new HashSet<>(codecFor(product.variant()).keys(ops).toList());
                    for (String common : Set.of("type", "variant", "delay_ticks", "chance", "conditions")) {
                        allowed.add(ops.createString(common));
                    }
                    var unknown = input.entries().map(Pair::getFirst).filter(key -> !allowed.contains(key)).findFirst();
                    return unknown.<DataResult<EntityProduct>>map(key -> DataResult.error(() -> "产出子类 "
                            + product.variant() + " 不接受字段 " + ops.getStringValue(key).result().orElse("?")))
                            .orElseGet(() -> DataResult.success(product));
                });
            }
            @Override public <T> RecordBuilder<T> encode(EntityProduct product, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return dispatch.encode(product, ops, prefix);
            }
        };
    }

    // 新产出可扩充此分发；第三方独立效果仍通过顶层 EffectType SPI 注册。
    private static MapCodec<? extends EntityProduct> codecFor(String variant) {
        return switch (variant) {
            case "item" -> Item.CODEC;
            case "entity" -> Generic.CODEC;
            case "experience" -> Experience.CODEC;
            default -> throw new IllegalArgumentException("未知实体产出子类: " + variant);
        };
    }

    /*** 掉落物数量按物品件数计算。 */
    record Item(TaggedId item, int count) implements EntityProduct {
        public static final MapCodec<Item> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.fieldOf("item").forGetter(Item::item),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Item::count)
        ).apply(instance, Item::new));
        @Override public String variant() { return "item"; }
        @Override public boolean validate(IssueCollector issues, String path) {
            String field = ParamChecks.child(path, "item");
            boolean valid = ParamChecks.required(item, "item", issues, field);
            valid &= RefChecks.check(item, BuiltInRegistries.ITEM, "item", issues, field);
            valid &= ParamChecks.inRange(count, 1, 64, "count", issues, ParamChecks.child(path, "count"));
            return valid;
        }
    }

    /*** 通用实体包含生物和其他实体，特殊产物固定走专用入口。 */
    record Generic(TaggedId entity, int count, int age) implements EntityProduct {
        public static final MapCodec<Generic> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.fieldOf("entity").forGetter(Generic::entity),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Generic::count),
                Codec.INT.optionalFieldOf("age", 0).forGetter(Generic::age)
        ).apply(instance, Generic::new));
        @Override public String variant() { return "entity"; }
        @Override public boolean validate(IssueCollector issues, String path) {
            String field = ParamChecks.child(path, "entity");
            boolean valid = ParamChecks.required(entity, "entity", issues, field);
            valid &= RefChecks.check(entity, BuiltInRegistries.ENTITY_TYPE, "entity", issues, field);
            valid &= ParamChecks.inRange(count, 1, 64, "count", issues, ParamChecks.child(path, "count"));
            if (entity != null) {
                boolean special = entity.tag()
                        ? BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, entity.id()))
                            .map(tag -> tag.stream().anyMatch(holder -> isSpecial(holder.value()))).orElse(false)
                        : BuiltInRegistries.ENTITY_TYPE.getOptional(entity.id()).map(Generic::isSpecial).orElse(false);
                if (special) {
                    issues.error("掉落物和经验球请使用对应的专用产出子类", null, field);
                    valid = false;
                }
            }
            return valid;
        }
        // 防止标签运行期变更后绕过专用入口约束。
        public static boolean isSpecial(EntityType<?> type) {
            return type == EntityType.ITEM || type == EntityType.EXPERIENCE_ORB;
        }
    }

    /*** 经验数量按点数计算，倍率取本组实际支付的源物品件数。 */
    record Experience(int amount, boolean perSourceItem) implements EntityProduct {
        public static final MapCodec<Experience> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 65536).optionalFieldOf("amount", 1).forGetter(Experience::amount),
                Codec.BOOL.optionalFieldOf("per_source_item", false).forGetter(Experience::perSourceItem)
        ).apply(instance, Experience::new));
        @Override public String variant() { return "experience"; }
        @Override public boolean validate(IssueCollector issues, String path) {
            return ParamChecks.inRange(amount, 1, 65536, "amount", issues, ParamChecks.child(path, "amount"));
        }
    }
}
