package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.LightningExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * lightning：在触发位置召唤闪电。
 * 注册为一次性效果（oneShot）：不参与候选容量计算，对同一源最多尝试一组。
 * 本类只承载参数模型与参数校验，运行时执行逻辑见 LightningExecutor。
 */
public record LightningEffect(
        int count,
        int delayTicks,
        double chance
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "lightning");

    // 类型专属参数字段名
    public static final String COUNT_FIELD = "count";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 16;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<LightningEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(LightningEffect::count),
                CommonFields.delayTicks(LightningEffect::delayTicks),
                CommonFields.chance(LightningEffect::chance)
        ).apply(instance, (count, delayTicks, chance) ->
                new LightningEffect(count, delayTicks, chance)));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现执行器契约：闪电序列只能经预算内的执行状态推进，直接 execute 一律拒绝；
    // 本类型没有自有的成功时输入，只声明准备阶段以让概率在成功转化当刻判定一次。
    public static EffectType<LightningEffect> effectType() {
        EffectExecutor<LightningEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(LightningEffect effect, EffectContext context) {
                return LightningExecutor.execute(effect, context);
            }

            @Override
            public EffectPreparation prepare(LightningEffect effect, ServerLevel level, Vec3 position,
                                             ItemEntity sourceEntity, ItemStack sourceStack, boolean fillOrigin) {
                // 落点高度、地表与区块加载都属执行期实时量，此时只标记本次参与成功当刻判定
                return EffectPreparation.ready();
            }

            @Override
            public EffectExecution start(LightningEffect effect, EffectContext context, @Nullable EffectPreparation preparation) {
                return LightningExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(), LightningEffect::validateParams, executor, true,
                ProductKind.WORLD);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(LightningEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

}
