package com.meteorite.itemdespawntowhat.client.ui.condition;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 保存客户端条件类型定义，按注册顺序提供给编辑器。
 */
public final class ClientConditionTypeRegistry {
    private static final Map<ResourceLocation, ClientConditionTypeDefinition> TYPES = new LinkedHashMap<>();

    static {
        BuiltinClientConditionTypes.registerAll();
    }

    private ClientConditionTypeRegistry() {
    }

    public static synchronized void register(ClientConditionTypeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (TYPES.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate client condition type: " + definition.id());
        }
    }

    public static synchronized @Nullable ClientConditionTypeDefinition byId(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static synchronized ClientConditionTypeDefinition require(ResourceLocation id) {
        ClientConditionTypeDefinition definition = byId(id);
        if (definition == null) {
            throw new IllegalArgumentException("No client condition type registered for: " + id);
        }
        return definition;
    }

    public static synchronized List<ClientConditionTypeDefinition> all() {
        return List.copyOf(TYPES.values());
    }
}
