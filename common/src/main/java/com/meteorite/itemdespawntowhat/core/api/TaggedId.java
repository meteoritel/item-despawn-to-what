package com.meteorite.itemdespawntowhat.core.api;

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

    // 解析 "minecraft:stone" / "#minecraft:logs" 形式
    public static DataResult<TaggedId> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataResult.error(() -> "引用不能为空");
        }
        String value = raw.trim();
        boolean isTag = value.startsWith("#");
        String idPart = isTag ? value.substring(1) : value;
        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) {
            return DataResult.error(() -> "非法的引用标识: " + raw);
        }
        return DataResult.success(new TaggedId(id, isTag));
    }

    // 序列化回字符串形式
    public String serialized() {
        return tag ? "#" + id : id.toString();
    }
}
