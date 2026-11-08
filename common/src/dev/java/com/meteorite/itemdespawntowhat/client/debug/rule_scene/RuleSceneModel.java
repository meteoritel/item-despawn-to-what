package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.screen.RulePreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleTriggers;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * 单条规则在三维舞台上的展示模型：把规则 JSON 解析为物品、条件、效果与触发信息。
 *
 * <p>只读且与界面无关：舞台、面板与探针都消费本模型，不直接读 JSON。</p>
 */
public final class RuleSceneModel {

    /** 场景语言键前缀，页面标题、面板标题与样例名称共用。 */
    public static final String UI = "gui.itemdespawntowhat.debug.workbench.rule_scene.";

    /** 页面标题与样例 key 的无尾点前缀。 */
    public static final String TITLE = "gui.itemdespawntowhat.debug.workbench.rule_scene";

    /** 页脚返回按钮沿用工作台公共 key。 */
    public static final String BACK_KEY = "gui.itemdespawntowhat.debug.workbench.back";

    /** 消耗型或常驻型物品槽：count 为单轮数量，consumed 表示本规则会扣除。 */
    public record ItemSlot(ItemStack stack, Component name, int count, boolean consumed) { }

    /** 结果条目：图标与名称由规则列表同一套产出解析得到。 */
    public record EffectSlot(RuleValue effect, @Nullable UiIcon icon, Component name, int count) { }

    /** 条件叶的可视化分类；每种子类型对应一种舞台表现。 */
    public sealed interface Condition permits Biome, Dimension, TimeOfDay, Weather, LightLevel, YLevel,
            Outdoor, Surrounding, FluidPresent, CatalystPresent { }

    /** 群系条件；climate 模式取温度与湿度的上界，用于挑选底板材质。 */
    public record Biome(List<String> biomes, boolean climate, double temperature, double humidity)
            implements Condition { }

    /** 维度条件。 */
    public record Dimension(List<String> dimensions) implements Condition { }

    /** 时刻条件，区间为 0..23999 刻。 */
    public record TimeOfDay(int from, int to) implements Condition { }

    /** 天气条件，取值为 clear / rain / thunder。 */
    public record Weather(String weather) implements Condition { }

    /** 光照条件，区间 0..15。 */
    public record LightLevel(int min, int max) implements Condition { }

    /** 高度条件。 */
    public record YLevel(int min, int max) implements Condition { }

    /** 露天空地条件。 */
    public record Outdoor() implements Condition { }

    /** 周围方块条件：六方向到方块 id 的有序映射。 */
    public record Surrounding(Map<String, String> sides) implements Condition { }

    /** 流体条件；fluids 为空表示任意流体，consumed 表示液位会被消耗。 */
    public record FluidPresent(List<String> fluids, boolean consumed) implements Condition { }

    /** 催化剂存在条件；counts 为逐物品门槛，未列出时按 1。 */
    public record CatalystPresent(List<String> items, Map<String, Integer> counts) implements Condition { }

    private final List<ItemSlot> inputs;
    private final List<ItemSlot> catalysts;
    private final List<Condition> conditions;
    private final List<EffectSlot> effects;
    private final List<String> triggers;
    private final int delaySeconds;
    private final int chancePercent;

    private RuleSceneModel(List<ItemSlot> inputs, List<ItemSlot> catalysts, List<Condition> conditions,
                           List<EffectSlot> effects, List<String> triggers, int delaySeconds, int chancePercent) {
        this.inputs = List.copyOf(inputs);
        this.catalysts = List.copyOf(catalysts);
        this.conditions = List.copyOf(conditions);
        this.effects = List.copyOf(effects);
        this.triggers = List.copyOf(triggers);
        this.delaySeconds = delaySeconds;
        this.chancePercent = chancePercent;
    }

    /** 源物品槽。 */
    public List<ItemSlot> inputs() { return inputs; }

