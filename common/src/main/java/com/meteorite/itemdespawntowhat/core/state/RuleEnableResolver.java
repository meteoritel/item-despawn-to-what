package com.meteorite.itemdespawntowhat.core.state;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 本服启用状态的只读解析面。
 * 加载链路只依赖这个窄接口，不直接持有存档数据对象；返回 null 表示该规则没有本服覆盖，继承规则内容里的默认启用。
 */
@FunctionalInterface
public interface RuleEnableResolver {

    /**
     * 读取某条规则的本服启用覆盖。
     *
     * @param id 规则标识
     * @return 本服覆盖值；没有覆盖时返回 null，表示继承默认启用
     */
    @Nullable Boolean overrideFor(ResourceLocation id);
}
