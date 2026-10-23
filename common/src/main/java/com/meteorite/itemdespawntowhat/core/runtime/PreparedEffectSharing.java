package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;

import java.util.HashMap;
import java.util.Map;

/** 同一结算内按候选与槽位连接固定输入，领先准备与续接准备共用同一共享规则。 */
final class PreparedEffectSharing {
    private final Map<ConversionRuntime.PreparedEffectKey, EffectPreparation> firstBySlot = new HashMap<>();

    EffectPreparation share(ConversionRuntime.PreparedEffectKey key, EffectPreparation preparation) {
        if (!(preparation instanceof EffectPreparation.SharedPreparation)
                && !(preparation instanceof EffectPreparation.GroupedPreparation)) {
            return preparation;
        }
        EffectPreparation first = firstBySlot.putIfAbsent(
                new ConversionRuntime.PreparedEffectKey(key.candidateId(), key.slot(), 0), preparation);
        if (first == null) {
            return preparation;
        }
        if (preparation instanceof EffectPreparation.SharedPreparation
                && first instanceof EffectPreparation.SharedPreparation) {
            return first;
        }
        if (preparation instanceof EffectPreparation.GroupedPreparation grouped
                && first instanceof EffectPreparation.GroupedPreparation firstGrouped) {
            grouped.shareStateWith(firstGrouped);
        }
        return preparation;
    }
}
