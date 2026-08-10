package com.meteorite.itemdespawntowhat.config.condition;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.condition.type.ConditionType;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 表示一个可取反的强类型原子条件。
 */
public final class ConditionLeaf {
    private static final Gson GSON = new Gson();

    @SerializedName("type")
    private String type;

    @SerializedName("params")
    private JsonObject params = new JsonObject();

    @SerializedName("negated")
    private boolean negated;

    public ConditionLeaf() {
    }

    public ConditionLeaf(ConditionType type, Object parameters, boolean negated) {
        this.type = Objects.requireNonNull(type, "type").id().toString();
        this.params = GSON.toJsonTree(parameters).getAsJsonObject();
        this.negated = negated;
    }

    /** 从原始标识和参数创建条件叶，供客户端未知类型回退使用。 */
    public ConditionLeaf(ResourceLocation typeId, JsonObject params, boolean negated) {
        this.type = Objects.requireNonNull(typeId, "typeId").toString();
        this.params = params == null ? new JsonObject() : params.deepCopy();
        this.negated = negated;
    }

    public ResourceLocation typeId() {
        ResourceLocation parsed = ResourceLocation.tryParse(type);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid condition type id: " + type);
        }
        return parsed;
    }

    public String rawTypeId() {
        return type;
    }

    public void setTypeId(ResourceLocation typeId) {
        this.type = Objects.requireNonNull(typeId, "typeId").toString();
    }

    public JsonObject params() {
        return params == null ? new JsonObject() : params;
    }

    public void setParams(JsonObject params) {
        this.params = params == null ? new JsonObject() : params.deepCopy();
    }

    public void setNegated(boolean negated) {
        this.negated = negated;
    }

    public <P> P parametersAs(Class<P> parameterType) {
        return GSON.fromJson(params(), parameterType);
    }

    public boolean negated() {
        return negated;
    }
}
