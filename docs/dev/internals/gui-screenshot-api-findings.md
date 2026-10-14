# GUI 截图 API 核查事实

> 范围：Minecraft 1.21.1、Fabric API 0.109.0+1.21.1 与 NeoForge 21.1.218 中，Screen 的无世界渲染、主 RenderTarget 截图和写盘完成时序，以及本项目的构建配置和 Screen 依赖。本文记录静态核查事实；工具实现与运行参数见 [GUI 截图工具](gui-screenshot-tool.md)，客户端截图效果需运行验收。

## 客户端加载与无世界 Screen 渲染

- `Minecraft` 构造期间会先设置 `GenericMessageScreen`，随后创建并设置 `LoadingOverlay`。资源加载完成时，`onResourceLoadFinished` 将 `gameLoadFinished` 设为 `true`，再进入初始页面流程。
- `GameRenderer.render` 以 `Minecraft.isGameLoadFinished()` 作为 GUI Screen 分支的前置条件。当 `Minecraft.overlay` 非空时，它渲染 Overlay；只有 Overlay 为空且 `Minecraft.screen` 非空时，才调用 `Screen.renderWithTooltip`。
- 上述 `Screen.renderWithTooltip` 分支没有 `Minecraft.level != null` 条件。`GameRenderer.render` 中与 `ClientLevel` 有关的世界与 HUD 渲染位于另一个同时要求 `renderLevel` 和 `level != null` 的分支。
- `LoadingOverlay.render` 在资源重载完成后的淡出阶段才会调用 `Minecraft.setOverlay(null)`；在此之前，`GameRenderer.render` 不会走常规的 `Screen.renderWithTooltip` 分支。

事实来源：原版源码包 `common/build/moddev/artifacts/vanilla-1.21.1-20240808.144430-minecraft-sources.jar` 中的 `net.minecraft.client.Minecraft`、`net.minecraft.client.renderer.GameRenderer` 与 `net.minecraft.client.gui.screens.LoadingOverlay`。

## 主 RenderTarget 与 PNG 写盘

- `Screenshot.takeScreenshot(RenderTarget)` 按 RenderTarget 的像素尺寸创建 `NativeImage`，绑定 `RenderTarget.getColorTextureId()`，调用 `NativeImage.downloadTexture(0, true)`，再调用 `NativeImage.flipY()`。
- `Screenshot.grab(File, String, RenderTarget, Consumer<Component>)` 在非渲染线程会通过 `RenderSystem.recordRenderCall` 延迟到渲染线程执行；在渲染线程则直接读取 RenderTarget。
- `Screenshot.grab` 读取完成后把 `NativeImage.writeToFile(File)` 提交给 `Util.ioPool()`。PNG 写入成功后才调用传入的 `Consumer<Component>`；发生异常时调用失败消息，随后关闭 `NativeImage`。
- `NativeImage.writeToFile(Path)` 在调用线程内打开目标文件的 `WritableByteChannel` 并写入 PNG。因此 `Screenshot.grab` 的消息消费者由其 `Util.ioPool()` 任务调用。
- `Minecraft.stop()` 仅将客户端的 `running` 字段设为 `false`；`Screenshot.grab` 的实现本身不调用该方法。

事实来源：原版源码包中的 `net.minecraft.client.Screenshot`、`com.mojang.blaze3d.platform.NativeImage` 与 `net.minecraft.client.Minecraft`。

## 原版 GUI 刷新位置

`Minecraft.runTick` 将主 RenderTarget 绑定为写入目标，调用 `GameRenderer.render`，然后解除写入绑定并调用 `RenderTarget.blitToScreen`。在 `GameRenderer.render` 内，常规 Screen 的 `renderWithTooltip` 调用发生在 `GuiGraphics.flush()` 之前；同一轮中 Toast 渲染也发生在该 `flush()` 之前。

事实来源：原版源码包中的 `net.minecraft.client.Minecraft` 与 `net.minecraft.client.renderer.GameRenderer`。

## Fabric Screen 事件

