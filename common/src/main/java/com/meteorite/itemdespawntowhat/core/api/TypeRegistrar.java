package com.meteorite.itemdespawntowhat.core.api;

/**
 * 类型装配窗口内的注册能力：供内置登记与 SPI provider 使用，冻结由装配所有者负责。
 * 注册期仅在装配线程使用；装配结束后保留的句柄也不能改变已发布内容。
 *
 * @param <T> 注册的条件或效果类型定义
 */
public interface TypeRegistrar<T extends TypeDefinition<?>> extends TypeRegistry<T> {

    /**
     * 登记类型定义；重复 id 或装配窗口已关闭时抛出异常，不覆盖已登记类型。
     *
     * @param definition 待登记的类型定义
     */
    void register(T definition);
}
