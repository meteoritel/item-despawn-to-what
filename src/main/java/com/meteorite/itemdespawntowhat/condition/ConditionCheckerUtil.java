package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

// 此类用来统一管理所有的条件检查器
public class ConditionCheckerUtil {
    private static final Logger LOGGER = LogManager.getLogger();

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

        List<ConditionChecker> checkers = new ArrayList<>();

        for (var factory : ConditionCheckerRegistry.getFactories()) {
            AbstractConditionChecker checker = factory.get();

            AbstractConditionChecker parsed = checker.parse(conditions);
            if (parsed != null) {
                checkers.add(parsed);
            }
        }
        return combineAll(checkers);
    }

    // 外部使用，针对需要的条件构建
    public static ConditionChecker buildCombinedChecker(String dimension, boolean needOutdoor, SurroundingBlocks surroundingBlocks) {
        // 如果所有条件都是默认值，直接返回始终为true的检查器
        if ((dimension == null || dimension.isEmpty()) && !needOutdoor && (!surroundingBlocks.hasAnySurroundBlock())) {
            return (itemEntity, level) -> true;
        }

        Map<String, String> conditions = buildConditionMap(dimension, needOutdoor, surroundingBlocks);

        return buildCombinedChecker(conditions);
    }

    // 将参数转换为统一的条件Map格式
    private static Map<String, String> buildConditionMap(String dimension, boolean needOutdoor,
                                                         SurroundingBlocks surroundingBlocks) {
        Map<String, String> conditions = new HashMap<>();

        // 添加维度条件
        if (dimension != null && !dimension.isEmpty()) {
            conditions.put("dimension", dimension);
        }

        // 添加露天条件
        if (needOutdoor) {
            conditions.put("need_outdoor", "true");
        }

        // 添加周围方块条件
        if (surroundingBlocks != null) {
            addSurroundingBlockConditions(conditions, surroundingBlocks);
        }

        return conditions;
    }

    private static void addSurroundingBlockConditions(Map<String, String> conditions,
                                                      SurroundingBlocks surroundingBlocks) {
        for (ConfigDirection direction : ConfigDirection.values()) {
            String blockCondition = surroundingBlocks.get(direction);
            if (blockCondition != null && !blockCondition.isEmpty()) {
                String key = "surrounding_blocks." + direction.name().toLowerCase();
                conditions.put(key, blockCondition);
            }
        }
    }
}
