package com.meteorite.itemdespawntowhat.core.registry;

import net.minecraft.resources.ResourceLocation;

/**
 * 类型 id 重复注册时抛出的异常。
 *
 * <p>注册表不允许静默覆盖：同一 id 第二次注册必须由调用方显式处理（改用新 id，或先移除旧定义）。
 * 异常信息与 {@link #typeId()} 同时给出冲突 id、已注册一方与本次注册一方的实现类，
 * 便于在启动/重载日志中直接定位是哪个扩展重复注册。
 */
public final class DuplicateTypeException extends IllegalStateException {

    // 固定的序列化版本号：异常不承载跨进程语义，版本变化无需兼容
    private static final long serialVersionUID = 1L;

    // 冲突的类型 id 文本（不持有 ResourceLocation，避免异常序列化时携带不可序列化字段）
    private final String typeId;

    // 已注册一方的描述：实现类名 + 身份哈希
    private final String existingDescription;

    // 本次试图注册一方的描述：实现类名 + 身份哈希
    private final String incomingDescription;

    // 构造异常：id 为冲突的类型 id，existing 为已注册方，incoming 为本次试图注册方
    public DuplicateTypeException(ResourceLocation id, Object existing, Object incoming) {
        super("类型 id 重复: " + id + "，已注册: " + describe(existing) + "，冲突方: " + describe(incoming));
        this.typeId = String.valueOf(id);
        this.existingDescription = describe(existing);
        this.incomingDescription = describe(incoming);
    }

    // 冲突的类型 id 文本
    public String typeId() {
        return typeId;
    }

    // 已注册一方的描述
    public String existingDescription() {
        return existingDescription;
    }

    // 本次试图注册一方的描述
    public String incomingDescription() {
        return incomingDescription;
    }

    // 生成可读描述：实现类名 + 身份哈希，不依赖可能未实现的 toString()
    private static String describe(Object definition) {
        if (definition == null) {
            return "<null>";
        }
        return definition.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(definition));
    }
}
