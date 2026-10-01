package com.meteorite.itemdespawntowhat.core.load;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 规则来源的可读标识：所属层 + 数据包标识 + 文件路径。
 * 作为 {@code Issue.origin} 的统一取值，用于在报错与 {@code /idtw config list} 中定位规则出处。
 * 数据包层的 path 形如 {@code data/<ns>/idtw/rules/x.json}；覆盖层的 path 相对于 config/itemdespawntowhat 目录。
 */
public record RuleOrigin(RuleSourceLayer layer, @Nullable String packId, String path) {

    public RuleOrigin {
        Objects.requireNonNull(layer, "layer");
        path = path == null ? "" : path.replace('\\', '/');
    }

    // 数据包来源：packId 取自 Resource.sourcePackId()，location 为 data 目录下的资源位置
    public static RuleOrigin datapack(RuleSourceLayer layer, String packId, ResourceLocation location) {
        return new RuleOrigin(layer, packId, "data/" + location.getNamespace() + "/" + location.getPath());
    }

    // 覆盖层来源：relativePath 相对于 config/itemdespawntowhat 目录（如 rules/example.json）
    public static RuleOrigin overlay(String relativePath) {
        return new RuleOrigin(RuleSourceLayer.OVERLAY, null, relativePath);
    }

    // 面向命令与日志的单行可读标识
    public String display() {
        String prefix = packId == null || packId.isEmpty()
                ? layer.displayName()
                : layer.displayName() + "[" + packId + "]";
        return path.isEmpty() ? prefix : prefix + ":" + path;
    }
}
