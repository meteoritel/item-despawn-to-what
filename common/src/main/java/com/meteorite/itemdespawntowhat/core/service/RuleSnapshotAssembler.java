package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.OverlayRuleReader;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleOrigin;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCategoryClassifier;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.state.RuleEnableResolver;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSourceView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 规则快照装配器：把"来源分层索引 + 当前生效规则"组装成下发给客户端的快照。
 * 条目形状：RuleSnapshotEntry(id, origin, source, status, editable, defaultEnabled, enabled, enabledOverride,
 * category, effective, hasBase, hasOverlay, issues)
 * - 条目只携带生效视图与两个存在标记；基底与覆盖内容由 {@link #sourceView(RuleSourceIndex.Entry)} 按需生成；
 * - category 先读取作者在规则原始 JSON 顶层的显式分类（覆盖层视图优先于数据包视图），
 *   未声明时由分类器按生效效果的产出类别派生；两者不一致时以显式值为准；无法派生的条目为空；
 * - effective 优先使用模型编码后的最终结果（解码成功时权威），否则退回原始 JSON；
 * - 启用三值：defaultEnabled 取规则内容声明值（原始 JSON 的 enabled，缺失时按默认启用），
 *   enabledOverride 取本服状态层记录（null 表示继承默认），enabled 为二者的生效结果；
 * - origin 取 overlay | datapack | mixed，status 按生效启用值取 active | disabled | invalid；
 * - source 取 builtin | world | overlay | mixed，标识条目内容的物理层：内置/世界数据包为只读，覆盖与混合可写；
 * - 解码失败或校验致命错误时 status=invalid，并在 issues 中带上 severity/ruleId/origin/fieldPath。
 */
public final class RuleSnapshotAssembler {

    public static final String RULE_KEY = "rule";
    public static final String ORIGIN_KEY = "origin";
    public static final String EDITABLE_KEY = "editable";

    // 兜底问题码：某条规则解码失败但加载层没给出可定位问题时使用
    public static final String ISSUE_DECODE = "itemdespawntowhat.edit.issue.decode";
    // 快照级问题码：无法定位到具体条目
    public static final String ISSUE_LOAD = "itemdespawntowhat.edit.issue.load";

    private RuleSnapshotAssembler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 完整装配：index 为三层来源分层索引（见 RuleLoadingService#sourceIndex），merged 为加载合并结果
    // version 为覆盖层修订号，contextRevision 为客户端应跟踪的上下文中枢修订号（两者当前一致）
    public static RuleSnapshot assemble(RuleSourceIndex index,
                                        List<LoadedRule<Rule>> merged,
                                        int version,
                                        int contextRevision,
                                        TypeRegistry<EffectType<?>> effectTypes,
                                        TypeRegistry<ConditionType<?>> conditionTypes,
                                        @Nullable List<Issue> issues,
                                        @Nullable RuleEnableResolver enableResolver) {
        Map<ResourceLocation, JsonObject> encoded = new LinkedHashMap<>();
        if (merged != null) {
            for (LoadedRule<Rule> loaded : merged) {
                if (loaded != null && loaded.id() != null) {
                    JsonObject body = encodeRule(loaded.value(), effectTypes, conditionTypes);
                    if (body != null) { encoded.put(loaded.id(), body); }
                }
            }
        }
        return assembleEncoded(index, merged, effectTypes, version, contextRevision, encoded, issues, enableResolver);
    }

    /**
     * 使用服务端线程预编码的模型 JSON 装配来源三视图；不调用 Codec，可在独占后台任务中使用。
     * @param index 原始来源索引
     * @param merged 完整校验后的规则
     * @param effectTypes 效果类型注册表，用于派生未显式声明的分类；须为已冻结的只读视图
     * @param version 覆盖层版本
     * @param contextRevision 上下文版本
     * @param encoded 规则 id 对应的模型编码结果
     * @param issues 加载与校验问题
     * @param enableResolver 本服启用状态解析器；为 null 时按「全部继承默认」装配
     * @return 完整规则快照
     */
    public static RuleSnapshot assembleEncoded(RuleSourceIndex index, List<LoadedRule<Rule>> merged,
                                               TypeRegistry<EffectType<?>> effectTypes,
                                               int version, int contextRevision,
                                               Map<ResourceLocation, JsonObject> encoded,
                                               @Nullable List<Issue> issues,
                                               @Nullable RuleEnableResolver enableResolver) {
        Map<ResourceLocation, LoadedRule<Rule>> remaining = new LinkedHashMap<>();
        if (merged != null) {
            for (LoadedRule<Rule> loaded : merged) {
                if (loaded != null && loaded.id() != null) {
                    remaining.put(loaded.id(), loaded);
                }
            }
        }

        Map<ResourceLocation, RuleSnapshotEntry> entries = new LinkedHashMap<>();
        IssueAssociations associatedIssues = new IssueAssociations(index, issues);

        // 1) 分层索引条目：唯一能给出 base/overlay 完整来源语义的入口
        if (index != null) {
            for (RuleSourceIndex.Entry source : index.entries()) {
                LoadedRule<Rule> loaded = remaining.remove(source.id());
                JsonObject overlay = copy(source.overlayBody());
                JsonObject base = copy(source.baseBody());
                JsonObject effective = loaded == null ? null : copy(encoded.get(loaded.id()));
                if (effective == null) {
                    // 解码失败（或该 id 只被控制条目接管）时退回原始 JSON，保证客户端仍能看到内容
                    effective = overlay != null ? copy(overlay) : copy(base);
                }
                boolean defaultEnabled = defaultEnabled(loaded, overlay != null ? overlay : base);
                Boolean enabledOverride = override(enableResolver, source.id());
                boolean enabled = enabledOverride == null ? defaultEnabled : enabledOverride;
                String status = status(loaded != null, enabled);
                List<RuleIssue> entryIssues = associatedIssues.entries.getOrDefault(source.id(), List.of());
                if (RuleSnapshotEntry.STATUS_INVALID.equals(status) && entryIssues.isEmpty()) {
                    entryIssues = List.of(new RuleIssue(RuleIssue.SEVERITY_ERROR, source.id().toString(),
                            RuleIssue.ORIGIN_RUNTIME, "", ISSUE_DECODE, List.of(), "规则解码失败: " + source.id()));
                }
                String physicalSource = source(source);
                entries.put(source.id(), new RuleSnapshotEntry(source.id(), origin(source), physicalSource, status,
                        source.hasBody() && RuleSnapshotEntry.isWritableSource(physicalSource),
                        defaultEnabled, enabled, enabledOverride,
                        category(loaded, overlay != null ? overlay : base, effectTypes),
                        effective, base != null, overlay != null, entryIssues));
            }
        }

        // 2) 兜底：索引未覆盖但已解码的规则（原始条目读取异常时仍要下发本体）
        for (Map.Entry<ResourceLocation, LoadedRule<Rule>> leftover : remaining.entrySet()) {
            JsonObject body = copy(encoded.get(leftover.getKey()));
            if (body == null) {
                continue;
            }
            RuleSourceLayer layer = leftover.getValue().origin().layer();
            boolean overlayLayer = layer == RuleSourceLayer.OVERLAY;
            String category = RuleCategoryClassifier.classify(leftover.getValue().value(), effectTypes);
            boolean defaultEnabled = defaultEnabled(leftover.getValue(), body);
            Boolean enabledOverride = override(enableResolver, leftover.getKey());
            boolean enabled = enabledOverride == null ? defaultEnabled : enabledOverride;
            // 原始条目读取异常时没有分层视图：把模型编码结果当作基底内容下发，存在标记与来源按层补齐；
            // source 保留 builtin/world 与 overlay 的区分，供客户端判定只读。
            entries.put(leftover.getKey(), new RuleSnapshotEntry(leftover.getKey(),
                    overlayLayer ? RuleSnapshotEntry.ORIGIN_OVERLAY : RuleSnapshotEntry.ORIGIN_DATAPACK,
                    layer.id(), status(true, enabled),
                    RuleSnapshotEntry.isWritableSource(layer.id()), defaultEnabled, enabled, enabledOverride,
                    category, body, true, overlayLayer, List.of()));
        }

        return new RuleSnapshot(version, contextRevision, List.copyOf(entries.values()),
                List.copyOf(associatedIssues.snapshot));
    }

    // 简化入口：仅有覆盖层与合并结果时装配（命令查询等只读路径使用）
    public static RuleSnapshot assemble(List<LoadedRule<Rule>> merged,
                                        Path overlayRoot,
                                        String overlayNamespace,
                                        int version,
                                        TypeRegistry<EffectType<?>> effectTypes,
                                        TypeRegistry<ConditionType<?>> conditionTypes,
                                        List<String> issues,
                                        @Nullable RuleEnableResolver enableResolver) {
        IssueCollector collector = new IssueCollector();
        List<RawRuleEntry> raw = OverlayRuleReader.read(overlayRoot, overlayNamespace, collector);
        RuleSnapshot snapshot = assemble(RuleSourceIndex.build(raw), merged, version, version,
                effectTypes, conditionTypes, collector.issues(), enableResolver);
        if (issues == null || issues.isEmpty()) {
            return snapshot;
        }
        // 纯文本问题（以字符串形式传入）追加为快照级告警
        List<RuleIssue> merged2 = new ArrayList<>(snapshot.issues());
        for (String text : issues) {
            merged2.add(new RuleIssue(RuleIssue.SEVERITY_WARNING, null, RuleIssue.ORIGIN_RUNTIME,
                    "", ISSUE_LOAD, List.of(), text == null ? "" : text));
        }
        return new RuleSnapshot(snapshot.version(), snapshot.contextRevision(), snapshot.entries(), List.copyOf(merged2));
    }

    /**
     * 把来源分层条目转成按需下发的来源视图：基底与覆盖都保留文件原文（不做模型往返）。
     * @param source 来源分层条目
     * @return 来源视图；两层副本都不存在时仍返回只带 id 的空视图
     */
    public static RuleSourceView sourceView(RuleSourceIndex.Entry source) {
        return new RuleSourceView(source.id(), sourceViewOf(source.base()), sourceViewOf(source.overlay()));
    }

    // 单层来源视图：层标识、数据包 id 与文件路径取原始条目的来源坐标，内容深拷贝
    private static @Nullable RuleSourceView.View sourceViewOf(@Nullable RawRuleEntry raw) {
        if (raw == null) {
            return null;
        }
        RuleOrigin origin = raw.origin();
        return new RuleSourceView.View(origin.layer().id(), origin.packId(), origin.path(), raw.body().deepCopy());
    }

    // 分类：显式声明优先，缺失时按生效效果派生；无已解码规则（如已删除条目）时为空
    private static @Nullable String category(@Nullable LoadedRule<Rule> loaded, @Nullable JsonObject body,
                                             TypeRegistry<EffectType<?>> effectTypes) {
        String explicit = explicitCategory(body);
        if (explicit != null) {
            return explicit;
        }
        return loaded == null ? null : RuleCategoryClassifier.classify(loaded.value(), effectTypes);
    }

    // 显式分类：取胜出原始视图的顶层 category；缺失、非字符串或空白一律视为未声明
    private static @Nullable String explicitCategory(@Nullable JsonObject body) {
        if (body == null) {
            return null;
        }
        JsonElement raw = body.get(RuleFields.CATEGORY);
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            return null;
        }
        String category = raw.getAsString();
        return category.isBlank() ? null : category;
    }

    // 条目状态：内容非法优先（无法执行也无从启停），否则按本服生效启用值取 active / disabled
    private static String status(boolean decoded, boolean enabled) {
        if (!decoded) {
            return RuleSnapshotEntry.STATUS_INVALID;
        }
        return enabled ? RuleSnapshotEntry.STATUS_ACTIVE : RuleSnapshotEntry.STATUS_DISABLED;
    }

    // 默认启用值：原始 JSON 的 enabled 声明优先，缺失或非布尔时退回解码值（解码器按默认启用补齐）
    private static boolean defaultEnabled(@Nullable LoadedRule<Rule> loaded, @Nullable JsonObject raw) {
        JsonElement flag = raw == null ? null : raw.get(RuleFields.ENABLED);
        if (flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean()) {
            return flag.getAsBoolean();
        }
        return loaded != null ? loaded.value().enabled() : RuleCodecs.DEFAULT_ENABLED;
    }

    // 本服覆盖：null 表示没有记录（继承默认），与「显式设成和默认相同」区分
    private static @Nullable Boolean override(@Nullable RuleEnableResolver resolver, ResourceLocation id) {
        return resolver == null ? null : resolver.overrideFor(id);
    }

    /**
     * 物理来源标识：区分内置数据包、世界数据包与用户覆盖内容。
     * 覆盖副本与数据包基底共存时为 mixed，只有其一则取该层。
     *
     * @param source 来源分层条目
     * @return builtin / world / overlay / mixed
     */
    private static String source(RuleSourceIndex.Entry source) {
        if (source.overlay() != null) {
            return source.base() != null ? RuleSnapshotEntry.SOURCE_MIXED : RuleSnapshotEntry.SOURCE_OVERLAY;
        }
        if (source.base() != null) {
            return source.base().origin().layer().id();
        }
        // 空条目不存在可写内容：按只读数据包回退，避免误判为可编辑
        return RuleSnapshotEntry.SOURCE_DATAPACK;
    }

    // 来源标识：数据包与覆盖层同时存在即为 mixed
    private static String origin(RuleSourceIndex.Entry source) {
        if (source.overlay() != null) {
            return source.base() != null ? RuleSnapshotEntry.ORIGIN_MIXED : RuleSnapshotEntry.ORIGIN_OVERLAY;
        }
        return RuleSnapshotEntry.ORIGIN_DATAPACK;
    }

    // 每份快照独占的关联结果：来源精确索引与路径有序索引只构建一次，问题按输入顺序分发。
    private static final class IssueAssociations {
        private final Map<ResourceLocation, List<RuleIssue>> entries = new HashMap<>();
        private final List<RuleIssue> snapshot = new ArrayList<>();

        private IssueAssociations(@Nullable RuleSourceIndex index, @Nullable List<Issue> issues) {
            if (issues == null || issues.isEmpty()) {
                return;
            }
            List<RuleSourceIndex.Entry> sources = index == null ? List.of() : index.entries();
            Map<String, List<Integer>> origins = new HashMap<>();
            NavigableMap<String, List<Integer>> paths = new TreeMap<>();
            boolean[] overlays = new boolean[sources.size()];
            for (int number = 0; number < sources.size(); number++) {
                RuleSourceIndex.Entry source = sources.get(number);
                // base/overlay 允许为空，同一条目多次命中由分发时的 BitSet 合并。
                for (RawRuleEntry raw : Arrays.asList(source.base(), source.overlay())) {
                    if (raw == null) {
                        continue;
                    }
                    String origin = raw.origin().display();
                    origins.computeIfAbsent(origin, ignored -> new ArrayList<>()).add(number);
                    overlays[number] |= origin.startsWith(RuleSourceLayer.OVERLAY.displayName());
                    if (!raw.fieldPath().isEmpty()) {
                        paths.computeIfAbsent(raw.fieldPath(), ignored -> new ArrayList<>()).add(number);
                    }
                }
            }
            BitSet matched = new BitSet(sources.size());
            for (Issue issue : issues) {
                if (issue == null) {
                    continue;
                }
                matched.clear();
                if (issue.origin() != null && !issue.origin().isEmpty()) {
                    mark(matched, origins.get(issue.origin()));
                }
                matchPaths(matched, paths, issue.fieldPath());
                if (matched.isEmpty()) {
                    snapshot.add(new RuleIssue(severityOf(issue), null, RuleIssue.ORIGIN_RUNTIME,
                            issue.fieldPath() == null ? "" : issue.fieldPath(), ISSUE_LOAD, List.of(),
                            issue.message() == null ? "" : issue.message()));
                } else {
                    for (int number = matched.nextSetBit(0); number >= 0; number = matched.nextSetBit(number + 1)) {
                        ResourceLocation id = sources.get(number).id();
                        entries.computeIfAbsent(id, ignored -> new ArrayList<>())
                                .add(toRuleIssue(issue, id, overlays[number]));
                    }
                }
            }
        }

        // 保留字符串双向前缀语义：逐前缀查祖先，按排序后的连续区间查后代。
        private static void matchPaths(BitSet matched, NavigableMap<String, List<Integer>> paths,
                                       @Nullable String issuePath) {
            if (issuePath == null || issuePath.isEmpty() || paths.isEmpty()) {
                return;
            }
            for (int length = 1; length < issuePath.length(); length++) {
                mark(matched, paths.get(issuePath.substring(0, length)));
            }
            for (Map.Entry<String, List<Integer>> path : paths.tailMap(issuePath, true).entrySet()) {
                if (!path.getKey().startsWith(issuePath)) {
                    break;
                }
                mark(matched, path.getValue());
            }
        }

        private static void mark(BitSet matched, @Nullable List<Integer> numbers) {
            if (numbers != null) {
                for (int number : numbers) {
                    matched.set(number);
                }
            }
        }
    }

    // Issue → RuleIssue：severity 与 ruleId 必须带上
    private static RuleIssue toRuleIssue(Issue issue, ResourceLocation id, boolean overlay) {
        return new RuleIssue(severityOf(issue), id.toString(),
                overlay ? RuleIssue.ORIGIN_OVERLAY : RuleIssue.ORIGIN_DATAPACK,
                issue.fieldPath() == null ? "" : issue.fieldPath(),
                ISSUE_LOAD, List.of(), issue.message() == null ? "" : issue.message());
    }

    // IssueSeverity → RuleIssue 的 severity 字符串
    private static String severityOf(Issue issue) {
        return issue.severity() == IssueSeverity.ERROR ? RuleIssue.SEVERITY_ERROR : RuleIssue.SEVERITY_WARNING;
    }

    /**
     * 编码一条模型用于快照；第三方 Codec 必须在服务端线程调用。
     * @param rule 已解码规则
     * @param effectTypes 效果类型注册表
     * @param conditionTypes 条件类型注册表
     * @return 模型 JSON；编码失败时为 null
     */
    public static @Nullable JsonObject encodeRule(Rule rule, TypeRegistry<EffectType<?>> effectTypes,
                                                  TypeRegistry<ConditionType<?>> conditionTypes) {
        return encode(RuleCodecs.codec(effectTypes, conditionTypes), rule);
    }

    // 模型编码：失败返回 null（由调用方决定退回原始 JSON 或标记 invalid）
    static @Nullable JsonObject encode(Codec<Rule> codec, Rule rule) {
        if (rule == null) {
            return null;
        }
        DataResult<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, rule);
        JsonElement json = encoded.result().orElse(null);
        return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
    }

    // 深拷贝原始 JSON；空值透传
    private static @Nullable JsonObject copy(@Nullable JsonObject json) {
        return json == null ? null : json.deepCopy();
    }
}
