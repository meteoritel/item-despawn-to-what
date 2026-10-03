# 横向系统：平台抽象与依赖倒置

> 事实来源：`platform/Services.java`、`platform/services/IPlatformHelper.java`、`core/command/RuleCommandContext.java`、`core/network/transport/{RuleEditServerContext, RuleEditPayloadRouter, OpenRuleEditorPayload}.java`、`core/runtime/LifespanProvider.java`、`core/state/DropStateStore.java`、两端 `runtime/RuleRuntimeHost.java`。
> 具体平台类与两端差异见 [platform.md](../modules/platform.md)。

## 1. 三条隔离铁律

1. **平台隔离**：`common/` 不 import 任何 Fabric/NeoForge 类；平台专属代码只能在 `fabric/`、`neoforge/`。
2. **端隔离**：服务端严格禁止引用客户端类。
3. **依赖倒置**：`core` 不反向依赖平台运行时或 client；平台通过**窄接口**、**静态 sink** 与**注入点**把能力注入 core。

## 2. 平台能力：`Services` + `IPlatformHelper`

- `Services.PLATFORM` 是唯一入口：`ServiceLoader.load(IPlatformHelper.class).findFirst()`，失败抛 NPE（服务发现文件必须随 jar 打包）。
- 服务发现文件：两端 `META-INF/services/com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper`（Fabric → `FabricPlatformHelper`，NeoForge → `NeoForgePlatformHelper`）。
- 契约：`getPlatformName` / `isModLoaded` / `isDevelopmentEnvironment` / `getConfigDir` / `getEnvironmentName()`（默认派生）/ `sendToPlayer(player, payload)`（默认抛异常）/ `getDropState(entity)` / `setDropState(entity, state)`（默认无状态 / 空操作）。

用途举例：`DebugMode.ENABLED` 依赖 `isDevelopmentEnvironment()`；`RuleRuntimeHost` 用 `getConfigDir()` 定位覆盖层与 `server.json`；编辑层用 `sendToPlayer` 发包；实体层掉落物状态经 `getDropState` / `setDropState` 持久化。

**实体状态持久化**由平台差异承载，core 只经 `DropStateStore` 门面访问：NeoForge 走 `Entity#getPersistentData`（`CompoundTag`），Fabric 走持久化 Data Attachment。未实现平台静默降级（读 `NONE`、写空操作），**状态读写位于伤害判定等热路径，绝不抛异常**。

## 3. 窄接口：core 定义，平台实现

core 侧只声明它需要的最小能力，具体状态由平台 `RuleRuntimeHost` 的匿名实现提供（字段都是 volatile 静态状态，调用时实时读取）：

| 窄接口 | 需求方 | 提供方 | 暴露的能力 |
|---|---|---|---|
| `RuleCommandContext` | `core/command` | `RuleRuntimeHost.COMMAND_CONTEXT` | `runtime` / `serverConfig` / `editContext` / `overlayNamespace` / `overlayVersion` / `activeSessionCount` / `reloadRules` |
| `RuleEditServerContext` | `core/network` | `RuleRuntimeHost.EDIT_CONTEXT` | `overlayRoot` / `overlayNamespace` / `typeRegistries` / `loadMerged` / `rebuildAndRescan` / `sendTo` |

好处：`core/command` 与 `core/network` 不必依赖平台运行时类；平台侧也无需把运行时内部状态提升为公共 API。窄接口所有成员都可能返回 null（服务端启动完成前命令树尚未就绪）。

## 4. 静态 sink：core 不反向依赖 client

S2C 的「打开编辑界面 / 收到快照 / 收到回执 / 收到目录」需要触达客户端，但 core 不能引用 client。做法：core 暴露静态 sink，由客户端安装回调，服务端不安装即空操作。

