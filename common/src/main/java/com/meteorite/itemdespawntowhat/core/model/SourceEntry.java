package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

/**
 * 源匹配项：一个物品 id 或一个物品标签。
 * JSON 中以字符串表示，标签以 # 前缀区分；解析与序列化复用 core/api/TaggedId。
 */
public record SourceEntry(ResourceLocation id, boolean tag) {

    // 字符串编解码：解析失败时返回可读错误而不是 null
    public static final Codec<SourceEntry> CODEC = TaggedId.CODEC.xmap(SourceEntry::fromTagged, SourceEntry::toTagged);

    // 由通用引用转换为源匹配项
    public static SourceEntry fromTagged(TaggedId tagged) {
        return new SourceEntry(tagged.id(), tagged.tag());
    }

    // 转换为通用引用
    public TaggedId toTagged() {
        return new TaggedId(id, tag);
    }

    // 解析字符串形式；非法时返回可读错误
    public static DataResult<SourceEntry> parse(String raw) {
        return TaggedId.parse(raw).map(SourceEntry::fromTagged);
    }

    // 序列化回字符串形式
    public String serialized() {
        return toTagged().serialized();
    }
}