- Fabric API 提供按 Screen 实例注册的 `ScreenEvents.afterRender(Screen)`，回调类型为 `ScreenEvents.AfterRender`。
- `fabric-screen-api-v1` 的 `GameRendererMixin` 用 `WrapOperation` 包裹 `GameRenderer.render` 对 `Screen.renderWithTooltip` 的调用，顺序为 `beforeRender`、`renderWithTooltip`、`afterRender`。
- 该 `afterRender` 调用发生在原版 `GameRenderer.render` 的 `GuiGraphics.flush()` 之前。

事实来源：`.gradle/loom-cache/remapped_mods/loom_mappings_1_21_1_layered_hash_730628366_v2/net/fabricmc/fabric-api/fabric-screen-api-v1/2.0.25+8b68f1c719/fabric-screen-api-v1-2.0.25+8b68f1c719-sources.jar` 中的 `net.fabricmc.fabric.api.client.screen.v1.ScreenEvents` 与 `net.fabricmc.fabric.mixin.screen.GameRendererMixin`；原版 `GameRenderer` 源码包见上文。

## NeoForge 帧事件

- NeoForge 提供 `RenderFrameEvent.Post`，其 API 文档定义为每帧在 `GameRenderer.render` 之后触发，并发布到 `NeoForge.EVENT_BUS`。
- NeoForge 合并后的 `Minecraft.runTick` 在 `noRender` 为 `false` 时，依次调用 `ClientHooks.fireRenderFramePre`、`GameRenderer.render` 与 `ClientHooks.fireRenderFramePost`。`fireRenderFramePost` 创建并发布 `RenderFrameEvent.Post`。
- 此事件位于 `GameRenderer.render` 返回之后、`Minecraft.runTick` 的 fps pie 绘制和主 RenderTarget `blitToScreen` 之前。
- NeoForge 的 `ScreenEvent.Render.Post` 文档定义为 Screen 绘制后触发；它描述 Screen 层，而不是完整 `GameRenderer.render` 调用。

事实来源：`C:/Users/Geng/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.218/e6bdfa7148ee6a1f17e9b00938e9028f449cd594/neoforge-21.1.218-sources.jar` 中的 `net.neoforged.neoforge.client.event.RenderFrameEvent` 与 `ScreenEvent`；`neoforge/build/moddev/artifacts/neoforge-21.1.218-minecraft-merged.jar` 中 `net.minecraft.client.Minecraft`、`net.neoforged.neoforge.client.ClientHooks` 的反编译字节码。

## 窗口与 Java headless 模式

- 原版 `net.minecraft.client.main.Main` 的静态初始化已将 `java.awt.headless` 设为 `true`。客户端的图形窗口仍由 GLFW 创建，这个 AWT 属性没有使客户端跳过图形初始化。
- 原版 `Window` 构造调用 `glfwCreateWindow`、`glfwMakeContextCurrent` 和 `GL.createCapabilities`，建立真实 OpenGL 上下文。
- NeoForge 合并后的 `Window` 通过 `ImmediateWindowHandler.setupMinecraftWindow` 获取窗口。FML 的 earlydisplay `DisplayWindow` 创建加载窗口后会调用 `glfwShowWindow`。
- FML `ImmediateWindowHandler.load` 在 `earlyWindowControl` 为 `false` 时选择 `DummyProvider`。这只关闭 early window provider；Minecraft 的窗口与 OpenGL 初始化仍然存在。
- LWJGL 的 `GLFW.glfwHideWindow` 隐藏已有的窗口，不销毁窗口；它只允许在主线程调用，对全屏窗口不生效。`glfwIconifyWindow` 是另一个用于最小化的 API。
- 原版 `Minecraft.runTick` 仅在 `noRender` 为 `false` 时调用 `GameRenderer.render`。`setWindowActive` 更新焦点状态，没有在该方法中设置 `noRender`。

事实来源：原版源码包中的 `Main`、`Window`、`Minecraft`；NeoForge 合并后的 `Window`；本地 Gradle 缓存中 FML `4.0.42` 的 loader 和 earlydisplay 源码包（`ImmediateWindowHandler`、`FMLConfig`、`DisplayWindow`）及 LWJGL `3.3.3` 的 glfw 源码包（`GLFW`）。上述内容为源码核查，未验证本机隐藏窗口渲染、锁屏或远程桌面断开后的行为。

