package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectOffset;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** 一次结算的冻结触发点与行为原点，准备、空间预算和交付共享同一解析结果。 */
final class EffectOrigins {
    private final Vec3 triggerPosition;
    private final Map<EffectOffset, Vec3> positions = new HashMap<>();

    EffectOrigins(Vec3 triggerPosition) {
        this.triggerPosition = triggerPosition;
    }

    Vec3 triggerPosition() { return triggerPosition; }

    Vec3 position(Effect effect) {
        return positions.computeIfAbsent(effect.offset(), offset -> offset.applyTo(triggerPosition));
    }
}
