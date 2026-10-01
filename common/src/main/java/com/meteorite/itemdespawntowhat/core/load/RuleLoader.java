package com.meteorite.itemdespawntowhat.core.load;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 三层作用域规则加载入口。
 * 流程：读取（内置 + 世界数据包 → 覆盖层，按优先级升序）→ 按 id 覆盖合并 → 逐条解码为模型对象。
 * 错误策略（规划书 3.3 / Q19）：文件解析失败整文件拒载，单条非法仅该条拒载，其余照常；
 * 所有问题携带来源标识与字段路径进入 IssueCollector。
 * 本类不依赖 core/model，模型由调用方以 RuleDecoder 注入。
 */
public final class RuleLoader {

    private static final Logger LOGGER = LogManager.getLogger(RuleLoader.class);

    private RuleLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 执行一次完整加载（读取 → 合并 → 解码）
    public static <T> RuleLoadResult<T> load(RuleLoadRequest<T> request) {
        IssueCollector issues = new IssueCollector();
        List<RawRuleEntry> raw = new ArrayList<>();
        if (request.resourceManager() != null) {
            raw.addAll(DatapackRuleReader.read(request.resourceManager(), request.layerResolver(), issues));
        }
        if (request.overlayRoot() != null) {
            raw.addAll(OverlayRuleReader.read(request.overlayRoot(), request.overlayDefaultNamespace(), issues));
        }
        List<RawRuleEntry> merged = RuleMerger.merge(raw, issues);

        List<LoadedRule<T>> rules = new ArrayList<>(merged.size());
        for (RawRuleEntry entry : merged) {
            decodeEntry(entry, request.decoder(), issues).ifPresent(value ->
                    rules.add(new LoadedRule<>(entry.id(), entry.origin(), value)));
        }
        return new RuleLoadResult<>(List.copyOf(rules), issues);
    }

    // 只做读取与合并，不做模型解码（供 /idtw config list、convert 等只关心原始条目的场景使用）
    public static List<RawRuleEntry> loadRaw(RuleLoadRequest<?> request, IssueCollector issues) {
        List<RawRuleEntry> raw = new ArrayList<>();
        if (request.resourceManager() != null) {
            raw.addAll(DatapackRuleReader.read(request.resourceManager(), request.layerResolver(), issues));
        }
        if (request.overlayRoot() != null) {
            raw.addAll(OverlayRuleReader.read(request.overlayRoot(), request.overlayDefaultNamespace(), issues));
        }
        return RuleMerger.merge(raw, issues);
    }

    // 解码单条规则；失败即该条拒载，并把解码期问题补全为带来源与字段路径的记录
    private static <T> Optional<T> decodeEntry(RawRuleEntry entry, RuleDecoder<T> decoder, IssueCollector issues) {
        IssueCollector local = new IssueCollector();
        Optional<T> decoded;
        try {
            decoded = decoder.decode(decodeBody(entry), local)
                    .resultOrPartial(message -> local.error(message, null, null));
        } catch (RuntimeException e) {
            // 完整堆栈进日志，Issue 里保留类名与首个栈帧，避免异常被折叠成不可定位的字符串
            LOGGER.error("解码规则 {} 时发生异常，来源: {}", entry.id(), entry.origin().display(), e);
            local.error("解码规则时发生异常: " + describe(e), null, null);
            decoded = Optional.empty();
        }
        transferIssues(local, issues, entry);
        return decoded;
    }

    // 异常摘要：异常类名 + 首个栈帧
    private static String describe(RuntimeException e) {
        StackTraceElement[] frames = e.getStackTrace();
        String frame = frames.length == 0 ? "<无栈帧>" : frames[0].toString();
        return e.getClass().getName() + " @ " + frame;
    }

    // 把解码器产生的问题补全来源与字段路径后并入总收集器
    private static void transferIssues(IssueCollector source, IssueCollector target, RawRuleEntry entry) {
        for (Issue issue : source.issues()) {
            String origin = issue.origin() == null || issue.origin().isEmpty()
                    ? entry.origin().display()
                    : issue.origin();
            target.add(new Issue(issue.severity(), issue.message(), origin,
                    RulePaths.joinFieldPath(entry.fieldPath(), issue.fieldPath())));
        }
    }

    // 构造交给 RuleDecoder 的对象：保留原始 body，另行复制一份并补齐 id、剔除控制字段
    private static JsonObject decodeBody(RawRuleEntry entry) {
        JsonObject body = entry.body().deepCopy();
        body.remove(RuleFields.DISABLED);
        body.remove(RuleFields.DELETE);
        ResourceLocation id = entry.id();
        if (id != null && !body.has(RuleFields.ID)) {
            body.addProperty(RuleFields.ID, id.toString());
        }
        // 被覆盖层停用的基底规则：交给模型前显式置 enabled=false
        if (entry.disabled()) {
            body.addProperty(RuleFields.ENABLED, false);
        }
        return body;
    }
}
