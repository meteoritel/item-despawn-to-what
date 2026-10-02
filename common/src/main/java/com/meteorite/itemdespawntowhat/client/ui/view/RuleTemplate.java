package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第一屏的「模板」：原转化类型的 UI 化表达，一个模板对应一种效果类型。
 * 点击模板 = 新建一条只含该效果的规则。
 */
public record RuleTemplate(ResourceLocation uiId, ResourceLocation effectType) {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    // 按钮文案 key（沿用原转化类型的既有 key，视觉与交互冻结）
    public String labelKey() {
        return "gui.itemdespawntowhat.config_type." + uiId.getPath();
    }

    // 按钮 tooltip key
    public String tooltipKey() {
        return labelKey() + ".tooltip";
    }

    // 新建一条只含对应效果的规则
    public RuleView createRule() {
        RuleView rule = RuleView.blank(nextRuleId());
        rule.setEffects(java.util.List.of(EffectView.fromJson(EffectParams.defaultEffectJson(effectType))));
        return rule;
    }

    // 生成稳定、合法的默认规则 id（namespace:path_序号）
    private ResourceLocation nextRuleId() {
        String path = uiId.getPath() + "_" + SEQUENCE.incrementAndGet();
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
