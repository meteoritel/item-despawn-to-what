package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.type.BuiltinTypeRegistries;

import com.mojang.serialization.DataResult;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleDocuments;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;

/** 网络保存与既有配置迁移共用的提交校验；解码或语义错误必须发生在写入前。 */
public final class RuleSubmissionValidator {
    private RuleSubmissionValidator() {}

    /**
     * 准备并校验单条编辑；成功交出实际写入的当前文档，必须在服务端线程调用。
     * @param edit 编辑意图
     * @param server 当前服务端
     * @param types 类型注册表
     * @param runtimeLimits 当前服务端生效的运行期上限
     * @param issues 本条问题收集器
     * @return 已准备编辑；失败时为空并登记问题
     */
    public static @Nullable RuleEdit prepareEdit(RuleEdit edit, MinecraftServer server,
                                    BuiltinTypeRegistries types, RuleRuntimeLimits runtimeLimits,
                                    IssueCollector issues) {
        // 删除只改覆盖层内容的有无；启停只改本服状态层，两者都没有可解码的规则内容
        if (!edit.contentEdit() || edit.action() == RuleEdit.Action.DELETE) { return edit; }
        try {
            var migration = RuleDocuments.upgrade(Objects.requireNonNull(edit.rule()));
            if (migration.error().isPresent()) {
                issues.error(migration.error().orElseThrow().message(), edit.id().toString(), RuleFields.SCHEMA_VERSION);
                return null;
            }
            JsonObject body = migration.result().orElseThrow().document();
            body.addProperty(RuleFields.ID, edit.id().toString());
            var decoded = RuleCodecs.decoder(server.registryAccess(), types.effectTypes(), types.conditionTypes()).decode(body, issues);
            Rule rule = decoded.result().orElse(null);
            if (decoded.error().isPresent() || rule == null) {
                issues.error(decoded.error().map(DataResult.Error::message).orElse("规则解码失败"), edit.id().toString(), null);
            } else {
                RuleValidation.validate(rule, types.effectTypes(), types.conditionTypes(), runtimeLimits, issues,
                        edit.id().toString());
                RuleReferenceValidator.validate(rule, server, issues, edit.id().toString());
                if (!issues.hasErrors()) { return new RuleEdit(edit.id(), edit.action(), body); }
            }
        } catch (RuntimeException | StackOverflowError failure) {
            // 第三方类型的 Codec/校验器失误也必须拒绝整批，不能进入落盘阶段；
            // 条件树过深引发的栈溢出是 Error，不捕获会直接击穿校验流程
            issues.error("规则校验异常: " + failure, edit.id().toString(), null);
        }
        return null;
    }
}
