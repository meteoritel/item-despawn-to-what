package com.meteorite.itemdespawntowhat.core.catalog;

/** 服务内部读取同批内容；公开来源仍独立提供 revision 与 all。 */
interface SnapshotCatalogSource extends RuleCatalogSource {
    CatalogSourceSnapshot snapshot();
}
