package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamPreset;
import com.meteorite.itemdespawntowhat.core.api.param.RefSpec;
import com.meteorite.itemdespawntowhat.core.api.param.SelectionIntent;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
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
import java.util.function.Predicate;
import java.util.stream.Stream;

/** 实体产出子类：专用参数独立建模，公共执行字段由外层效果承载。 */
public sealed interface EntityProduct permits EntityProduct.Item, EntityProduct.Mob, EntityProduct.Generic, EntityProduct.Experience {
    MapCodec<EntityProduct> CODEC = strict(Codec.STRING.comapFlatMap(value -> switch (value) {
        case "item", "mob", "entity", "experience" -> DataResult.success(value);
        default -> DataResult.error(() -> "未知实体产出子类: " + value);
    }, value -> value).dispatchMap("variant", EntityProduct::variant, EntityProduct::codecFor));

    // 新建规则时默认的产出子类
    String DEFAULT_VARIANT = "item";

    String variant();
    boolean validate(IssueCollector issues, String path);

    // 产出子类对应的参数声明
    default ParamList<?> params() {
        return variantParams(variant());
    }

    // 按 variant 取参数声明；未知子类没有声明
    static ParamList<?> variantParams(String variant) {
        return switch (variant) {
            case "item" -> Item.PARAMS;
            case "mob" -> Mob.PARAMS;
            case "entity" -> Generic.PARAMS;
            case "experience" -> Experience.PARAMS;
            default -> ParamList.empty();
        };
    }

    /**
     * 该产出子类对应的规则分类。
     *
     * @return 物品 / 生物 / 其他实体 / 经验各自的产出类别
     */
    ProductKind productKind();

