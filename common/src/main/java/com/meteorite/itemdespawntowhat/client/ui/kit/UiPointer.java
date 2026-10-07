package com.meteorite.itemdespawntowhat.client.ui.kit;

/*** 渲染指针快照；被模态或拖动捕获遮挡时，统一禁用底层 hover 与 tooltip 命中。 */
public record UiPointer(int x, int y) {
    private static final UiPointer BLOCKED = new UiPointer(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2);

    // 宿主只将真实指针交给当前可交互作用域，其余作用域使用屏幕外坐标。
    public static UiPointer gated(boolean allowed, int x, int y) {
        return allowed ? new UiPointer(x, y) : BLOCKED;
    }
}
