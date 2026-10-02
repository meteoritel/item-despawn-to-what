package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/***
 * 模板来源插槽：默认使用 {@link RuleTemplates} 提供的 8 个内置样本模板（固定文件名与稳定顺序，
 * 与表单规格里的样本一一对应，标签键只对已知文件成立）；可通过 {@code setProvider(Provider)}
 * 安装第三方覆盖来源，保证「模板创建」入口始终可用。
 */
public final class RuleTemplateHooks {

    // 模板来源
    public interface Provider {
        // 模板清单
        List<Suggestion> list();

        // 读取模板规则体
        @Nullable JsonObject load(ResourceLocation id);
    }

    // 已注册来源
    private static @Nullable Provider provider;

    // 工具类
    private RuleTemplateHooks() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册模板来源（Lead 接线用）
    public static void setProvider(@Nullable Provider newProvider) {
        provider = newProvider;
    }

    // 是否已安装专用来源
    public static boolean hasProvider() {
        return provider != null;
    }

    // 模板清单
    public static List<Suggestion> list() {
        Provider current = provider;
        return current != null ? List.copyOf(current.list()) : RuleTemplates.list();
    }

    // 读取模板规则体
    public static @Nullable JsonObject load(ResourceLocation id) {
        Provider current = provider;
        return current != null ? current.load(id) : RuleTemplates.load(id);
    }
}
