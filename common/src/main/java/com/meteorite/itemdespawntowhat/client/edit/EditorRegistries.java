package com.meteorite.itemdespawntowhat.client.edit;

/**
 * 编辑器类型注册表的持有者：条件与效果各持一份。
 * <p>两份注册表结构同构，差异只在未注册类型的回退标签 key，
 * 因此集中在这里定义；调用方直接使用字段，不再经由单方法门面类。
 * <p>注册表本身不自动装载内容，内置类型由 {@code BuiltinEditorDescriptors} 注册，
 * 第三方类型由扩展入口注册。
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
