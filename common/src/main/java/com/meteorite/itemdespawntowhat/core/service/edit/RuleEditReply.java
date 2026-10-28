package com.meteorite.itemdespawntowhat.core.service.edit;

import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalog;
import java.util.List;

/** 编辑业务结论；不携带包类型、codec 或分片状态。 */
public sealed interface RuleEditReply {
    /** 结论所属会话。 */
    String sessionId();

    /** 开屏取锁结论，协议版本由网络适配器填入。 */
    record Open(String sessionId, String targetId, int resultVersion, RuleRuntimeLimits limits,
                RuleSaveStatus status, List<String> messageArgs) implements RuleEditReply { }
    /** 保存终态；幂等表保存此结论，线上诊断裁剪在出站时进行。 */
    record Save(String sessionId, String operationId, RuleSaveStatus status, List<String> messageArgs,
                int resultVersion, boolean writtenToDisk, boolean reloaded, List<RuleIssue> issues) implements RuleEditReply { }
    /** 一个独立查询的失败结论。 */
    record QueryResult(RuleEditRequest.Kind kind, String sessionId, String requestId,
                       RuleSaveStatus status) implements RuleEditReply { }
    /** 会话事件，不借用保存操作标识。 */
    record SessionEvent(String sessionId, RuleSaveStatus status) implements RuleEditReply { }
    /** 已在后台装配的完整列表内容。 */
    record Snapshot(String sessionId, String requestId, String json) implements RuleEditReply { }
    /** 分层来源内容。 */
    record SourceView(String sessionId, String requestId, RuleSourceView view) implements RuleEditReply { }
    /** 目录分页内容。 */
    record Catalog(String sessionId, String requestId, RuleCatalog catalog) implements RuleEditReply { }
    /** 参考寿命结果。 */
    record Lifespan(String sessionId, String requestId, int ticks) implements RuleEditReply { }
    /** 保存进度样本。 */
    record Progress(String sessionId, String operationId, long sequence, RuleSaveProgress progress) implements RuleEditReply { }
}