## 构建配置与 Source Set 接口

- 根构建使用 Fabric Loom `1.8-SNAPSHOT` 和 ModDevGradle `2.0.49-beta`。NeoForge 声明独立的 `guiScreenshot` Source Set；common 与 Fabric 没有该开发 Source Set。
- `multiloader-loader` 将 `common/src/main` 的 Java 源码和资源引入平台的 `compileJava` 与 `processResources`；公共代码在两个平台分别编译，而不是直接以 `common` 的运行产物替代平台代码。
- Loom 的 `RunConfigSettings.source(SourceSet)` 可指定 run 的 Source Set；`AbstractRunTask` 使用该 Source Set 的 `runtimeClasspath`。`ModSettings.sourceSet` 另外管理 mod 的 classpath 分组。
- ModDevGradle 的 `NeoForgeExtension.addModdingDependenciesTo(SourceSet)` 为额外 Source Set 添加 Minecraft 开发依赖；`RunModel.getSourceSet` 指定运行 classpath，`getLoadedMods` 指定所加载的 mod。`ModDevPlugin` 据此配置启动 classpath 和 mod 目录。
- `RunModel` 默认使用 `main` Source Set，并默认加载项目登记的全部 mod。Source Set 选择与 mod 加载范围是两个独立配置。
- Fabric 与 NeoForge 的 `src/main/resources` 元数据只声明正式模组。NeoForge 的 `src/guiScreenshot/resources/META-INF/neoforge.mods.toml` 单独声明开发 mod `idtw_gui_screenshot`，由截图 run 加载。

事实来源：[根构建配置](../../../build.gradle)、[公共代码引入约定](../../../buildSrc/src/main/groovy/multiloader-loader.gradle)、[Fabric 构建配置](../../../fabric/build.gradle)、[NeoForge 构建配置](../../../neoforge/build.gradle)；本地 Gradle 缓存中上述版本插件的 `sources.jar`，对应成员见各条说明。

## 现有 Screen 的状态与世界依赖

- `UiPrototypeScreen` 使用无参构造，初始化控件和示例内容；其源码没有直接引用客户端世界、玩家或网络连接。
- `RuleEditorScreen` 的构造参数是 `EditorWorkspaceView`，该接口导出工作区状态、快照、目录、目录请求与刷新、保存及关闭等操作。正式开屏入口 `RuleEditorOpener.open` 注入的是 `LiveEditorWorkspace.instance()`。
- `RuleEditorScreen` 初始模式为管理列表；打开规则编辑页的方法 `openEditor` 是私有方法。构造 Screen 与选择编辑对象、分页是不同操作。
- `RuleEditorModel` 构造时恢复磁盘草稿，`RuleEditorScreen.tick` 调用草稿持久化逻辑，`onClose` 调用即时持久化。`DraftStore` 默认目录为相对进程工作目录的 `config/itemdespawntowhat/client/editor-drafts`。
- `EntityPreviewIcons.create` 在 `Minecraft.level == null` 时将预览标记为不可用，绘制时使用中性占位标记；实体创建使用 `EntityType.create(level)`。
- `FieldCatalogChoices.tags` 对 BIOME 使用客户端世界的注册表访问器；无世界时返回空集合。其他类型的标签选择以及 `TagPreviewIcons` 的成员展开直接读取注册表标签，而非仅依赖工作区目录 DTO。
- 当前 UI 中存在按 `Util.getMillis()` 变化的光标闪烁、图标轮播、模型旋转和提示存活时间；固定 fixture 内容本身不能固定这些动态画面。

事实来源：`common/src/main/java/com/meteorite/itemdespawntowhat/client/` 下的 `ui/prototype/UiPrototypeScreen`、`ui/screen/RuleEditorScreen`、`RuleEditorModel`、`RuleEditorOpener`、`edit/EditorWorkspaceView`、`edit/draft/DraftStore`、`ui/shared/EntityPreviewIcons`、`FieldCatalogChoices`、`TagPreviewIcons`，以及 `ui/widget/UiTextArea`。
