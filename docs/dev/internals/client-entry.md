# 入口层：按键入口与平台客户端接入（client/key、fabric/neoforge 客户端源集）

> 层级：入口层；模块路径：`client/key`（按键入口）、`fabric`/`neoforge` 客户端接入（平台客户端接入）；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/key`、`fabric/src/main/java/com/meteorite/itemdespawntowhat/client`、`neoforge/src/main/java/com/meteorite/itemdespawntowhat/client`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 按键入口 | `client/key` | §2 |
| 平台客户端接入 | `fabric/…/client`、`neoforge/…/client` | §3 |

## 2. 模块：按键入口

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.key`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/key`
- 覆盖：2 个 `.java` 文件（包内无 `package-info.java`）

### 2.2 职责

- 声明客户端按键的类别键、名称键与唯一的 `KeyMapping` 实例。
- 把一次快捷键触发解释为「重开已有会话的界面」，不构造上行载荷，不发起编辑会话。
- 没有可用会话时按运行环境分流：开发环境打开 `client/ui/prototype` 的原型屏，其它环境显示使用指令的提示。
- 为两个平台的按键事件提供同一份快捷键处理逻辑。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `ModKeyBindings` | class | `KEY_CATEGORY`、`KEY_NAME_OPEN_GUI`、`openGuiKey` | 按键类别与名称的翻译键，以及唯一的按键实例 |
| `EditorShortcut` | class（final，工具类） | `HINT_KEY`、`activate()` | 无会话提示的翻译键与快捷键处理入口 |

常量与初始值：

- `ModKeyBindings#KEY_CATEGORY` = `"key.category.itemdespawntowhat.general"`
- `ModKeyBindings#KEY_NAME_OPEN_GUI` = `"key.itemdespawntowhat.open_gui"`
- `EditorShortcut#HINT_KEY` = `"gui.itemdespawntowhat.edit.hint.use_command"`
- `ModKeyBindings#openGuiKey`：按键类型 `InputConstants.Type.KEYSYM`，键值为 `InputConstants.UNKNOWN.getValue()`，类别取 `KEY_CATEGORY`

### 2.4 依赖关系

- 依赖本模组：`client/net`（1 个文件 import，共 5 处：`EditorOpenRequest`、`EditorScreenHooks`、`EditorScreenOpener`、`RuleEditClientState`、`RuleEditClientWorkspace`）
- 依赖本模组：`client/ui/prototype`（1 个文件 import，共 1 处：`UiPrototypeScreen`）
- 依赖本模组：`core/debug`（1 个文件 import，共 1 处：`DebugMode`）
- 依赖外部：`com.mojang.blaze3d.platform`（`InputConstants`）、`net.minecraft.client`（`KeyMapping`、`Minecraft`）、`net.minecraft.client.gui.screens`（`Screen`）、`net.minecraft.network.chat`（`Component`）
- 被依赖：`client/event`（2 个文件 import，共 4 处：`EditorShortcut`、`ModKeyBindings`）
- 被依赖：`client/register`（2 个文件 import，共 2 处：`ModKeyBindings`）

### 2.5 关键实现位置

- `ModKeyBindings#openGuiKey`：唯一的按键实例，名称与类别直接取同类的两个字符串常量，键值取 `InputConstants.UNKNOWN.getValue()`。
- `EditorShortcut#activate`：先检查 `Minecraft#level` 与 `Minecraft#player` 非空。
- `EditorShortcut#activate`：以 `RuleEditClientWorkspace#state` 是否等于 `RuleEditClientState.FREE` 决定 `EditorOpenRequest` 取 `null` 还是取 `RuleEditClientWorkspace#openRequest`。
- `EditorShortcut#activate`：当 `EditorScreenHooks#opener` 与请求都非空时调用 `EditorScreenOpener#open` 并返回，不改变工作区状态。
- `EditorShortcut#prototypeScreen`：`DebugMode.ENABLED` 为真时返回 `new UiPrototypeScreen()`，否则返回 `null`。
- `EditorShortcut#activate` 的兜底：前两个分支都不成立时，以 `Component#translatable(HINT_KEY)` 调用 `Player#displayClientMessage(component, true)`。

