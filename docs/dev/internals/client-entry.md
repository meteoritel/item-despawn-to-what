# 入口层：平台客户端接入（fabric/neoforge 客户端源集）

> 层级：入口层；模块路径：`fabric`/`neoforge` 客户端接入；源码目录：`fabric/src/main/java/com/meteorite/itemdespawntowhat/client`、`neoforge/src/main/java/com/meteorite/itemdespawntowhat/client`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 平台客户端接入 | `fabric/…/client`、`neoforge/…/client` | §2 |

## 2. 模块：平台客户端接入

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client`、`com.meteorite.itemdespawntowhat.client.event`、`com.meteorite.itemdespawntowhat.client.network`（Fabric 源集）；`com.meteorite.itemdespawntowhat.client.event`、`com.meteorite.itemdespawntowhat.client.register`（NeoForge 源集）
- 源码目录：`fabric/src/main/java/com/meteorite/itemdespawntowhat/client`、`neoforge/src/main/java/com/meteorite/itemdespawntowhat/client`
- 覆盖：5 个 `.java` 文件（Fabric 3、NeoForge 2；相关包内均无 `package-info.java`）

### 2.2 职责

- Fabric：由 `ClientModInitializer` 入口按固定顺序注册客户端 tick 钩子与 S2C 接收器，并接线编辑工作区与界面实现。
- NeoForge：以 `Dist.CLIENT` 侧的事件订阅者接收客户端 tick，并在 `FMLClientSetupEvent` 回调里完成工作区与界面接线。
- 两个平台都只把客户端 tick 用于驱动编辑工作区心跳，不提供任何客户端侧的编辑器打开入口。
- Fabric 的 S2C 接收器只做线程切换与分发，不直接依赖工作区门面实现。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `ItemDespawnToWhatClient`（fabric） | class（final，实现 `ClientModInitializer`） | `onInitializeClient()` | Fabric 客户端初始化：依次调用 `InputEvents#register`、`FabricRuleEditClientRegistrar#register`、`installSinks`、`installSender` 与 `RuleEditorOpener#bootstrap` |
| `InputEvents`（fabric） | class | `register()` | 注册 `ClientTickEvents.END_CLIENT_TICK` 回调，驱动工作区心跳 |
| `FabricRuleEditClientRegistrar`（fabric） | class（final，工具类） | `register()` | 注册 7 个 S2C 全局接收器 |
| `InputEvents`（neoforge） | class（`@EventBusSubscriber`，`value = Dist.CLIENT`） | `onClientTick(ClientTickEvent.Post)` | 客户端 tick 心跳 |
| `ClientSetup`（neoforge） | class（`@EventBusSubscriber`，`value = Dist.CLIENT`） | `onClientSetup(FMLClientSetupEvent)` | 客户端启动时接线工作区与界面 |

`FabricRuleEditClientRegistrar#register` 注册的 7 个接收器：`RuleSnapshotPayload`、`RuleSnapshotChunkPayload`、`RuleSaveProgressPayload`、`RuleSaveResultPayload`、`RuleCatalogPayload`、`RuleLifespanPayload`、`OpenRuleEditorPayload`。

### 2.4 依赖关系

以下计数按源码 `import` 语句统计；`client/event` 在 Fabric、NeoForge 两个源集同名同包，包级统计会把两侧合并，`client`、`client/network`、`client/register` 只存在于单一平台源集。

- 依赖本模组：`client/net`（4 个文件 import，共 4 处：`RuleEditClientWorkspace`；Fabric 的 `ItemDespawnToWhatClient` 与 `InputEvents`、NeoForge 的 `InputEvents` 与 `ClientSetup` 各 1 处）
- 模块内部引用：`ItemDespawnToWhatClient` 对 `client/event`、`client/network` 各 1 个 import（1 个文件，共 2 处）
- 依赖本模组：`client/ui/screen`（2 个文件 import，共 2 处：`RuleEditorOpener`；Fabric 初始化类与 NeoForge `ClientSetup` 各 1 处）
- 依赖本模组：`core/network/transport`（1 个文件 import，共 8 处：`FabricRuleEditClientRegistrar` 直接引用 8 个载荷或分发类）
- 依赖外部：`net.fabricmc`（`ClientModInitializer`、`ClientTickEvents`、`ClientPlayNetworking`）、`net.neoforged`（`Dist`、`SubscribeEvent`、`EventBusSubscriber`、`ClientTickEvent`、`FMLClientSetupEvent`、`PacketDistributor`）、`com.meteorite.itemdespawntowhat.ItemDespawnToWhat`（`MOD_ID`，NeoForge 两个类）
- 被依赖：本模块之外没有其它包 import 上述类型；Fabric 入口由 `fabric/src/main/resources/fabric.mod.json` 的 `client` entrypoint 字符串引用，NeoForge 两个类由 `@EventBusSubscriber` 注解注册

