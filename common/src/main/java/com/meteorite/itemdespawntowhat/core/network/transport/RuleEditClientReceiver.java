package com.meteorite.itemdespawntowhat.core.network.transport;

/** 单次客户端连接的下行接收面；common 注册器只引用该中立接口。 */
public interface RuleEditClientReceiver {
    /** 接收 OpenRuleEditorPayload。 */
    void open(OpenRuleEditorPayload payload);
    /** 接收 RuleSnapshotPayload。 */
    void snapshot(RuleSnapshotPayload payload);
    /** 接收 RuleChunkPayload。 */
    void chunk(RuleChunkPayload payload);
    /** 接收 RuleSaveResultPayload。 */
    void result(RuleSaveResultPayload payload);
    /** 接收 RuleSaveProgressPayload。 */
    void progress(RuleSaveProgressPayload payload);
    /** 接收 RuleCatalogPayload。 */
    void catalog(RuleCatalogPayload payload);
    /** 接收 RuleLifespanPayload。 */
    void lifespan(RuleLifespanPayload payload);
    /** 接收 RuleSourceViewPayload。 */
    void sourceView(RuleSourceViewPayload payload);
    /** 接收 RuleQueryResultPayload。 */
    void queryResult(RuleQueryResultPayload payload);
    /** 接收 RuleSessionEventPayload。 */
    void sessionEvent(RuleSessionEventPayload payload);
}