### 2.6 代码现状（可核对）

- `client/key` 无 `package-info.java`。
- `EditorShortcut` 在入口层内 import `client/ui/prototype`（`UiPrototypeScreen`）与 `core/debug`（`DebugMode`）；对原型层的引用只出现在 `EditorShortcut#prototypeScreen`。
- `EditorShortcut#activate` 在 `DebugMode.ENABLED` 为真且没有会话时直接 `Minecraft#setScreen(new UiPrototypeScreen())`，该路径不读写 `RuleEditClientWorkspace` 的会话状态；发布环境该分支返回 `null`，走提示分支。
- `ModKeyBindings#openGuiKey` 是 `public static` 可变字段，没有 `final`；同类的两个字符串键为 `public static final`。
- `ModKeyBindings` 的类声明不是 `final`，也没有私有构造；`EditorShortcut` 为 `final`，私有构造抛 `UnsupportedOperationException("Utility class")`。
- 两个类的公开成员都没有 JavaDoc，只有行注释：`ModKeyBindings` 的三个字段无注释，`EditorShortcut#activate` 与 `EditorShortcut#prototypeScreen` 上方为 `//` 注释。
- `EditorShortcut` 的类注释声明快捷键「绝不自行发起编辑会话」；代码中确实没有构造任何上行载荷，开屏只经 `EditorScreenOpener#open` 传入已存在的 `EditorOpenRequest`。

## 3. 模块：平台客户端接入

### 3.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client`、`com.meteorite.itemdespawntowhat.client.event`、`com.meteorite.itemdespawntowhat.client.register`、`com.meteorite.itemdespawntowhat.client.network`（Fabric 源集）；`com.meteorite.itemdespawntowhat.client.event`、`com.meteorite.itemdespawntowhat.client.register`（NeoForge 源集）
- 源码目录：`fabric/src/main/java/com/meteorite/itemdespawntowhat/client`、`neoforge/src/main/java/com/meteorite/itemdespawntowhat/client`
- 覆盖：6 个 `.java` 文件（Fabric 4、NeoForge 2；相关包内均无 `package-info.java`）

### 3.2 职责

- Fabric：由 `ClientModInitializer` 入口按固定顺序注册按键绑定、客户端 tick 钩子与 S2C 接收器，并接线编辑工作区与界面实现。
- NeoForge：以 `Dist.CLIENT` 侧的事件订阅者接收客户端 tick 与按键事件，并在 `client/register` 的静态初始化块完成工作区与界面接线。
- 两个平台都把按键点击交给 `client/key` 的 `EditorShortcut#activate`，不各自实现开屏逻辑。
- Fabric 的 S2C 接收器只做线程切换与分发，不直接依赖工作区门面实现。

### 3.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `ItemDespawnToWhatClient`（fabric） | class（final，实现 `ClientModInitializer`） | `onInitializeClient()` | Fabric 客户端初始化：依次调用三个 `register()`、`installSinks`、`installSender` 与 `RuleEditorOpener#bootstrap` |
| `InputEvents`（fabric） | class | `register()` | 注册 `ClientTickEvents.END_CLIENT_TICK` 回调，驱动心跳并消费按键 |
| `RegisterEvent`（fabric） | class | `register()` | 通过 `KeyBindingHelper#registerKeyBinding` 注册按键 |
| `FabricRuleEditClientRegistrar`（fabric） | class（final，工具类） | `register()` | 注册 7 个 S2C 全局接收器 |
| `InputEvents`（neoforge） | class（`@EventBusSubscriber`，`value = Dist.CLIENT`） | `onClientTick(ClientTickEvent.Post)`、`onKeyInput(InputEvent.Key)` | 客户端 tick 心跳与按键消费 |
| `RegisterEvent`（neoforge） | class（`@EventBusSubscriber`，`value = Dist.CLIENT`） | `onRegisterKeyMappings(RegisterKeyMappingsEvent)` | 注册按键，并在静态初始化块接线工作区与界面 |