### 2.5 关键实现位置

Fabric：

- `ItemDespawnToWhatClient#onInitializeClient`：依次调用 `InputEvents#register`、`FabricRuleEditClientRegistrar#register`、`RuleEditClientWorkspace#installSinks`、`RuleEditClientWorkspace#installSender(ClientPlayNetworking::send)`、`RuleEditorOpener#bootstrap`。
- `InputEvents#register`：在 `ClientTickEvents.END_CLIENT_TICK` 回调里调用 `RuleEditClientWorkspace#instance().tick()`。
- `FabricRuleEditClientRegistrar#register`：每个接收器以 `context.client().execute(...)` 切到客户端线程；6 个接收器分派到 `RuleEditPayloadRouter` 的对应方法，开屏接收器调用 `OpenRuleEditorPayload#dispatchOpenEditor`。

NeoForge：

- `InputEvents#onClientTick`：调用 `RuleEditClientWorkspace#instance().tick()`。
- `ClientSetup#onClientSetup`：用 `event.enqueueWork` 排到客户端主线程，依次调用 `RuleEditClientWorkspace#installSinks`、`RuleEditClientWorkspace#installSender(PacketDistributor::sendToServer)`、`RuleEditorOpener#bootstrap`。

### 2.6 代码现状（可核对）

- `fabric/…/client/event/InputEvents` 与 `neoforge/…/client/event/InputEvents` 同包同名、源码不同；两个文件分属各自平台源集，没有共享实现。
- Fabric 有显式初始化类 `ItemDespawnToWhatClient`，并在 `fabric/src/main/resources/fabric.mod.json` 的 `client` entrypoint 中声明；NeoForge 没有对应的客户端初始化类，工作区与界面接线放在 `ClientSetup#onClientSetup`。
- NeoForge 的 S2C 接收器不在本模块：由 `neoforge/src/main/java/com/meteorite/itemdespawntowhat/network/registrar/RuleEditPayloadRegistrar` 在公共 `RegisterPayloadHandlersEvent` 中注册 `playToClient`，处理器只经 `RuleEditPayloadRouter` 与 `OpenRuleEditorPayload#dispatchOpenEditor` 分发；Fabric 的对应接收器在 `client/network`。
- 客户端没有任何按键绑定：两个平台都不注册 `KeyMapping`，编辑器只能由 `/idtw config edit` 指令经服务端取锁后打开。
- `client`、`client/event`、`client/network`、`client/register` 均无 `package-info.java`。
- 两个 `InputEvents` 与 `ClientSetup` 的类声明都不是 `final`；`ItemDespawnToWhatClient` 与 `FabricRuleEditClientRegistrar` 为 `final`。
- JavaDoc 覆盖不齐：`InputEvents`（neoforge）、`ClientSetup`（neoforge）与 `InputEvents`（fabric）的类与注册方法都有 JavaDoc；`ItemDespawnToWhatClient#onInitializeClient` 无 JavaDoc。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `ItemDespawnToWhatClient`（fabric） | Fabric 客户端初始化与接线 | `onInitializeClient()` |
| `InputEvents`（fabric） | Fabric 客户端 tick 钩子：心跳 | `register()` |
| `FabricRuleEditClientRegistrar`（fabric） | Fabric S2C 接收器注册与线程切换 | `register()` |
| `InputEvents`（neoforge） | NeoForge 客户端 tick 事件 | `onClientTick(ClientTickEvent.Post)` |
| `ClientSetup`（neoforge） | NeoForge 客户端启动接线 | `onClientSetup(FMLClientSetupEvent)` |

## 4. 相关文档

- [客户端前端实现文档索引](README.md)
- [接口层：协议工作区](client-net.md)
- [抽象层：客户端](../layers/client.md)
- [文档约定](../conventions.md)
- [开发者文档索引](../README.md)
