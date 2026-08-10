package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.form.FormFieldInput;

/**
 * 条件参数控件的统一 JSON 读写与校验契约。
 */
public interface ConditionParameterInput extends FormFieldInput<JsonObject> {
    boolean isValid();

    void setEditorWidth(int width);

    default void setEditable(boolean editable) {
        widget().active = editable;
    }
}
