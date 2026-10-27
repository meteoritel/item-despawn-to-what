package com.meteorite.itemdespawntowhat.core.host;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import org.jetbrains.annotations.Nullable;

/** 宿主提供给开发场景和诊断工具的能力视图；正式命令只接收基础命令上下文。 */
public interface RuleRuntimeDiagnosticsContext extends RuleCommandContext {

    /** 当前运行时，未启动或已停止时为空。 */
    @Nullable ConversionRuntime runtime();
}
