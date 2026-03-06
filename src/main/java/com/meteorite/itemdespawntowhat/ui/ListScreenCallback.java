package com.meteorite.itemdespawntowhat.ui;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.ui.panel.ConfigListPanel;

// 回调到父 EditScreen 的接口
public interface ListScreenCallback<T extends BaseConversionConfig> {
    // 用户选择了某条配置进行编辑
    void onEditRequested(ConfigListPanel.EntrySource source, int indexInSource);
    // 用户删除了某条配置
    void onDeleteRequested(ConfigListPanel.EntrySource source, int indexInSource);
    // 列表 screen 关闭后通知父 screen 刷新自身按钮文字
    void onListScreenClosed();
}
