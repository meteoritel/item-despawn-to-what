package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/***
 * 界面层使用的编辑工作区只读视图（契约 §5.3）：不暴露任何传输 payload 类型，
 * 界面只按 statusCode 派生出的 {@link RuleSaveStatus} 分支，禁止解析文案。
 */
public interface EditorWorkspaceView {

    // 当前会话状态
    RuleEditClientState state();

    // 是否可编辑（仅 ACTIVE 可编辑）
    default boolean active() {
        return state() == RuleEditClientState.ACTIVE;
    }

    // 会话 id（无会话时为空串）
    String sessionId();

    // 服务端上下文版本
    int contextRevision();

    // 服务端下发的开屏请求
    @Nullable EditorOpenRequest openRequest();

    // 最近一次快照
    @Nullable RuleSnapshot snapshot();

    // 指定类型的目录页
    @Nullable RuleCatalog catalog(RuleCatalogType type);

    // 懒加载的完整目录：lastPage=false 时展示为部分结果。
    @Nullable RuleCatalog directory(RuleCatalogType type);
    void refreshDirectory(RuleCatalogType type);
    boolean directoryFailed(RuleCatalogType type);
    void retryDirectory(RuleCatalogType type);
    void invalidateDirectories();

    // 请求目录页
    boolean requestCatalog(RuleCatalogType type, @Nullable String filter, int page, int pageSize);

    // 提交变更集 JSON
    boolean save(@Nullable String operationId, @Nullable String changeSetJson);

    // 生成新的操作 id（保存回执用它配对）
    String newOperationId();

    // 关闭会话（玩家关屏用 screen_closed）
    void close(@Nullable String reasonCode);

    // 回执序号：每收到一次新回执自增，界面据此判定是否是新回执
    long resultSeq();

    // 最近一次回执状态
    @Nullable RuleSaveStatus lastStatus();

    // 最近一次回执的翻译 key（itemdespawntowhat.edit.* ）
    @Nullable String lastMessageCode();

    // 回执参数
    List<String> lastMessageArgs();

    // 回执携带的结果版本
    int lastResultVersion();

    // 回执：是否已写入磁盘
    boolean lastWrittenToDisk();

    // 回执：是否已运行时重载
    boolean lastReloaded();

    // 回执附带的问题清单
    List<RuleIssue> lastIssues();
}
