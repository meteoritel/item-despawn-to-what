package com.meteorite.itemdespawntowhat.client.net;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * 注册槽（契约 §5.3）：客户端初始化时由界面层注册实现，client/net 通过它有条件地打开/关闭界面。
 * 未注册时只记日志，绝不抛异常；未注册也不影响协议层其它功能。
 */
public final class EditorScreenHooks {

    // 日志
    private static final Logger LOGGER = LoggerFactory.getLogger("itemdespawntowhat-client-net");

    // 当前注册的界面实现；未注册或已注销时为 null
    private static volatile EditorScreenOpener opener;

    private EditorScreenHooks() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册/注销界面实现；opener 为 null 表示注销
    public static void setOpener(@Nullable EditorScreenOpener opener) {
        EditorScreenHooks.opener = opener;
    }

    // 当前注册的界面实现，未注册时为 null
    public static @Nullable EditorScreenOpener opener() {
        return opener;
    }

    // 打开界面：未注册时只记日志
    public static void open(EditorOpenRequest request) {
        EditorScreenOpener current = opener;
        if (current == null) {
            LOGGER.info("编辑器界面未注册，忽略打开请求: session={} status={}",
                    request == null ? "" : request.sessionId(), request == null ? "" : request.statusCode());
            return;
        }
        current.open(request);
    }

    // 关闭界面：未注册时只记日志
    public static void close() {
        EditorScreenOpener current = opener;
        if (current == null) {
            LOGGER.info("编辑器界面未注册，忽略关闭请求");
            return;
        }
        current.close();
    }
}
