package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.client.network.RuleEditorClient;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端规则编辑会话：缓存服务端快照解析出的视图模型，并把界面操作打包成变更集提交。
 * 保存语义是「提交变更集」，不再回写整份快照，因此不会误删未参与编辑的规则（含 disabled 规则）。
 */
public final class RuleEditorSession {

    private static final RuleEditorSession INSTANCE = new RuleEditorSession();

    private final List<RuleEntry> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private int version = -1;
    private @Nullable String lastResult;
    private boolean installed;

    private RuleEditorSession() {
    }

    public static RuleEditorSession get() {
        return INSTANCE;
    }

    // 挂载门面回调（重复调用无副作用）
    public void install() {
        if (installed) {
            return;
        }
        installed = true;
        RuleEditorClient.setSnapshotListener(this::acceptSnapshot);
        RuleEditorClient.setResultListener(this::acceptResult);
    }

    // 向服务端请求覆盖层合并快照
    public void requestSnapshot() {
        install();
        RuleEditorClient.requestSnapshot();
    }

    // 快照到达：重建视图模型并通知界面刷新
    private void acceptSnapshot(RuleSnapshot snapshot) {
        entries.clear();
        entries.addAll(RuleEntry.listFrom(snapshot));
        version = snapshot.version();
        notifyListeners();
    }

    // 保存回执到达：只保存文本，界面自行展示（不做成功/失败区分）
    private void acceptResult(String message) {
        lastResult = message;
        notifyListeners();
    }

    public List<RuleEntry> entries() {
        return List.copyOf(entries);
    }

    public List<RuleView> rules() {
        List<RuleView> result = new ArrayList<>();
        for (RuleEntry entry : entries) {
            result.add(entry.rule());
        }
        return result;
    }

    // 快照版本戳；-1 表示尚未收到快照
    public int version() {
        return version;
    }

    public @Nullable String lastResult() {
        return lastResult;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    // 提交单条 upsert 变更
    public void submitUpsert(RuleView rule) {
        submit(List.of(rule.toUpsertEdit()));
    }

    // 提交单条 delete 变更
    public void submitDelete(String ruleId) {
        ResourceLocation id = ResourceLocation.tryParse(ruleId);
        if (id == null) {
            lastResult = Component.translatable("gui.itemdespawntowhat.rule.id_invalid").getString();
            notifyListeners();
            return;
        }
        submit(List.of(new RuleEdit(id, RuleEdit.Action.DELETE, null)));
    }

    // 组装并提交变更集（expectedVersion 取当前快照版本戳）
    public void submit(List<RuleEdit> edits) {
        install();
        if (version < 0) {
            lastResult = Component.translatable("gui.itemdespawntowhat.rule.no_snapshot").getString();
            notifyListeners();
            return;
        }
        RuleEditorClient.sendChangeSet(new RuleEditChangeSet(version, edits));
    }

    // 便于界面自检/展示当前快照对应的变更集
    public RuleEditChangeSet buildChangeSet(List<RuleEdit> edits) {
        return new RuleEditChangeSet(version, edits);
    }

    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
