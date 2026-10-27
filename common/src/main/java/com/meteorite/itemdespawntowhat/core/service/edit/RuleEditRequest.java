package com.meteorite.itemdespawntowhat.core.service.edit;

import java.util.function.Supplier;

/** 已完成帧解码和文本重组的编辑业务请求；传输过程不进入保存任务。 */
public sealed interface RuleEditRequest {
    /** 请求所属独占会话。 */
    String sessionId();

    /** 独立查询的关联种类。 */
    enum Kind { SNAPSHOT, CATALOG, SOURCE_VIEW, LIFESPAN }

    /** 确认已取得的会话。 */
    record Confirm(String sessionId) implements RuleEditRequest { }
    /** 续租。 */
    record Heartbeat(String sessionId) implements RuleEditRequest { }
    /** 释放会话。 */
    record Close(String sessionId) implements RuleEditRequest { }
    /** 刷新编辑列表。 */
    record Snapshot(String sessionId, String requestId) implements RuleEditRequest { }
    /** 读取目录的一页。 */
    record Catalog(String sessionId, String requestId, String catalogType, String filter, int page, int pageSize) implements RuleEditRequest { }
    /** 读取一条规则的分层原始来源。 */
    record SourceView(String sessionId, String requestId, String ruleId) implements RuleEditRequest { }
    /** 读取参考寿命。 */
    record Lifespan(String sessionId, String requestId) implements RuleEditRequest { }
    /** 完整变更集；文本仅在已有文件队列准备阶段取得。 */
    record Save(String sessionId, String operationId, Supplier<String> text) implements RuleEditRequest { }
    /** 取消对应保存。 */
    record Cancel(String sessionId, String operationId) implements RuleEditRequest { }
}
