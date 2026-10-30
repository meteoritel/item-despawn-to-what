package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogTypePresentation;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;

/**
 * 选择页的条目策略：物品与流体的默认排除规则写在同一处。
 *
 * <p>物品排除空气与仅命令可得的技术性条目；流体只保留可放置的源流体——空流体与流动变体一律排除。
 * 标签引用（{@code #} 开头）是标签本身，不参与排除判定。</p>
 *
 * <p>排除规则固定生效、不提供界面开关；选择范围由宿主目录视图承载。</p>
 */
public final class CatalogEntryPolicy implements UiCatalogGrid.EntryPolicy {

    private final RuleCatalogType type;
    private final boolean scoped;

    private CatalogEntryPolicy(RuleCatalogType type, boolean scoped) {
        this.type = type;
        this.scoped = scoped;
    }

    // 物品页、流体页等只有单一来源的目录：不出现范围按钮
    public static CatalogEntryPolicy fixed(RuleCatalogType type) {
        return new CatalogEntryPolicy(type, false);
    }

    // 同时存在主分类与同注册表标签的目录：提供「只主分类 / 只标签 / 一起」切换
    public static CatalogEntryPolicy scoped(RuleCatalogType type) {
        return new CatalogEntryPolicy(type, true);
    }

    // 默认排除：物品是技术性条目清单，流体是空流体与流动变体；两者写在一起便于对照维护
    @Override
    public boolean hidden(TaggedId reference) {
        return !reference.tag() && CatalogTypePresentation.of(type).hidden(reference.id());
    }

    @Override
    public boolean scoped() {
        return scoped;
    }

}
