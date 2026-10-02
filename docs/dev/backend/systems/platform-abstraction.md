# 横向系统：平台抽象与依赖倒置

> 事实来源：`platform/Services.java`、`platform/services/IPlatformHelper.java`、`core/command/RuleCommandContext.java`、`core/network/transport/{RuleEditServerContext, RuleEditPayloadRouter}.java`、`core/network/transport/OpenRuleEditorPayload.java`、两端 `runtime/RuleRuntimeHost.java`。
> 具体平台类见 [platform.md](../modules/platform.md)。

## 1. 三条隔离铁律

1. **平台隔离**：`common/` 不 import 任何 Fabric/NeoForge 类；平台专属代码只能在 `fabric/`、`neoforge/`。
2. **端隔离**：服务端严格禁止引用客户端类。
3. **依赖倒置**：`core` 不反向依赖平台运行时或 client；平台通过**窄接口**与**静态 sink**把能力注入 core。

## 2. 平台能力：`Services` + `IPlatformHelper`

- `Services.PLATFORM` 是唯一入口：`ServiceLoader.load(IPlatformHelper.class).findFirst()`，失败抛 NPE。
- 服务发现文件：两端 `META-INF/services/com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper`（Fabric → `FabricPlatformHelper`，NeoForge → `NeoForgePlatformHelper`）。
- 契约：`getPlatformName` / `isModLoaded` / `isDevelopmentEnvironment` / `getConfigDir` / `getEnvironmentName()` / `sendToPlayer(player, payload)`。

用途举例：`DebugMode.ENABLED` 依赖 `isDevelopmentEnvironment()`；`RuleRuntimeHost` 用 `getConfigDir()` 定位覆盖层与 `server.json`；编辑层用 `sendToPlayer` 发包。

## 3. 窄接口：core 定义，平台实现

core 侧只声明它需要的最小能力，具体状态由平台 `RuleRuntimeHost` 的匿名实现提供（字段都是 volatile 静态状态，调用时实时读取）：

| 窄接口 | 需求方 | 提供方 | 暴露的能力 |
|---|---|---|---|
| `RuleCommandContext` | `core/command` | `RuleRuntimeHost.COMMAND_CONTEXT` | `runtime` / `serverConfig` / `editContext` / `overlayNamespace` / `overlayVersion` / `activeSessionCount` / `reloadRules` |
| `RuleEditServerContext` | `core/network` | `RuleRuntimeHost.EDIT_CONTEXT` | `overlayRoot` / `overlayNamespace` / `typeRegistries` / `loadMerged` / `rebuildAndRescan` / `sendTo` |

好处：`core/command` 与 `core/network` 不必依赖平台运行时类；平台侧也无需把运行时内部状态提升为公共 API。窄接口所有成员都可能返回 null（服务端启动完成前命令树尚未就绪）。

## 4. 静态 sink：core 不反向依赖 client

S2C 的"打开编辑界面 / 收到快照 / 收到回执"需要触达客户端，但 core 不能引用 client。做法：core 暴露静态 sink，由客户端安装回调，服务端不安装即空操作。

| sink | 安装方 | 分发 |
|---|---|---|
| `OpenRuleEditorPayload.installOpenEditorSink(Runnable)` | 客户端 | `dispatchOpenEditor()` |
| `RuleEditPayloadRouter.installSnapshotSink / installResultSink` | 客户端门面 | `dispatchSnapshot` / `dispatchResult` |

平台接收器**只调 router**；专用服务端未安装 sink 时静默丢弃。

## 5. 事件即调用时机

平台 `RuleRuntimeEvents` 只做"事件 → `RuleRuntimeHost` 转发"，业务在 core。**两端事件与 `RuleRuntimeHost` 方法的一一映射表见 [platform.md](../modules/platform.md) §3**；这里只强调：`core` 不知道任何加载器事件，它的全部入口是 `RuleRuntimeHost` 的静态方法。

## 6. 扩展点与踩坑

- 新增平台能力 → 扩 `IPlatformHelper` + 两端 helper。
- 新增"core 需要平台状态"的能力 → 优先扩窄接口，而不是让 core import 平台类。
- 新增"服务端主动触达客户端" → 用静态 sink 模式，不由 core 引用 client。
- 踩坑：窄接口返回值可能为 null；`Services` 装配失败是硬 NPE（服务发现文件必须随 jar 打包）。

## 7. 相关

- 平台接入类与两端差异：[../modules/platform.md](../modules/platform.md)
- 编辑协议：[../modules/edit-protocol.md](../modules/edit-protocol.md)
- 命令：[../modules/command.md](../modules/command.md)
