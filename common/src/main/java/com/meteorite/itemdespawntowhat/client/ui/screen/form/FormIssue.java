package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import net.minecraft.network.chat.Component;

/**
 * 表单校验问题：保存前本地拦截的最小单位。
 * <p>path 是草稿路径（供界面定位字段），label 是字段名，message 是给玩家看的说明。
 * <p>blocking 为 true 表示「本地可证非法」，必须先修正才能保存；为 false 表示仅提醒
 * （典型是未注册的第三方类型：原数据原样保留，由服务端做权威校验，不能挡住玩家保存其他字段）。
 */
public record FormIssue(String path, Component label, Component message, boolean blocking) {

    // 紧凑构造器：不允许空路径
    public FormIssue {
        if (path == null) {
            path = "";
        }
    }

    // 阻塞项：本地可证非法，保存前必须修正
    public static FormIssue error(String path, Component label, Component message) {
        return new FormIssue(path, label, message, true);
    }

    // 提醒项：不阻塞保存，只做提示
    public static FormIssue warning(String path, Component label, Component message) {
        return new FormIssue(path, label, message, false);
    }

    // 该问题是否属于指定路径或其子路径
    public boolean matches(String otherPath) {
        if (otherPath == null) {
            return false;
        }
        return otherPath.equals(path) || otherPath.startsWith(path + ".");
    }
}
