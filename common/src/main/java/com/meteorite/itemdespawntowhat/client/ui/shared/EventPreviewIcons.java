package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiEventIcons;
import org.jetbrains.annotations.Nullable;

/**
 * 事件图标绑定：把动作 JSON 的天气与天象参数解析成 {@link UiEventIcons.Event}。
 *
 * <p>规则列表、配方预览、结果行与详情卡共用这一处判定，各页面不再各写一份天气分支；
 * 天气效果的三种形态（放晴、下雨、雷雨）与 {@code RuleNaming} 的天气短标题口径一致：
 * {@code mode=clear} 为晴天，否则按 {@code thundering} 区分雷雨与下雨。</p>
 */
public final class EventPreviewIcons {

    private static final String TYPE_LIGHTNING = "itemdespawntowhat:lightning";
    private static final String TYPE_ARROW_RAIN = "itemdespawntowhat:arrow_rain";
    private static final String TYPE_WEATHER = "itemdespawntowhat:weather";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_THUNDERING = "thundering";
    private static final String MODE_CLEAR = "clear";

    private EventPreviewIcons() {
    }

    /**
     * 动作对应的事件图标。
     *
     * @param action 单个动作映射，可为 {@code null}
     * @return 事件图标；天气与天象之外的动作为 {@code null}
     */
    public static @Nullable UiIcon icon(@Nullable RuleValue action) {
        UiEventIcons.Event event = event(action);
        return event == null ? null : UiEventIcons.icon(event);
    }

    /**
     * 动作对应的事件种类。
     *
     * @param action 单个动作映射，可为 {@code null}
     * @return 事件种类；非天气与天象动作为 {@code null}
     */
    public static @Nullable UiEventIcons.Event event(@Nullable RuleValue action) {
        if (action == null) {
            return null;
        }
        return switch (text(action, FIELD_TYPE)) {
            case TYPE_LIGHTNING -> UiEventIcons.Event.LIGHTNING;
            case TYPE_ARROW_RAIN -> UiEventIcons.Event.ARROW_RAIN;
            case TYPE_WEATHER -> weather(action);
            default -> null;
        };
    }

    // 天气效果按 mode 与 thundering 分三态；mode 缺省按下雨处理，与编辑默认值一致。
    private static UiEventIcons.Event weather(RuleValue action) {
        if (MODE_CLEAR.equals(text(action, FIELD_MODE))) {
            return UiEventIcons.Event.CLEAR;
        }
        return boolOf(action.child(FIELD_THUNDERING)) ? UiEventIcons.Event.THUNDER : UiEventIcons.Event.RAIN;
    }

    // 兼容迁移前的 JsonElement#getAsBoolean：布尔标量直读，文本标量按 Boolean.parseBoolean 宽松解析
    private static boolean boolOf(@Nullable RuleValue value) {
        if (value == null || !value.isScalar()) {
            return false;
        }
        return value.isFlag() ? value.asFlag(false) : Boolean.parseBoolean(value.asText());
    }

    private static String text(RuleValue object, String key) {
        RuleValue value = object.child(key);
        return value != null && value.isText() ? value.asText() : "";
    }
}
