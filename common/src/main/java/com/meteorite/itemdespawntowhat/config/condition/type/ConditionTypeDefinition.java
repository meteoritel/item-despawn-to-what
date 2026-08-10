package com.meteorite.itemdespawntowhat.config.condition.type;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 定义一种条件的参数 DTO 及其运行时检查器工厂。
 */
public final class ConditionTypeDefinition<P> {
    private static final Gson GSON = new Gson();

    private final Class<P> parameterType;
    private final BiFunction<P, BaseConversionConfig, ConditionChecker> checkerFactory;
    private final Set<String> allowedFields;

    public ConditionTypeDefinition(
            Class<P> parameterType,
            BiFunction<P, BaseConversionConfig, ConditionChecker> checkerFactory
    ) {
        this.parameterType = Objects.requireNonNull(parameterType, "parameterType");
        this.checkerFactory = Objects.requireNonNull(checkerFactory, "checkerFactory");
        this.allowedFields = collectAllowedFields(parameterType);
    }

    ConditionChecker createChecker(JsonObject parameters, BaseConversionConfig config) {
        for (String field : parameters.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new JsonParseException("Unknown condition parameter: " + field);
            }
        }
        P parsed = GSON.fromJson(parameters, parameterType);
        if (parsed == null) {
            throw new JsonParseException("Condition parameters cannot be null");
        }
        ConditionChecker checker = checkerFactory.apply(parsed, config);
        if (checker == null) {
            throw new JsonParseException("Condition parameters are invalid for " + parameterType.getSimpleName());
        }
        return checker;
    }

    private static Set<String> collectAllowedFields(Class<?> parameterType) {
        Set<String> names = new HashSet<>();
        for (Field field : parameterType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            SerializedName serializedName = field.getAnnotation(SerializedName.class);
            names.add(serializedName == null ? field.getName() : serializedName.value());
            if (serializedName != null) {
                names.addAll(java.util.List.of(serializedName.alternate()));
            }
        }
        return Set.copyOf(names);
    }
}