    /** 催化剂槽。 */
    public List<ItemSlot> catalysts() { return catalysts; }

    /** 条件叶，按规则 JSON 中的声明顺序。 */
    public List<Condition> conditions() { return conditions; }

    /** 产出效果，顶层 effects 在前、outcomes 候选依次追加。 */
    public List<EffectSlot> effects() { return effects; }

    /** 有效触发方式，缺省为自然消失。 */
    public List<String> triggers() { return triggers; }

    /** 自然消失的等待秒数。 */
    public int delaySeconds() { return delaySeconds; }

    /** 首条效果的触发概率百分比；未声明按 100。 */
    public int chancePercent() { return chancePercent; }

    /**
     * 解析规则草稿；缺失或畸形的字段按展示兜底处理，不抛异常。
     *
     * @param rule 规则草稿，可为空
     * @return 展示模型
     */
    public static RuleSceneModel of(@Nullable RuleValue rule) {
        RuleValue body = object(rule);
        List<ItemSlot> inputs = new ArrayList<>();
        for (RuleValue entry : array(object(body.child(RuleFields.SOURCE)).child(RuleFields.SOURCE_ITEMS)).items()) {
            if (!entry.isText()) continue;
            String reference = entry.asText();
            inputs.add(slot(reference, sourceCost(body, reference), true));
        }
        List<ItemSlot> catalysts = new ArrayList<>();
        addCatalysts(catalysts, object(body.child(RuleFields.CATALYST_COST)));
        List<Condition> conditions = new ArrayList<>();
        boolean consumeFluid = body.flag(RuleFields.CONSUME_FLUID, false);
        collectConditions(body.child(RuleFields.CONDITIONS), consumeFluid, conditions, catalysts);
        mergeCatalysts(catalysts);
        List<EffectSlot> effects = new ArrayList<>();
        appendEffects(effects, body.child(RuleFields.EFFECTS));
        for (RuleValue outcome : array(body.child(RuleFields.OUTCOMES)).items()) {
            appendEffects(effects, object(outcome).child(RuleFields.CANDIDATE_EFFECTS));
        }
        int delay = integer(body, RuleFields.TRIGGER_AFTER_SECONDS, RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS);
        int chance = chancePercent(body);
        return new RuleSceneModel(inputs, catalysts, conditions, effects,
                RuleTriggers.effective(rule), delay, chance);
    }

    /** 条件分类的本地化标题。 */
    public static Component conditionLabel(Condition condition) {
        return Component.translatable("condition.itemdespawntowhat.type." + conditionType(condition));
    }

    /** 条件参数的紧凑说明；无参数时返回空文本。 */
    public static Component conditionDetail(Condition condition) {
        if (condition instanceof Biome biome) {
            return biome.climate() || biome.biomes().isEmpty()
                    ? Component.translatable(UI + "condition.climate")
                    : names(biome.biomes(), "biome.");
        }
        if (condition instanceof Dimension dimension) {
            return names(dimension.dimensions(), null);
        }
        if (condition instanceof TimeOfDay time) {
            return Component.translatable(UI + "condition.time", clock(time.from()), clock(time.to()));
        }
        if (condition instanceof Weather weather) {
            String key = switch (weather.weather()) {
                case "rain" -> "raining";
                case "thunder" -> "thundering";
                case "clear" -> "clear";
                default -> "any";
            };
            return Component.translatable("condition.itemdespawntowhat.weather." + key);
        }
        if (condition instanceof LightLevel light) {
            return Component.literal(light.min() + "–" + light.max());
        }
        if (condition instanceof YLevel y) {
            return Component.literal(y.min() + "–" + y.max());
        }
        if (condition instanceof Outdoor) return Component.empty();
        if (condition instanceof Surrounding surrounding) return surrounding(surrounding);
        if (condition instanceof FluidPresent fluid) return fluidNames(fluid);
        if (condition instanceof CatalystPresent catalyst) return names(catalyst.items(), null);
        return Component.empty();
    }

