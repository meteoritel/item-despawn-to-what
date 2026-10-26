package com.meteorite.itemdespawntowhat.client.edit;

/**
 * 编辑器类型注册表的持有者：条件与效果各持一份。
 * <p>两份注册表结构同构，差异只在未注册类型的回退标签 key，
 * 因此集中在这里定义；调用方直接使用字段，不再经由单方法门面类。
 * <p>{@code BuiltinEditorDescriptors} 为客户端已发现且声明参数的类型自动注册描述符；
 * 扩展入口也可在自动装配前手动注册定制描述符。
 */
public final class EditorRegistries {

    // 条件类型编辑器注册表，回退标签 key 为 gui.itemdespawntowhat.edit.unknown_condition
    public static final TypeEditorRegistry CONDITIONS =
            new TypeEditorRegistry(TypeLabels.UI_PREFIX + "unknown_condition");

    // 效果类型编辑器注册表，回退标签 key 为 gui.itemdespawntowhat.edit.unknown_effect
    public static final TypeEditorRegistry EFFECTS =
            new TypeEditorRegistry(TypeLabels.UI_PREFIX + "unknown_effect");

    private EditorRegistries() { }
}
