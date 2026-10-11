package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 选择页的条目策略：物品与流体的默认排除规则写在同一处，并持有工具栏「物品 / 标签 / 一起」的范围状态。
 *
 * <p>物品排除空气与仅命令可得的技术性条目；流体只保留可放置的源流体——空流体与流动变体一律排除。
 * 标签引用（{@code #} 开头）是标签本身，不参与排除判定。</p>
 *
 * <p>排除规则固定生效、不提供界面开关；范围状态由页面按钮回写、宿主在分页前读取，
 * 因此页码、总数与可见条目始终基于同一份事实。</p>
 */
public final class CatalogEntryPolicy implements UiCatalogGrid.EntryPolicy {

    // 物品：空气与仅命令/创造可得、正常玩法中不会作为掉落物出现的技术性条目
    private static final Set<String> TECHNICAL_ITEMS = Set.of(
            "minecraft:air",
            "minecraft:light",
            "minecraft:barrier",
            "minecraft:structure_void",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:debug_stick",
            "minecraft:petrified_oak_slab");

    // 流体：显式写出的空流体 id，作为注册表判定的兜底
    private static final Set<String> EMPTY_FLUID_IDS = Set.of("minecraft:empty");

    private final RuleCatalogType type;
    private final boolean scoped;
    private UiCatalogGrid.Scope scope = UiCatalogGrid.Scope.BOTH;

    private CatalogEntryPolicy(RuleCatalogType type, boolean scoped) {
        this.type = type;
        this.scoped = scoped;
    }

    // 物品页、流体页等只有单一来源的目录：不出现范围按钮
    public static CatalogEntryPolicy fixed(@Nullable RuleCatalogType type) {
        return new CatalogEntryPolicy(type == null ? RuleCatalogType.ITEM : type, false);
    }

    // 同时存在物品与同注册表标签的目录：提供「只物品 / 只标签 / 一起」切换
    public static CatalogEntryPolicy scoped(@Nullable RuleCatalogType type) {
        return new CatalogEntryPolicy(type == null ? RuleCatalogType.ITEM : type, true);
    }

    // 默认排除：物品是技术性条目清单，流体是空流体与流动变体；两者写在一起便于对照维护
    @Override
    public boolean hidden(String id) {
        if (id == null || id.isBlank() || id.startsWith("#")) {
            return false;
        }
        return switch (type) {
            case FLUID -> EMPTY_FLUID_IDS.contains(id) || FluidPreviewIcons.isExcluded(id);
            case ITEM -> TECHNICAL_ITEMS.contains(id);
            default -> false;
        };
    }

    @Override
    public boolean scoped() {
        return scoped;
    }

    @Override
    public UiCatalogGrid.Scope scope() {
        return scope;
    }

    @Override
    public void setScope(UiCatalogGrid.Scope next) {
        this.scope = next == null ? UiCatalogGrid.Scope.BOTH : next;
    }
}
