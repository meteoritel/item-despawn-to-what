package com.meteorite.itemdespawntowhat.core.catalog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 基于注册表 id 集合的候选数据源实现（物品、方块、实体、流体、战利品表、群系、维度）。
 * <p>id 集合由 {@code idSupplier} 现场取得：静态注册表取 {@code keySet()}，数据包注册表取可重载查找或
 * {@code registryAccess()}，因此内容在 reload 后自然会变化。</p>
 * <p>条目按 id 字典序排序并缓存；修订号按 id 集合的内容哈希计算，内容未变时稳定，reload 后内容变化即改变。
 * 缓存按数据源实例保存，而实例由 {@link RuleCatalogSources#builtin(MinecraftServer)} 按服务端实例复用。</p>
 * <p>本类只在服务端主线程调用。</p>
 */
final class RegistryCatalogSource implements RuleCatalogSource {

    // 条目构造：由具体类型决定本地化键与图标约定
    @FunctionalInterface
    interface EntryFactory {
        RuleCatalogEntry create(ResourceLocation id);
    }

    private final MinecraftServer server;
    private final RuleCatalogType type;
    private final Function<MinecraftServer, Set<ResourceLocation>> idSupplier;
    private final EntryFactory factory;

    private Snapshot snapshot = new Snapshot(Integer.MIN_VALUE, Set.of(), List.of());

    RegistryCatalogSource(MinecraftServer server, RuleCatalogType type,
                          Function<MinecraftServer, Set<ResourceLocation>> idSupplier, EntryFactory factory) {
        this.server = server;
        this.type = type;
        this.idSupplier = idSupplier;
        this.factory = factory;
    }

    @Override
    public RuleCatalogType type() {
        return this.type;
    }

    @Override
    public int revision() {
        return snapshot().revision();
    }

    @Override
    public List<RuleCatalogEntry> all() {
        return snapshot().entries();
    }

    private synchronized Snapshot snapshot() {
        Set<ResourceLocation> ids = this.idSupplier.apply(this.server);
        if (ids.equals(snapshot.ids())) return snapshot;
        List<RuleCatalogEntry> built = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            RuleCatalogEntry entry = this.factory.create(id);
            if (entry != null) built.add(entry);
        }
        built.sort(Comparator.comparing(RuleCatalogEntry::id));
        List<RuleCatalogEntry> entries = List.copyOf(built);
        int revision = revisionOf(entries);
        snapshot = new Snapshot(revision, Set.copyOf(ids), entries);
        return snapshot;
    }

    // 修订覆盖实际下发字段；排序后的 DTO 列表使结果与来源枚举顺序无关。
    private static int revisionOf(List<RuleCatalogEntry> entries) {
        return entries.hashCode();
    }

    private record Snapshot(int revision, Set<ResourceLocation> ids, List<RuleCatalogEntry> entries) { }
}
