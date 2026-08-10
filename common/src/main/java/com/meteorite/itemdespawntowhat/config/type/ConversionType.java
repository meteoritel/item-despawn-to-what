package com.meteorite.itemdespawntowhat.config.type;

import net.minecraft.resources.ResourceLocation;

/**
 * 可扩展的转换类型注册对象。
 */
public final class ConversionType {
    private final ResourceLocation id;
    private final ConversionTypeDefinition<?> definition;

    ConversionType(ResourceLocation id, ConversionTypeDefinition<?> definition) {
        this.id = id;
        this.definition = definition;
    }

    public ResourceLocation id() {
        return id;
    }

    public String getSerializedId() {
        return id.toString();
    }

    public String getFileName() {
        return id.getNamespace() + "/" + id.getPath() + ".json";
    }

    public ConversionTypeDefinition<?> definition() {
        return definition;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
