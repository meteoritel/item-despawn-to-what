package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 调试用测试规则的生成约定：统一负责身份前缀、分类归属与最小合法规则体。
 *
 * <p>测试规则以固定 id 前缀标识，删除入口据此只作用于测试规则，不触碰其它规则；
 * 规则体显式声明 debug 分类并随快照下发，未经显式分类的旧测试规则仍按 id 前缀回落到 debug 分类。</p>
 *
 * <p>规则体是结构合法的最小规则：单个源物品、映射形式的固定源成本，以及一个经验产出的候选结果；
 * 其余字段（enabled / priority / trigger_after_seconds / triggers / combination）依赖服务端默认值，不写入 JSON。</p>
 */
public final class DebugTestRules {

    // 列表页与规则分类使用的分类标识
    public static final String CATEGORY = "debug";

    // 测试规则 id 的路径前缀
    public static final String ID_PREFIX = "debug_test_";

    // 测试规则 id 的完整前缀（含命名空间）
    public static final String QUALIFIED_ID_PREFIX = TypeLabels.OWN_NAMESPACE + ":" + ID_PREFIX;

    // 单次填充允许的条数区间与默认值
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 1000;
    public static final int DEFAULT_COUNT = 10;

    // 模板源物品与固定源成本
    public static final String SOURCE_ITEM = "minecraft:stone";
    public static final int COST_COUNT = 1;

    // 产出子类字段：与服务端 EntityProduct 的分发键保持一致
    public static final String VARIANT_FIELD = "variant";
    public static final String VARIANT_EXPERIENCE = "experience";
    public static final String AMOUNT_FIELD = "amount";
    public static final int AMOUNT = 1;

    // 工具类
    private DebugTestRules() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 第 index 条测试规则的 id。
     *
     * @param index 序号，从 1 开始
     * @return 形如 {@code idtw:debug_test_1} 的规则 id
     */
    public static String ruleId(int index) {
        return QUALIFIED_ID_PREFIX + index;
    }

    /**
     * 判断规则 id 是否属于测试规则。
     *
     * @param id 规则 id，允许为 null
     * @return 命中测试规则前缀时为 true，null 或其它规则一律为 false
     */
    public static boolean isTestRule(@Nullable String id) {
        return id != null && id.startsWith(QUALIFIED_ID_PREFIX);
    }

    /**
     * 构造最小合法规则体。
     *
     * <p>固定源成本必须写成映射形式（{@code {"minecraft:stone": 1}}）：服务端按
     * {@code SourceCost} 的映射编解码器读取，整数简写无法解码。规则体顶层声明
     * {@code category = debug}，使测试规则在列表页独立成组。</p>
     *
     * @param ruleId 规则 id
     * @return 可直接交给草稿会话的规则体
     */
    public static RuleValue ruleBody(String ruleId) {
        RuleValue.Mapping source = RuleValue.mapping();
        source.put(RuleFields.SOURCE_ITEMS, RuleValue.textList(List.of(SOURCE_ITEM)).build());

        RuleValue.Mapping cost = RuleValue.mapping();
        cost.put(SOURCE_ITEM, RuleValue.integer(COST_COUNT));

        RuleValue.Mapping effect = RuleValue.mapping();
        effect.put(RuleFields.TYPE, RuleValue.text(SpawnEntityEffect.ID.toString()));
        effect.put(VARIANT_FIELD, RuleValue.text(VARIANT_EXPERIENCE));
        effect.put(AMOUNT_FIELD, RuleValue.integer(AMOUNT));

        RuleValue.Mapping candidate = RuleValue.mapping();
        candidate.put(RuleFields.CANDIDATE_ID, RuleValue.text(EditorFactories.newCandidateId()));
        candidate.put(RuleFields.CANDIDATE_EFFECTS, RuleValue.list().add(effect.build()).build());

        RuleValue.Mapping root = RuleValue.mapping();
        root.put(RuleFields.ID, RuleValue.text(ruleId));
        root.put(RuleFields.SOURCE, source.build());
        root.put(RuleFields.SOURCE_COST, cost.build());
        root.put(RuleFields.SCHEMA_VERSION, RuleValue.integer(RuleCodecs.DEFAULT_SCHEMA_VERSION));
        root.put(RuleFields.CATEGORY, RuleValue.text(CATEGORY));
        root.put(RuleFields.OUTCOMES, RuleValue.list().add(candidate.build()).build());
        return root.build();
    }
}
