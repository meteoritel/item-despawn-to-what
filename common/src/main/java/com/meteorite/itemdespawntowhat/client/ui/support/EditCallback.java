package com.meteorite.itemdespawntowhat.client.ui.support;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.network.chat.Component;

// screen 回调接口，用于后端操作完成后通知 UI 层
public interface EditCallback<T extends BaseConversionConfig> {
    // 操作完成后清空表单字段
    void onClearFields();
    // 操作完成后关闭界面
    void onClose();
    // 从当前表单构建一条配置，由 screen 实现
    T buildConfigFromFields();
    // 将指定配置回填到表单以供重新编辑
    void onRefillFields(T config);
    // 通知 UI 列表数据已变化，需要刷新 ConfigListPanel
    void onListChanged();
    default void onSaveError() {}
    // 显示自定义错误信息（红色警告）
    default void onDisplayError(Component message) {}
}
