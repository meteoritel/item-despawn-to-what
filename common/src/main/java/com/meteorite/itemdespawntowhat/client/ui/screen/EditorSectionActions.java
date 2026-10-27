package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DraftListInputs;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;

/** 分区编辑动作：完成旧输入、写入一条历史并请求组合视图重建。 */
final class EditorSectionActions {
    private final EditorShell shell;
    private final Runnable finish;
    private final Runnable rebuild;
    private final Runnable restoreSwitches;
    private boolean pendingRebuild;

    EditorSectionActions(EditorShell shell, Runnable finish, Runnable rebuild, Runnable restoreSwitches) {
        this.shell = shell;
        this.finish = finish;
        this.rebuild = rebuild;
        this.restoreSwitches = restoreSwitches;
    }

    void finishEditing() { finish.run(); }
    void requestRebuild() { pendingRebuild = true; }
    boolean rebuildPending() { return pendingRebuild; }
    void flushRebuild() {
        if (!pendingRebuild) return;
        pendingRebuild = false;
        rebuild.run();
    }

    boolean apply(String opKey, java.util.function.Consumer<RuleDraft> change) {
        return apply(opKey, change, false);
    }

    /**
     * 结构类编辑的统一入口：与产出相关的操作会顺带按「感知默认值」同步规则分类。
     *
     * <p>分类写入与产出改动处在同一条撤销记录里，撤销该次产出编辑时分类一并回退。</p>
     *
     * @param opKey 操作标签（i18n key）
     * @param change 只做规则结构写入的操作
     * @return 是否应用成功
     */
    boolean applyStructure(String opKey, java.util.function.Consumer<RuleDraft> change) {
        return apply(opKey, change, true);
    }

    // followCategory 为真时在产出变化后同步分类
    private boolean apply(String opKey, java.util.function.Consumer<RuleDraft> change, boolean followCategory) {
        EditSession session = shell.editingSession();
        if (session == null || !shell.editable()) {
            restoreSwitches.run();
            return false;
        }
        finish.run();
        DraftListInputs inputs = DraftListInputs.capture(session.root());
        // 感知默认值的比较基准是变化前的派生值，写入后就取不到了
        String derivedBefore = followCategory ? RuleCategoryAssignment.derive(session.root()) : null;
        session.apply(opKey, edits -> {
            change.accept(edits);
            inputs.remap(session);
            if (followCategory) {
                RuleCategoryAssignment.follow(edits, derivedBefore);
            }
        });
        shell.draftChanged();
        requestRebuild();
        return true;
    }

}
