package com.meteorite.itemdespawntowhat.util;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.*;

public class ConditionCheckerUtil {
    private static final Logger LOGGER = LogManager.getLogger();

    // 条件映射表
    private static final Map<ResourceLocation, ConditionBuilder> CONDITION_BUILDER_MAP = new HashMap<>(3);

    // 键名
    private static final ResourceLocation DIMENSION_KEY = ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "dimension");
    private static final ResourceLocation NEED_OUTDOOR_KEY = ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "need_outdoor");
    private static final ResourceLocation SURROUNDING_BLOCKS_KEY = ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "surrounding_blocks");

    static {
        registerConditionBuilders();
    }

    // 注册所有的单独条件构建器
    private static void registerConditionBuilders() {
        CONDITION_BUILDER_MAP.put(DIMENSION_KEY, ConditionCheckerUtil::buildDimensionChecker);
        CONDITION_BUILDER_MAP.put(NEED_OUTDOOR_KEY, ConditionCheckerUtil::buildOutdoorChecker);
        CONDITION_BUILDER_MAP.put(SURROUNDING_BLOCKS_KEY, ConditionCheckerUtil::buildSurroundingBlocksChecker);
    }

    // ========== 组合所有的条件检查器 ========== //

    public static ConditionChecker combineAll(List<ConditionChecker> checkers) {
        if (checkers.isEmpty()) {
            return (itemEntity, level) -> true;
        }

        // 任何一项条件检查器没有通过就不通过
        return (itemEntity, level) -> {
            for (ConditionChecker checker : checkers) {
                if (!checker.checkCondition(itemEntity,level)) {
                    return false;
                }
            }
            return true;
        };
    }

    // 构建结合条件检查器 : 从条件映射来构建。
    public static ConditionChecker buildCombinedChecker (Map<String, String> conditions) {

        if (conditions == null || conditions.isEmpty()) {
            return (itemEntity, level) -> true;
        }

        List<ConditionChecker> checkers = new ArrayList<>(3);

        for (Map.Entry<ResourceLocation, ConditionBuilder> entry : CONDITION_BUILDER_MAP.entrySet()) {
            ConditionChecker checker = entry.getValue().build(conditions);
            checkers.add(checker);
        }
        return combineAll(checkers);
    }

    // 外部使用，针对需要的条件构建
    public static ConditionChecker buildCombinedChecker(String dimension, boolean needOutdoor, SurroundingBlocks surroundingBlocks) {
        // 如果所有条件都是默认值，直接返回始终为true的检查器
        if ((dimension == null || dimension.isEmpty()) && !needOutdoor && (!surroundingBlocks.hasAnySurroundBlock())) {
            return (itemEntity, level) -> true;
        }

        Map<String, String> conditions = new HashMap<>();

        // 将条件转换为统一的键值对格式
        conditions.put("dimension", dimension != null ? dimension : "");
        conditions.put("need_outdoor", Boolean.toString(needOutdoor));

        // 将周围方块条件展开为键值对
        if (surroundingBlocks != null) {
            conditions.put("surrounding_blocks.north", surroundingBlocks.getNorth());
            conditions.put("surrounding_blocks.south", surroundingBlocks.getSouth());
            conditions.put("surrounding_blocks.east", surroundingBlocks.getEast());
            conditions.put("surrounding_blocks.west", surroundingBlocks.getWest());
            conditions.put("surrounding_blocks.up", surroundingBlocks.getUp());
            conditions.put("surrounding_blocks.down", surroundingBlocks.getDown());
        }

        return buildCombinedChecker(conditions);
    }

    // =========== 构建所有的单个条件检查器 ========== //

    // 构建维度检查器
    private static ConditionChecker buildDimensionChecker(Map<String, String> conditions) {
        String dimensionStr = conditions.getOrDefault("dimension", "");
        if (dimensionStr.isEmpty()) {
            return (itemEntity, level) -> true; // 无条件通过
        }

        ResourceKey<Level> dimensionKey = parseDimensionKey(dimensionStr);
        if (dimensionKey == null) {
            LOGGER.warn("Invalid dimension key: {}", dimensionStr);
            return (itemEntity, level) -> false;
        }

        return (itemEntity, level) -> level.dimension().equals(dimensionKey);
    }

    // 构建露天检查器
    private static ConditionChecker buildOutdoorChecker(Map<String, String> conditions) {

        String needOutdoor = conditions.getOrDefault("need_outdoor", "");
        if (needOutdoor.isEmpty()) {
            return (itemEntity, level) -> true;
        }

        // 不要求露天
        if (!Boolean.parseBoolean(needOutdoor)) {
            return (itemEntity, level) -> true;
        }

        return (itemEntity, level) -> {
            BlockPos pos = itemEntity.blockPosition();
            // 检查从当前位置到世界顶部的路径上是否有阻挡方块
            for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
                BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState state = level.getBlockState(checkPos);

                // 如果遇到非空气且非透明方块，则不是露天
                if (!state.isAir() && state.canOcclude()) {
                    return false;
                }
            }
            return true;
        };

    }

    // 构建周围方块检查器
    private static ConditionChecker buildSurroundingBlocksChecker(Map<String, String> conditions) {

        // 解析方块条件
        Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> directionConditions = parseDirectionConditions(conditions);

        if (directionConditions.isEmpty()) {
            return (itemEntity, level) -> true;
        }

        return (itemEntity, level) -> {
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
        };
    }

    // ========== 辅助方法：解析所有的条件键 ========== //

    // 解析维度
    private static ResourceKey<Level> parseDimensionKey(String dimensionStr) {
        try{
            ResourceLocation location = ResourceLocation.parse(dimensionStr);
            return ResourceKey.create(Registries.DIMENSION, location);
        } catch (Exception e) {
            LOGGER.error("Failed to parse dimension: {}", dimensionStr, e);
            return null;
        }
    }

    // 解析方块名称类型：标签或注册名
    private static Either<ResourceLocation, TagKey<Block>> parseBlockCondition(String conditionStr) {
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

    // 解析六面方块类型：将六面方块分别与对应面进行映射
    private static Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> parseDirectionConditions(
            Map<String, String> conditions) {
        Map<ConfigDirection, Either<ResourceLocation, TagKey<Block>>> directionConditions = new EnumMap<>(ConfigDirection.class);

        conditions.forEach((key, value) -> {
            if (key.startsWith("surrounding_blocks.")) {
                String directionStr = key.substring("surrounding_blocks.".length());
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

    // 检查方块是否匹配
    private static boolean checkBlockCondition(BlockState blockState,
                                               Either<ResourceLocation, TagKey<Block>> condition) {
        return condition.map(
                blockId -> BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).equals(blockId),
                blockState::is
        );
    }

}
