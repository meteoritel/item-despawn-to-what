/**
 * 输入作用域、持续交互、焦点登记与导航历史；宿主选择活动域并注入内容、历史和退出动作。
 *
 * <p>本目录的 public 类型是受支持的公开入口；包内 helper 由 Java 可见性保护。
 * 消费角色中 host 表示 kit 外生产源码直接引用，library 表示供其它宿主使用，example 表示装配示例。
 * library 项逐项注明用途与 kit 外当前无直接生产引用；公开返回链随用途说明；嵌套公开契约随顶层类型维护。</p>
 * <ul>
 *   <li>{@code UiPointer}: host</li>
 *   <li>{@code UiChildren}: host</li>
 *   <li>{@code UiInputContainer}: host</li>
 *   <li>{@code UiPointerHit}: host</li>
 *   <li>{@code UiFocusManager}: host</li>
 *   <li>{@code UiFocusFollow}: host</li>
 *   <li>{@code UiFocusState}: host</li>
 *   <li>{@code UiFocusTarget}: host</li>
 *   <li>{@code UiHistoryShortcut}: host</li>
 *   <li>{@code UiInputContext}: host</li>
 *   <li>{@code UiInputRouter}: library；用途：选择稳定作用域并统一派发；宿主经 UiScreen.inputRouter 取得同一实例；当前无直接生产引用</li>
 *   <li>{@code UiInputScope}: host</li>
 *   <li>{@code UiInputTarget}: host</li>
 *   <li>{@code UiInteractionSession}: host</li>
 *   <li>{@code UiNavigationHistory}: library；用途：有界导航历史；宿主提供快照、有效性检查与恢复，输入和模态优先级由宿主决定；当前无直接生产引用</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;
