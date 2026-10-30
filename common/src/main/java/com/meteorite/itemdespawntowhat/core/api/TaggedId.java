package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用引用：一个注册表 id，或一个以 # 前缀表示的标签。
 * 用于效果/条件参数中的物品、方块、生物群系等引用，统一 JSON 形状与错误信息。
 */
public record TaggedId(ResourceLocation id, boolean tag) {

    // 字符串编解码：解析失败时返回可读错误而不是 null
    public static final Codec<TaggedId> CODEC = Codec.STRING.comapFlatMap(TaggedId::parse, TaggedId::serialized);

    /** Codec 解析保留外围空白归一化与原有诊断。 */
    public static DataResult<TaggedId> parse(String raw) {
        if (raw == null || raw.isBlank()) return DataResult.error(() -> "引用不能为空");
        TaggedId value = tryParse(raw.trim());
        return value == null ? DataResult.error(() -> "非法的引用标识: " + raw) : DataResult.success(value);
    }

    /** 轻量解析完整引用；不接受外围空白，供目录与字段校验直接消费。 */
    public static @Nullable TaggedId tryParse(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        boolean tag = raw.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
        return id == null ? null : new TaggedId(id, tag);
    }

    /** 输出规则与目录使用的字符串形状。 */
    public String serialized() {
        return tag ? "#" + id : id.toString();
    }
}
