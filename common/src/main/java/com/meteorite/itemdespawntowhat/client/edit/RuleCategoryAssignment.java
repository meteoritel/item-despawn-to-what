package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.RuleCategoryClassifier;
import org.jetbrains.annotations.Nullable;

/**
 * 规则分类的自动分配：打开编辑会话时补回服务端下发的分类，产出结构变化时按「感知默认值」更新。
 *
 * <p>判定口径见 ADR-0039 决策 6：草稿当前的分类为空、或等于变化前按产出派生的值，视为自动值，
 * 跟随更新为变化后的派生值；其余情况视为作者手写，保持原值。写入只改草稿 JSON 顶层
 * {@code category}，由调用方包在同一次可撤销编辑里，撤销该次产出编辑时分类一并回退。</p>
 *
 * <p>生效视图来自模型编码结果，不含 {@code category}，因此打开会话时必须把快照条目下发的分类
 * 补进草稿 JSON，否则作者手写的分类会在保存时丢失。分类来源（显式 / 自动）按「当前值是否等于
 * 派生值」判定：手写值与派生值相同的情况下该值行为与自动值一致，展示为自动。</p>
 */
public final class RuleCategoryAssignment {

    // 分类来源：作者显式声明
    public static final String SOURCE_EXPLICIT = "explicit";
    // 分类来源：按产出自动派生
    public static final String SOURCE_DERIVED = "derived";

    private RuleCategoryAssignment() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 草稿声明的分类值。
     *
     * @param body 规则体，允许为 null
     * @return 顶层 {@code category} 的文本；未声明、非字符串或空白时为 null
     */
    public static @Nullable String declared(@Nullable RuleValue body) {
        if (body == null || !body.isMapping()) {
            return null;
        }
        String value = body.text(RuleFields.CATEGORY, "");
        return value.isBlank() ? null : value;
    }

    /**
     * 按草稿当前产出派生分类。
     *
     * @param body 规则体，允许为 null
     * @return 派生分类标识；无产出、含未知产出或草稿为空时为扩展分类
     */
    public static String derive(@Nullable RuleValue body) {
        return RuleCategoryClassifier.classifyJson(mappingOf(body), ClientTypeRegistries.effects());
    }

    /**
     * 分类来源：与当前派生值不同的声明值视为作者手写，其余按自动值处理。
     *
     * @param body 规则体，允许为 null
     * @return {@link #SOURCE_EXPLICIT} 或 {@link #SOURCE_DERIVED}
     */
    public static String source(@Nullable RuleValue body) {
        String declared = declared(body);
        return declared != null && !declared.equals(derive(body)) ? SOURCE_EXPLICIT : SOURCE_DERIVED;
    }

    /**
     * 打开会话时把服务端下发的分类补进草稿 JSON。
     *
     * <p>草稿已声明分类时原样返回：来源视图可能带回作者原文，此时以原文为准。补回的分类
     * 同时进入草稿基线与工作副本，因此未改动直接保存既不会丢分类，也不会被判定为改动。</p>
     *
     * @param body 服务端下发的规则体，允许为 null
     * @param serverCategory 快照条目下发的分类，允许为 null
     * @return 补回分类后的规则体；无需补回时返回原值
     */
    public static RuleValue seed(@Nullable RuleValue body, @Nullable String serverCategory) {
        if (body == null || !body.isMapping() || serverCategory == null || serverCategory.isBlank()
                || declared(body) != null) {
            return body;
        }
        return RuleValue.mapping(body).put(RuleFields.CATEGORY, RuleValue.text(serverCategory)).build();
    }

    /**
     * 产出结构变化后按「感知默认值」更新草稿分类。
     *
     * @param draft 当前草稿
     * @param derivedBefore 变化前按产出派生的分类；变化前草稿不可读时为 null
     * @return 是否写入了新的分类值
     */
    public static boolean follow(RuleDraft draft, @Nullable String derivedBefore) {
        if (draft == null) {
            return false;
        }
        RuleValue body = draft.root();
        String declared = declared(body);
        if (declared != null && !declared.equals(derivedBefore)) {
            // 与变化前的派生值不同：作者手写值，保持不动
            return false;
        }
        String derived = derive(body);
        if (derived.equals(declared)) {
            return false;
        }
        draft.setString(RuleFields.CATEGORY, derived);
        return true;
    }

    // 规则体的 JSON 对象视图；非映射值返回 null
    private static @Nullable JsonObject mappingOf(@Nullable RuleValue body) {
        return body == null || !body.isMapping() ? null : body.unwrap().getAsJsonObject();
    }
}
