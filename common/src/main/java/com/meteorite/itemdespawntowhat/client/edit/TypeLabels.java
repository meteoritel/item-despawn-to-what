package com.meteorite.itemdespawntowhat.client.edit;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 类型标签工具：把条件/效果类型 id 转成本地化文本。
 * <p>按契约 §5.1 的裁决，类型显示名使用 {@code gui.itemdespawntowhat.edit.condition.<type>} 与
 * {@code gui.itemdespawntowhat.edit.effect.<type>}；本模组命名空间下 {@code <type>} 即路径
 * （例如 {@code gui.itemdespawntowhat.edit.condition.y_level}），第三方命名空间下为了不撞键
 * 使用 {@code <namespace>.<path>}。若新键缺失，回退到旧版约定 {@code <kind>.<namespace>.type.<path>}，
 * 再回退到原始 id 字符串，保证第三方扩展类型在没有 lang 条目时也能被识别。
 */
public final class TypeLabels {

    // 界面文本前缀（契约 §5.1）
    public static final String UI_PREFIX = "gui.itemdespawntowhat.edit.";

    // 本模组命名空间
    public static final String OWN_NAMESPACE = "itemdespawntowhat";

    private TypeLabels() {
    }

    // 条件类型标签
    public static Component conditionLabel(ResourceLocation id) {
        return label("condition", id);
    }

    // 效果类型标签
    public static Component effectLabel(ResourceLocation id) {
        return label("effect", id);
    }

    // 按契约 §5.1 生成类型显示名 key（conditions/effects 的类型名）
    public static String displayKey(String kind, ResourceLocation id) {
        return UI_PREFIX + kind + "." + typeSegment(id);
    }

    // 类型显示名 key 的兼容别名：对外统一走契约 §5.1 的口径
    public static String key(String kind, ResourceLocation id) {
        return displayKey(kind, id);
    }

    // 旧版约定 key：<kind>.<namespace>.type.<path>（仅作回退）
    public static String legacyKey(String kind, ResourceLocation id) {
        return kind + "." + id.getNamespace() + ".type." + id.getPath();
    }

    // 类型在 key 中的片段：本模组用 path，第三方用 namespace + "." + path
    public static String typeSegment(ResourceLocation id) {
        return OWN_NAMESPACE.equals(id.getNamespace())
                ? id.getPath()
                : id.getNamespace() + "." + id.getPath();
    }

    // 依次尝试新键、旧键、原始 id；键是否存在与语言无关（en_us 与 zh_cn 键集合必须一致），
    // 因此这里的判断在切换语言后依然成立，文本本身仍是惰性翻译。
    private static Component label(String kind, ResourceLocation id) {
        Language language = Language.getInstance();
        String displayKey = displayKey(kind, id);
        if (language.has(displayKey)) {
            return Component.translatable(displayKey);
        }
        String legacyKey = legacyKey(kind, id);
        if (language.has(legacyKey)) {
            return Component.translatable(legacyKey);
        }
        return Component.literal(id.toString());
    }
}
