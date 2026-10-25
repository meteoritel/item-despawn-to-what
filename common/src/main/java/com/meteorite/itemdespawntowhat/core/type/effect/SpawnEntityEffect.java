package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.model.CapacityDemand;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnEntityExecutor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 统一实体生成效果：按 variant 分发产物，公共执行字段只定义一次。 */
public record SpawnEntityEffect(EntityProduct product, int delayTicks, double chance) implements Effect {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "spawn_entity");

    // 专用参数与公共字段同层编码，方便直接使用子类编辑表单。
    public static MapCodec<SpawnEntityEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                EntityProduct.CODEC.forGetter(SpawnEntityEffect::product),
                CommonFields.delayTicks(SpawnEntityEffect::delayTicks),
                CommonFields.chance(SpawnEntityEffect::chance)
        ).apply(instance, SpawnEntityEffect::new));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现准备入口：三个产出子类的引用与产出量都在成功转化当刻固定，交付期不再读原源实体。
    public static EffectType<SpawnEntityEffect> effectType() {
        EffectExecutor<SpawnEntityEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(SpawnEntityEffect effect, EffectContext context) {
                return SpawnEntityExecutor.execute(effect, context);
            }

            @Override
            public @Nullable EffectPreparation prepare(SpawnEntityEffect effect, ServerLevel level, Vec3 position,
                                                       ItemEntity sourceEntity, ItemStack sourceStack,
                                                       boolean fillOrigin) {
                return SpawnEntityExecutor.prepare(effect, level, position, sourceEntity, sourceStack, fillOrigin);
            }

            @Override
            public EffectExecution start(SpawnEntityEffect effect, EffectContext context,
                                         @Nullable EffectPreparation preparation) {
                return SpawnEntityExecutor.start(effect, context, preparation);
            }
        };
        // 产出类别与容量需求都随 variant 变化：物品 / 生物 / 其他实体 / 经验各自声明自己的产出与占用
        return new SimpleEffectType<>(ID, codec(), SpawnEntityEffect::validateParams, executor,
                false, SpawnEntityEffect::productKind, SpawnEntityEffect::capacityDemand);
    }

    // 分类器读取的产出类别；缺少产出子类的规则无法加载，按未知产出兜底
    private static ProductKind productKind(SpawnEntityEffect effect) {
        return effect.product() == null ? ProductKind.EXTENSION : effect.product().productKind();
    }

    // 容量需求声明：物品按件数、实体按个数计入邻近上限；经验只受执行批次预算约束，不占邻近容量
    private static CapacityDemand capacityDemand(SpawnEntityEffect effect) {
        if (effect.product() == null) {
            return CapacityDemand.NONE;
        }
        return switch (effect.product()) {
            case EntityProduct.Item item -> CapacityDemand.item(item.item(), item.count());
            case EntityProduct.Mob mob -> CapacityDemand.entity(mob.entity(), mob.count());
            case EntityProduct.Generic entity -> CapacityDemand.entity(entity.entity(), entity.count());
            case EntityProduct.Experience ignored -> CapacityDemand.NONE;
        };
    }

    // 参数语义校验：子类选择必填，随后交给对应子类校验自身字段
    public static boolean validateParams(SpawnEntityEffect effect, IssueCollector issues, String path) {
        if (effect.product() == null) {
            issues.error("请选择实体产出子类", null, path + ".variant");
            return false;
        }
        return effect.product().validate(issues, path);
    }

    @Override public ResourceLocation type() { return ID; }

}
