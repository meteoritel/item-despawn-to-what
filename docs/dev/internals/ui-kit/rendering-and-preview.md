# UI kit：预览、渲染层级与悬停门禁

> 事实来源：`client/ui/kit/` 的 `UiEntityPreview`、`UiModelBounds`、`UiBlockPreview`、`UiIcon.Rendered`、`UiSpinner`、`UiRenderLayers`、`UiPointer`、`UiModalStack`、`UiScrollView`、`UiCarousel`；宿主 `EntityPreviewIcons` / `RulePreviewIcons`。
> 上承 [UI kit 入口](README.md)。

## 1. 通用实体预览 API

```java
boolean drawn = UiEntityPreview.render(graphics, box, preparedEntity,
        angleRadians, visualWidth, visualHeight, centerY);
UiIcon icon = new UiIcon.Rendered(18, 18, (g, target) -> {
    if (!UiEntityPreview.render(g, target, preparedEntity, angleRadians,
            visualWidth, visualHeight, centerY)) {
        // 宿主绘制兜底，并提供自己的本地化提示。
    }
});
```

在客户端渲染线程调用。宿主准备实体、参数、单调时间角度和生命周期；Kit 不创建实体、不运行 tick、不加入世界、不保留业务缓存。尺寸是宿主估计的可视范围，正数且有限；box 太小或无效参数返回 false。固定全周尺度、局部矩阵与裁剪保证常规 renderer 的等比适框，模型实际边界超出估计时仍可能被裁剪。渲染异常/缺 renderer 返回 false；静默无几何的第三方 renderer 没有通用检测保证。

Kit 提交缓冲、恢复 pose/camera/裁剪，并回到普通 GUI 的 3D 物品光照基线；不保存任意先前光照状态、不更改 dispatcher 的阴影/命中框开关，也不保证恢复第三方 renderer 私自修改的所有全局状态。宿主应在 false 时画替代物品图标及说明，缓存失败避免每帧重试。

`UiIcon.Rendered(width,height,Painter)` 是受控回调适配，不修改 UiImageView 的纹理契约；回调不得泄漏矩阵/裁剪状态。sealed UiIcon 新增子类可能要求外部穷尽 switch 更新。`UiSpinner.render(GuiGraphics,int x,int y,long milliseconds,int color)` 提供环形追逐点阵，时间与颜色由宿主传入；宿主仍负责加载/失败/完成状态文本。

本项目四类展示点均通过 host `EntityPreviewIcons` / `RulePreviewIcons` 使用此 API。缓存 256 个预览，资源/语言/世界变化失效，退出屏幕释放；已知原版龙、恶魂、鱿鱼有尺寸修正。不可预览显示屏障物品及“预览不可用”，不因此禁止选择。Kit 不依赖规则 JSON、网络、loader 或宿主主题。

## 2. 模型适框与方块预览

实体尺寸估计接口继续保留。当前宿主优先调用 `UiEntityPreview.measure(entity)` 获取 renderer 提交顶点的范围，缓存后交给重载 `render(graphics, box, entity, angle, bounds)`；测量失败时才使用加留边的碰撞尺寸估计。测量不提交绘制、不运行 tick、不加入世界，但会调用 renderer，因此须在客户端渲染线程执行；缓存由宿主在世界/资源变化和关闭屏幕时失效。

`UiModelBounds` 包含实际中心与宽/高/深，`scale(box, pitch)` 用整圈水平对角直径和俯仰后的纵向投影计算固定尺度，保留边距。不能以一次测量保证第三方动态 renderer 在所有动画姿态下都不超框；本项目样例不运行 tick，按静态姿态预览。无法通过标准 VertexConsumer 测量的特殊 renderer 可以返回 null，由宿主提供范围。

`UiBlockPreview.measure(state)` / `render(graphics, box, state, angle, bounds)` 绘制真实默认状态的方块模型，包括原版方块实体的物品 renderer。固定 22° 俯仰和绕模型中轴的 yaw，满亮、无世界环境遮蔽、无落地阴影；矩阵、裁剪与 GUI 光照基线在 finally 恢复。无可测模型或绘制失败返回失败，由宿主绘制兜底。

## 3. 渲染层级

绘制层级按作用域管理，不依赖组件绘制先后：

| 层 | 相对 z | 调用方式 |
|---|---:|---|
| 背景/边框 | 0 | 普通 fill/主题绘制 |
| 图标/模型 | 20 | UiIcon.Item 自动补偿原版 +150；模型使用 ICON |
| 输入框文字/角标 | 40 | UiRenderLayers.draw(graphics, FOREGROUND, painter) |
| 原版 tooltip | 400 | 当前作用域下原版 renderTooltip |
| 下一层弹窗遮罩/正文 | 600/601 | 模态栈为每层独立提升 MODAL_STEP |

`UiRenderLayers.draw` 以当前 pose 为基础，相对提升并在进出时提交缓冲、恢复矩阵。模型绘制压缩 z 比例，让较大预览仍处于图标层附近。受控 Rendered 回调须遵循该层级规则。层级负责视觉遮挡；输入隔离仍必须同时实施。

方块预览只在顶点位置矩阵中压缩 z，法线矩阵保持等比缩放后的真实朝向；使用 GUI 3D 物品打光，避免非等比深度缩放和平面物品打光造成灰暗。满亮、无世界环境遮蔽和落地阴影的约定保持有效。

## 4. 指针门禁与滚动容器悬停

宿主在绘制底层控件前调用 `UiPointer.gated(modals.isEmpty(), mouseX, mouseY)`，只把结果坐标交给底层 renderer；顶层模态继续使用真实坐标，其余模态也使用 gated。打开模态时通过 `UiModalStack.setOnScopeChanged` 结束底层捕获。模态栈消费鼠标点击、拖动、释放和滚轮，仅将事件交给顶层；关闭后底层不残留拖动状态。

滚动容器的条目高亮、tooltip 和行内入口应统一通过 `UiScrollView.canHoverContent(x,y)`：滚动条轨道和拖动期间返回 false，即使指针已拖出轨道。UiTreeView 与 UiListView 已接入；页面外层滚动条拖动时也给子组件屏蔽坐标。实际选择态不因屏蔽 hover 而清除。

## 5. 轮播 UiCarousel

`UiCarousel` 接收宿主准备的图标列表，通过 `index(milliseconds)`、`select(index,milliseconds)`、`step(direction,milliseconds)` 和 `render(graphics,box,milliseconds)` 控制轮播。默认 1.5 秒切换，手动选择重新计时；空列表不绘制。标签注册表查询、显示范围、展开容器和本地化仍属于宿主。

## 6. 依赖白名单

精确依赖白名单包含 `com.mojang.blaze3d.vertex.PoseStack` 与 `VertexConsumer`（和 `com.mojang.blaze3d.platform.Lighting`），边界检查脚本同步维护，不放开整个父包。