    /** 条件在语言文件中的类型 id（不含命名空间）。 */
    public static String conditionType(Condition condition) {
        if (condition instanceof Biome) return "biome";
        if (condition instanceof Dimension) return "dimension";
        if (condition instanceof TimeOfDay) return "time_of_day";
        if (condition instanceof Weather) return "weather";
        if (condition instanceof LightLevel) return "light_level";
        if (condition instanceof YLevel) return "y_level";
        if (condition instanceof Outdoor) return "outdoor";
        if (condition instanceof Surrounding) return "surrounding_blocks";
        if (condition instanceof FluidPresent) return "fluid_present";
        return "catalyst_present";
    }

    // ---- 解析 ----

    // 条件树只收集正向叶：取反枝在场景里没有正向表现，交给条件面板之外的文本说明
    private static void collectConditions(@Nullable RuleValue raw, boolean consumeFluid, List<Condition> out,
                                          List<ItemSlot> catalysts) {
        RuleValue node = object(raw);
        String op = text(node, RuleFields.OP);
        if (RuleFields.OP_INVERTED.equals(op)) return;
        if (RuleFields.OP_LEAF.equals(op)) {
            Condition condition = condition(object(node.child(RuleFields.CONDITION)), consumeFluid, catalysts);
            if (condition != null) out.add(condition);
            return;
        }
        for (RuleValue term : array(node.child(RuleFields.TERMS)).items()) {
            collectConditions(term, consumeFluid, out, catalysts);
        }
    }

    // 单叶解析：未知或参数不完整的条件不进入舞台，避免生成有歧义的画面
    private static @Nullable Condition condition(RuleValue condition, boolean consumeFluid, List<ItemSlot> catalysts) {
        ResourceLocation type = ResourceLocation.tryParse(text(condition, RuleFields.TYPE));
        if (type == null) return null;
        return switch (type.getPath()) {
            case "biome" -> new Biome(texts(condition, RuleFields.CONDITION_BIOMES),
                    RuleFields.CONDITION_MODE_CLIMATE.equals(text(condition, RuleFields.CONDITION_MODE)),
                    axisMax(condition, RuleFields.CONDITION_AXIS_TEMPERATURE),
                    axisMax(condition, RuleFields.CONDITION_AXIS_HUMIDITY));
            case "dimension" -> new Dimension(texts(condition, RuleFields.CONDITION_DIMENSIONS));
            case "time_of_day" -> new TimeOfDay(
                    integer(condition, RuleFields.CONDITION_FROM, 0), integer(condition, RuleFields.CONDITION_TO, 0));
            case "weather" -> new Weather(text(condition, RuleFields.CONDITION_WEATHER));
            case "light_level" -> new LightLevel(
                    integer(condition, RuleFields.CONDITION_MIN, 0), integer(condition, RuleFields.CONDITION_MAX, 15));
            case "y_level" -> new YLevel(
                    integer(condition, RuleFields.CONDITION_MIN, -64), integer(condition, RuleFields.CONDITION_MAX, 320));
            case "outdoor" -> new Outdoor();
            case "surrounding_blocks" -> new Surrounding(sides(condition));
            case "fluid_present" -> new FluidPresent(fluids(condition), consumeFluid);
            case "catalyst_present" -> {
                List<String> items = texts(condition, RuleFields.CONDITION_ITEMS);
                addCatalysts(catalysts, condition);
                yield new CatalystPresent(items, counts(condition));
            }
            default -> null;
        };
    }

    private static void appendEffects(List<EffectSlot> out, @Nullable RuleValue raw) {
        for (RuleValue effect : array(raw).items()) {
            if (!effect.isMapping()) continue;
            UiIcon icon = RulePreviewIcons.action(effect);
            Component name = RuleNaming.effectTitle(effect, RuleDisplayLabels.nameSource());
            int count = Math.max(1, integer(effect, "count", 1));
            out.add(new EffectSlot(effect, icon, name, count));
        }
    }

