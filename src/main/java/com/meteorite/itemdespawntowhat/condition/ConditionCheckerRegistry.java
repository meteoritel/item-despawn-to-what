package com.meteorite.itemdespawntowhat.condition;

import java.util.*;
import java.util.function.Supplier;


// 条件检查器统一注册类
public class ConditionCheckerRegistry {
    private static final List<Supplier<AbstractConditionChecker>> CHECKER_FACTORIES = new ArrayList<>();

    static {
        registerDefaultCheckers();
    }

    private static void registerDefaultCheckers() {
        register(DimensionConditionChecker::new);
        register(OutdoorConditionChecker::new);
        register(SurroundingBlocksConditionChecker::new);
    }

    public static void register(Supplier<AbstractConditionChecker> factory) {
        CHECKER_FACTORIES.add(factory);
    }

    public static List<Supplier<AbstractConditionChecker>> getFactories() {
        return new ArrayList<>(CHECKER_FACTORIES);
    }

}
