package com.meteorite.itemdespawntowhat.client.ui;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.client.ui.panel.ConfigListPanel;

// 回调到父 EditScreen 的接口
public interface ListScreenCallback<T extends BaseConversionConfig> {
    // 玩家选择了某条配置进行编辑
    void onEditRequested(ConfigListPanel.EntrySource source, int indexInSource);
    // 玩家复制了某条配置
    void onCopyRequested(ConfigListPanel.EntrySource source, int indexInSource);
    // 列表数据发生变化
    void onListDataChanged();
    // 列表 screen 关闭
    void onListScreenClosed();
}
