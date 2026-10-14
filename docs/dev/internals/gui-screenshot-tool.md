# NeoForge GUI 截图工具

> 事实来源：`neoforge/build.gradle`、`neoforge/src/guiScreenshot/`。

## 1. 边界

GUI 截图工具只属于 NeoForge 的 `guiScreenshot` Source Set，使用独立 mod id `idtw_gui_screenshot` 和 `runs/gui-screenshot` 工作目录。工具的类、资源和开发 mod 元数据不会从 `sourceSets.main` 进入正式 NeoForge mod；`verifyGuiScreenshotIsolation` 会检查正式 `jar`、`sourcesJar`、普通 run 的加载范围以及主运行时 classpath。

截图 run 仍启动完整 Minecraft 客户端和 OpenGL 窗口，但不创建世界、不连接服务器。`hidden=true` 时，在窗口可用的首次帧回调中通过 GLFW 隐藏窗口；OpenGL 上下文和渲染循环继续运行。启动过程中是否完全不闪现窗口不属于工具契约。

## 2. 启动参数

使用 `:neoforge:runGuiScreenshot` 启动。`guiScreen`、`guiOutput`、`guiHidden`、`guiFrames` 和 `guiTimeoutSeconds` 转换为 JVM system property；尺寸作为客户端启动参数，语言和 GUI scale 写入专用 run 的 `options.txt`：

| Gradle 属性 | 默认值 | 作用 |
|---|---:|---|
| `guiScreen` | `prototype` | Screen id：`prototype` 或 `editor-list` |
| `guiOutput` | `build/gui-screenshots` | PNG 与结果 JSON 目录，相对路径以 `neoforge/` 为基准 |
| `guiHidden` | `false` | 窗口可用后隐藏 GLFW 窗口 |
| `guiFrames` | `5` | 目标 Screen 完整渲染后的预热帧数 |
| `guiTimeoutSeconds` | `120` | 从工具初始化起计时，取值 5–600 秒 |
| `guiWidth` / `guiHeight` | `1280` / `720` | 客户端启动窗口尺寸 |
| `guiLanguage` / `guiScale` | `zh_cn` / `2` | 专用工作目录的语言和 GUI scale |

示例：

```powershell
powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks ":neoforge:runGuiScreenshot -PguiScreen=prototype -PguiHidden=true"
```

成功产出 `<screen>.png` 和 `<screen>.result.json`；结果 JSON 的 `status` 为 `success`，并记录实际像素尺寸与渲染帧数。失败或超时以非零进程状态结束，并写入失败结果（能够创建结果文件时）。

## 3. Screen 注册与 fixture

`ScreenshotScreens` 是工具内的 Screen id 注册表：

- `prototype` 构造现有 `UiPrototypeScreen`，用于验证 UI kit 和基础渲染链路。
- `editor-list` 构造 `RuleEditorScreen`，注入 `ScreenshotWorkspace`。

`ScreenshotWorkspace` 实现 `EditorWorkspaceView`，提供固定规则快照、有限选择目录、分页查询和活动会话状态。它不发送网络包，也不写入覆盖层；保存请求始终拒绝。正式编辑器仍执行本地草稿逻辑，专用 run 每次启动前只清理自己的草稿目录，不读取或删除普通客户端的草稿。世界相关实体、方块和 biome 数据缺失时沿用正式 UI 的占位或空目录行为。

## 4. 截图时序

`ScreenshotRunner` 等待资源加载遮罩消失后设置目标 Screen。它先通过 NeoForge `ScreenEvent.Render.Post` 确认目标 Screen 实际绘制，再在 `RenderFrameEvent.Post` 中累计预热帧。该事件发生在 `GameRenderer.render` 返回后，GUI batch 已 flush，读取主 `RenderTarget` 不会截取半完成的 Screen 绘制。

截图使用 `Screenshot.takeScreenshot` 获取 `NativeImage`，在 IO 线程写入临时 PNG，写盘成功后原子移动到目标路径，再写出结果 JSON，最后调用 `Minecraft.stop()`。超时或异常不会复用旧图片。

## 5. 当前限制

- 该工具验证的是 Screen 渲染，不模拟服务端同步；依赖世界、玩家或服务端目录的内容可能为空或使用占位图。
- 首期没有为编辑器自动切换到指定规则或分页增加命令参数；`editor-list` 截图固定管理列表页。
- 动态光标、图标轮播、实体旋转和提示计时会使像素结果随帧变化；回归比较需要继续固定或屏蔽这些动态内容。
- 超时计时从开发 mod 初始化开始，不覆盖此前的 Gradle 构建和加载器启动；外部自动化仍需为整个进程设置时间上限。
- 客户端运行、隐藏窗口行为和生成图片效果交由用户验收；编译和产物隔离检查不会启动 Minecraft。

## 6. 验证与扩展

```powershell
powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build :neoforge:checkGuiScreenshot"
```

普通 `build` 的 `check` 包含产物隔离检查，`checkGuiScreenshot` 另外编译开发工具，不启动客户端。新增 Screen 时在 `ScreenshotScreens` 注册工厂，状态依赖保留在开发 Source Set；正式模组不得引用工具包。
