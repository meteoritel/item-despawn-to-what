package com.meteorite.itemdespawntowhat.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

/**
 * 源匹配项：一个物品 id 或一个物品标签。
 * JSON 中以字符串表示，标签以 # 前缀区分（如 "#minecraft:saplings"）。
 */
public record SourceEntry(ResourceLocation id, boolean tag) {

    // 字符串编解码：解析失败时返回可读错误而不是 null
    public static final Codec<SourceEntry> CODEC = Codec.STRING.comapFlatMap(SourceEntry::parse, SourceEntry::serialized);

    // 解析字符串形式；非法时返回可读错误
    public static DataResult<SourceEntry> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataResult.error(() -> "源匹配项不能为空");
        }
        String value = raw.trim();
        boolean isTag = value.startsWith("#");
        String idPart = isTag ? value.substring(1) : value;
        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) {
            return DataResult.error(() -> "非法的源匹配标识: " + raw);
        }
        return DataResult.success(new SourceEntry(id, isTag));
    }

    // 序列化回字符串形式
    public String serialized() {
        return tag ? "#" + id : id.toString();
    }
}
