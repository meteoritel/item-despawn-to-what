package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveProgress;
import org.jetbrains.annotations.Nullable;

/** 保存工单的只读快照；取消请求不提前终结工单。 */
public record EditorSaveView(@Nullable RuleSaveProgress progress, boolean cancelRequested, EditorSaveOutcome outcome) { }
