package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/***
 * 内置规则模板：从资源包 data/itemdespawntowhat/idtw/rules/ 下读取 8 个内置样本规则，
 * 供「模板创建」入口列出候选并读取模板内容。
 * <p>只读，不写任何文件；资源缺失、解析失败一律跳过，不向界面抛异常。
 * <p>形状与 {@link RuleTemplateHooks.Provider} 一致，可由接线方注册为模板来源。
 */
public final class RuleTemplates {

    // 本模组命名空间
    private static final String NAMESPACE = "itemdespawntowhat";

    // 内置模板所在目录
    private static final String DIRECTORY = "idtw/rules";

    // 模板标签键前缀
    private static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.template.";

    // 模板文件名前缀
    private static final String NAME_PREFIX = "builtin_";

    // 模板文件扩展名
    private static final String EXTENSION = ".json";

    // 8 个内置模板文件名（固定顺序，保证列表稳定）
    private static final List<String> FILES = List.of(
            "builtin_item_to_item.json",
            "builtin_item_to_entity.json",
            "builtin_item_to_block.json",
            "builtin_multi_effect.json",
            "builtin_loot_and_chance.json",
            "builtin_conditions.json",
            "builtin_weather_and_light.json",
            "builtin_arrow_rain.json");

    // 工具类
    private RuleTemplates() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 模板清单：value 为完整资源 id，label 为本地化标签键
    public static List<Suggestion> list() {
        List<Suggestion> result = new ArrayList<>();
        for (String file : FILES) {
            ResourceLocation id = idOf(file);
            if (id == null || manager().getResource(id).isEmpty()) {
                continue;
            }
            result.add(new Suggestion(id.toString(), Component.translatable(LABEL_PREFIX + nameOf(file))));
        }
        return List.copyOf(result);
    }

    // 读取模板规则体：id 为 null、资源不存在或解析失败时返回 null
    public static @Nullable JsonObject load(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        String file = path.substring(path.lastIndexOf('/') + 1);
        return read(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), DIRECTORY + "/" + file));
    }

    // 读取指定资源的顶层 JSON 对象；失败一律返回 null 并记一条警告
    private static @Nullable JsonObject read(ResourceLocation id) {
        Resource resource = manager().getResource(id).orElse(null);
        if (resource == null) {
            return null;
        }
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement element = JsonParser.parseReader(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception exception) {
            Constants.LOG.warn("读取内置规则模板失败: {}", id, exception);
            return null;
        }
    }

    // 资源管理器（客户端资源包视图）
    private static ResourceManager manager() {
        return Minecraft.getInstance().getResourceManager();
    }

    // 模板文件的资源 id
    private static @Nullable ResourceLocation idOf(String file) {
        return ResourceLocation.tryParse(NAMESPACE + ":" + DIRECTORY + "/" + file);
    }

    // 由文件名取标签名：去掉 builtin_ 前缀与 .json 后缀
    private static String nameOf(String file) {
        String name = file;
        if (name.startsWith(NAME_PREFIX)) {
            name = name.substring(NAME_PREFIX.length());
        }
        if (name.endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        return name;
    }
}
