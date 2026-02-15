package com.meteorite.itemdespawntowhat.condition;

import java.util.Map;

@FunctionalInterface
public interface ConditionBuilder {
    ConditionChecker build(Map<String, String> conditions);
}
