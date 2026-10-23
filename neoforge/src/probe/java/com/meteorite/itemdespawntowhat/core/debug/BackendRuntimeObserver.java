package com.meteorite.itemdespawntowhat.core.debug;

import net.minecraft.server.MinecraftServer;

/** 保留场景观测，同时在正式索引发布前提供一次性故障。 */
public final class BackendRuntimeObserver extends DebugRuntimeObserver {
    /** 创建开发观测器，夹具状态由正在运行的探针持有。 */
    public BackendRuntimeObserver() {}

    /** 故障只中断正式重载提交，不改变候选查询与世界写入。 */
    @Override
    public void reloadApplying(MinecraftServer server) {
        super.reloadApplying(server);
        BackendWorldProbe.reloadApplying();
    }

    /** 真实加入世界后按源 UUID 关联产物，场景之间的实物分别计数。 */
    @Override
    public void outputAdded(com.meteorite.itemdespawntowhat.core.api.EffectContext context, net.minecraft.world.entity.Entity entity) {
        super.outputAdded(context, entity);
        BackendWorldProbe.outputAdded(context.source(), entity);
    }
}
