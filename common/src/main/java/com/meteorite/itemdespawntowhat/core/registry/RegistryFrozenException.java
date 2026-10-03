package com.meteorite.itemdespawntowhat.core.registry;

import net.minecraft.resources.ResourceLocation;

/**
 * 在已冻结的注册表上继续注册时抛出的异常。
 *
 * <p>冻结（freeze）是注册表的终态：{@link SimpleTypeRegistry#freeze()} 之后注册表只读，
 * 任何注册请求都会被拒绝，以免已发布的不可变快照与注册表内容不一致。
 */
public final class RegistryFrozenException extends IllegalStateException {

    // 固定的序列化版本号：异常不承载跨进程语义，版本变化无需兼容
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    // 被拒绝注册的类型 id 文本（不持有 ResourceLocation，避免异常序列化时携带不可序列化字段）
    private final String typeId;

    // 被拒绝一方的描述：实现类名 + 身份哈希
    private final String definitionDescription;

    // 构造异常：id 为被拒绝的类型 id，definition 为本次试图注册方
    public RegistryFrozenException(ResourceLocation id, Object definition) {
        super("注册表已冻结，拒绝注册类型 id: " + id + "（实现: " + describe(definition)
                + "）；freeze() 之后注册表不可变，只能查询");
        this.typeId = String.valueOf(id);
        this.definitionDescription = describe(definition);
    }

    // 被拒绝注册的类型 id 文本
    public String typeId() {
        return typeId;
    }

    // 被拒绝一方的描述
    public String definitionDescription() {
        return definitionDescription;
    }

    // 生成可读描述：实现类名 + 身份哈希，不依赖可能未实现的 toString()
    private static String describe(Object definition) {
        if (definition == null) {
            return "<null>";
        }
        return definition.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(definition));
    }
}
