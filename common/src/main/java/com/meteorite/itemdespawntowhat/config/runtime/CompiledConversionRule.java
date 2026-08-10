package com.meteorite.itemdespawntowhat.config.runtime;

import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 封装已解析条件与执行配置的服务端运行时规则。
 */
public final class CompiledConversionRule {
    private final BaseConversionConfig definition;
    private final ConditionChecker conditionChecker;
    private final int complexity;

    CompiledConversionRule(BaseConversionConfig definition, ConditionChecker conditionChecker) {
        this.definition = definition;
        this.conditionChecker = conditionChecker;
        this.complexity = definition.computeComplexity();
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

    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return definition.isResultLimitExceeded(itemEntity);
    }

    public boolean performConversion(ItemEntity itemEntity, ServerLevel level) {
        return definition.performConversion(itemEntity, level);
    }
}
