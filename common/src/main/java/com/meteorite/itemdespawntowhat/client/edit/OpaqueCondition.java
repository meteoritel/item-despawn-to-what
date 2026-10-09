package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端无法识别的条件叶：只提供类型标识，完整节点 JSON 原样保留。
 * 不参与运行时求值；是否能正式保存由服务端注册表与校验决定。
 */
public final class OpaqueCondition implements Condition {

    private final ResourceLocation type;
    private final JsonObject rawNode;

    OpaqueCondition(ResourceLocation type, JsonObject rawNode) {
        this.type = type;
        this.rawNode = rawNode.deepCopy();
    }

    @Override
    public ResourceLocation type() {
        return type;
    }

    /** 返回独立副本，结构编辑与外部读取不能改变保留的原文。 */
    public JsonObject rawNode() {
        return rawNode.deepCopy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OpaqueCondition opaque
                && type.equals(opaque.type) && rawNode.equals(opaque.rawNode);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + rawNode.hashCode();
    }
}
