# 功能模块：平台接入与生命周期（`core/config` + `platform` + `fabric` + `neoforge`）

> 事实来源：`Constants.java`、`core/config/ServerConfig.java`、`platform/**`、`core/state/**`、`core/runtime/{LifespanProvider, PlayerDeathDrops}.java`、Fabric 与 NeoForge 两端的 `runtime/`、`network/`、`client/`、`mixin/`、主类与 `resources/`。
> 平台层**只做入口**：把加载器事件转发给 `core`，不含业务逻辑。

## 1. 平台隔离层

| 类 | 职责 |
|---|---|
| `Services` | `ServiceLoader.load(IPlatformHelper.class).findFirst()` 静态装配；静态常量 `PLATFORM` |
| `IPlatformHelper` | 两端共用的平台能力契约：`getPlatformName()`、`isModLoaded`、`isDevelopmentEnvironment`、`getConfigDir`、`getEnvironmentName()`、`sendToPlayer(player, payload)`、`getDropState(entity)` / `setDropState(entity, state)`（`getEnvironmentName` 与两个 `DropState` 方法为 default 实现） |

服务发现文件：`META-INF/services/com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper`（Fabric 内容 `FabricPlatformHelper`，NeoForge 内容 `NeoForgePlatformHelper`）。

| 平台 | `getPlatformName` / `isModLoaded` / `isDevelopmentEnvironment` / `getConfigDir` / `sendToPlayer` 实现 |
|---|---|
| Fabric | `FabricLoader` 系列 + `ServerPlayNetworking.send` |
| NeoForge | `ModList.get().isLoaded` / `!FMLLoader.isProduction()` / `FMLPaths.CONFIGDIR.get()` / `PacketDistributor.sendToPlayer` |

机制与铁律见 [../systems/platform-abstraction.md](../systems/platform-abstraction.md)。

## 2. `RuleRuntimeHost`：引导与生命周期持有者

两端同名类，均为**静态持有者**，暴露相同方法名（NeoForge 额外持有 `currentServer` 供数据包重载枚举维度）：

| 方法 | 作用 |
|---|---|
| `start(server)` | `shutdown()` 清上一轮 → 读 `server.json` → `BuiltinTypeRegistries.create()` → 建 `ConversionRuntime`（注入平台 `LifespanProvider`）→ 加载三层规则 → `replaceRules` → `DropStateStore.configure(新产物保护刻数, 转化冷却刻数)` → 对全部已加载维度 `rescan`。**必须等 `ServerStarted`**（全部维度已创建），否则会漏掉启动期已存在的掉落物 |
| `reload(server[, resourceManager])` | 重建索引 + 全维度 rescan + `bumpVersion()`；失败保留旧索引并返回 null |
| `shutdown()` | 释放全部维度状态 + `RuleEditServerHandler.reset()` + `DropStateStore.configure(0, 0)`（避免同进程下一个服务端实例沿用旧配置） |
| `onItemAdded` / `onItemRemoved` / `deferNaturalExpiry` / `requestEnvironmentalConversion` / `tickServer` / `levelLoaded` / `levelUnloaded` | 转发给 `ConversionRuntime` |
| `editContext()` / `commandContext()` | 返回两个匿名窄接口实现（`EDIT_CONTEXT` / `COMMAND_CONTEXT`），字段都是 volatile 静态状态，调用时实时读取 |

覆盖层路径 = `getConfigDir()/server.json` 里的 `overlay_directory`；但 **`server.json` 自身位置固定为 `config/itemdespawntowhat/server.json`**，不受该字段影响，避免引导期自引用。

## 3. 事件入口（`RuleRuntimeEvents`）

纯转发，无业务逻辑。**事件即 `core` 的调用时机**，因此这里最重要。

