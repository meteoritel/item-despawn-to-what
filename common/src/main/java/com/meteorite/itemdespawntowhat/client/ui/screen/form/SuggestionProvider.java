package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import java.util.List;

/**
 * 输入建议提供者：登记表 / 数据包 / 内置模板等候选值的来源。
 * <p>界面只依赖本接口，候选值由屏幕在构造时注入。
 */
public interface SuggestionProvider {

    // 返回该字段的候选值；没有候选时返回空列表
    List<Suggestion> suggestions(EditorField field);
}
