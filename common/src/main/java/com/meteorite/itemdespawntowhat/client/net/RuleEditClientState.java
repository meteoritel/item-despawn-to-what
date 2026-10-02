package com.meteorite.itemdespawntowhat.client.net;

/***
 * 客户端编辑会话状态机（契约 §3.1 的服务端状态在客户端的镜像）。
 * 界面层据此决定是否显示编辑控件、是否允许提交：只有 {@link #ACTIVE} 可编辑。
 */
public enum RuleEditClientState {

    // 无会话：服务端尚未授权编辑，快捷键只提示用指令发起
    FREE,
    // 已收到授权并已发送确认，等待服务端快照
    OPENING,
    // 会话可编辑：可请求快照/目录、提交变更集、发送心跳
    ACTIVE,
    // 变更集已提交、等待服务端回执
    APPLYING
}
