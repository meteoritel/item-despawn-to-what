package com.meteorite.itemdespawntowhat.core.api;

/**
 * 规则运行期上限：由服务端配置决定、在规则加载与保存校验时统一适用的参数边界。
 *
 * <p>这些边界不写进规则 JSON，也不参与编解码，只在规则被拒载或保存被拒时生效；
 * 目的是让单个效果的作用范围落在服务端可承受的范围内。</p>
 *
 * @param maxPlaceRadius place_block 单次放置半径（radius）允许的最大值
 */
public record RuleRuntimeLimits(int maxPlaceRadius) {

    /**
     * 没有配置来源的校验路径（例如调试场景）使用的口径：不施加半径限制。
     * 服务端一旦读到配置，加载与保存校验都使用配置换算出的真实上限。
     */
    public static final RuleRuntimeLimits UNLIMITED = new RuleRuntimeLimits(Integer.MAX_VALUE);
}