    /** 催化剂可能同时出现在 catalyst_cost 与 catalyst_present 条件里，按物品合并成一行。 */
    private static void mergeCatalysts(List<ItemSlot> catalysts) {
        Map<String, ItemSlot> merged = new LinkedHashMap<>();
        for (ItemSlot slot : catalysts) {
            String key = slot.stack().isEmpty() ? slot.name().getString() : slot.stack().getItem().toString();
            ItemSlot previous = merged.get(key);
            merged.put(key, previous == null ? slot : new ItemSlot(slot.stack(),
                    previous.name(), Math.max(previous.count(), slot.count()), previous.consumed() || slot.consumed()));
        }
        catalysts.clear();
        catalysts.addAll(merged.values());
    }

    private static void addCatalysts(List<ItemSlot> target, RuleValue owner) {
        Map<String, Integer> consume = counts(owner, RuleFields.CONSUME_COUNTS);
        for (RuleValue entry : array(owner.child(RuleFields.CATALYST_ITEMS)).items()) {
            if (!entry.isText()) continue;
            String reference = entry.asText();
            int count = integer(owner, RuleFields.CATALYST_COUNT, 1);
            RuleValue perItem = object(owner.child(RuleFields.ITEM_COUNTS)).child(reference);
            if (perItem != null && perItem.isNumber()) count = Math.max(0, perItem.asInteger(count));
            int used = consume.getOrDefault(reference, 0);
            if (used > 0) count = Math.max(count, used);
            target.add(slot(reference, count, used > 0));
        }
    }

    private static ItemSlot slot(String reference, int count, boolean consumed) {
        TaggedId tagged = TaggedId.tryParse(reference);
        Item item = tagged == null || tagged.tag() ? null
                : BuiltInRegistries.ITEM.getOptional(tagged.id()).orElse(null);
        if (item == null) {
            return new ItemSlot(ItemStack.EMPTY, Component.literal(reference), Math.max(1, count), consumed);
        }
        ItemStack stack = new ItemStack(item);
        return new ItemSlot(stack, stack.getHoverName(), Math.max(1, count), consumed);
    }

    private static Map<String, String> sides(RuleValue condition) {
        Map<String, String> sides = new LinkedHashMap<>();
        for (String side : RuleFields.CONDITION_SIDES) {
            String value = text(condition, side);
            if (!value.isBlank()) sides.put(side, value);
        }
        return sides;
    }

    private static Map<String, Integer> counts(RuleValue owner) { return counts(owner, RuleFields.ITEM_COUNTS); }

