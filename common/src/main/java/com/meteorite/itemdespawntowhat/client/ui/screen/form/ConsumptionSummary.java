package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.network.chat.Component;

/** 规则源物品门槛与每轮扣除量的只读摘要。 */
public final class ConsumptionSummary {
    private ConsumptionSummary() { throw new UnsupportedOperationException("Utility class"); }

    /** 根据规则是否显式配置源数量，显示对应结算提示。 */
    public static Component hintRule(RuleValue rule) {
        return Component.translatable("gui.itemdespawntowhat.edit.consumption.hint."
                + (rule.has(RuleFields.SOURCE_COST) ? "fixed" : "implicit"));
    }
}