| 需求 | Fabric | NeoForge |
|---|---|---|
| 服务端就绪 | `ServerLifecycleEvents.SERVER_STARTED` → `start` | `ServerStartedEvent` → `start` |
| 服务端停止 | `SERVER_STOPPING`（先 `DebugSessionManager.shutdown` 再 `RuleRuntimeHost.shutdown`） | `ServerStoppingEvent`（同左） |
| 数据包重载 | `END_DATA_PACK_RELOAD`（**success 才重建**） | `OnDatapackSyncEvent`（**仅 `player==null` 时**） |
| 掉落物入世界 | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinLevelEvent` |
| 掉落物离开 | `ServerEntityEvents.ENTITY_UNLOAD` | `EntityLeaveLevelEvent` |
| 自然消失拦截 | **Mixin**（见 §4） | `ItemExpireEvent` + `addExtraLife(1)`；`age≥32765` 时 `setExtendedLifetime()` |
| 环境伤害保护（三类） | **Mixin**（见 §4） | `EntityInvulnerabilityCheckEvent` → `DropStateStore.blocksEnvironmentalDamage` 为真则 `setInvulnerable(true)` |
| 致死分支请求转化 | **Mixin**（见 §4） | **Mixin**（见 §4，两端各用不同注入描述符） |
| 死亡背包排除 | **两个 Mixin** + `PlayerDeathDrops.mark` | `LivingDropsEvent` 直接给 drops 打 `Constants.CHECK_LOCK_TAG` |
| 服务端 tick / 调试 / expireIdle | `END_SERVER_TICK`（`beginRuntimeTick` → `tickServer` → `DebugSessionManager.tick`；`tickCount%20==0` 时 `expireIdle`） | `ServerTickEvent.Post`（同左） |
| 维度加载 / 卸载 | `ServerWorldEvents.LOAD` / `UNLOAD` | `LevelEvent.Load` / `Unload` |
| 包层判定 | `FabricLoader.getAllMods()` 命中 mod id → BUILTIN，否则 WORLD | `packId.startsWith("mod/")` → BUILTIN，否则 WORLD |

> **单一服务端 tick 推进入口**：两端都只在"服务端 tick 结束"事件里推进一次公共预算（所有维度共享，ADR-0002），不再按维度 tick 推进。顺序固定为 `beginRuntimeTick → RuleRuntimeHost.tickServer → DebugSessionManager.tick → (每 20 tick) RuleEditServerHandler.expireIdle`。

## 4. Mixin（无原生等价事件时的最小入口）

**Fabric 三个 Mixin**：

| Mixin | 注入 | 作用 | 性质 |
|---|---|---|---|
| `ItemEntityMixin` | `@Redirect` `ItemEntity.tick` 中 `discard()` **ordinal=1** | 拦截自然寿命到期的 discard；`deferNaturalExpiry` 为真则不改由原版 discard | **功能性拦截** |
| `ItemEntityMixin` | `@Inject` `ItemEntity.hurt(DamageSource,F)Z` **HEAD**，`cancellable` | 三类环境伤害保护：`blocksEnvironmentalDamage` 为真则 `setReturnValue(false)` | 保护入口 |
| `ItemEntityMixin` | `@Inject` `ItemEntity.hurt`，`at = INVOKE ItemStack.onDestroyed` | **真实致死分支**：调用 `requestEnvironmentalConversion(level, self, source)` | 功能入口 |
| `ItemEntityMixin` | `@Inject` `ItemEntity.tryToMerge` **HEAD**，`cancellable` | 永久标志不一致时禁止原版合并（D3） | 入口 |
| `ItemEntityMixin` | `@Inject` `ItemEntity.merge` **TAIL** | 临时状态并集写回合并目标，源实体状态清除（D3） | 入口 |
| `PlayerMixin` | `@Inject` `Player.drop(ItemStack,ZZ)` **RETURN** | 返回值 ItemEntity 交 `PlayerDeathDrops.mark`（入世界前打 tag） | 入口，仅设标记 |
| `EntityMixin` | `@Redirect` `Entity.spawnAtLocation` 内 `Level.addFreshEntity` | 若为 ItemEntity 则先 `PlayerDeathDrops.mark` | 入口，仅设标记 |

**NeoForge 一个 Mixin**（自然消失/环境伤害保护走原生事件，此处只补两处平台没有等价事件的能力）：

| Mixin | 注入 | 作用 |
|---|---|---|
| `ItemEntityMixin` | `@Inject` `ItemEntity.hurt`，`at = INVOKE ItemStack.onDestroyed(ItemEntity,DamageSource)` | **真实致死分支**：调用 `requestEnvironmentalConversion` |
| `ItemEntityMixin` | `@Inject` `ItemEntity.tryToMerge` HEAD（cancellable）/ `ItemEntity.merge` TAIL | 合并兼容与临时状态并集（同 Fabric） |

`PlayerDeathDrops.mark` 仅当 `owner instanceof Player && player.isDeadOrDying()` 时打 `Constants.CHECK_LOCK_TAG`。**标记必须在实体入世界前完成**；两端都只对死亡玩家生效，普通丢物品不受影响。致死转化分类由 `core/state/DamageClassification`（common 唯一来源）负责，平台层只判定"是否关键致死点"，不重复定义伤害类别。

`fabric.mod.json` 的 `mixins` 列表声明 `itemdespawntowhat.mixins.json`；该文件列出 `PlayerMixin / ItemEntityMixin / EntityMixin`，`defaultRequire:1`。NeoForge 的 `neoforge.mods.toml` 用 `[[mixins]]` 声明 `itemdespawntowhat.mixins.json`，该文件只列 `ItemEntityMixin`。

## 5. 网络注册器与客户端

| 项 | Fabric | NeoForge |
|---|---|---|
| 主类 | `ItemDespawnToWhat implements ModInitializer`；`onInitialize` 注册事件 / payload / 命令 | `ItemDespawnToWhat @Mod`，**空构造器**；事件与协议靠 `@EventBusSubscriber` 自动发现 |
| C2S/S2C 类型注册 | `PayloadTypeRegistry.playC2S/playS2C`（C2S ×7、S2C ×5） | `RegisterPayloadHandlersEvent` + `registrar("2")`（C2S ×7、S2C ×5） |
| C2S 接收器 | `ServerPlayNetworking.registerGlobalReceiver` ×7 | `playToServer` ×7 |
| S2C 接收器 | 客户端 `FabricRuleEditClientRegistrar` 注册（×5） | `playToClient` ×5，处理器**只做 `RuleEditPayloadRouter` / `OpenRuleEditorPayload.dispatchOpenEditor` 分发**，不引用客户端类 |
| 断线清理 | `ServerPlayConnectionEvents.DISCONNECT` | `PlayerEvent.PlayerLoggedOutEvent` |
| 命令注册 | `CommandRegistrationCallback.EVENT` | `RegisterCommandsEvent` |
| 客户端主类 | `ItemDespawnToWhatClient`（`ClientModInitializer`）：按键 / S2C 接收器 / 工作区接线 / 界面 bootstrap | 无独立主类，用 `@EventBusSubscriber(Dist.CLIENT)`（`RegisterEvent` 静态块接线工作区与界面） |

协议版本两端对齐（`RuleEditProtocol.VERSION = 2`，NeoForge registrar 传 `"2"`）。**C2S 与 S2C 的类型都必须在公共初始化注册**，否则专用服务端连接协商会判定通道缺失。

## 6. 掉落物状态与寿命的平台适配

| 维度 | Fabric | NeoForge |
|---|---|---|
| 状态存取 | Fabric Data Attachment（`AttachmentRegistry.createPersistent`，id `itemdespawntowhat:state`，随实体 NBT 持久化） | `Entity#getPersistentData()` 的 NBT 键 `itemdespawntowhat:state` |
| 状态门面 | `DropStateStore.get/set/clear` 统一入口；`set` 传 null 即清除（不写空数据） | 同左 |
| 计时基准 | `ServerLevel#getGameTime()` 的**绝对游戏刻**（卸载不暂停、重启后继续） | 同左 |
| 自然消失拦截 | Mixin `@Redirect` tick 第二处 `discard()` | `ItemExpireEvent` + `addExtraLife(1)` |
| 环境伤害保护 | Mixin `@Inject` `hurt` HEAD（三类全防） | `EntityInvulnerabilityCheckEvent`（三类全防） |
| lifespan 取值 | `config.fabricLifespanFallbackTicks()`（无加载器寿命 API，兜底常量） | `entity.lifespan`（`ItemStack#getEntityLifespan`，保留其它模组修改） |
| lifespan 数值钳制 | 配置兜底值；`expiry` 场景 >32767 拒绝 | 原版钳制 32766，`age≥32765` 触发 `setExtendedLifetime` |
| 死亡掉落排除 | 两个 Mixin + `PlayerDeathDrops.mark` | `LivingDropsEvent` 直接打 tag |
| 包层判定 | 查 `FabricLoader.getAllMods()` | `packId.startsWith("mod/")` |

