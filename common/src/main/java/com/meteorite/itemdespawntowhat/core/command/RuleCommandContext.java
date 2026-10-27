package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.RuleReloadOutcome;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** 正式命令所需的宿主能力；运行时未启动时由就绪谓词和可空结果表达。 */
public interface RuleCommandContext {

    /** 当前运行时是否已启动。 */
    boolean available();

    /** 当前模组级配置，未启动时为空。 */
    @Nullable ServerConfig serverConfig();

    /** 编辑会话、覆盖层目录与重载所需的能力。 */
    RuleEditServerContext editContext();

    /** 覆盖层规则的缺省命名空间。 */
    String overlayNamespace();

    /** 当前覆盖层版本；会话管理器未就绪时为 -1。 */
    int overlayVersion();

    /** 重载规则并重建运行时索引；运行时未启动时返回空，加载问题由结构化回执携带。 */
    @Nullable RuleReloadOutcome<Rule> reloadRules(MinecraftServer server);
}
