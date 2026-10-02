package com.meteorite.itemdespawntowhat.client.edit;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 类型编辑器描述：一个类型 id 对应一份「有序字段列表」。
 * <p>字段顺序即表单顺序；{@link #readOnly()} 为 true 时表示没有注册编辑器，
 * 界面只能展示 {@code rawJson} 字段的原始 JSON 摘要（不允许改写未识别字段）。
 * <p>类型标签由调用方传入，便于宿主统一走本地化 key。
 */
public record TypeEditorDescriptor(ResourceLocation id, Component label, List<EditorField> fields, boolean readOnly) {

    public TypeEditorDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        fields = List.copyOf(fields);
    }

    // 注册一个可编辑类型
    public static TypeEditorDescriptor of(ResourceLocation id, Component label, List<EditorField> fields) {
        return new TypeEditorDescriptor(id, label, fields, false);
    }

    // 注册一个可编辑类型（可变参数写法）
    public static TypeEditorDescriptor of(ResourceLocation id, Component label, EditorField... fields) {
        return new TypeEditorDescriptor(id, label, List.of(fields), false);
    }

    // 只读回退：未注册编辑器时展示原始 JSON 摘要，并原样保留未识别字段
    public static TypeEditorDescriptor readOnly(ResourceLocation id, Component label) {
        return new TypeEditorDescriptor(id, label, List.of(EditorField.rawJson()), true);
    }

    // 是否声明了同名字段
    public boolean hasField(String name) {
        return field(name) != null;
    }

    // 按字段名查找
    public @Nullable EditorField field(String name) {
        for (EditorField field : fields) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }

    // 字段数量
    public int fieldCount() {
        return fields.size();
    }
}
