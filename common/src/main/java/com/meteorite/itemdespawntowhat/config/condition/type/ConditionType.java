package com.meteorite.itemdespawntowhat.config.condition.type;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 使用稳定标识符关联条件参数和运行时检查器的条件类型。
 */
public final class ConditionType {
    private final ResourceLocation id;
    private final String debugName;
    private final ConditionTypeDefinition<?> definition;

    ConditionType(ResourceLocation id, String debugName, ConditionTypeDefinition<?> definition) {
        this.id = Objects.requireNonNull(id, "id");
        this.debugName = Objects.requireNonNull(debugName, "debugName");
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public ResourceLocation id() {
        return id;
    }

    public String debugName() {
        return debugName;
    }

    ConditionTypeDefinition<?> definition() {
        return definition;
    }
}