> 共同点：`start/reload/shutdown/onItemAdded/onItemRemoved/requestEnvironmentalConversion/deferNaturalExpiry/tickServer/levelLoaded/levelUnloaded` 语义两端口径一致；保存后的重建走同一 `reload` 路径。`DropState` 与 `DamageClassification` 在 common，平台层不重复定义状态结构或伤害类别。

## 7. 模组级配置

`ServerConfig`（`server.json`，含 `overlay_directory`、`new_product_protection_ticks`、`conversion_cooldown_ticks`、寿命兜底刻数等字段）见 [../systems/config.md](../systems/config.md)。引导时两端都用 `DropStateStore.configure(...)` 注入状态持续时长。

## 8. 扩展点

- **加平台能力**：扩 `IPlatformHelper` + 两端实现；core 通过 `Services.PLATFORM` 使用。
- **加平台事件**：在两端 `RuleRuntimeEvents` 加转发，业务留在 core。
- **必须新增 Mixin**：优先找已有 Event API；确无等价事件时才写 Mixin，且**仅作入口**，业务放 core（如 `ItemEntityMixin` 只调 `deferNaturalExpiry` / `requestEnvironmentalConversion`）。注意两端回调签名不同时**各自写独立描述符**，不强行共用。
- **页面/客户端入口**：属于 client 范围，本轮不在文档内。

## 9. 相关

- 平台抽象机制：[../systems/platform-abstraction.md](../systems/platform-abstraction.md)
- 配置：[../systems/config.md](../systems/config.md)
- 引导链路与重载：[../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)
- 决策：[ADR-0017](../../../adr/0017-backend-cutover-and-budgeted-effects.md)、[ADR-0024](../../../adr/0024-shared-server-tick-budget-scheduler.md)
