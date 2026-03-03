package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.condition.checker.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

// 此类用来统一管理所有的条件检查器
public class ConditionCheckerUtil {
    private static final Logger LOGGER = LogManager.getLogger();

    // 核心方法：从通用条件 Map 构建组合条件检查器（配置文件加载场景）
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

    // 便捷重载：接受强类型参数，各配置对象通过 toConditionMap() 自行写入 Map，再走统一解析路径
    public static ConditionChecker buildCombinedChecker(ConditionContext ctx) {
        Map<String, String> conditions = new HashMap<>();

        // 每种 Checker 自行声明键名，复合对象自行序列化
        for (var factory : ConditionCheckerRegistry.getFactories()) {
            AbstractConditionChecker checker = factory.get();
            if (checker.shouldApply(ctx)) {
                checker.applyCondition(conditions,ctx);
            }
        }
        return buildCombinedChecker(conditions);
    }

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
}
