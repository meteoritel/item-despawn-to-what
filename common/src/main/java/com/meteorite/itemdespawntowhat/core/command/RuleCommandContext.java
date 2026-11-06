package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleReloadOutcome;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 由宿主提供当前服务端的命令能力，命令在服务端线程按需读取。
 * 发布结果与覆盖快照供只读查询，编辑服务负责修改和文件工作。
 */
public interface RuleCommandContext {

    /**
     * @return 当前运行时是否已启动
     */
    boolean available();

    /**
     * @return 当前已发布的完整加载结果，包含合法的停用规则；尚未就绪时为 null
     */
    @Nullable RuleLoadResult<Rule> publishedRules();

    /**
     * @return 与已发布规则同批捕捉的本服覆盖快照，保存期间保持上一份发布值
     */
    Map<ResourceLocation, Boolean> publishedOverrides();

    /**
     * @return 当前模组级配置，宿主未启动时为 null
     */
    @Nullable ServerConfig serverConfig();

    /**
     * @return 宿主的编辑能力；具体服务是否就绪由上下文中的可空结果表达
     */
    RuleEditServerContext editContext();

    /**
     * @return 当前覆盖层版本，编辑宿主未就绪时为 -1
     */
    int overlayVersion();

    /**
     * @return 当前保存或恢复隔离是否占用编辑目标
     */
    default boolean editingBusy() {
        var service = editContext().editService();
        return service != null && service.saving();
    }

    /**
     * 读取当前来源并发布新索引；换入前失败时保留上一份运行状态。
     *
     * @param server 本次服务端
     * @return 包含加载问题与应用状态的结果；运行时未启动时为 null
     */
    @Nullable RuleReloadOutcome<Rule> reloadRules(MinecraftServer server);
}
