/**
 * 纯 kit 接入样板：仅消费 kit.api、原版与许可绘制类型，演示模型在样板内持有。
 *
 * <p>工作台导航、宿主主题集和平台 preview 工厂在外部装配；本目录同一份源码由
 * compileUiKitSamples 独立编译，并由开发工作台直接运行。样板不进入正式 main jar。
 * 点阵与 PreviewScenes 由创建者关闭，工厂注入的 executor 仍归宿主所有。</p>
 */
package com.meteorite.itemdespawntowhat.client.debug.kit;
