package com.meteorite.itemdespawntowhat.core.catalog;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 候选数据源入口：返回服务端当前可用的全部候选数据源。
 * <b>接口形状已冻结（契约 §3.6.1），双方只读不改。</b>
 * <p>覆盖九类目录：物品、方块、实体、战利品表、群系、维度、标签、流体、状态效果。物品/方块/实体/流体/状态效果来自静态注册表，
 * 战利品表来自可重载的服务端数据包查找，群系来自动态注册表，维度取服务端已加载的维度键，标签合并物品、
 * 方块、实体、流体、群系五个注册表的标签。只读的 {@code DIMENSION} 名称使用原文 id：原版语言文件不含维度翻译键。</p>
 * <p>条目字段约定（配合冻结 DTO，客户端选择器按此渲染）：</p>
 * <ul>
 *   <li>label：物品/方块/实体/状态效果使用 {@code getDescriptionId()}，群系使用 {@code biome.命名空间.路径}，
 *       战利品表、维度、标签使用原文 id；</li>
 *   <li>subLabel：注册表 id 的命名空间，标签条目为标签所属注册表短名（item / block / entity_type / fluid / biome）；</li>
 *   <li>icon：物品与方块为条目 id 字符串（客户端据此取物品/方块模型），其余类型为空串；
 *       客户端解析失败必须走缺省渲染，不得抛异常。</li>
 * </ul>
 * <p>列表按 id 字典序稳定排序；结果缓存在数据源实例内，而实例按服务端实例复用，因此重复请求不会重建全部字符串。
 * {@code revision()} 每次现场按 id 集合的内容哈希计算（不缓存），内容未变时稳定，reload 后内容变化即改变。</p>
 * <p>全部方法只在服务端主线程调用。</p>
 */
public final class RuleCatalogSources {

    // 按服务端实例缓存的源列表：换服（或换存档）时整体重建，旧服务端随之可回收
    private static volatile CacheEntry cache;

    private RuleCatalogSources() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 服务端内置候选数据源
    public static List<RuleCatalogSource> builtin(MinecraftServer server) {
        if (server == null) {
            return List.of();
        }
        CacheEntry cached = cache;
        if (cached != null && cached.server() == server) {
            return cached.sources();
        }
        List<RuleCatalogSource> sources = create(server);
        cache = new CacheEntry(server, sources);
        return sources;
    }

    // 按 RuleCatalogType 的声明顺序建立九类数据源
    private static List<RuleCatalogSource> create(MinecraftServer server) {
        List<RuleCatalogSource> sources = new ArrayList<>(RuleCatalogType.values().length);
        sources.add(items(server));
        sources.add(blocks(server));
        sources.add(entities(server));
        sources.add(lootTables(server));
        sources.add(biomes(server));
        sources.add(dimensions(server));
        sources.add(new TagCatalogSource(server));
        sources.add(fluids(server));
        sources.add(mobEffects(server));
        return List.copyOf(sources);
    }

    // 物品：静态注册表；图标用物品 id，客户端据此取物品模型
    private static RuleCatalogSource items(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.ITEM,
                s -> BuiltInRegistries.ITEM.keySet(),
                id -> {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    return registered(id, item == null ? id.toString() : item.getDescriptionId(), id.toString());
                });
    }

    // 方块：静态注册表；图标用方块 id，客户端据此取方块物品或方块模型
    private static RuleCatalogSource blocks(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.BLOCK,
                s -> BuiltInRegistries.BLOCK.keySet(),
                id -> {
                    Block block = BuiltInRegistries.BLOCK.get(id);
                    return registered(id, block == null ? id.toString() : block.getDescriptionId(), id.toString());
                });
    }

    // 实体：静态注册表；实体没有统一图标，icon 留空由客户端走缺省渲染
    private static RuleCatalogSource entities(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.ENTITY,
                s -> BuiltInRegistries.ENTITY_TYPE.keySet(),
                id -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                    return registered(id, type == null ? id.toString() : type.getDescriptionId(), "");
                });
    }

    // 战利品表：数据包内容，必须走可重载查找（静态注册表取不到）
    private static RuleCatalogSource lootTables(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.LOOT_TABLE,
                RuleCatalogSources::lootTableIds,
                id -> registered(id, id.toString(), ""));
    }

    // 已加载的战利品表 id 集合：数据包内容只能通过可重载注册表持有者枚举
    private static Set<ResourceLocation> lootTableIds(MinecraftServer server) {
        return new LinkedHashSet<>(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
    }

    // 群系：动态注册表；原版语言文件带 biome.命名空间.路径 键
    private static RuleCatalogSource biomes(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.BIOME,
                RuleCatalogSources::biomeIds,
                id -> registered(id, "biome." + id.getNamespace() + "." + id.getPath(), ""));
    }

    private static Set<ResourceLocation> biomeIds(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.BIOME).keySet();
    }

    // 维度：取服务端已加载的维度键，与维度条件的校验口径一致
    private static RuleCatalogSource dimensions(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.DIMENSION,
                RuleCatalogSources::dimensionIds,
                id -> registered(id, id.toString(), ""));
    }

    private static Set<ResourceLocation> dimensionIds(MinecraftServer server) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        server.levelKeys().forEach(key -> ids.add(key.location()));
        return ids;
    }

    // 流体：原版没有流体翻译键，改用流体默认状态的方块名称（水、岩浆），取不到方块时回退原文 id
    private static RuleCatalogSource fluids(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.FLUID,
                s -> BuiltInRegistries.FLUID.keySet(),
                id -> {
                    Fluid fluid = BuiltInRegistries.FLUID.get(id);
                    Block block = fluid == null ? Blocks.AIR : fluid.defaultFluidState().createLegacyBlock().getBlock();
                    return registered(id, block == Blocks.AIR ? id.toString() : block.getDescriptionId(), "");
                });
    }

    // 状态效果：静态注册表；原版语言文件带 effect.命名空间.路径 键（MobEffect.getDescriptionId 生成）
    private static RuleCatalogSource mobEffects(MinecraftServer server) {
        return new RegistryCatalogSource(server, RuleCatalogType.MOB_EFFECT,
                s -> BuiltInRegistries.MOB_EFFECT.keySet(),
                id -> {
                    MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
                    return registered(id, effect == null ? id.toString() : effect.getDescriptionId(), "");
                });
    }

    // 统一组装条目：id 为注册表 id，subLabel 为命名空间
    private static RuleCatalogEntry registered(ResourceLocation id, String label, String icon) {
        return new RuleCatalogEntry(id.toString(), label, id.getNamespace(), icon);
    }

    // 源列表与其所属服务端：同一服务端复用实例，从而复用 all() 的条目缓存
    private record CacheEntry(MinecraftServer server, List<RuleCatalogSource> sources) {}
}
