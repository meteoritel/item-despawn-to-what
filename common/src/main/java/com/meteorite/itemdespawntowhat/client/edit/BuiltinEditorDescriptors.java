package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/***
 * 内置条件/效果编辑器描述符表：10 个条件 + 12 个效果各注册一条描述符。
 * <p>字段名与 {@code docs/plan/plan-frontend-rewrite-forms.md} 的 JSON 字段名逐字一致，
 * 取值域与契约 §5.2 一致；界面通过通用表单引擎按描述符渲染，不写 22 个手写表单类。
 * <p>未在此注册的第三方类型由 {@link ConditionEditorRegistry}/{@link EffectEditorRegistry}
 * 回退为只读 JSON 摘要，且草稿原样保留未识别字段。
 */
public final class BuiltinEditorDescriptors {

    // 字段标签前缀
    private static final String FIELD = "gui.itemdespawntowhat.edit.field.";
    // 效果公共字段标签前缀
    private static final String COMMON = FIELD + "common.";

    // 规则基本信息描述符 id（仅界面按名取用）
    public static final ResourceLocation RULE_DESCRIPTOR = id("rule");
    // 源物品对象描述符 id（仅界面按名取用）
    public static final ResourceLocation SOURCE_DESCRIPTOR = id("source");

    private static volatile boolean bootstrapped;

