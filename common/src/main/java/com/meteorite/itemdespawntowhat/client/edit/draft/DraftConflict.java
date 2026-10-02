package com.meteorite.itemdespawntowhat.client.edit.draft;

/***
 * 草稿与目标不一致的冲突描述（任务书交付项 4）。
 * <p>只做「整条规则」级别的判断，不做逐字段合并：字段级三方合并对不懂 JSON 的玩家
 * 没有可解释性，且在界面上无法给出可信的结果。
 */
public record DraftConflict(String targetId, Reason reason) {

    // 冲突类型
    public enum Reason {
        // 原始目标在服务端已不存在（被删除或已回退到无覆盖层）
        TARGET_MISSING,
        // 草稿是按「新建规则」保存的，但服务端已存在同名规则
        TARGET_EXISTS,
        // 服务端整条规则内容与草稿记录的基线不同（被他人或其他端修改）
        REMOTE_CHANGED
    }

    // 对应 lang 文案后缀：gui.itemdespawntowhat.edit.conflict.message.<suffix>
    public String messageSuffix() {
        return switch (this.reason) {
            case TARGET_MISSING -> "target_missing";
            case TARGET_EXISTS -> "target_exists";
            case REMOTE_CHANGED -> "remote_changed";
        };
    }
}
