package com.meteorite.itemdespawntowhat.core.catalog;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;

import java.util.List;

/**
 * 候选数据源：按类别提供可供编辑器选择的候选项清单。
 * <b>接口形状已冻结（契约 §3.6.1），双方只读不改。</b>
 * 本文件由 session-dev 在 P4b 建的骨架；枚举实现（物品/方块/实体/流体/战利品表/群系/维度/标签）
 * 归 tree-dev 的 task-8，落在 core/catalog + core/registry。
 * 分页、过滤与修订失效由 core/network/catalog/RuleCatalogService 负责，本接口只提供全量与修订号。
 */
public interface RuleCatalogSource {

    // 本数据源对应的候选类别
    RuleCatalogType type();

    // 内容修订号：内容未变化时必须返回同一个值，客户端据此判断本地缓存是否失效
    int revision();

    // 该类型的全量候选清单，已按展示顺序排序；分页由调用方切分
    List<RuleCatalogEntry> all();
}