`FabricRuleEditClientRegistrar#register` 注册的 7 个接收器：`RuleSnapshotPayload`、`RuleSnapshotChunkPayload`、`RuleSaveProgressPayload`、`RuleSaveResultPayload`、`RuleCatalogPayload`、`RuleLifespanPayload`、`OpenRuleEditorPayload`。

### 3.4 依赖关系

以下计数按源码 `import` 语句统计；`client/event` 与 `client/register` 在 Fabric、NeoForge 两个源集同名同包，包级统计会把两侧合并，`client` 与 `client/network` 只存在于 Fabric 源集。

- 依赖本模组：`client/key`（2 个文件 import，共 4 处：`EditorShortcut`、`ModKeyBindings`；两个平台各 1 个 `InputEvents`）
- 依赖本模组：`client/net`（4 个文件 import，共 4 处：`RuleEditClientWorkspace`；Fabric 的 `ItemDespawnToWhatClient` 与 `client/event`、NeoForge 的 `client/event` 与 `client/register` 各 1 处）
- 模块内部引用：`ItemDespawnToWhatClient` 对 `client/event`、`client/register`、`client/network` 各 1 个 import（1 个文件，共 3 处）
- 依赖本模组：`client/ui/screen`（2 个文件 import，共 2 处：`RuleEditorOpener`；Fabric 初始化类与 NeoForge `RegisterEvent` 各 1 处）
- 依赖本模组：`core/network/transport`（1 个文件 import，共 8 处：`FabricRuleEditClientRegistrar` 直接引用 8 个载荷或分发类）
- 依赖外部：`net.fabricmc`（`ClientModInitializer`、`ClientTickEvents`、`KeyBindingHelper`、`ClientPlayNetworking`）、`net.neoforged`（`Dist`、`SubscribeEvent`、`EventBusSubscriber`、`ClientTickEvent`、`InputEvent`、`RegisterKeyMappingsEvent`、`PacketDistributor`）、`com.meteorite.itemdespawntowhat.ItemDespawnToWhat`（`MOD_ID`，NeoForge 两个类）
- 被依赖：本模块之外没有其它包 import 上述类型；Fabric 入口由 `fabric/src/main/resources/fabric.mod.json` 的 `client` entrypoint 字符串引用，NeoForge 两个类由 `@EventBusSubscriber` 注解注册

### 3.5 关键实现位置

Fabric：

- `ItemDespawnToWhatClient#onInitializeClient`：依次调用 `RegisterEvent#register`、`InputEvents#register`、`FabricRuleEditClientRegistrar#register`、`RuleEditClientWorkspace#installSinks`、`RuleEditClientWorkspace#installSender(ClientPlayNetworking::send)`、`RuleEditorOpener#bootstrap`。
- `InputEvents#register`：在 `ClientTickEvents.END_CLIENT_TICK` 回调里先调用 `RuleEditClientWorkspace#instance().tick()`，再用 `while (ModKeyBindings#openGuiKey.consumeClick())` 循环调用 `EditorShortcut#activate`。
- `RegisterEvent#register`：只注册 `ModKeyBindings#openGuiKey`。
- `FabricRuleEditClientRegistrar#register`：每个接收器以 `context.client().execute(...)` 切到客户端线程；6 个接收器分派到 `RuleEditPayloadRouter` 的对应方法，开屏接收器调用 `OpenRuleEditorPayload#dispatchOpenEditor`。

NeoForge：

- `InputEvents#onClientTick`：调用 `RuleEditClientWorkspace#instance().tick()`。
- `InputEvents#onKeyInput`：用 `if (!ModKeyBindings#openGuiKey.consumeClick()) return;` 做单次消费，再调用 `EditorShortcut#activate`。
- `RegisterEvent` 的静态初始化块：依次调用 `RuleEditClientWorkspace#installSinks`、`RuleEditClientWorkspace#installSender(PacketDistributor::sendToServer)`、`RuleEditorOpener#bootstrap`。
- `RegisterEvent#onRegisterKeyMappings`：调用 `RegisterKeyMappingsEvent#register(ModKeyBindings#openGuiKey)`。

