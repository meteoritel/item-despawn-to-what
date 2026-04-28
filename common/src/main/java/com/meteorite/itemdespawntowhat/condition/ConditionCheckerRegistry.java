package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.condition.checker.*;

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
        register(CatalystConditionChecker::new);
        register(InnerFluidConditionChecker::new);
    }

    public static void register(Supplier<AbstractConditionChecker> factory) {
        CHECKER_FACTORIES.add(factory);
    }

    public static List<Supplier<AbstractConditionChecker>> getFactories() {
        return Collections.unmodifiableList(CHECKER_FACTORIES);
    }
}
