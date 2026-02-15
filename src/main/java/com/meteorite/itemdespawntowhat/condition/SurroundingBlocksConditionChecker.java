package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
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

    private static final String CONDITION_PREFIX = "surrounding_blocks.";
    private Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> directionConditions;

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        this.directionConditions = parseDirectionConditions(conditions);

        // 如果没有任何方向的条件，返回null跳过
        if (directionConditions.isEmpty()) {
            return null;
        }

        return this;
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

    // 解析六面方块
    private Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> parseDirectionConditions(
            Map<String, String> conditions) {
        Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> directionConditions =
                new EnumMap<>(ConfigDirection.class);

        conditions.forEach((key, value) -> {
            if (key.startsWith(CONDITION_PREFIX)) {
                String directionStr = key.substring(CONDITION_PREFIX.length());
                ConfigDirection direction = ConfigDirection.fromString(directionStr);

                if (direction != null && !value.isEmpty()) {
                    Either<ResourceLocation, TagKey<Block>> condition = parseBlockCondition(value);
                    if (condition != null) {
                        directionConditions.put(direction, condition);
                    }
                }
            }
        });

        return directionConditions;
    }

    // 解析方块
    private Either<ResourceLocation, TagKey<Block>> parseBlockCondition(String conditionStr) {
        // 标签检测
        if (conditionStr.startsWith("#")) {
            String tagName = conditionStr.substring(1);
            ResourceLocation tagLocation = ResourceLocation.parse(tagName);
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagLocation);
            return Either.right(tagKey);
        } else {
            ResourceLocation blockLocation = ResourceLocation.parse(conditionStr);
            return Either.left(blockLocation);
        }
    }

    private boolean checkBlockCondition(BlockState blockState,
                                        Either<ResourceLocation, TagKey<Block>> condition) {
        return condition.map(
                blockId -> BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).equals(blockId),
                blockState::is
        );
    }

}
