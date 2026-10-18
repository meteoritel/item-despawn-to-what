package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import org.jetbrains.annotations.Nullable;

/** 规则效果对象的只读类型定位。 */
public final class RuleCostBinding {
    private RuleCostBinding() { throw new UnsupportedOperationException("Utility class"); }
    /** 读取效果类型字符串 */
    public static @Nullable String typeOf(@Nullable RuleValue effect) {
        if (effect == null || !effect.isMapping()) {
            return null;
        }
        RuleValue type = effect.child(RuleFields.TYPE);
        if (type == null || !type.isText()) {
            return null;
        }
        return type.asText();
    }

}
