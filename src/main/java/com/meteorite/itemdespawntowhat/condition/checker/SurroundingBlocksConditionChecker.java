package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

public class SurroundingBlocksConditionChecker extends AbstractConditionChecker {

    public static final String KEY = "surrounding_blocks";
    private Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> directionConditions;

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.surroundingBlocks() != null;
    }

    @Override
    public void applyCondition(Map<String, String> conditions, ConditionContext ctx) {
        ctx.surroundingBlocks().toConditionMap(conditions, getConditionKey());
    }

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        SurroundingBlocks parsed = new SurroundingBlocks().fromConditionMap(conditions, getConditionKey());
        return from(parsed);
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        BlockPos centerPos = itemEntity.blockPosition();

        // 检查6个面的方块是否符合要求
        for (Map.Entry<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> entry : directionConditions.entrySet()) {
            BlockPos checkPos = centerPos.relative(entry.getKey().getDirection());
            BlockState blockState = level.getBlockState(checkPos);

            if (!checkBlockCondition(blockState, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getConditionKey() {
        return KEY;
    }

    // 解析六面的方块
    public static SurroundingBlocksConditionChecker from(SurroundingBlocks sbs) {
        if (sbs == null || !sbs.hasAnySurroundBlock()) {
            return null;
        }

        Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> conditions = new EnumMap<>(ConfigDirection.class);

        for (ConfigDirection dir : ConfigDirection.values()) {
            Either<ResourceLocation, TagKey<Block>> condition = parseBlockCondition(sbs.get(dir));
            if (condition != null) {
                conditions.put(dir, condition);
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }

        SurroundingBlocksConditionChecker checker = new SurroundingBlocksConditionChecker();
        checker.directionConditions = conditions;
        return checker;
    }

    // 解析方块
    private static Either<ResourceLocation, TagKey<Block>> parseBlockCondition(String conditionStr) {
        if (conditionStr == null || conditionStr.isEmpty()) {
            return null;
        }

        // 标签检测
        if (conditionStr.startsWith("#")) {
            ResourceLocation tagLocation = ResourceLocation.parse(conditionStr.substring(1));
            return Either.right(TagKey.create(Registries.BLOCK, tagLocation));
        } else {
            return Either.left(ResourceLocation.parse(conditionStr));
        }
    }

    private static boolean checkBlockCondition(BlockState blockState,
                                        Either<ResourceLocation, TagKey<Block>> condition) {
        return condition.map(
                blockId -> BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).equals(blockId),
                blockState::is
        );
    }
}
