package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadRequest;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleLoader;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceRead;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.state.RuleEnableResolver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 规则加载装配层。
 * 加载链路的装配点（load 与 model 的桥接）：core/load 不 import core/model（泛型 T），
 * 模型经 RuleDecoder 注入，由本层把三层来源、覆盖合并与规则解码、校验串联起来。
 * 本层不做缓存、不做单例、不管理生命周期——那些属运行时。
 */
public final class RuleLoadingService {

    private RuleLoadingService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 加载（含解码），不额外做语义校验
     */
    public static RuleLoadResult<Rule> load(RuleLoadContext context) {
        RuleDecoder<Rule> decoder = RuleCodecs.decoder(
                context.registryAccess(), context.effectTypes(), context.conditionTypes());
        RuleLoadRequest<Rule> request = new RuleLoadRequest<>(
                context.resourceManager(), context.overlayRoot(), context.overlayNamespace(),
                decoder, context.layerResolver());
        return applyEnableState(context, RuleLoader.load(request));
    }

    /**
     * 加载 + 语义校验：语义非法的规则被剔除，问题保留在结果的问题收集器中
     */
    public static RuleLoadResult<Rule> loadAndValidate(RuleLoadContext context) {
        RuleLoadResult<Rule> loaded = load(context);
        return validateLoaded(context, loaded);
    }

    /**
     * 用已读取的数据包基底执行完整加载，供平台发布保存重载快照时复用同一来源集合。
     * @param context 当前加载上下文
     * @param base 数据包层原始条目
     * @return 覆盖合并、解码与校验后的结果
     */
    public static RuleLoadResult<Rule> loadAndValidate(RuleLoadContext context, List<RawRuleEntry> base) {
        IssueCollector issues = new IssueCollector();
        RuleSourceRead source = RuleSourceRead.read(base, context.overlayRoot(), context.overlayNamespace(), issues);
        var decoder = RuleCodecs.decoder(context.registryAccess(), context.effectTypes(), context.conditionTypes());
        List<LoadedRule<Rule>> decoded = new ArrayList<>();
        for (RawRuleEntry entry : source.entries()) {
            RuleLoader.decodeEntry(entry, decoder, issues).ifPresent(rule -> decoded.add(new LoadedRule<>(entry.id(), entry.origin(), rule)));
        }
        return validateLoaded(context, applyEnableState(context,
                new RuleLoadResult<>(List.copyOf(decoded), issues, source.index())));
    }

    /**
     * 运行时启用注入：生效启用 = 本服覆盖 ?? 默认启用。
     * 只重建运行时规则对象，不改解码用的 JSON 副本，因此快照、导出与复制看到的仍是规则文件内容。
     * 保存后的轻量重建复用同一口径，避免「刚保存的规则按 JSON 默认值生效」这类分叉。
     *
     * @param context 当前加载上下文（提供本服启用状态）
     * @param rules   解码后的规则；其 enabled 视为规则内容声明的默认值
     * @return 注入生效启用值后的规则列表
     */
    public static List<LoadedRule<Rule>> applyEnableState(RuleLoadContext context, List<LoadedRule<Rule>> rules) {
        RuleEnableResolver resolver = context.enableResolver();
        if (resolver == null || rules.isEmpty()) {
            return rules;
        }
        List<LoadedRule<Rule>> result = new ArrayList<>(rules.size());
        for (LoadedRule<Rule> entry : rules) {
            Rule rule = entry.value();
            Boolean override = resolver.overrideFor(rule.id());
            if (override == null || override == rule.enabled()) {
                result.add(entry);
            } else {
                result.add(new LoadedRule<>(entry.id(), entry.origin(), rule.withEnabled(override)));
            }
        }
        return List.copyOf(result);
    }

    // 加载结果级注入：保留原问题收集器
    private static RuleLoadResult<Rule> applyEnableState(RuleLoadContext context, RuleLoadResult<Rule> loaded) {
        return new RuleLoadResult<>(applyEnableState(context, loaded.rules()), loaded.issues(), loaded.sourceIndex());
    }

    private static RuleLoadResult<Rule> validateLoaded(RuleLoadContext context, RuleLoadResult<Rule> loaded) {
        var server = context.server();
        List<LoadedRule<Rule>> valid = new ArrayList<>(loaded.rules().size());
        for (LoadedRule<Rule> entry : loaded.rules()) {
            // 必须使用注册表感知重载：否则未注册引用、区间/结构类非法参数会在装配层被静默放行
            if (RuleValidation.validate(entry.value(), context.effectTypes(), context.conditionTypes(),
                    context.runtimeLimits(), loaded.issues(), entry.origin().display())
                    && (server == null || RuleReferenceValidator.validate(entry.value(), server, loaded.issues(), entry.origin().display()))) {
                valid.add(entry);
            }
        }
        return new RuleLoadResult<>(List.copyOf(valid), loaded.issues(), loaded.sourceIndex());
    }

    /**
     * 按来源层统计规则条数，供 /idtw config list 与 /idtw debug stats 输出
     */
    public static Map<RuleSourceLayer, Integer> countByLayer(RuleLoadResult<Rule> result) {
        Map<RuleSourceLayer, Integer> counts = new EnumMap<>(RuleSourceLayer.class);
        for (LoadedRule<Rule> entry : result.rules()) {
            counts.merge(entry.origin().layer(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 生成规则可读清单：每条一行，含来源层、优先级、触发时间与效果数量
     */
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

    /**
     * 汇总一行统计文本，便于日志与命令回显
     */
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
