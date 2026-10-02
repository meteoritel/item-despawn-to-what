package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.load.DatapackRuleReader;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.OverlayRuleReader;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadRequest;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleLoader;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 规则加载装配层。
 * 唯一同时依赖 core/load（三层来源与覆盖合并）与 core/model（规则解码与校验）的类，
 * 使加载层与模型层各自保持单向依赖。
 * 本层不做缓存、不做单例、不管理生命周期——那些属运行时（阶段③）。
 */
public final class RuleLoadingService {

    private RuleLoadingService() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 加载（含解码），不额外做语义校验
    public static RuleLoadResult<Rule> load(RuleLoadContext context) {
        RuleDecoder<Rule> decoder = RuleCodecs.decoder(
                context.registryAccess(), context.effectTypes(), context.conditionTypes());
        RuleLoadRequest<Rule> request = new RuleLoadRequest<>(
                context.resourceManager(), context.overlayRoot(), context.overlayNamespace(),
                decoder, context.layerResolver());
        return RuleLoader.load(request);
    }

    // 加载 + 语义校验：语义非法的规则被剔除，问题保留在结果的问题收集器中
    public static RuleLoadResult<Rule> loadAndValidate(RuleLoadContext context) {
        RuleLoadResult<Rule> loaded = load(context);
        var server = context.server();
        List<LoadedRule<Rule>> valid = new ArrayList<>(loaded.rules().size());
        for (LoadedRule<Rule> entry : loaded.rules()) {
            // 必须使用注册表感知重载：否则未注册引用、区间/结构类非法参数会在装配层被静默放行
            if (RuleValidation.validate(entry.value(), context.effectTypes(), context.conditionTypes(),
                    loaded.issues(), entry.origin().display())
                    && (server == null || RuleReferenceValidator.validate(entry.value(), server, loaded.issues(), entry.origin().display()))) {
                valid.add(entry);
            }
        }
        return new RuleLoadResult<>(List.copyOf(valid), loaded.issues());
    }

    // 重建来源分层索引：数据包层原始条目在前、覆盖层原始条目在后（层优先级升序）
    // 保留被覆盖/停用/屏蔽的原始基底，供快照装配（RuleSnapshotAssembler）与编辑判定使用
    public static RuleSourceIndex sourceIndex(RuleLoadContext context, IssueCollector issues) {
        List<RawRuleEntry> raw = new ArrayList<>();
        if (context.resourceManager() != null && context.layerResolver() != null) {
            raw.addAll(DatapackRuleReader.read(context.resourceManager(), context.layerResolver(), issues));
        }
        if (context.overlayRoot() != null) {
            raw.addAll(OverlayRuleReader.read(context.overlayRoot(), context.overlayNamespace(), issues));
        }
        return RuleSourceIndex.build(List.copyOf(raw));
    }

    // 按来源层统计规则条数，供 /idtw config list 与 /idtw debug stats 输出
    public static Map<RuleSourceLayer, Integer> countByLayer(RuleLoadResult<Rule> result) {
        Map<RuleSourceLayer, Integer> counts = new EnumMap<>(RuleSourceLayer.class);
        for (LoadedRule<Rule> entry : result.rules()) {
            counts.merge(entry.origin().layer(), 1, Integer::sum);
        }
        return counts;
    }

    // 生成规则可读清单：每条一行，含来源层、优先级、触发时间与效果数量
    public static List<String> describeRules(RuleLoadResult<Rule> result) {
        List<String> lines = new ArrayList<>(result.rules().size());
        for (LoadedRule<Rule> entry : result.rules()) {
            Rule rule = entry.value();
            lines.add(rule.id()
                    + " [" + entry.origin().layer().displayName() + "]"
                    + " priority=" + rule.priority()
                    + " trigger=" + rule.triggerAfterSeconds() + "s"
                    + " effects=" + rule.effects().size()
                    + " leaves=" + rule.complexity()
                    + (rule.enabled() ? "" : " (已停用)")
                    + " <- " + entry.origin().display());
        }
        return lines;
    }

    // 汇总一行统计文本，便于日志与命令回显
    public static String summarize(RuleLoadResult<Rule> result) {
        Map<RuleSourceLayer, Integer> counts = countByLayer(result);
        StringBuilder sb = new StringBuilder("规则 ").append(result.rules().size()).append(" 条");
        for (RuleSourceLayer layer : RuleSourceLayer.values()) {
            sb.append("，").append(layer.displayName()).append(' ').append(counts.getOrDefault(layer, 0));
        }
        sb.append("；问题 ").append(result.allIssues().size())
                .append("（错误 ").append(result.issues().errors().size())
                .append("，告警 ").append(result.issues().warnings().size()).append("）");
        return sb.toString();
    }
}