    // DFU 分发的 keys 只有判别键，需提供所有子类字段并拒绝当前子类不适用的参数。
    private static MapCodec<EntityProduct> strict(MapCodec<EntityProduct> dispatch) {
        return new MapCodec<>() {
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.concat(Stream.of(ops.createString("variant")),
                        Stream.of(Item.CODEC, Mob.CODEC, Generic.CODEC, Experience.CODEC).flatMap(codec -> codec.keys(ops))).distinct();
            }
            @Override public <T> DataResult<EntityProduct> decode(DynamicOps<T> ops, MapLike<T> input) {
                return dispatch.decode(ops, input).flatMap(product -> {
                    Set<T> allowed = new HashSet<>(codecFor(product.variant()).keys(ops).toList());
                    for (String common : Set.of("type", "variant", "delay_ticks", "chance")) {
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
            case "mob" -> Mob.CODEC;
            case "entity" -> Generic.CODEC;
            case "experience" -> Experience.CODEC;
            default -> throw new IllegalArgumentException("未知实体产出子类: " + variant);
        };
    }

    /** 掉落物数量按物品件数计算。 */
    record Item(TaggedId item, int count) implements EntityProduct {
        public static final String ITEM_FIELD = "item";
        public static final String COUNT_FIELD = "count";
        public static final RefSpec ITEM = RefSpec.required(ITEM_FIELD, "minecraft:item")
                .label("spawn_item.item");
        public static final IntSpec COUNT = IntSpec.defaulted(COUNT_FIELD, 1, 1, 64)
                .initial(1)
                .label("spawn_item.count");
        public static final ParamList<Item> PARAMS = ParamList.of(ITEM.bind(Item::item), COUNT.bind(Item::count));

        public static final MapCodec<Item> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(ITEM),
                PARAMS.field(COUNT)
        ).apply(instance, Item::new));
        @Override public String variant() { return "item"; }
        @Override public ProductKind productKind() { return ProductKind.ENTITY_ITEM; }
        @Override public boolean validate(IssueCollector issues, String path) {
            String field = ParamChecks.child(path, ITEM_FIELD);
            boolean valid = PARAMS.check(this, issues, path);
            valid &= RefChecks.check(item, BuiltInRegistries.ITEM, ITEM_FIELD, issues, field);
            return valid;
        }
    }

    /**
     * 其他实体：不含生物、掉落物与经验球，因此不携带年龄。
     *
     * <p>界面在该档位只提供非生物实体；手写规则 JSON 不做生物归属强校验——加载期没有世界上下文，
     * 且实体标签可能混含生物，强校验会让合法的标签引用随机失败。
     */
    record Generic(TaggedId entity, int count) implements EntityProduct {
        public static final String ENTITY_FIELD = "entity";
        public static final String COUNT_FIELD = "count";
        public static final RefSpec ENTITY = RefSpec.required(ENTITY_FIELD, "minecraft:entity_type")
                .label("spawn_entity.entity")
                .selectionIntent(SelectionIntent.ENTITY_TYPES_EXCLUDING_PRODUCTS);
        public static final IntSpec COUNT = IntSpec.defaulted(COUNT_FIELD, 1, 1, 64)
                .initial(1)
                .label("spawn_entity.count");
        public static final ParamList<Generic> PARAMS = ParamList.of(
                ENTITY.bind(Generic::entity),
                COUNT.bind(Generic::count));

        public static final MapCodec<Generic> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(ENTITY),
                PARAMS.field(COUNT)
        ).apply(instance, Generic::new));
        @Override public String variant() { return "entity"; }
        @Override public ProductKind productKind() { return ProductKind.ENTITY_ENTITY; }
        @Override public boolean validate(IssueCollector issues, String path) {
            boolean valid = PARAMS.check(this, issues, path);
            valid &= checkSpawnable(entity, ENTITY_FIELD, issues, path);
            return valid;
        }
    }

    /**
     * 生物：只有 Mob 及其子类可能读取年龄，因此年龄只属于本子类。
     *
     * <p>运行时只对 {@link net.minecraft.world.entity.AgeableMob} 写入年龄；史莱姆、恶魂等非 AgeableMob
     * 的生物可以生成，但不会体现年龄差异。
     */
    record Mob(TaggedId entity, int count, int age) implements EntityProduct {
        /** 幼年起始年龄：与 {@link net.minecraft.world.entity.AgeableMob} 的幼年起点一致。 */
        public static final int BABY_AGE = -24000;
        /** 成年年龄。 */
        public static final int ADULT_AGE = 0;

        public static final String ENTITY_FIELD = "entity";
        public static final String COUNT_FIELD = "count";
        public static final String AGE_FIELD = "age";
        public static final RefSpec ENTITY = RefSpec.required(ENTITY_FIELD, "minecraft:entity_type")
                .label("spawn_entity.mob");
        public static final IntSpec COUNT = IntSpec.defaulted(COUNT_FIELD, 1, 1, 64)
                .initial(1)
                .label("spawn_entity.count");
        public static final IntSpec AGE = IntSpec.defaulted(AGE_FIELD, ADULT_AGE, BABY_AGE, ADULT_AGE)
                .window(BABY_AGE, ADULT_AGE)
                .label("spawn_entity.age")
                .presets(new ParamPreset(String.valueOf(BABY_AGE), "preset.baby"),
                        new ParamPreset(String.valueOf(ADULT_AGE), "preset.adult"));
        public static final ParamList<Mob> PARAMS = ParamList.of(
                ENTITY.bind(Mob::entity),
                COUNT.bind(Mob::count),
                AGE.bind(Mob::age));

        public static final MapCodec<Mob> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(ENTITY),
                PARAMS.field(COUNT),
                PARAMS.field(AGE)
        ).apply(instance, Mob::new));
        @Override public String variant() { return "mob"; }
        @Override public ProductKind productKind() { return ProductKind.ENTITY_MOB; }
        @Override public boolean validate(IssueCollector issues, String path) {
            boolean valid = PARAMS.check(this, issues, path);
            valid &= checkSpawnable(entity, ENTITY_FIELD, issues, path);
            return valid;
        }
    }

    // 生物档与其他实体档共用的引用与类别检查；数量和年龄由各自声明校验。
    private static boolean checkSpawnable(TaggedId entity, String fieldName,
                                          IssueCollector issues, String path) {
        String field = ParamChecks.child(path, fieldName);
        boolean valid = RefChecks.check(entity, BuiltInRegistries.ENTITY_TYPE, fieldName, issues, field);
        if (entity != null) {
            if (matchesAny(entity, EntityProduct::isSpecial)) {
                issues.error("掉落物和经验球请使用对应的专用产出子类", null, field);
                valid = false;
            } else if (matchesAny(entity, EntityProduct::isExcludedEntityProduct)) {
                issues.error("该实体类型不能作为转化产物：它不可召唤，或是展示、标记一类的技术性实体", null, field);
                valid = false;
            }
        }
        return valid;
    }

    // 标签在运行期可能变更成员，所以引用里的每个类型都要按同一口径判定。
    private static boolean matchesAny(TaggedId entity, Predicate<EntityType<?>> predicate) {
        if (entity.tag()) {
            return BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, entity.id()))
                    .map(tag -> tag.stream().anyMatch(holder -> predicate.test(holder.value())))
                    .orElse(false);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity.id()).orElse(null);
        return type != null && predicate.test(type);
    }

    // 防止标签运行期变更后绕过专用入口约束。
    static boolean isSpecial(EntityType<?> type) {
        return type == EntityType.ITEM || type == EntityType.EXPERIENCE_ORB;
    }

    // 技术性实体：展示、标记与瞬态表现类，作为转化产物没有意义
    Set<EntityType<?>> TECHNICAL_ENTITIES = Set.of(
            EntityType.FALLING_BLOCK,
            EntityType.BLOCK_DISPLAY,
            EntityType.ITEM_DISPLAY,
            EntityType.TEXT_DISPLAY,
            EntityType.MARKER,
            EntityType.INTERACTION,
            EntityType.AREA_EFFECT_CLOUD,
            EntityType.LIGHTNING_BOLT,
            EntityType.EYE_OF_ENDER,
            EntityType.OMINOUS_ITEM_SPAWNER);

    /**
     * 该实体类型能否作为转化产物。
     *
     * <p>掉落物与经验球有专用产出子类；不可召唤的类型（玩家、浮漂）与技术性表现实体都不作为产物。
     * 界面按同一份判定过滤选择列表，这里保证手写规则 JSON 也会在加载期被拒载。
     */
    static boolean isExcludedEntityProduct(EntityType<?> type) {
        return isSpecial(type) || !type.canSummon() || TECHNICAL_ENTITIES.contains(type);
    }

    /** 经验点数按每件参与转化源物品计算：本次产出的总点数 = 每件点数 × 本组实际扣减的源物品件数。 */
    record Experience(int amount) implements EntityProduct {
        public static final String AMOUNT_FIELD = "amount";
        public static final IntSpec AMOUNT = IntSpec.defaulted(AMOUNT_FIELD, 1, 1, 65536)
                .initial(1)
                .label("spawn_xp.amount");
        public static final ParamList<Experience> PARAMS = ParamList.of(AMOUNT.bind(Experience::amount));

        public static final MapCodec<Experience> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(AMOUNT)
        ).apply(instance, Experience::new));
        @Override public String variant() { return "experience"; }
        @Override public ProductKind productKind() { return ProductKind.ENTITY_EXPERIENCE; }
        @Override public boolean validate(IssueCollector issues, String path) {
            return PARAMS.check(this, issues, path);
        }
    }
}
