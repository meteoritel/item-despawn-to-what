package com.meteorite.expiringitemlib.util;

import java.util.Map;

@FunctionalInterface
public interface ConditionBuilder {
    ConditionChecker build(Map<String, String> conditions);
}
