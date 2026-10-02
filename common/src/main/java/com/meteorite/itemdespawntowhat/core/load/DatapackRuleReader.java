package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 数据包层规则读取：内置数据包与世界数据包共用 MinecraftServer 的 ResourceManager，
 * 读取 data/&lt;ns&gt;/idtw/rules/**&#47;*.json。
 * 逐个资源位置调用 getResourceStack 取回全部包的同名副本，使不同数据包的同名文件都参与合并，
 * 避免只取最高优先级副本而丢掉其它包的内容；同一包内按包优先级从低到高展开，由合并阶段实现"后者胜"。
 */
public final class DatapackRuleReader {

    private DatapackRuleReader() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 读取全部数据包层的规则原始条目，返回顺序满足：层优先级升序 → 包优先级升序 → 文件路径 → 文件内顺序
    // resourceManager 为 MinecraftServer 的资源管理器；layerResolver 为包 id → 来源层 的判定实现（由引导层注入）
    public static List<RawRuleEntry> read(ResourceManager resourceManager,
                                          PackLayerResolver layerResolver,
                                          IssueCollector issues) {
        Map<ResourceLocation, Resource> locations;
        try {
            locations = resourceManager.listResources(RulePaths.DATAPACK_RULES_DIRECTORY,
                    location -> location.getPath().endsWith(RulePaths.RULE_FILE_EXTENSION));
        } catch (RuntimeException e) {
            throw new IllegalStateException("列举数据包规则文件失败，保留现有规则", e);
        }

        Map<PackResources, Integer> packRanks = packRanks(resourceManager, issues);
        Set<String> unrankedPacks = new HashSet<>();
        List<ScoredEntry> scored = new ArrayList<>();
        for (ResourceLocation location : locations.keySet()) {
            List<Resource> stack;
            try {
                stack = resourceManager.getResourceStack(location);
            } catch (RuntimeException e) {
                issues.error("读取数据包规则资源栈失败: " + e, null,
                        "data/" + location.getNamespace() + "/" + location.getPath());
                continue;
            }
            @Nullable ResourceLocation derivedId = deriveId(location);
            for (Resource resource : stack) {
                String packId = resource.sourcePackId();
                RuleSourceLayer layer = layerResolver.resolve(packId);
                RuleOrigin origin = RuleOrigin.datapack(layer, packId, location);
                String text = readText(resource, origin, issues);
                if (text == null) {
                    continue;
                }
                List<RawRuleEntry> entries = RuleFileParser.parse(text, origin, derivedId, issues);
                Integer rank = packRanks.get(resource.source());
                if (rank == null) {
                    // 身份不一致或 listPacks 失败：按最低包优先级参与排序，并如实告警（每个包只报一次）
                    rank = -1;
                    if (unrankedPacks.add(packId)) {
                        issues.warn("数据包未出现在 listPacks() 中，其规则按最低包优先级参与排序: " + packId,
                                origin.display(), location.getPath());
                    }
                }
                int packRank = rank;
                for (int index = 0; index < entries.size(); index++) {
                    scored.add(new ScoredEntry(layer, packRank, location.getPath(), index, entries.get(index)));
                }
            }
        }

        Comparator<ScoredEntry> byLayer = Comparator.comparingInt(entry -> entry.layer().priority());
        Comparator<ScoredEntry> byPackRankDesc = Comparator.comparingInt(ScoredEntry::packRank);
        Comparator<ScoredEntry> byFilePath = Comparator.comparing(ScoredEntry::filePath);
        Comparator<ScoredEntry> byIndex = Comparator.comparingInt(ScoredEntry::index);
        scored.sort(byLayer.thenComparing(byPackRankDesc).thenComparing(byFilePath).thenComparing(byIndex));

        List<RawRuleEntry> result = new ArrayList<>(scored.size());
        for (ScoredEntry entry : scored) {
            result.add(entry.entry());
        }
        return result;
    }

    // 包优先级表：listPacks 的顺序为包优先级从低到高，序号越大越优先
    // 取表失败时排序会退化为文件路径，因此必须产出 WARN 而不是静默降级；未登记的资源按最低包优先级处理并逐个包告警一次
    private static Map<PackResources, Integer> packRanks(ResourceManager resourceManager, IssueCollector issues) {
        Map<PackResources, Integer> ranks = new IdentityHashMap<>();
        try (Stream<PackResources> packs = resourceManager.listPacks()) {
            List<PackResources> ordered = packs.toList();
            for (int index = 0; index < ordered.size(); index++) {
                ranks.put(ordered.get(index), index);
            }
        } catch (RuntimeException e) {
            issues.warn("无法获取数据包优先级表，层内排序退化为文件路径: " + e,
                    null, RulePaths.DATAPACK_RULES_DIRECTORY);
            ranks.clear();
        }
        return ranks;
    }

    // 读取资源文本；失败即该文件拒载并报 ERROR
    private static @Nullable String readText(Resource resource, RuleOrigin origin, IssueCollector issues) {
        try (InputStream stream = resource.open()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            issues.error("读取规则文件失败: " + e.getMessage(), origin.display(), "");
            return null;
        }
    }

    // 由资源位置推导默认 id：<ns>:<去掉 idtw/rules/ 前缀与 .json 扩展名的相对路径>
    private static @Nullable ResourceLocation deriveId(ResourceLocation location) {
        String directory = RulePaths.DATAPACK_RULES_DIRECTORY + "/";
        String path = location.getPath();
        if (!path.startsWith(directory)) {
            return null;
        }
        String relative = RulePaths.stripExtension(path.substring(directory.length()));
        if (relative.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(location.getNamespace() + ":" + relative);
    }

    // 排序用的中间载体：携带层、包优先级、文件路径与文件内序号
    private record ScoredEntry(RuleSourceLayer layer, int packRank, String filePath, int index, RawRuleEntry entry) {
    }
}
