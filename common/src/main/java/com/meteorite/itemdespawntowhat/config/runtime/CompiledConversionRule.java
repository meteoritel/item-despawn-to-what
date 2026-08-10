package com.meteorite.itemdespawntowhat.config.runtime;

import com.meteorite.itemdespawntowhat.condition.checker.CombinedConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionDebugResult;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.execution.ConversionExecutor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

/**
 * 封装已解析条件与执行配置的服务端运行时规则。
 */
public final class CompiledConversionRule {
    private final BaseConversionConfig definition;
    private final CombinedConditionChecker conditionChecker;
    private final int complexity;
    private final ConversionExecutor<BaseConversionConfig> executor;

    @SuppressWarnings("unchecked")
    CompiledConversionRule(BaseConversionConfig definition, CombinedConditionChecker conditionChecker,
                           ConversionExecutor<? super BaseConversionConfig> executor) {
        this.definition = definition;
        this.conditionChecker = conditionChecker;
        this.complexity = definition.computeComplexity();
        this.executor = (ConversionExecutor<BaseConversionConfig>) executor;
    }

    public BaseConversionConfig definition() {
        return definition;
    }

    public String internalId() {
        return definition.getInternalId();
    }

    public int complexity() {
        return complexity;
    }

    public int sourceMultiple() {
        return definition.getSourceMultiple();
    }

    public int conversionTime() {
        return definition.getConversionTime();
    }

    public boolean matches(ItemEntity itemEntity, ServerLevel level) {
        return conditionChecker.checkCondition(itemEntity, level);
    }

    public List<ConditionDebugResult> debugConditions(ItemEntity itemEntity, ServerLevel level) {
        return conditionChecker.debug(itemEntity, level);
    }

    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return executor.isResultLimitExceeded(definition, itemEntity);
    }

    public boolean performConversion(ItemEntity itemEntity, ServerLevel level) {
        return executor.performConversion(definition, itemEntity, level);
    }
}
