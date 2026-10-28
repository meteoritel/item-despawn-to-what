package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * 行为相对成功触发点的世界坐标偏移，单位为整数格。
 * 源触发点保留小数位置，偏移仅在解析行为原点时相加。
 *
 * @param x 东向为正、西向为负
 * @param y 上向为正、下向为负
 * @param z 南向为正、北向为负
 */
public record EffectOffset(int x, int y, int z) {
    public static final String X_FIELD = "x";
    public static final String Y_FIELD = "y";
    public static final String Z_FIELD = "z";
    public static final EffectOffset ZERO = new EffectOffset(0, 0, 0);

    public static final IntSpec X = axis(X_FIELD);
    public static final IntSpec Y = axis(Y_FIELD);
    public static final IntSpec Z = axis(Z_FIELD);
    public static final ParamList<EffectOffset> PARAMS = ParamList.of(
            X.bind(EffectOffset::x), Y.bind(EffectOffset::y), Z.bind(EffectOffset::z));
    public static final Codec<EffectOffset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PARAMS.field(X), PARAMS.field(Y), PARAMS.field(Z)
    ).apply(instance, EffectOffset::new));

    private static IntSpec axis(String name) {
        return IntSpec.defaulted(name, 0, Integer.MIN_VALUE, Integer.MAX_VALUE)
                .exact().label("common.offset." + name);
    }

    /** 从冻结的触发点解析行为原点；零偏移直接复用输入向量。 */
    public Vec3 applyTo(Vec3 triggerPosition) {
        return equals(ZERO) ? triggerPosition : triggerPosition.add(x, y, z);
    }

    /** 加载与保存共用服务端逐轴上限，错误路径指向行为的具体轴。 */
    public void validateLimits(RuleRuntimeLimits limits, IssueCollector issues, String path) {
        ParamChecks.inRange(x, -limits.maxEffectOffsetX(), limits.maxEffectOffsetX(),
                X_FIELD, issues, ParamChecks.child(path, X_FIELD));
        ParamChecks.inRange(y, -limits.maxEffectOffsetY(), limits.maxEffectOffsetY(),
                Y_FIELD, issues, ParamChecks.child(path, Y_FIELD));
        ParamChecks.inRange(z, -limits.maxEffectOffsetZ(), limits.maxEffectOffsetZ(),
                Z_FIELD, issues, ParamChecks.child(path, Z_FIELD));
    }
}
