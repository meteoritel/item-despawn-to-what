package com.meteorite.itemdespawntowhat.client.net;

/***
 * 界面层实现的回调（契约 §5.3）：client/net 只负责在客户端主线程调用，不 import 任何 client/ui 或 client/edit 类。
 */
public interface EditorScreenOpener {

    // 会话已授权：界面层据此打开编辑器界面并展示请求中的状态/消息
    void open(EditorOpenRequest request);

    // 会话失效、被强制释放或断线：界面层据此关闭编辑器界面
    void close();
}
