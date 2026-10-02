package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;

/** 网络保存与旧配置迁移共用的提交校验；解码或语义错误必须发生在写入前。 */
public final class RuleSubmissionValidator {
    private RuleSubmissionValidator() {}

    // 控制条目只允许 id 与一个值为 true 的控制字段；普通条目走完整 Codec 和类型校验。
    public static boolean validate(RuleEditChangeSet changes, net.minecraft.server.MinecraftServer server,
                                   BuiltinTypeRegistries types, IssueCollector issues) {
        for (RuleEdit edit : changes.edits()) {
            if (edit.action() == RuleEdit.Action.DELETE) { continue; }
            JsonObject body = java.util.Objects.requireNonNull(edit.rule()).deepCopy();
            body.addProperty("id", edit.id().toString());
            if (body.has("disabled") || body.has("delete")) {
                String field = body.has("delete") ? "delete" : "disabled";
                boolean valid = body.size() == 2 && body.get(field).isJsonPrimitive()
                        && body.get(field).getAsJsonPrimitive().isBoolean() && body.get(field).getAsBoolean();
                if (!valid) { issues.error("控制条目只能包含 id 和一个值为 true 的 disabled/delete 字段", edit.id().toString(), field); }
                continue;
            }
            try {
                var decoded = RuleCodecs.decoder(server.registryAccess(), types.effectTypes(), types.conditionTypes()).decode(body, issues);
                Rule rule = decoded.result().orElse(null);
                if (rule == null) {
                    issues.error(decoded.error().map(error -> error.message()).orElse("规则解码失败"), edit.id().toString(), null);
                } else {
                    RuleValidation.validate(rule, types.effectTypes(), types.conditionTypes(), issues, edit.id().toString());
                    RuleReferenceValidator.validate(rule, server, issues, edit.id().toString());
                }
            } catch (RuntimeException failure) {
                // 第三方类型的 Codec/校验器失误也必须拒绝整批，不能进入落盘阶段。
                issues.error("规则校验异常: " + failure, edit.id().toString(), null);
            }
        }
        return issues.errors().isEmpty();
    }
}
