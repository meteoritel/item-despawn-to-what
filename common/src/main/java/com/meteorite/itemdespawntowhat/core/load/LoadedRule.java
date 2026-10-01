package com.meteorite.itemdespawntowhat.core.load;

import net.minecraft.resources.ResourceLocation;

/**
 * 解码成功的一条规则及其最终来源。
 * 泛型 T 由调用方注入的 RuleDecoder 决定（如 core/model 的 Rule），加载层不依赖具体模型类型。
 */
public record LoadedRule<T>(ResourceLocation id, RuleOrigin origin, T value) {
}
