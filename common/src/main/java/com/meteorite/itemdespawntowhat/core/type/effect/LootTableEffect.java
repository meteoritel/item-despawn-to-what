package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.param.FloatSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ResLocSpec;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceCheck;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceContext;
import com.meteorite.itemdespawntowhat.core.model.CapacityDemand;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.LootTableExecutor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * loot_table：在触发位置按战利品表产出掉落物。
 * loot_table 为纯 ResourceLocation（无 tag 语义，故不使用 TaggedId）。本类只承载参数模型与参数校验。
 */
public record LootTableEffect(
        ResourceLocation lootTable,
        float luck,
        int delayTicks,
        double chance
) implements Effect, DynamicReferenceCheck {

    /** 效果类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "loot_table");

    // 类型专属参数字段名
    public static final String LOOT_TABLE_FIELD = "loot_table";
    public static final String LUCK_FIELD = "luck";

    // 参数默认值与取值区间
    public static final float DEFAULT_LUCK = 0.0F;
    public static final float MIN_LUCK = -100.0F;
    public static final float MAX_LUCK = 100.0F;

    /** 参数声明：类型专属字段在前，通用字段复用 CommonFields 的声明 */
    public static final ResLocSpec LOOT_TABLE = ResLocSpec.required(LOOT_TABLE_FIELD);
    public static final FloatSpec LUCK = FloatSpec.defaulted(LUCK_FIELD, DEFAULT_LUCK, MIN_LUCK, MAX_LUCK)
            .initial(DEFAULT_LUCK);
    public static final ParamList<LootTableEffect> PARAMS = ParamList.of(LOOT_TABLE.bind(LootTableEffect::lootTable), LUCK.bind(LootTableEffect::luck)).plus(CommonFields.EFFECT_PARAMS);

    /** 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null） */
    public static MapCodec<LootTableEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(LOOT_TABLE),
                PARAMS.field(LUCK),
                PARAMS.field(CommonFields.DELAY_TICKS),
                PARAMS.field(CommonFields.CHANCE)
        ).apply(instance, LootTableEffect::new));
    }

    /** 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。；显式实现准备入口与按准备创建执行状态的重载：本次战利品在成功转化当刻开表一次并固定，；交付期不再读取原源实体、不再重开战利品表，也不再次随机抽取。；容量需求：产物类型未知，按「每组至少 1 件物品」对区域物品总量保守占用容量。 */
    public static EffectType<LootTableEffect> effectType() {
        return new SimpleEffectType<>(ID, codec(), new EffectExecutor<>() {
                    @Override
                    public EffectResult execute(LootTableEffect effect, EffectContext context) {
                        return LootTableExecutor.execute(effect, context);
                    }

                    @Override
                    public EffectPreparation prepare(LootTableEffect effect, ServerLevel level, Vec3 position,
                                                               ItemEntity sourceEntity, ItemStack sourceStack,
                                                               boolean fillOrigin) {
                        // 战利品求值需要真实源实体作为 THIS_ENTITY，故这一步必须在源实体清空之前完成
                        return LootTableExecutor.prepare(effect, level, position, sourceEntity, sourceStack, fillOrigin);
                    }

                    @Override
                    public EffectExecution start(LootTableEffect effect, EffectContext context) {
                        // 无固定输入入口同样不做到期重新抽取：产物只在成功转化当刻固定
                        return LootTableExecutor.start(effect, context, null);
                    }

                    @Override
                    public EffectExecution start(LootTableEffect effect, EffectContext context,
                                                 @Nullable EffectPreparation preparation) {
                        return LootTableExecutor.start(effect, context, preparation);
                    }
                }, ProductKind.LOOT, params -> CapacityDemand.unspecified(1), PARAMS);
    }

    /**
     * 动态引用校验：战利品表必须能在服务端已加载的战利品表注册表中查到。
     *
     * @param context 当前服务端、统一议题收集通道与当前效果的字段路径
     */
    @Override
    public void checkDynamicReferences(DynamicReferenceContext context) {
        if (context.server().reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
                .flatMap(tables -> tables.get(ResourceKey.create(Registries.LOOT_TABLE, lootTable))).isEmpty()) {
            context.issues().error("未找到已加载的战利品表: " + lootTable, null,
                    ParamChecks.child(context.fieldPath(), LOOT_TABLE_FIELD));
        }
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

}