### 3.6 代码现状（可核对）

- `fabric/…/client/event/InputEvents` 与 `neoforge/…/client/event/InputEvents` 同包同名、源码不同；`fabric/…/client/register/RegisterEvent` 与 `neoforge/…/client/register/RegisterEvent` 同样同包同名、源码不同。四个文件分属各自平台源集，没有共享实现。
- 按键消费方式不对称：Fabric 用 `while` 循环消费排队中的多次点击，NeoForge 每次 `InputEvent.Key` 只消费一次。
- Fabric 有显式初始化类 `ItemDespawnToWhatClient`，并在 `fabric/src/main/resources/fabric.mod.json` 的 `client` entrypoint 中声明；NeoForge 声明范围内没有对应的客户端初始化类，工作区与界面接线放在 `RegisterEvent` 的静态初始化块。
- NeoForge 的 S2C 接收器不在本模块：由 `neoforge/src/main/java/com/meteorite/itemdespawntowhat/network/registrar/RuleEditPayloadRegistrar` 在公共 `RegisterPayloadHandlersEvent` 中注册 `playToClient`，处理器只经 `RuleEditPayloadRouter` 与 `OpenRuleEditorPayload#dispatchOpenEditor` 分发；Fabric 的对应接收器在 `client/network`。
- NeoForge 的 `RegisterEvent` 同时承担按键注册与工作区、界面接线；Fabric 把接线放在 `ItemDespawnToWhatClient`，`RegisterEvent` 只注册按键。
- `client`、`client/event`、`client/register`、`client/network` 均无 `package-info.java`。
- 两个 `InputEvents` 与两个 `RegisterEvent` 的类声明都不是 `final`；`ItemDespawnToWhatClient` 与 `FabricRuleEditClientRegistrar` 为 `final`。
- JavaDoc 覆盖不齐：`InputEvents`（neoforge）的类与两个 `@SubscribeEvent` 方法都有 JavaDoc；`RegisterEvent`（neoforge）只有类 JavaDoc，`onRegisterKeyMappings` 上方是 `//` 注释；`RegisterEvent`（fabric）只有类 JavaDoc，`register()` 无 JavaDoc；`InputEvents`（fabric）的 `register()` 有 JavaDoc，而 `ItemDespawnToWhatClient#onInitializeClient` 无 JavaDoc。

## 4. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `ModKeyBindings` | 客户端按键定义：类别键、名称键与按键实例 | `KEY_CATEGORY`、`KEY_NAME_OPEN_GUI`、`openGuiKey` |
| `EditorShortcut` | 快捷键共用处理：重开已有会话的界面，无会话时提示或打开开发环境原型屏 | `HINT_KEY`、`activate()`、`prototypeScreen()` |
| `ItemDespawnToWhatClient`（fabric） | Fabric 客户端初始化与接线 | `onInitializeClient()` |
| `InputEvents`（fabric） | Fabric 客户端 tick 钩子：心跳与按键消费 | `register()` |
| `RegisterEvent`（fabric） | Fabric 按键绑定注册 | `register()` |
| `FabricRuleEditClientRegistrar`（fabric） | Fabric S2C 接收器注册与线程切换 | `register()` |
| `InputEvents`（neoforge） | NeoForge 客户端 tick 与按键事件 | `onClientTick(ClientTickEvent.Post)`、`onKeyInput(InputEvent.Key)` |
| `RegisterEvent`（neoforge） | NeoForge 按键注册与客户端接线 | `onRegisterKeyMappings(RegisterKeyMappingsEvent)`、静态初始化块 |

## 5. 相关文档

- [客户端前端实现文档索引](README.md)
- [接口层：协议工作区](client-net.md)
- [抽象层：客户端](../layers/client.md)
- [文档约定](../conventions.md)
- [开发者文档索引](../README.md)
