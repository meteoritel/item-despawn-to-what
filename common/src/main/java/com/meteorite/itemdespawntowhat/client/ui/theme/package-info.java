/**
 * 灰色像素主题层。
 * <p>本包只依赖 Minecraft 客户端通用类型、本模组的 UI kit 与 {@code client.edit.TypeLabels}
 * （取模组命名空间常量拼贴图路径），不依赖 core、不依赖平台 API。
 * 主题负责视觉，控件（{@code client.ui.widget}）负责交互，两者解耦。
 * <p>宿主入口是 {@link com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes}：屏幕从这里取
 * {@link com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme} 后注入自己装配的控件，控件不再
 * 静态读取本包的主题类。画布页的扁平外壳不属于默认主题，仍由该页在调用点显式注入 {@code UiFlatSkin}。
 */
package com.meteorite.itemdespawntowhat.client.ui.theme;
