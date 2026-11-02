/**
 * 数值策略、滑块、值交互与精确文本绑定；共享区间内核保持包内可见。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与当前无生产引用；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code PixelSliderPainter}: host</li>
 *   <li>{@code UiCyclicRange}: host</li>
 *   <li>{@code UiLinkedValues}: host</li>
 *   <li>{@code UiNumberPolicy}: host</li>
 *   <li>{@code UiNumbers}: host</li>
 *   <li>{@code UiRangeEnd}: host</li>
 *   <li>{@code UiRangeSlider}: host</li>
 *   <li>{@code UiRangeValue}: host</li>
 *   <li>{@code UiScalarSlider}: host</li>
 *   <li>{@code UiSliderExamples}: example</li>
 *   <li>{@code UiSliderPainter}: host</li>
 *   <li>{@code UiSliderRole}: host</li>
 *   <li>{@code UiSliderSkin}: host</li>
 *   <li>{@code UiSliderStyle}: host</li>
 *   <li>{@code UiSliderWindow}: host</li>
 *   <li>{@code UiValueInteraction}: host</li>
 *   <li>{@code UiValueTextBinding}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;
