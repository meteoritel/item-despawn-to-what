package com.meteorite.itemdespawntowhat.core.model;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.JsonDocumentMigrations;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.mojang.serialization.DataResult;
import java.util.List;

/** 规则文件与编辑文档共用的正式格式；无版本始终表示 2.0.0 的格式 1。 */
public final class RuleDocuments {
    public static final int BASELINE_VERSION = 1;
    public static final int CURRENT_VERSION = 1;
    private static final JsonDocumentMigrations MIGRATIONS = new JsonDocumentMigrations(
            RuleFields.SCHEMA_VERSION, BASELINE_VERSION, CURRENT_VERSION, List.of());

    private RuleDocuments() { }

    /**
     * 准备当前格式副本，保留显式默认值、未知字段与缺省含义。
     * @param source 来源原文或编辑文档
     * @return 当前文档与版本信息，错误时保留来源原文
     */
    public static DataResult<JsonDocumentMigrations.Result> upgrade(JsonObject source) {
        return MIGRATIONS.upgrade(source);
    }
}
