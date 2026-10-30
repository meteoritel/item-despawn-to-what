package com.meteorite.itemdespawntowhat.core.catalog;

import net.minecraft.core.Registry;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签候选数据源：合并物品、方块、实体、流体、群系标签，条目 id 与标签引用写法一致（{@code #命名空间:路径}），
 * 可直接填入规则里的标签字段。
 * <p>同名标签若同时存在于多个注册表（例如物品与方块都有 {@code minecraft:logs}），只保留一条，
 * 其 {@code subLabel} 记录首次命中的注册表短名（item / block / entity_type / fluid / biome）。</p>
 * <p>原版语言文件不含标签翻译键（en_us 中无 {@code tag.*} 条目），因此 label 使用原文 {@code #命名空间:路径}，
 * 由界面按需自行本地化或直接展示。</p>
 * <p>按「标签 id → 首次所属注册表」映射比较内容；相同内容复用快照。
 * 条目按 id 排序，修订覆盖实际下发字段。只在服务端主线程调用。</p>
 */
final class TagCatalogSource implements SnapshotCatalogSource {

    private final MinecraftServer server;

    private Map<ResourceLocation, String> cachedTags;
    private CatalogSourceSnapshot cached;

    TagCatalogSource(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public RuleCatalogType type() {
        return RuleCatalogType.TAG;
    }

    @Override
    public int revision() { return snapshot().revision(); }

    @Override
    public List<RuleCatalogEntry> all() { return snapshot().entries(); }

    @Override
    public CatalogSourceSnapshot snapshot() {
        Map<ResourceLocation, String> tags = collect();
        if (tags.equals(cachedTags)) return cached;
        List<RuleCatalogEntry> built = new ArrayList<>(tags.size());
        tags.forEach((id, kind) -> {
            String reference = new TaggedId(id, true).serialized();
            built.add(new RuleCatalogEntry(reference, reference, kind, ""));
        });
        built.sort(Comparator.comparing(RuleCatalogEntry::id));
        cachedTags = Map.copyOf(tags);
        cached = CatalogSourceSnapshot.of(built);
        return cached;
    }

    // 汇总各注册表的标签 id：键为标签 id，值为首次命中的注册表短名
    private Map<ResourceLocation, String> collect() {
        Map<ResourceLocation, String> tags = new LinkedHashMap<>();
        collectFrom(tags, BuiltInRegistries.ITEM, "item");
        collectFrom(tags, BuiltInRegistries.BLOCK, "block");
        collectFrom(tags, BuiltInRegistries.ENTITY_TYPE, "entity_type");
        collectFrom(tags, BuiltInRegistries.FLUID, "fluid");
        collectFrom(tags, this.server.registryAccess().registryOrThrow(Registries.BIOME), "biome");
        return tags;
    }

    // 单个注册表：取全部标签 id，已存在则不覆盖（保留先命中的注册表短名）
    private static void collectFrom(Map<ResourceLocation, String> out, Registry<?> registry, String kind) {
        registry.getTagNames().forEach(tag -> out.putIfAbsent(tag.location(), kind));
    }
}
