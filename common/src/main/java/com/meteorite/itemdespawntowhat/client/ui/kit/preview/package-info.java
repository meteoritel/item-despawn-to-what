/**
 * 图标场景：主体快照、异步静态编译、冻结实例、转台取景与有界资源生命周期。
 *
 * <p>依赖方向为 preview 到基础 kit；宿主注入线程池、时钟、平台几何与诊断。
 * 静态编译在工作线程进行，实例和 GPU 交接在渲染线程进行。</p>
 * <p>公开面角色：</p>
 * <ul>
 *   <li>{@code PreviewSubject}: host</li>
 *   <li>{@code PreviewStructure}: host</li>
 *   <li>{@code PreviewScene}: host</li>
 *   <li>{@code PreviewScenes}: host</li>
 *   <li>{@code PreviewState}: host</li>
 *   <li>{@code PreviewMotion}: host</li>
 *   <li>{@code UiSceneCamera}: host</li>
 *   <li>{@code UiSceneStyle}: host</li>
 *   <li>{@code UiSceneView}: host</li>
 * </ul>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit.preview;
