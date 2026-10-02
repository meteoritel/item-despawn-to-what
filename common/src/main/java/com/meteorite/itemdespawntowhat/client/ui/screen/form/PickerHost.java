package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import java.util.function.Consumer;

/**
 * 候选值选择框宿主：表单引擎内部使用，屏幕不实现。
 */
interface PickerHost {

    // 为字段打开候选值选择框，选定后回调
    void openPicker(EditorField field, Consumer<String> onPicked);
}