    private BuiltinEditorDescriptors() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 幂等注册全部内置描述符；界面初始化时调用
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (BuiltinEditorDescriptors.class) {
            if (bootstrapped) {
                return;
            }
            registerConditions();
            registerEffects();
            bootstrapped = true;
        }
    }

    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, path);
    }

    // ---- 条件 ----

    private static void registerConditions() {
        String h = "gui.itemdespawntowhat.edit.field.";

        // dimension：维度白名单（纯资源位置列表，不支持标签）
        ResourceLocation dimension = id("dimension");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(dimension, TypeLabels.conditionLabel(dimension), List.of(
                EditorField.rlList("dimensions", h + "dimension.dimensions", "minecraft:dimension")
                    .asRequired()
        ), false));

        // biome：exact 用 biomes；climate 用 6 个气候区间
        ResourceLocation biome = id("biome");
        List<EditorField> biomeFields = new ArrayList<>();
        biomeFields.add(EditorField.enumIn("mode", h + "biome.mode", "biome_mode", "exact", "climate"));
        biomeFields.add(EditorField.tagList("biomes", h + "biome.biomes", "minecraft:worldgen/biome")
                .withHint(h + "biome.biomes.hint"));
        biomeFields.add(EditorField.climateRange("temperature", h + "biome.temperature"));
        biomeFields.add(EditorField.climateRange("humidity", h + "biome.humidity"));
        biomeFields.add(EditorField.climateRange("continentalness", h + "biome.continentalness"));
        biomeFields.add(EditorField.climateRange("erosion", h + "biome.erosion"));
        biomeFields.add(EditorField.climateRange("depth", h + "biome.depth"));
        biomeFields.add(EditorField.climateRange("weirdness", h + "biome.weirdness"));
        biomeFields.add(EditorField.note(h + "biome.note"));
        ConditionEditorRegistry.register(new TypeEditorDescriptor(biome, TypeLabels.conditionLabel(biome), biomeFields, false));

        // weather：三种天气
        ResourceLocation weather = id("weather");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(weather, TypeLabels.conditionLabel(weather), List.of(
                EditorField.enumIn("weather", h + "weather.weather", "weather_kind", "clear", "rain", "thunder")
        ), false));

        // outdoor：无参数，取反请用父节点 inverted
        ResourceLocation outdoor = id("outdoor");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(outdoor, TypeLabels.conditionLabel(outdoor), List.of(
                EditorField.note(h + "outdoor.note")
        ), false));

        // surrounding_blocks：六个方向至少填一个
        ResourceLocation surrounding = id("surrounding_blocks");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(surrounding, TypeLabels.conditionLabel(surrounding), List.of(
                EditorField.optionalTag("up", h + "surrounding_blocks.up", "minecraft:block"),
                EditorField.optionalTag("down", h + "surrounding_blocks.down", "minecraft:block"),
                EditorField.optionalTag("north", h + "surrounding_blocks.north", "minecraft:block"),
                EditorField.optionalTag("south", h + "surrounding_blocks.south", "minecraft:block"),
                EditorField.optionalTag("east", h + "surrounding_blocks.east", "minecraft:block"),
                EditorField.optionalTag("west", h + "surrounding_blocks.west", "minecraft:block"),
                EditorField.note(h + "surrounding_blocks.note")
        ), false));

        // catalyst_present：催化剂物品与数量
        ResourceLocation catalyst = id("catalyst_present");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(catalyst, TypeLabels.conditionLabel(catalyst), List.of(
                EditorField.tagList("items", h + "catalyst_present.items", "minecraft:item").asRequired(),
                EditorField.integer("count", h + "catalyst_present.count", 1, 64).optional()
        ), false));

        // fluid_present：留空 = 任意流体
        ResourceLocation fluid = id("fluid_present");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(fluid, TypeLabels.conditionLabel(fluid), List.of(
                EditorField.optionalTag("fluid", h + "fluid_present.fluid", "minecraft:fluid")
                        .withHint(h + "fluid_present.fluid.hint"),
                EditorField.bool("require_source", h + "fluid_present.require_source")
        ), false));

        // time_of_day：可跨零点
        ResourceLocation timeOfDay = id("time_of_day");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(timeOfDay, TypeLabels.conditionLabel(timeOfDay), List.of(
                EditorField.integer("from", h + "time_of_day.from", 0, 23999),
                EditorField.integer("to", h + "time_of_day.to", 0, 23999),
                EditorField.note(h + "time_of_day.note")
        ), false));

        // y_level：两端全空 = 恒真
        ResourceLocation yLevel = id("y_level");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(yLevel, TypeLabels.conditionLabel(yLevel), List.of(
                EditorField.optionalInteger("min", h + "y_level.min", -2048, 2048),
                EditorField.optionalInteger("max", h + "y_level.max", -2048, 2048),
                EditorField.note(h + "y_level.note")
        ), false));

        // light_level：两端全空 = 恒真
        ResourceLocation lightLevel = id("light_level");
        ConditionEditorRegistry.register(new TypeEditorDescriptor(lightLevel, TypeLabels.conditionLabel(lightLevel), List.of(
                EditorField.optionalInteger("min", h + "light_level.min", 0, 15),
                EditorField.optionalInteger("max", h + "light_level.max", 0, 15),
                EditorField.note(h + "light_level.note")
        ), false));
    }

    // ---- 效果 ----

    private static void registerEffects() {
        String h = "gui.itemdespawntowhat.edit.field.";

        // spawn_item
        ResourceLocation spawnItem = id("spawn_item");
        registerEffect(spawnItem, List.of(
                EditorField.tag("item", h + "spawn_item.item", "minecraft:item"),
                EditorField.integer("count", h + "spawn_item.count", 1, 64).optional(),
                EditorField.optionalInteger("limit", h + "spawn_item.limit", 1, 4096),
                EditorField.optionalInteger("radius", h + "spawn_item.radius", 1, 32)
        ));

        // spawn_entity：age 提供幼年/成年预设
        ResourceLocation spawnEntity = id("spawn_entity");
        registerEffect(spawnEntity, List.of(
                EditorField.tag("entity", h + "spawn_entity.entity", "minecraft:entity_type"),
                EditorField.integer("count", h + "spawn_entity.count", 1, 64).optional(),
                EditorField.integerSlider("age", h + "spawn_entity.age", Integer.MIN_VALUE, Integer.MAX_VALUE, -24000, 24000).optional().withPresets(
                        new EditorPreset("-24000", "gui.itemdespawntowhat.edit.preset.baby"),
                        new EditorPreset("0", "gui.itemdespawntowhat.edit.preset.adult")),
                EditorField.optionalInteger("limit", h + "spawn_entity.limit", 1, 4096),
                EditorField.optionalInteger("radius", h + "spawn_entity.radius", 1, 32)
        ));

        // place_block：block 与 use_source_block 至少一个
        ResourceLocation placeBlock = id("place_block");
        registerEffect(placeBlock, List.of(
                EditorField.optionalTag("block", h + "place_block.block", "minecraft:block"),
                EditorField.bool("use_source_block", h + "place_block.use_source_block"),
                EditorField.enumIn("shape", h + "place_block.shape", "place_block_shape", "square", "circle", "cross").optional(),
                EditorField.integer("count", h + "place_block.count", 1, 64).optional(),
                EditorField.integer("radius", h + "place_block.radius", 1, 32).optional(),
                EditorField.optionalInteger("limit", h + "place_block.limit", 1, 4096),
                EditorField.note(h + "place_block.note")
        ));

        // spawn_xp
        ResourceLocation spawnXp = id("spawn_xp");
        registerEffect(spawnXp, List.of(
                EditorField.integer("amount", h + "spawn_xp.amount", 1, 65536).optional(),
                EditorField.bool("per_source_item", h + "spawn_xp.per_source_item")
        ));

        // loot_table：战利品表不支持标签
        ResourceLocation lootTable = id("loot_table");
        registerEffect(lootTable, List.of(
                EditorField.resourceLocation("loot_table", h + "loot_table.loot_table"),
                EditorField.decimal("luck", h + "loot_table.luck", -100.0D, 100.0D).optional()
        ));

        // lightning
        ResourceLocation lightning = id("lightning");
        registerEffect(lightning, List.of(
                EditorField.integer("count", h + "lightning.count", 1, 16).optional()
        ));

        // explosion
        ResourceLocation explosion = id("explosion");
        registerEffect(explosion, List.of(
                EditorField.decimal("power", h + "explosion.power", 0.0D, 16.0D).optional(),
                EditorField.bool("fire", h + "explosion.fire"),
                EditorField.bool("visual_only", h + "explosion.visual_only")
        ));

        // arrow_rain：药水效果子列表
        ResourceLocation arrowRain = id("arrow_rain");
        registerEffect(arrowRain, List.of(
                EditorField.integer("count", h + "arrow_rain.count", 1, 256).optional(),
                EditorField.enumIn("pickup", h + "arrow_rain.pickup", "arrow_rain_pickup", "disallowed", "allowed", "creative_only").optional(),
                EditorField.subList("potion_effects", h + "arrow_rain.potion_effects",
                        EditorField.tag("effect", h + "arrow_rain.effect", "minecraft:mob_effect"),
                        EditorField.integer("duration_ticks", h + "arrow_rain.duration_ticks", 1, 1000000).optional(),
                        EditorField.amplifier("amplifier", h + "arrow_rain.amplifier").optional())
                        .withHint(h + "arrow_rain.potion_effects.hint")
        ));

        // weather（效果）：mode=clear 时 thundering 无效
        ResourceLocation weatherEffect = id("weather");
        registerEffect(weatherEffect, List.of(
                EditorField.enumIn("mode", h + "weather.mode", "weather_effect_mode", "rain", "clear"),
                EditorField.integer("duration_ticks", h + "weather.duration_ticks", 1, 24000).optional(),
                EditorField.bool("thundering", h + "weather.thundering"),
                EditorField.note(h + "weather.note")
        ));

        // consume_source
        ResourceLocation consumeSource = id("consume_source");
        registerEffect(consumeSource, List.of(
                EditorField.integer("count", h + "consume_source.count", 1, 64).optional()
        ));

        // consume_catalyst
        ResourceLocation consumeCatalyst = id("consume_catalyst");
        registerEffect(consumeCatalyst, List.of(
                EditorField.tagList("items", h + "consume_catalyst.items", "minecraft:item").asRequired(),
                EditorField.integer("count", h + "consume_catalyst.count", 1, 64).optional(),
                EditorField.integer("radius", h + "consume_catalyst.radius", 1, 8).optional()
        ));

        // consume_fluid
        ResourceLocation consumeFluid = id("consume_fluid");
        registerEffect(consumeFluid, List.of(
                EditorField.optionalTag("fluid", h + "consume_fluid.fluid", "minecraft:fluid")
                        .withHint(h + "consume_fluid.fluid.hint"),
                EditorField.bool("require_source", h + "consume_fluid.require_source")
        ));
    }

    // 效果描述符 = 专属字段 + 公共字段（delay_ticks / chance / conditions）
    private static void registerEffect(ResourceLocation typeId, List<EditorField> ownFields) {
        List<EditorField> fields = new ArrayList<>(ownFields);
        fields.add(EditorField.ticks("delay_ticks", COMMON + "delay_ticks", 0, Integer.MAX_VALUE)
                .optional()
                .withNumbers(NumericDomain.integers(0, Integer.MAX_VALUE, 0, 1200))
                .withHint(COMMON + "delay_ticks.hint"));
        fields.add(EditorField.percent("chance", COMMON + "chance").optional());
        fields.add(EditorField.conditionTree("conditions", COMMON + "conditions"));
        EffectEditorRegistry.register(new TypeEditorDescriptor(typeId, TypeLabels.effectLabel(typeId), fields, false));
    }

    // ---- 规则级与来源对象（不在条件/效果注册表内，界面按需取用） ----

    // 规则基本信息页签的字段描述符
    public static TypeEditorDescriptor ruleDescriptor() {
        String r = "gui.itemdespawntowhat.edit.rule.";
        return new TypeEditorDescriptor(RULE_DESCRIPTOR, Component.translatable(r + "info"), List.of(
                EditorField.bool(RuleFields.ENABLED, r + "enabled"),
                EditorField.integerSlider(RuleFields.PRIORITY, r + "priority", Integer.MIN_VALUE, Integer.MAX_VALUE, -100, 100)
                        .optional(),
                EditorField.optionalText(RuleFields.DISPLAY_NAME, r + "display_name"),
                EditorField.longText(RuleFields.NOTES, r + "notes"),
                EditorField.integerSlider("trigger_after_seconds", r + "trigger_after_seconds", 0, Integer.MAX_VALUE, 0, 600)
                        .optional()
        ), false);
    }

    // ---- 规则级固定成本（可省略字段，界面按需取用） ----

    // 源成本字段描述符 id（仅界面按名取用）
    public static final ResourceLocation SOURCE_COST_DESCRIPTOR = id("source_cost");

    // 催化剂成本对象描述符 id（仅界面按名取用）
    public static final ResourceLocation CATALYST_COST_DESCRIPTOR = id("catalyst_cost");

    // 源固定成本字段：可省略（省略时后端推导）；合法域 1..INT_MAX，常用窗口 1..64
    public static TypeEditorDescriptor sourceCostDescriptor() {
        String r = "gui.itemdespawntowhat.edit.rule.";
        return new TypeEditorDescriptor(SOURCE_COST_DESCRIPTOR, Component.translatable(r + "source_cost"), List.of(
                EditorField.integerSlider(RuleFields.SOURCE_COST, r + "source_cost", 1, Integer.MAX_VALUE, 1, 64)
                        .optional()
        ), false);
    }

    // 催化剂固定成本对象：items 必填（支持标签），count/radius 可省略
    public static TypeEditorDescriptor catalystCostDescriptor() {
        String r = "gui.itemdespawntowhat.edit.rule.";
        return new TypeEditorDescriptor(CATALYST_COST_DESCRIPTOR, Component.translatable(r + "catalyst_cost"), List.of(
                EditorField.tagList(RuleFields.CATALYST_ITEMS, r + "catalyst_cost.items", "minecraft:item").asRequired(),
                EditorField.optionalInteger(RuleFields.CATALYST_COUNT, r + "catalyst_cost.count", 1, 64),
                EditorField.optionalInteger(RuleFields.CATALYST_RADIUS, r + "catalyst_cost.radius", 1, 8)
        ), false);
    }

    // 源物品页签的字段描述符
    public static TypeEditorDescriptor sourceDescriptor() {
        String r = "gui.itemdespawntowhat.edit.rule.";
        return new TypeEditorDescriptor(SOURCE_DESCRIPTOR, Component.translatable(r + "source"), List.of(
                EditorField.tagList("items", r + "source.items", "minecraft:item")
                        .asRequired()
                        .withHint(r + "source.items.hint"),
                EditorField.tagList("exclude", r + "source.exclude", "minecraft:item")
                        .withHint(r + "source.exclude.hint")
        ), false);
    }
}
