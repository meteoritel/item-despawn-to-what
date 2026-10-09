package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 注册表引用（TaggedId）的存在性校验，供规则模型、服务与内置类型共用。
 * 语义：
 * - null 表示"未填写"，直接通过；
 * - 非标签引用：注册表 containsKey 未命中 → ERROR；
 * - 标签引用：仅在标签数据确已绑定时才校验，未命中记 WARN（数据包标签可能后加载，不能据此拒载）。
 */
public final class RefChecks {

    private RefChecks() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 校验单个引用；返回 false 表示存在 ERROR 级问题
    public static boolean check(@Nullable TaggedId reference, Registry<?> registry, String field,
                                IssueCollector issues, String path) {
        if (reference == null) {
            return true;
        }
        if (reference.tag()) {
            warnMissingTag(registry, reference.id(), field, issues, path);
            return true;
        }
        if (!registry.containsKey(reference.id())) {
            issues.error(field + " 引用了未注册的标识: " + reference.serialized(), null, path);
            return false;
        }
        return true;
    }

    // 校验引用列表；逐项路径形如 path[0]、path[1]
    public static boolean checkAll(@Nullable List<TaggedId> references, Registry<?> registry, String field,
                                   IssueCollector issues, String path) {
        if (references == null) {
            return true;
        }
        boolean valid = true;
        for (int index = 0; index < references.size(); index++) {
            valid &= check(references.get(index), registry, field, issues, ParamChecks.index(path, index));
        }
        return valid;
    }

    // 标签缺失告警：注册表尚未绑定标签数据时无法区分"未加载"与"真缺失"，直接跳过
    private static void warnMissingTag(Registry<?> registry, ResourceLocation id, String field,
                                       IssueCollector issues, String path) {
        if (!hasBoundTags(registry)) {
            return;
        }
        if (registry.getTagNames().noneMatch(tag -> tag.location().equals(id))) {
            issues.warn(field + " 引用了未定义的标签: #" + id, null, path);
        }
    }

    // 判定标签数据是否已绑定：存在任一非空标签即视为已加载。
    // 数据包未加载时，注册表里的标签都是 bootstrap 期由 getOrCreateTag 创建的空壳（size 为 0），
    // 此时不能据此判定标签缺失。
    private static boolean hasBoundTags(Registry<?> registry) {
        return registry.getTags().anyMatch(pair -> pair.getSecond().size() > 0);
    }
}
