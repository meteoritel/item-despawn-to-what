package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件/效果编辑器描述符装配表：为客户端已发现且具有参数声明的类型自动注册表单。
 * <p>字段由 core 侧的类型参数声明经 {@link ParamProjection} 投影得到，此处只补充参数声明之外的
 * 界面元素（说明行）与运行期配置相关的窗口覆盖；因此新增参数只需在 core 侧声明一次。
 * <p>手动预注册的描述符优先；内置无参类型保留说明行，其余空声明或本地未发现的类型回退为只读 JSON 摘要。
 */
public final class BuiltinEditorDescriptors {

    /** 寿命应答未抵达时的滑条窗口，沿用服务端默认自然寿命并换算为秒。 */
    public static final int DEFAULT_TRIGGER_WINDOW_SECONDS =
            (int) (ServerConfig.DEFAULT_FABRIC_LIFESPAN_FALLBACK_TICKS / RuleEditSessionLimits.TICKS_PER_SECOND);

    // 字段标签前缀
    private static final String FIELD = TypeLabels.UI_PREFIX + "field.";
    // 规则级标签前缀
    private static final String RULE = TypeLabels.UI_PREFIX + "rule.";

    /** 源物品对象描述符 id（仅界面按名取用） */
    public static final ResourceLocation SOURCE_DESCRIPTOR = id("source");

    // 纯说明行：不写入 JSON，只能由描述符表补充
    private static final Map<ResourceLocation, List<EditorField>> CONDITION_NOTES = Map.of(
            id("outdoor"), List.of(EditorField.note(FIELD + "outdoor.note")),
            id("surrounding_blocks"), List.of(EditorField.note(FIELD + "surrounding_blocks.note")),
            id("time_of_day"), List.of(EditorField.note(FIELD + "time_of_day.note")),
            id("light_level"), List.of(EditorField.note(FIELD + "light_level.note")));

    private static final Map<ResourceLocation, List<EditorField>> EFFECT_NOTES = Map.of(
            id("weather"), List.of(EditorField.note(FIELD + "weather.note")));

    private static volatile boolean bootstrapped;

    private BuiltinEditorDescriptors() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 幂等注册内置与本地 SPI 类型的表单描述符；界面初始化时调用。 */
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

    // ---- 条件与效果 ----

    private static void registerConditions() {
        for (TypeDefinition<?> type : ClientTypeRegistries.conditions().all()) {
            register(EditorRegistries.CONDITIONS, type, TypeLabels.conditionLabel(type.id()),
                    CONDITION_NOTES.getOrDefault(type.id(), List.of()));
        }
    }

    private static void registerEffects() {
        for (TypeDefinition<?> type : ClientTypeRegistries.effects().all()) {
            register(EditorRegistries.EFFECTS, type, TypeLabels.effectLabel(type.id()),
                    EFFECT_NOTES.getOrDefault(type.id(), List.of()));
        }
    }

    private static void register(TypeEditorRegistry registry, TypeDefinition<?> type, Component label,
                                  List<EditorField> notes) {
        if (registry.isRegistered(type.id()) || type.params().isEmpty() && notes.isEmpty()) {
            return;
        }
        registry.register(new TypeEditorDescriptor(type.id(), label, fields(type, notes), false));
    }

    // 参数投影 + 说明行；说明行插在公共字段（delay_ticks/chance）之前，与专属字段保持同组
    private static List<EditorField> fields(TypeDefinition<?> type, List<EditorField> notes) {
        List<EditorField> fields = new ArrayList<>(ParamProjection.fields(type.params(), type.id()));
        if (type.id().equals(id("place_block"))) {
            widenRadiusWindow(fields);
        }
        int insertAt = fields.size();
        for (int index = 0; index < fields.size(); index++) {
            String name = fields.get(index).name();
            if (RuleFields.DELAY_TICKS.equals(name) || RuleFields.CHANCE.equals(name)) {
                insertAt = index;
                break;
            }
        }
        fields.addAll(insertAt, notes);
        return fields;
    }

    // 半径的后端合法域不设静态上界，实际可用上限由服务端配置在加载/保存时校验；
    // 滑条窗口先取配置默认值，打开编辑器后再按服务端随开屏载荷下发的上限刷新。
    private static void widenRadiusWindow(List<EditorField> fields) {
        for (int index = 0; index < fields.size(); index++) {
            EditorField field = fields.get(index);
            if (PlaceBlockEffect.RADIUS_FIELD.equals(field.name())) {
                fields.set(index, field.withNumbers(NumericDomain.integers(
                        PlaceBlockEffect.MIN_RADIUS, Integer.MAX_VALUE,
                        PlaceBlockEffect.MIN_RADIUS, ServerConfig.DEFAULT.maxPlaceBlockRadius())));
                return;
            }
        }
    }

    /** 生成选中子类型的参数描述符；公共字段沿用注册表，第三方类型不受影响。 */
    public static TypeEditorDescriptor entityDescriptor(String variant) {
        ResourceLocation typeId = id("spawn_entity");
        List<EditorField> fields = new ArrayList<>(
                ParamProjection.fields(EntityProduct.variantParams(variant), typeId));
        fields.addAll(ParamProjection.fields(SpawnEntityEffect.PARAMS, typeId));
        return new TypeEditorDescriptor(typeId, TypeLabels.effectLabel(typeId), fields, false);
    }

