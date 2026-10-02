# 功能模块：平台接入与生命周期（`core/config` + `platform` + `fabric` + `neoforge`）

> 事实来源：`core/config/ServerConfig.java`、`Constants.java`、`platform/**`、Fabric 与 NeoForge 两端的 `runtime/`、`network/`、`client/`、`mixin/`、主类与 `resources/`。
> 平台层**只做入口**：把加载器事件转发给 `core`，不含业务逻辑。

## 1. 平台隔离层

| 类 | 职责 |
|---|---|
| `Services` | `ServiceLoader.load(IPlatformHelper.class).findFirst()` 静态装配；静态常量 `PLATFORM` |
| `IPlatformHelper` | 两端共用的平台能力契约：`getPlatformName()`、`isModLoaded`、`isDevelopmentEnvironment`、`getConfigDir`、`getEnvironmentName()`、`sendToPlayer(player, payload)` |

服务发现文件：`META-INF/services/com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper`（Fabric 内容 `FabricPlatformHelper`，NeoForge 内容 `NeoForgePlatformHelper`）。

| 平台 | `getPlatformName` / `isModLoaded` / `isDevelopmentEnvironment` / `getConfigDir` / `sendToPlayer` 实现 |
|---|---|
| Fabric | `FabricLoader` 系列 + `ServerPlayNetworking.send` |
| NeoForge | `ModList.get().isLoaded` / `!FMLLoader.isProduction()` / `FMLPaths.CONFIGDIR.get()` / `PacketDistributor.sendToPlayer` |

机制与铁律见 [../systems/platform-abstraction.md](../systems/platform-abstraction.md)。

## 2. `RuleRuntimeHost`：引导与生命周期持有者

两端同名类，均为**静态持有者**，暴露相同方法名：

| 方法 | 作用 |
|---|---|
| `start(server)` | `shutdown()` 清上一轮 → 读 `server.json` → `BuiltinTypeRegistries.create()` → 建 `ConversionRuntime`（注入平台 `LifespanProvider`）→ 加载三层规则 → `replaceRules` → 对全部已加载维度 `rescan`。**必须等 `ServerStarted`**（全部维度已创建） |
| `reload(server[, resourceManager])` | 重建索引 + 全维度 rescan + `bumpVersion()`；失败保留旧索引并返回 null |
| `shutdown()` | 释放全部维度状态 + 重置编辑会话 |
| `onItemAdded` / `onItemRemoved` / `deferNaturalExpiry` / `tickLevel` / `levelLoaded` / `levelUnloaded` | 转发给 `ConversionRuntime` |
| `commandContext()` / `editContext()` | 返回两个匿名窄接口实现（`COMMAND_CONTEXT` / `EDIT_CONTEXT`），字段都是 volatile 静态状态，调用时实时读取 |

覆盖层路径 = `getConfigDir()/server.json` 里的 `overlay_directory`；但 **`server.json` 自身位置固定为 `config/itemdespawntowhat/server.json`**，不受该字段影响，避免引导期自引用。

## 3. 事件入口（`RuleRuntimeEvents`）

纯转发，无业务逻辑。**事件即 `core` 的调用时机**，因此这里最重要。

