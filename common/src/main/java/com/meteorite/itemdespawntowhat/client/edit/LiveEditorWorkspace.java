package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/***
 * {@link EditorWorkspaceView} 的实机实现：包装协议工作区 {@link RuleEditClientWorkspace}，
 * 把传输 payload 收敛在本类内，界面层因此不接触任何 payload 类型（契约 §5.3）。
 */
public final class LiveEditorWorkspace implements EditorWorkspaceView {

    // 单例
    private static final LiveEditorWorkspace INSTANCE = new LiveEditorWorkspace();

    // 已被计入序号的回执
    private @Nullable RuleSaveResultPayload seenResult;
    // 回执序号
    private long resultSeq;

    // 私有构造
    private LiveEditorWorkspace() {
    }

    // 单例访问
    public static LiveEditorWorkspace instance() {
        return INSTANCE;
    }

    // 底层工作区
    private RuleEditClientWorkspace workspace() {
        return RuleEditClientWorkspace.instance();
    }

    // 刷新回执序号：发现新回执时自增
    private @Nullable RuleSaveResultPayload pollResult() {
        RuleSaveResultPayload payload = this.workspace().lastResult();
        if (payload != null && payload != this.seenResult) {
            this.seenResult = payload;
            this.resultSeq++;
        }
        return payload;
    }

    @Override
    public RuleEditClientState state() {
        return this.workspace().state();
    }

    @Override
    public String sessionId() {
        return this.workspace().sessionId();
    }

    @Override
    public int contextRevision() {
        return this.workspace().contextRevision();
    }

    @Override
    public @Nullable EditorOpenRequest openRequest() {
        return this.workspace().openRequest();
    }

    @Override
    public @Nullable RuleSnapshot snapshot() {
        return this.workspace().snapshot();
    }

    @Override
    public @Nullable RuleCatalog catalog(RuleCatalogType type) {
        return this.workspace().catalog(type);
    }

    @Override
    public boolean requestCatalog(RuleCatalogType type, @Nullable String filter, int page, int pageSize) {
        return this.workspace().requestCatalog(type, filter, page, pageSize);
    }

    @Override
    public boolean save(@Nullable String operationId, @Nullable String changeSetJson) {
        return this.workspace().save(operationId, changeSetJson);
    }

    @Override
    public String newOperationId() {
        return RuleEditClientWorkspace.newOperationId();
    }

    @Override
    public void close(@Nullable String reasonCode) {
        this.workspace().close(reasonCode);
    }

    @Override
    public long resultSeq() {
        this.pollResult();
        return this.resultSeq;
    }

    @Override
    public @Nullable RuleSaveStatus lastStatus() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload == null ? null : RuleSaveStatus.fromId(payload.statusCode());
    }

    @Override
    public @Nullable String lastMessageCode() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload == null ? null : payload.messageCode();
    }

    @Override
    public List<String> lastMessageArgs() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload == null || payload.messageArgs() == null ? List.of() : List.copyOf(payload.messageArgs());
    }

    @Override
    public int lastResultVersion() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload == null ? -1 : payload.resultVersion();
    }

    @Override
    public boolean lastWrittenToDisk() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload != null && payload.writtenToDisk();
    }

    @Override
    public boolean lastReloaded() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload != null && payload.reloaded();
    }

    @Override
    public List<RuleIssue> lastIssues() {
        RuleSaveResultPayload payload = this.pollResult();
        return payload == null || payload.issues() == null ? List.of() : List.copyOf(payload.issues());
    }
}
