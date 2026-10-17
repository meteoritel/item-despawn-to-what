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
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则快照装配器：把"来源分层索引 + 当前生效规则"组装成下发给客户端的快照。
 * 条目形状：RuleSnapshotEntry(id, origin, status, editable, category, effective, base, overlay, issues)
 * - base 视图来自数据包层原始 JSON，overlay 视图来自覆盖层原始 JSON，两者都保留原文（不做模型往返）；
 * - category 读取作者在规则原始 JSON 顶层的显式分类，覆盖层视图优先于数据包视图；未声明时为空；
 * - effective 优先使用模型编码后的最终结果（解码成功时权威），否则退回原始 JSON；
 * - origin 取 overlay | datapack | mixed，status 取 active | disabled | masked | invalid；
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
                                        @Nullable List<Issue> issues) {
        Map<ResourceLocation, JsonObject> encoded = new LinkedHashMap<>();
        if (merged != null) {
            for (LoadedRule<Rule> loaded : merged) {
                if (loaded != null && loaded.id() != null) {
                    JsonObject body = encodeRule(loaded.value(), effectTypes, conditionTypes);
                    if (body != null) { encoded.put(loaded.id(), body); }
                }
            }
        }
        return assembleEncoded(index, merged, version, contextRevision, encoded, issues);
    }

    /**
     * 使用服务端线程预编码的模型 JSON 装配来源三视图；不调用 Codec，可在独占后台任务中使用。
     * @param index 原始来源索引
     * @param merged 完整校验后的规则
     * @param version 覆盖层版本
     * @param contextRevision 上下文版本
     * @param encoded 规则 id 对应的模型编码结果
     * @param issues 加载与校验问题
     * @return 完整规则快照
     */
    public static RuleSnapshot assembleEncoded(RuleSourceIndex index, List<LoadedRule<Rule>> merged,
                                               int version, int contextRevision,
                                               Map<ResourceLocation, JsonObject> encoded,
                                               @Nullable List<Issue> issues) {
        Map<ResourceLocation, LoadedRule<Rule>> remaining = new LinkedHashMap<>();
        if (merged != null) {
            for (LoadedRule<Rule> loaded : merged) {
                if (loaded != null && loaded.id() != null) {
                    remaining.put(loaded.id(), loaded);
                }
            }
        }

        Map<ResourceLocation, RuleSnapshotEntry> entries = new LinkedHashMap<>();

        // 1) 分层索引条目：唯一能给出 base/overlay/masked/disabled 完整语义的来源
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
                String status = status(source, loaded != null);
                List<RuleIssue> entryIssues = entryIssues(source, issues);
                if (RuleSnapshotEntry.STATUS_INVALID.equals(status) && entryIssues.isEmpty()) {
                    entryIssues = List.of(new RuleIssue(RuleIssue.SEVERITY_ERROR, source.id().toString(),
                            RuleIssue.ORIGIN_RUNTIME, "", ISSUE_DECODE, List.of(), "规则解码失败: " + source.id()));
                }
                entries.put(source.id(), new RuleSnapshotEntry(source.id(), origin(source), status,
                        source.hasBody(), explicitCategory(overlay != null ? overlay : base),
                        effective, base, overlay, entryIssues));
            }
        }

        // 2) 兜底：索引未覆盖但已解码的规则（原始条目读取异常时仍要下发本体）
        for (Map.Entry<ResourceLocation, LoadedRule<Rule>> leftover : remaining.entrySet()) {
            JsonObject body = copy(encoded.get(leftover.getKey()));
            if (body == null) {
                continue;
            }
            boolean overlayLayer = leftover.getValue().origin().layer() == RuleSourceLayer.OVERLAY;
            // 能走到这里的都是已解码出本体的规则，一律可编辑
            entries.put(leftover.getKey(), new RuleSnapshotEntry(leftover.getKey(),
                    overlayLayer ? RuleSnapshotEntry.ORIGIN_OVERLAY : RuleSnapshotEntry.ORIGIN_DATAPACK,
                    RuleSnapshotEntry.STATUS_ACTIVE, true, null,
                    body, body.deepCopy(), overlayLayer ? body.deepCopy() : null, List.of()));
        }

        return new RuleSnapshot(version, contextRevision, List.copyOf(entries.values()),
                snapshotIssues(issues, index));
    }

    // 简化入口：仅有覆盖层与合并结果时装配（命令查询等只读路径使用）
    public static RuleSnapshot assemble(List<LoadedRule<Rule>> merged,
                                        Path overlayRoot,
                                        String overlayNamespace,
                                        int version,
                                        TypeRegistry<EffectType<?>> effectTypes,
                                        TypeRegistry<ConditionType<?>> conditionTypes,
                                        List<String> issues) {
        IssueCollector collector = new IssueCollector();
        List<RawRuleEntry> raw = OverlayRuleReader.read(overlayRoot, overlayNamespace, collector);
        RuleSnapshot snapshot = assemble(RuleSourceIndex.build(raw), merged, version, version,
                effectTypes, conditionTypes, collector.issues());
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

    // 条目状态：删贴（masked）与停用（disabled）优先，其次看解码是否成功
    private static String status(RuleSourceIndex.Entry source, boolean decoded) {
        if (source.deleted()) {
            return RuleSnapshotEntry.STATUS_MASKED;
        }
        if (source.disabled()) {
            return RuleSnapshotEntry.STATUS_DISABLED;
        }
        return decoded ? RuleSnapshotEntry.STATUS_ACTIVE : RuleSnapshotEntry.STATUS_INVALID;
    }

    // 来源标识：数据包与覆盖层同时存在即为 mixed
    private static String origin(RuleSourceIndex.Entry source) {
        if (source.overlay() != null) {
            return source.base() != null ? RuleSnapshotEntry.ORIGIN_MIXED : RuleSnapshotEntry.ORIGIN_OVERLAY;
        }
        if (source.base() != null) {
            return RuleSnapshotEntry.ORIGIN_DATAPACK;
        }
        return source.controlFromOverlay() ? RuleSnapshotEntry.ORIGIN_OVERLAY : RuleSnapshotEntry.ORIGIN_DATAPACK;
    }

    // 把加载/校验问题按来源与字段路径关联到具体条目
    private static List<RuleIssue> entryIssues(RuleSourceIndex.Entry source, @Nullable List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        Set<String> origins = new LinkedHashSet<>();
        Set<String> paths = new LinkedHashSet<>();
        // 用 Arrays.asList 而非 List.of：base/overlay/control 允许为 null，List.of 会抛 NPE
        for (RawRuleEntry raw : Arrays.asList(source.base(), source.overlay(), source.control())) {
            if (raw == null) {
                continue;
            }
            origins.add(raw.origin().display());
            if (raw.fieldPath() != null && !raw.fieldPath().isEmpty()) {
                paths.add(raw.fieldPath());
            }
        }
        List<RuleIssue> matched = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue != null && matches(issue, origins, paths)) {
                matched.add(toRuleIssue(issue, source.id(), origins));
            }
        }
        return List.copyOf(matched);
    }

    // 无法关联到任何条目的问题：作为快照级问题下发（origin=runtime）
    private static List<RuleIssue> snapshotIssues(@Nullable List<Issue> issues, @Nullable RuleSourceIndex index) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        List<RuleIssue> result = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue == null) {
                continue;
            }
            if (index != null && matchesAnyEntry(issue, index)) {
                continue;
            }
            result.add(new RuleIssue(severityOf(issue), null, RuleIssue.ORIGIN_RUNTIME,
                    issue.fieldPath() == null ? "" : issue.fieldPath(), ISSUE_LOAD, List.of(),
                    issue.message() == null ? "" : issue.message()));
        }
        return List.copyOf(result);
    }

    // 该问题是否已关联到某个分层条目
    private static boolean matchesAnyEntry(Issue issue, RuleSourceIndex index) {
        for (RuleSourceIndex.Entry source : index.entries()) {
            Set<String> origins = new LinkedHashSet<>();
            Set<String> paths = new LinkedHashSet<>();
            // 用 Arrays.asList 而非 List.of：base/overlay/control 允许为 null，List.of 会抛 NPE
        for (RawRuleEntry raw : Arrays.asList(source.base(), source.overlay(), source.control())) {
                if (raw == null) {
                    continue;
                }
                origins.add(raw.origin().display());
                if (raw.fieldPath() != null && !raw.fieldPath().isEmpty()) {
                    paths.add(raw.fieldPath());
                }
            }
            if (matches(issue, origins, paths)) {
                return true;
            }
        }
        return false;
    }

    // 问题定位匹配：来源标识命中，或字段路径命中（含路径前缀）
    private static boolean matches(Issue issue, Set<String> origins, Set<String> paths) {
        String issueOrigin = issue.origin();
        if (issueOrigin != null && !issueOrigin.isEmpty() && origins.contains(issueOrigin)) {
            return true;
        }
        String issuePath = issue.fieldPath();
        if (issuePath != null && !issuePath.isEmpty()) {
            for (String path : paths) {
                // 问题路径可能是条目路径本身、其前缀或其后缀，双向匹配避免漏挂问题
                if (path.equals(issuePath) || path.startsWith(issuePath) || issuePath.startsWith(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Issue → RuleIssue：severity 与 ruleId 必须带上
    private static RuleIssue toRuleIssue(Issue issue, ResourceLocation id, Set<String> origins) {
        boolean overlay = false;
        for (String origin : origins) {
            if (origin != null && origin.startsWith(RuleSourceLayer.OVERLAY.displayName())) {
                overlay = true;
                break;
            }
        }
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
