package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 客户端展示名称：直接读取已加载的注册表与语言，不触发目录请求。 */
public final class RuleDisplayLabels {
    private static final String TAG_LABEL_KEY = TypeLabels.UI_PREFIX + "tag.label";
    private RuleDisplayLabels() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 展示完整标签注册名，统一添加一个标签前缀，供目录、已选卡片与摘要共用。
     *
     * @param reference 已解析的标签引用
     * @return 只含一个前置 {@code #} 的标签显示名
     */
    public static Component tagLabel(TaggedId reference) {
        return Component.translatable(TAG_LABEL_KEY, new TaggedId(reference.id(), true).serialized());
    }

    /** 按目录类型查询已选引用的名称，复用目录名称策略与缺键回退。 */
    public static Component catalogLabel(RuleCatalogType type, ResourceLocation id) {
        return CatalogTypePresentation.of(type).label(id);
    }

    /** 为规则摘要按已注册资源或类型描述解析名称。 */
    public static @Nullable Component label(ResourceLocation id) {
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id).getDescription();
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).getName();
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        }
        TypeEditorDescriptor descriptor = EditorRegistries.EFFECTS.find(id);
        if (descriptor == null) {
            descriptor = EditorRegistries.CONDITIONS.find(id);
        }
        return descriptor == null ? null : descriptor.label();
    }

    /**
     * 语言键缺失时的兜底显示名：把资源路径按 {@code _} 与 {@code /} 切词并首字母大写，
     * 例如 {@code twilightforest:twilight_forest} 显示为 {@code Twilight Forest}。
     *
     * <p>命名空间不参与显示，因此玩家看到的不会是 {@code minecraft:} 一类原始 ID；
     * 完整注册名仍由目录条目的悬停提示给出。</p>
     *
     * @param id 查不到本地化显示名的资源 ID
     * @return 由路径各词拼出的人话名称，路径为空时返回原始路径
     */
    public static Component idFallback(ResourceLocation id) {
        StringBuilder text = new StringBuilder();
        for (String word : id.getPath().split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.literal(text.isEmpty() ? id.getPath() : text.toString());
    }
}
