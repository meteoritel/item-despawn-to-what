package com.meteorite.itemdespawntowhat.runtime;

/** Fabric 自然寿命参考值：首次服务端比较时采样，保留到停服。 */
public final class FabricLifespanReference {
    private static int lifespanTicks;

    private FabricLifespanReference() { }

    /** @return 缓存的参考寿命 tick；零表示尚未采样 */
    public static int ticks() { return lifespanTicks; }

    /**
     * 仅接受首次正寿命阈值；普通物品的统一参考值不跟随运行中的个别变化。
     * @param ticks 自然消失判断实际使用的阈值
     */
    public static void observe(int ticks) {
        if (lifespanTicks == 0 && ticks > 0) lifespanTicks = ticks;
    }

    /** 停服时清除缓存，避免切换单人世界继承参考值。 */
    public static void reset() { lifespanTicks = 0; }
}