| sink | 安装方 | 分发 |
|---|---|---|
| `OpenRuleEditorPayload.installOpenEditorPayloadSink(Consumer<OpenRuleEditorPayload>)` | 客户端（`client/net/RuleEditClientWorkspace`） | `dispatchOpenEditor(payload)` |
| `RuleEditPayloadRouter.installSnapshotPayloadSink` / `installChunkSink` / `installResultPayloadSink` / `installCatalogSink` | 客户端（`client/net/RuleEditClientWorkspace`） | `dispatchSnapshot` / `dispatchChunk` / `dispatchResult` / `dispatchCatalog` |

平台接收器**只调 router**；专用服务端未安装 sink 时静默丢弃。（router 另有 `installSnapshotSink(String)` / `installResultSink(String)` 两个文本重载，供不解析 payload 的客户端使用。）

## 5. 事件即调用时机

平台 `RuleRuntimeEvents` 只做「事件 → `RuleRuntimeHost`」转发，业务在 core。**core 不知道任何加载器事件，它的全部入口是 `RuleRuntimeHost` 的静态方法**；两端事件与方法的一一映射表见 [platform.md](../modules/platform.md) §3。这里只强调本系统新增 / 关键的几类注入点：

- **唯一服务器 tick 推进入口**：`RuleRuntimeHost.tickServer(server)`（Fabric `END_SERVER_TICK` / NeoForge `ServerTickEvent.Post`）推进**唯一一份**公共预算；**不再按维度推进**（旧实现每维度各 `tickLevel`，会让同一份总预算被重复推进）。所有维度共享一次推进（见 [scheduling-budget.md](scheduling-budget.md)）。
- **寿命提供者注入**：`LifespanProvider` 是 core 的平台差异收敛点；平台在 `start` 建 `ConversionRuntime` 时注入——Fabric 返回 `server.json` 的兜底刻数，NeoForge 返回 `entity.lifespan`（`ItemStack#getEntityLifespan`，保留其它模组的修改）。core 只依赖接口。
- **环境销毁与保护入口**：NeoForge 用原生事件——`ItemExpireEvent`（自然消失，配 `addExtraLife(1)` / `setExtendedLifetime()`）与 `EntityInvulnerabilityCheckEvent`（三类环境伤害保护，经 `DropStateStore.blocksEnvironmentalDamage`）。Fabric 无等价事件，由 `ItemEntityMixin` 补齐：自然到期 `discard()` 拦截（`@Redirect`）、`hurt` 头部环境伤害保护（`@Inject HEAD`，返回 `false` 即免疫）、真实致死分支转发 `requestEnvironmentalConversion`、合并兼容（`tryToMerge` / `merge`）。NeoForge 端仍保留一处 Mixin，但只补原生事件覆盖不到的真实致死 `ItemStack#onDestroyed` 转发与合并兼容。
- **实体移除**：两端分别经 `ENTITY_UNLOAD` / `EntityLeaveLevelEvent` 作纯入口转发。

事件映射表的**权威版本**在 modules 层（[platform.md](../modules/platform.md) §3），此处不复制。

## 6. 扩展点与踩坑

- 新增平台能力 → 扩 `IPlatformHelper` + 两端 helper。
- 新增「core 需要平台状态」的能力 → 优先扩窄接口或注入点（如 `LifespanProvider`），而不是让 core import 平台类。
- 新增「服务端主动触达客户端」→ 用静态 sink 模式，不由 core 引用 client。
- 必须新增 Mixin → 优先找已有 Event API；确无等价事件时才写，且**仅作入口**，业务放 core（如 `ItemEntityMixin` 只调 `deferNaturalExpiry` / 状态门面）。
- 踩坑：窄接口返回值可能为 null；`Services` 装配失败是硬 NPE；`tickServer` 是唯一推进入口，勿在维度事件里再补一次推进；实体状态读写热路径必须静默降级、不抛异常。

## 7. 相关

- 平台接入类与两端差异（含事件映射表）：[../modules/platform.md](../modules/platform.md)
- 编辑协议：[../modules/edit-protocol.md](../modules/edit-protocol.md)
- 命令：[../modules/command.md](../modules/command.md)
- 调度推进入口与预算：[scheduling-budget.md](scheduling-budget.md)