| 需求 | Fabric | NeoForge |
|---|---|---|
| 服务端就绪 | `ServerLifecycleEvents.SERVER_STARTED` → `start` | `ServerStartedEvent` → `start` |
| 服务端停止 | `SERVER_STOPPING`（先 `DebugSessionManager.shutdown` 再 `RuleRuntimeHost.shutdown`） | `ServerStoppingEvent` |
| 数据包重载 | `END_DATA_PACK_RELOAD`（**success 才重建**） | `OnDatapackSyncEvent`（**仅 `player==null` 时**） |
| 掉落物入世界 | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinLevelEvent` |
| 掉落物离开 | `ServerEntityEvents.ENTITY_UNLOAD` | `EntityLeaveLevelEvent` |
| 自然消失拦截 | **Mixin**（见 §4） | `ItemExpireEvent` + `addExtraLife(1)`；`age≥32765` 时 `setExtendedLifetime()` |
| 死亡背包排除 | **两个 Mixin** + `PlayerDeathDrops.mark` | `LivingDropsEvent` 直接给 drops 打 `CHECK_LOCK_TAG` |
| 维度 tick | `ServerTickEvents.END_WORLD_TICK` | `LevelTickEvent.Post` |
| 服务端 tick / 调试 / expireIdle | `END_SERVER_TICK`（`tickCount%20==0` 时 `expireIdle`） | `ServerTickEvent.Post`（同上） |
| 维度加载 / 卸载 | `ServerWorldEvents.LOAD` / `UNLOAD` | `LevelEvent.Load` / `Unload` |
| 包层判定 | `FabricLoader.getAllMods()` 命中 mod id → BUILTIN，否则 WORLD | `packId.startsWith("mod/")` → BUILTIN，否则 WORLD |

## 4. Fabric 三个 Mixin（无原生等价事件时的最小入口）

| Mixin | 注入 | 作用 | 性质 |
|---|---|---|---|
| `ItemEntityMixin` | `@Redirect` `ItemEntity.tick` 中 `discard()` **ordinal=1** | 拦截自然寿命到期的 discard；`deferNaturalExpiry` 为真则不改由原版 discard | **功能性拦截**（唯一非纯入口） |
| `PlayerMixin` | `@Inject` `Player.drop(ItemStack,ZZ)` **RETURN** | 返回值 ItemEntity 交 `PlayerDeathDrops.mark`（入世界前打 tag） | 入口，仅设标记 |
| `EntityMixin` | `@Redirect` `Entity.spawnAtLocation` 内 `Level.addFreshEntity` | 若为 ItemEntity 则先 `PlayerDeathDrops.mark` | 入口，仅设标记 |

`PlayerDeathDrops.mark` 仅当 `owner instanceof Player && player.isDeadOrDying()` 时打 `CHECK_LOCK_TAG`。**标记必须在实体入世界前完成**；两端都只对死亡玩家生效，普通丢物品不受影响。

`itemdespawntowhat.mixins.json` 声明顺序为 `PlayerMixin / ItemEntityMixin / EntityMixin`，`defaultRequire:1`。

## 5. 网络注册器与客户端

| 项 | Fabric | NeoForge |
|---|---|---|
| 主类 | `ItemDespawnToWhat implements ModInitializer`；`onInitialize` 注册事件 / payload / 命令 | `ItemDespawnToWhat @Mod`，**空构造器**；事件与协议靠 `@EventBusSubscriber` 自动发现 |
| C2S/S2C 类型注册 | `PayloadTypeRegistry.playC2S/playS2C` | `RegisterPayloadHandlersEvent` + `registrar("1")` |
| C2S 接收器 | `ServerPlayNetworking.registerGlobalReceiver` ×3 | `playToServer` ×3 |
| S2C 接收器 | 客户端 `FabricRuleEditClientRegistrar` 注册 | `playToClient` ×3，处理器**只做 `RuleEditPayloadRouter` 分发** |
| 断线清理 | `ServerPlayConnectionEvents.DISCONNECT` | `PlayerEvent.PlayerLoggedOutEvent` |
| 命令注册 | `CommandRegistrationCallback.EVENT` | `RegisterCommandsEvent` |
| 客户端主类 | `ItemDespawnToWhatClient`（`ClientModInitializer`） | 无独立主类，用 `@EventBusSubscriber(Dist.CLIENT)` |

协议版本两端对齐（`"1"`）。**C2S 与 S2C 的类型都必须在公共初始化注册**，否则专用服务端连接协商会判定通道缺失。

## 6. 两端差异汇总

| 维度 | Fabric | NeoForge |
|---|---|---|
| 自然消失拦截 | Mixin `@Redirect` tick 第二处 `discard()` | `ItemExpireEvent` + `addExtraLife(1)` |
| lifespan 取值 | `config.fabricLifespanFallbackTicks()`（无加载器寿命 API，兜底常量） | `entity.lifespan`（`ItemStack#getEntityLifespan`，保留其它模组修改） |
| lifespan 数值钳制 | 配置兜底值；`expiry` 场景 >32767 拒绝 | 原版钳制 32766，`age≥32765` 触发 `setExtendedLifetime` |
| 死亡掉落排除 | 两个 Mixin + `PlayerDeathDrops.mark` | `LivingDropsEvent` 直接打 tag |
| 包层判定 | 查 `FabricLoader.getAllMods()` | `packId.startsWith("mod/")` |

> 共同点：`start/reload/shutdown/onItemAdded/onItemRemoved/deferNaturalExpiry/tickLevel/levelLoaded/levelUnloaded` 语义两端口径一致；保存后的重建走同一 `reload` 路径。

## 7. 模组级配置

`ServerConfig`（`server.json`，6 个字段，默认值/区间/退避算法）见 [../systems/config.md](../systems/config.md)。

## 8. 扩展点

- **加平台能力**：扩 `IPlatformHelper` + 两端实现；core 通过 `Services.PLATFORM` 使用。
- **加平台事件**：在两端 `RuleRuntimeEvents` 加转发，业务留在 core。
- **必须新增 Mixin**：优先找已有 Event API；确无等价事件时才写 Mixin，且**仅作入口**，业务放 core（如 `ItemEntityMixin` 只调 `deferNaturalExpiry`）。
- **页面/客户端入口**：属于 client 范围，本轮不在文档内。

## 9. 相关

- 平台抽象机制：[../systems/platform-abstraction.md](../systems/platform-abstraction.md)
- 配置：[../systems/config.md](../systems/config.md)
- 引导链路与重载：[../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)
- 决策：[ADR-0017](../../../adr/0017-backend-cutover-and-budgeted-effects.md)
