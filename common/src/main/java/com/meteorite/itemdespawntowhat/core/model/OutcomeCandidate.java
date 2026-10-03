package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * 候选结果：转化组的候选之一，内含多个共同执行的效果。
 * 候选标识在规则内唯一；safe_spawn（安全生成位置）与 fill_origin（起点填充）由阶段 5 执行侧落地，
 * 阶段 1 只负责承载、往返与本层校验（默认值对齐 PLAN §4.2：安全生成关闭、起点填充开启）。
 */
public record OutcomeCandidate(
        String id,
        List<Effect> effects,
        boolean safeSpawn,
        boolean fillOrigin
) {

    // 顶层 effects 隐式映射出的候选标识
    public static final String IMPLICIT_ID = "default";
    // 安全生成位置默认关闭（沿用原点行为）
    public static final boolean DEFAULT_SAFE_SPAWN = false;
    // 起点填充默认开启
    public static final boolean DEFAULT_FILL_ORIGIN = true;

    public OutcomeCandidate {
        id = id == null ? "" : id;
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    // 候选结果编解码器；效果编解码器由调用方按注册表提供
    public static Codec<OutcomeCandidate> codec(Codec<Effect> effectCodec) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf(RuleFields.CANDIDATE_ID).forGetter(OutcomeCandidate::id),
                effectCodec.listOf().optionalFieldOf(RuleFields.CANDIDATE_EFFECTS, List.of())
                        .forGetter(OutcomeCandidate::effects),
                Codec.BOOL.optionalFieldOf(RuleFields.SAFE_SPAWN, DEFAULT_SAFE_SPAWN)
                        .forGetter(OutcomeCandidate::safeSpawn),
                Codec.BOOL.optionalFieldOf(RuleFields.FILL_ORIGIN, DEFAULT_FILL_ORIGIN)
                        .forGetter(OutcomeCandidate::fillOrigin)
        ).apply(instance, OutcomeCandidate::new));
    }

    // 隐式候选：把旧格式的顶层 effects 映射成唯一候选（兼容行为，不写入 JSON）
    public static OutcomeCandidate implicit(List<Effect> effects) {
        return new OutcomeCandidate(IMPLICIT_ID, effects, DEFAULT_SAFE_SPAWN, DEFAULT_FILL_ORIGIN);
    }
}