    // ---- 规则级与来源对象（不在条件/效果注册表内，界面按需取用） ----

    /** 基本信息的内容字段；本服启用意图由独立动作维护。 */
    public static TypeEditorDescriptor infoDescriptor() {
        return TypeEditorDescriptor.of(id("rule_info_page"), Component.translatable(TypeLabels.UI_PREFIX + "tab.info"), List.of(
                EditorField.optionalText(RuleFields.DISPLAY_NAME, RULE + "display_name").withHint(RULE + "display_name_hint"),
                EditorField.bool(RuleFields.ENABLED, RULE + "enabled"),
                EditorField.integerSlider(RuleFields.PRIORITY, RULE + "priority", Integer.MIN_VALUE, Integer.MAX_VALUE, -100, 100)
                        .optional().withHint(RULE + "priority.hint"),
                EditorField.longText(RuleFields.NOTES, RULE + "notes").withHint(RULE + "notes_hint")));
    }

    /** 自然触发延迟字段；实际寿命只更新界面的滑条窗口。 */
    public static TypeEditorDescriptor delayDescriptor() {
        return TypeEditorDescriptor.of(id("rule_delay_page"), Component.translatable(RULE + "trigger_after_seconds"), List.of(
                EditorField.integerSlider(RuleFields.TRIGGER_AFTER_SECONDS, RULE + "trigger_after_seconds",
                        0, Integer.MAX_VALUE, 0, DEFAULT_TRIGGER_WINDOW_SECONDS).optional()));
    }

    /** 规则级条件树字段。 */
    public static TypeEditorDescriptor conditionsDescriptor() {
        return TypeEditorDescriptor.of(id("conditions_page"), Component.translatable(TypeLabels.UI_PREFIX + "tab.trigger"), List.of(
                EditorField.conditionTree(RuleFields.CONDITIONS, RULE + "conditions")));
    }

    /** 输入分区内催化剂或流体存在条件的精简字段与说明。 */
    public static TypeEditorDescriptor presenceDescriptor(ResourceLocation type) {
        List<EditorField> fields = CatalystPresentCondition.ID.equals(type)
                ? List.of(EditorField.tagList(RuleFields.CATALYST_ITEMS, FIELD + "catalyst_present.items", "minecraft:item").asRequired(),
                        EditorField.note(RULE + "catalyst_all_required"))
                : List.of(EditorField.tagList(RuleFields.FLUIDS, FIELD + "fluid_present.fluid", "minecraft:fluid").optional(),
                        EditorField.note(FIELD + "fluid_present.fluid.hint"));
        return TypeEditorDescriptor.of(type, TypeLabels.conditionLabel(type), fields);
    }

    // ---- 规则级固定成本（可省略字段，界面按需取用） ----

    /** 源成本字段描述符 id（仅界面按名取用） */
    public static final ResourceLocation SOURCE_COST_DESCRIPTOR = id("source_cost");

    /** 催化剂成本对象描述符 id（仅界面按名取用） */
    public static final ResourceLocation CATALYST_COST_DESCRIPTOR = id("catalyst_cost");

    /** 源固定成本字段：可省略（省略时后端推导）；合法域 1..INT_MAX，常用窗口 1..64 */
    public static TypeEditorDescriptor sourceCostDescriptor() {
        String r = RULE;
        return new TypeEditorDescriptor(SOURCE_COST_DESCRIPTOR, Component.translatable(r + "source_cost"), List.of(
                EditorField.itemCounts(RuleFields.SOURCE_COST, r + "source_cost", 1, Integer.MAX_VALUE)
        ), false);
    }

    /** 催化剂门槛、可选实际消耗量和规则共享搜索半径。 */
    public static TypeEditorDescriptor catalystCostDescriptor() {
        String r = RULE;
        return new TypeEditorDescriptor(CATALYST_COST_DESCRIPTOR, Component.translatable(r + "catalyst_cost"), List.of(
                EditorField.tagList(RuleFields.CATALYST_ITEMS, FIELD + "catalyst_present.items", "minecraft:item").asRequired(),
                EditorField.itemCounts(RuleFields.ITEM_COUNTS, r + "catalyst_cost.count",
                        CatalystCost.MIN_COUNT, CatalystCost.MAX_COUNT),
                // 滑条最小为 1；精确输入 0 在输入页按「不消耗」处理（关闭开关并移除该表）
                EditorField.itemCounts(RuleFields.CONSUME_COUNTS, r + "catalyst_cost.consume_count",
                        CatalystCost.MIN_CONSUME_COUNT, CatalystCost.MAX_COUNT),
                EditorField.integerSlider(RuleFields.CATALYST_RADIUS, r + "catalyst_cost.radius",
                        CatalystCost.MIN_RADIUS, CatalystCost.MAX_RADIUS, CatalystCost.DEFAULT_RADIUS,
                        CatalystCost.MAX_RADIUS)
        ), false);
    }

    /** 源物品页签的字段描述符 */
    public static TypeEditorDescriptor sourceDescriptor() {
        String r = RULE;
        return new TypeEditorDescriptor(SOURCE_DESCRIPTOR, Component.translatable(r + "source"), List.of(
                EditorField.tagList("items", r + "source.items", "minecraft:item")
                        .asRequired()
                        .withHint(r + "source.items.hint"),
                EditorField.tagList("exclude", r + "source.exclude", "minecraft:item")
                        .withHint(r + "source.exclude.hint")
        ), false);
    }
}
