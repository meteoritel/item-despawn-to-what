package com.meteorite.itemdespawntowhat.core.host;

import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogService;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditTransport;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleEditPermissions;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService;
import com.meteorite.itemdespawntowhat.core.service.RuleOverlayStore;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

/** 一个服务端生命周期的编辑组合根；文件队列的进程级协调由覆盖层存储保留。 */
public final class AuthoringHost implements AutoCloseable {
    private final RuleOverlayStore store;
    private final RuleEditService service;
    private final RuleEditTransport transport;

    /**
     * 创建独占编辑状态与它使用的文件所有者。
     * @param server 当前服务端，目录来源只借用其注册表
     * @param root 覆盖层根目录
     */
    public AuthoringHost(MinecraftServer server, Path root) {
        store = new RuleOverlayStore(root);
        try {
            service = new RuleEditService(new EditSessionManager(), store,
                new RuleCatalogService(server), player -> RuleEditPermissions.hasAccess(player.createCommandSourceStack()));
            transport = new RuleEditTransport(service, store);
        } catch (RuntimeException failure) {
            store.close();
            throw failure;
        }
    }

    /** @return 当前实例的编辑编排能力 */
    public RuleEditService service() { return service; }

    /** @return 本次服务端的传输状态 */
    public RuleEditTransport transport() { return transport; }

    /** @return 当前覆盖层内容修订 */
    public int version() { return store.version(); }

    /** 成功发布数据包重载后推进内容修订，持久化仍经唯一文件队列。 */
    public void reloadApplied() { store.bumpVersion(); service.runtimeReloaded(); }

    /** 先停止逻辑编辑，再等待文件侧收尾；等待期间不持有会话或服务监视器。 */
    @Override
    public void close() {
        transport.close();
        service.reset();
        store.close();
    }
}
