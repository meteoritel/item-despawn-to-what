package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.OverlayRuleReader;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoader;
import com.meteorite.itemdespawntowhat.core.load.RuleMerger;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.state.RuleEnableState;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启用状态应用服务：把一批启停动作写入本服状态层，并按保存链路的轻量路径重建索引、回扫全部维度。
 * GUI 应用（纯启停批次）与 /idtw rule ... enable|disable|default 共用同一入口，避免命令侧改走
 * 完整数据包重载这种两套路径。索引重建只用保存链路缓存的数据包基底，不重读数据包；
 * 内容动作仍由保存任务先把覆盖层原子提交，再调用 {@link #commitState} 写状态，
 * 因此不会出现「状态已改、内容没落盘」或反过来的半成品。
 */
public final class RuleEnableApplyService {

    private RuleEnableApplyService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 一次启停应用的结果。
     *
     * @param changed 实际发生变化的记录数；0 表示提交前后状态一致
     * @param publication 已发布新索引后等待回扫收尾的观测任务；未进入运行时为 null
     */
    public record Outcome(int changed, @Nullable ServerTask publication) { }

    /**
     * 写入一批启用状态动作（同一 id 多次写入按顺序后者胜）。
     * 只处理 enable / disable / reset，内容动作忽略；调用方必须保证已经在提交阶段。
     *
     * @param server 当前服务端；状态随主世界存档保存
     * @param edits  启用状态动作
     * @return 实际发生变化的记录数
     */
    public static int commitState(MinecraftServer server, List<RuleEdit> edits) {
        if (edits.isEmpty()) {
            return 0;
        }
        RuleEnableState state = RuleEnableState.get(server.overworld());
        int changed = 0;
        for (RuleEdit edit : edits) {
            boolean applied = switch (edit.action()) {
                case ENABLE -> state.put(edit.id(), true);
                case DISABLE -> state.put(edit.id(), false);
                case RESET -> state.clear(edit.id());
                default -> false;
            };
            if (applied) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * 提交启用状态并按轻量路径应用：重建索引后发布，回扫由运行时自行排队。
     * 命令与纯启停批次没有可分层推进的 tick 预算，因此索引重建一次完成；规则量受运行期上限约束。
     *
     * @param server  当前服务端
     * @param context 编辑运行上下文；需要保存链路已捕捉的数据包基底
     * @param edits   启用状态动作
     * @return 应用结果；状态无变化或未进入运行时时为 null 发布任务
     */
    public static Outcome apply(MinecraftServer server, RuleEditServerContext context, List<RuleEdit> edits) {
        int changed = commitState(server, edits);
        if (changed == 0) {
            return new Outcome(0, null);
        }
        RuleIndex index = buildSavedIndex(server, context);
        if (index == null) {
            return new Outcome(changed, null);
        }
        return new Outcome(changed, context.publishIndex(server, index));
    }

    // 用保存链路缓存的数据包基底 + 当前覆盖层重建运行期索引；启用状态按「本服覆盖 ?? 默认启用」注入
    private static @Nullable RuleIndex buildSavedIndex(MinecraftServer server, RuleEditServerContext context) {
        RuleSaveReloadContext saved = context.saveReloadContext();
        BuiltinTypeRegistries types = context.typeRegistries();
        Path overlayRoot = context.overlayRoot();
        if (saved == null || types == null || overlayRoot == null) {
            return null;
        }
        IssueCollector issues = new IssueCollector();
        saved.baseIssues().forEach(issues::add);
        List<RawRuleEntry> raw = new ArrayList<>(saved.baseRules());
        raw.addAll(OverlayRuleReader.read(overlayRoot, context.overlayNamespace(), issues));
        var load = saved.loadContext();
        var decoder = RuleCodecs.decoder(load.registryAccess(), load.effectTypes(), load.conditionTypes());
        List<LoadedRule<Rule>> decoded = new ArrayList<>();
        for (RawRuleEntry entry : RuleMerger.merge(raw, issues)) {
            RuleLoader.decodeEntry(entry, decoder, issues)
                    .filter(rule -> RuleValidation.validate(rule, load.effectTypes(), load.conditionTypes(),
                                    load.runtimeLimits(), issues, entry.origin().display())
                            && RuleReferenceValidator.validate(rule, server, issues, entry.origin().display()))
                    .ifPresent(rule -> decoded.add(new LoadedRule<>(entry.id(), entry.origin(), rule)));
        }
        return RuleIndex.build(RuleLoadingService.applyEnableState(load, decoded), new IssueCollector());
    }
}