    private static Map<String, Integer> counts(RuleValue owner, String field) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        RuleValue raw = object(owner.child(field));
        if (!raw.isMapping()) return counts;
        for (String key : raw.keys()) {
            RuleValue value = raw.child(key);
            if (value != null && value.isNumber()) counts.put(key, value.asInteger(0));
        }
        return counts;
    }

    private static List<String> fluids(RuleValue condition) {
        List<String> fluids = texts(condition, RuleFields.FLUIDS);
        if (!fluids.isEmpty()) return fluids;
        String single = text(condition, RuleFields.FLUID);
        return single.isBlank() ? List.of() : List.of(single);
    }

    private static List<String> texts(RuleValue owner, String field) {
        List<String> values = new ArrayList<>();
        for (RuleValue entry : array(owner.child(field)).items()) {
            if (entry.isText()) values.add(entry.asText());
        }
        return values;
    }

    private static Component names(List<String> references, @Nullable String keyPrefix) {
        if (references.isEmpty()) return Component.empty();
        StringBuilder joined = new StringBuilder();
        for (String reference : references) {
            if (!joined.isEmpty()) joined.append(", ");
            TaggedId tagged = TaggedId.tryParse(reference);
            if (tagged == null) { joined.append(reference); continue; }
            if (tagged.tag()) { joined.append('#').append(tagged.id()); continue; }
            joined.append(keyPrefix == null ? dimensionName(tagged.id())
                    : keyPrefix + tagged.id().getNamespace() + "." + tagged.id().getPath());
        }
        return keyPrefix == null ? Component.literal(joined.toString()) : Component.translatable(joined.toString());
    }

    private static String dimensionName(ResourceLocation id) {
        return switch (id.getPath()) {
            case "overworld" -> Component.translatable(UI + "dimension.overworld").getString();
            case "the_nether" -> Component.translatable(UI + "dimension.nether").getString();
            case "the_end" -> Component.translatable(UI + "dimension.end").getString();
            default -> id.getPath();
        };
    }

    private static Component surrounding(Surrounding surrounding) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : surrounding.sides().entrySet()) {
            String side = Component.translatable(UI + "side." + entry.getKey()).getString();
            parts.add(side + ": " + blockName(entry.getValue()).getString());
        }
        return Component.literal(String.join(", ", parts));
    }

    private static Component blockName(String reference) {
        TaggedId tagged = TaggedId.tryParse(reference);
        if (tagged == null || tagged.tag()) return Component.literal(reference);
        Block block = BuiltInRegistries.BLOCK.getOptional(tagged.id()).orElse(null);
        if (block == null) return Component.literal(reference);
        return new ItemStack(block.asItem()).getHoverName();
    }

    private static Component fluidNames(FluidPresent fluid) {
        if (fluid.fluids().isEmpty()) return Component.translatable(UI + "condition.any_fluid");
        List<String> names = new ArrayList<>();
        for (String reference : fluid.fluids()) {
            TaggedId tagged = TaggedId.tryParse(reference);
            Fluid value = tagged == null || tagged.tag() ? null
                    : BuiltInRegistries.FLUID.getOptional(tagged.id()).orElse(null);
            names.add(value == null ? reference : new ItemStack(value.getBucket()).getHoverName().getString());
        }
        return Component.literal(String.join(", ", names));
    }

    private static String clock(int ticks) {
        int normalized = Math.floorMod(ticks, 24000);
        return String.format("%02d:%02d", normalized / 1000, normalized % 1000 * 60 / 1000);
    }

    /** 气候轴取区间上界；字段缺失时按温带 0 处理。 */
    private static double axisMax(RuleValue condition, String key) {
        RuleValue axis = object(condition.child(key));
        RuleValue max = axis.child("max");
        return max != null && max.isNumber() ? max.asDecimal(0.0D) : 0.0D;
    }

    private static int sourceCost(RuleValue rule, String reference) {
        RuleValue cost = object(rule.child(RuleFields.SOURCE_COST));
        return Math.max(1, integer(cost, reference, 1));
    }

    private static int chancePercent(RuleValue rule) {
        double chance = 1.0D;
        for (RuleValue effect : array(rule.child(RuleFields.EFFECTS)).items()) {
            RuleValue raw = object(effect).child(RuleFields.CHANCE);
            if (raw != null && raw.isNumber()) chance = Math.min(chance, raw.asDecimal(1.0D));
        }
        return (int) Math.round(Math.clamp(chance, 0.0D, 1.0D) * 100.0D);
    }

    private static RuleValue object(@Nullable RuleValue value) {
        return value != null && value.isMapping() ? value : RuleValue.mapping().build();
    }

    private static RuleValue array(@Nullable RuleValue value) {
        return value != null && value.isList() ? value : RuleValue.list().build();
    }

    private static String text(RuleValue value, String key) {
        RuleValue raw = value.child(key);
        return raw != null && raw.isText() ? raw.asText() : "";
    }

    private static int integer(RuleValue value, String key, int fallback) {
        RuleValue raw = value.child(key);
        if (raw == null) return fallback;
        try {
            if (raw.isNumber()) return raw.asInteger(fallback);
            if (raw.isText()) return Integer.parseInt(raw.asText().trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
        return fallback;
    }
}
