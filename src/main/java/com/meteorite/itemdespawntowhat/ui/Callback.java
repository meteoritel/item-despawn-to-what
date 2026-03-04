package com.meteorite.itemdespawntowhat.ui;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;

// Screen 回调接口，用于后端操作完成后通知 UI 层
public interface Callback<T extends BaseConversionConfig> {
    // 操作完成后清空表单字段
    void onClearFields();
    // 操作完成后关闭界面
    void onClose();
    // 从当前表单构建一条配置，由 Screen 实现
    T buildConfigFromFields();
    // 将指定配置回填到表单以供重新编辑
    void onRefillFields(T config);
    // 通知 UI 列表数据已变化，需要刷新 ConfigListPanel
    void onListChanged();
}
