package com.meteorite.itemdespawntowhat.core.catalog;

import java.util.List;

/** 内置来源一次采集得到的修订号与不可变条目。 */
record CatalogSourceSnapshot(int revision, List<RuleCatalogEntry> entries) {
    CatalogSourceSnapshot { entries = List.copyOf(entries); }

    static CatalogSourceSnapshot of(List<RuleCatalogEntry> entries) {
        return new CatalogSourceSnapshot(entries.hashCode(), entries);
    }
}
