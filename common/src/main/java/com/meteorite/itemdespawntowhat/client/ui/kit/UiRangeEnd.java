package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 区间端点身份：低端与高端。
 *
 * <p>端点身份与数值是两件事：拖动低端越过高端的表现是停在同值，而不是把两个端点的身份互换，
 * 因此「当前端」用本枚举记录，不用数值比较推断。</p>
 */
public enum UiRangeEnd {

    LOW,
    HIGH;

    // 另一端
    public UiRangeEnd other() {
        return this == LOW ? HIGH : LOW;
    }
}
