package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveProgressPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveProgress;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** 单次保存的关联、递增进度和取消请求；终态保留到下一次提交或连接重置。 */
final class SaveTicket {
    private String operationId = "";
    private @Nullable RuleSaveProgress progress;
    private long progressSequence;
    private boolean cancelRequested;
    private long resultSequence;
    private EditorSaveOutcome result = EditorSaveOutcome.empty(0);
    private @Nullable EditorSaveView published;

    String operationId() { return operationId; }
    EditorSaveView view() {
        if (published == null || published.progress() != progress || published.cancelRequested() != cancelRequested
                || published.outcome() != result) published = new EditorSaveView(progress, cancelRequested, result);
        return published;
    }
    boolean rejects(String operation) { return operationId.isEmpty() || !operationId.equals(operation); }
    boolean canCancel() { return progress != null && progress.cancellable() && !cancelRequested; }
    void requestCancel() { cancelRequested = true; }

    void start(String operation) {
        operationId = operation;
        progressSequence = 0;
        cancelRequested = false;
        progress = new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING, 0, 0, "", true);
        result = EditorSaveOutcome.empty(resultSequence);
    }

    void accept(RuleSaveProgressPayload payload) {
        if (rejects(payload.operationId()) || payload.sequence() <= progressSequence) return;
        progressSequence = payload.sequence();
        progress = payload.progress();
        if (progress.stage() != RuleSaveProgress.Stage.CANCELLING
                && progress.stage() != RuleSaveProgress.Stage.ROLLING_BACK && !progress.cancellable()) {
            cancelRequested = false;
        }
    }

    void complete(RuleSaveResultPayload payload) {
        result = new EditorSaveOutcome(++resultSequence, RuleSaveStatus.fromId(payload.statusCode()), payload.messageCode(),
                payload.messageArgs() == null ? List.of() : payload.messageArgs(), payload.resultVersion(),
                payload.writtenToDisk(), payload.reloaded(), payload.issues() == null ? List.of() : payload.issues());
        end();
    }

    void end() {
        operationId = "";
        progress = null;
        progressSequence = 0;
        cancelRequested = false;
    }

    void reset() { end(); result = EditorSaveOutcome.empty(resultSequence); }
}
