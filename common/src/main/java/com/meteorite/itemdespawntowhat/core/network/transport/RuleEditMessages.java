package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveProgress;
import java.util.Locale;

/** 协议状态与展示文案的映射，业务层只返回状态。 */
public final class RuleEditMessages {
    private RuleEditMessages() { }
    /** 保存或查询状态的本地化 key。 */
    public static String key(RuleSaveStatus status) { return "itemdespawntowhat.edit.status." + status.name().toLowerCase(Locale.ROOT); }
    /** 保存阶段的本地化 key。 */
    public static String progressKey(RuleSaveProgress.Stage stage) { return "gui.itemdespawntowhat.edit.save.stage." + stage.name().toLowerCase(Locale.ROOT); }
}

