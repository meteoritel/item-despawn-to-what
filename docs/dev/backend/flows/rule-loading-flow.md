# 纵向系统：规则加载链路

> 从"触发一次加载"到"规则索引就绪并可被运行时使用"的端到端流程。
> 类职责见 [rule-loading.md](../modules/rule-loading.md)；装配点见 [platform.md](../modules/platform.md)。决策：[ADR-0014](../../../adr/0014-three-layer-scope-and-overlay-merge.md)。

## 1. 触发时机

| 触发 | Fabric | NeoForge | 说明 |
|---|---|---|---|
| 服务端启动 | `SERVER_STARTED` → `RuleRuntimeHost.start` | `ServerStartedEvent` → `start` | **必须等全部维度已创建**，否则回扫漏掉落物 |
| 数据包重载 | `END_DATA_PACK_RELOAD`（`success` 才重建） | `OnDatapackSyncEvent`（仅 `player==null`） | 在新资源与标签切换后加载 |
| 命令重载 | `/idtw config reload` → `reloadRules` | 同 | 走同一 `reload` |
| 保存后重建 | 网络保存 → `rebuildAndRescan` | 同 | 见 [edit-save-protocol.md](edit-save-protocol.md) |

## 2. 主流程

```text
RuleRuntimeHost.start(server)
  ├─ shutdown()                          # 释放上一轮（单人世界切存档会先后启动多个服务端）
  ├─ ServerConfig.loadOrCreate(...)      # 读 server.json，缺失即写默认
  ├─ BuiltinTypeRegistries.create()      # 条件→freeze→表达式Codec→效果→freeze
  ├─ new ConversionRuntime(config, registries, lifespanProvider)
  ├─ loadRules(context, "服务端启动")
  │    └─ RuleLoadingService.loadAndValidate(ctx)
  │         ├─ RuleCodecs.decoder(registryAccess, effectTypes, conditionTypes)
  │         ├─ RuleLoader.load(request)
  │         │    ├─ DatapackRuleReader.read(rm, layerResolver, issues)   # listResources→getResourceStack→排序
  │         │    ├─ OverlayRuleReader.read(overlayRoot, ns, issues)      # walk rules/**/*.json
  │         │    ├─ RuleMerger.merge(raw, issues)                        # 按层/按id合并 + 控制条目后置
  │         │    └─ 逐条 decodeEntry → RuleCodecs decoder → LoadedRule
  │         └─ 逐条 RuleValidation.validate + RuleReferenceValidator.validate
  ├─ newRuntime.replaceRules(rules, issues)   # 重建索引 + 清空追踪 + tags/climate 置 null
  └─ for 每个已加载维度: newRuntime.rescan(level)   # 回扫 ENTITY → onItemAdded
```

## 3. 分步证据

| 步骤 | 动作 | 关键类/方法 |
|---|---|---|
| 1 | 定位数据包层资源 | `DatapackRuleReader.read`：`listResources("idtw/rules", *.json)` → `getResourceStack()` 取**所有包的同名副本** |
| 2 | 包优先级 | `listPacks()` 建表（序号越大越优先）；未登记包 rank=-1 并每包告警一次；取表失败 WARN 退化排序 |
| 3 | 定位覆盖层 | `OverlayRuleReader.read`：`Files.walk("rules/**/*.json")` 按绝对路径排序 |
| 4 | 逐文件解析 | `RuleFileParser.parse(json, origin, derivedId, issues)`：严格策略、单条才允许 id 推导、控制字段校验 |
| 5 | 合并 | `RuleMerger.merge`：`TreeMap` 按层优先级 → 层内重排 `normals + controls` → `disabled`/`delete` 后应用 |
| 6 | 解码 | `RuleLoader.decodeEntry`：剔除控制字段、补 id、`disabled` 注入 `enabled=false`；解码器 `RuntimeException` 记堆栈 |
| 7 | 校验 | `RuleValidation.validate`（带注册表重载）+ `RuleReferenceValidator.validate`（`server==null` 跳过动态引用） |
| 8 | 替换 | `ConversionRuntime.replaceRules`：重建 `RuleIndex`、清空 `tracked`/`scheduler`、`tags`/`climate` 置 null |
| 9 | 回扫 | `ConversionRuntime.rescan(level)`：`level.getAllEntities()` 中 `ItemEntity` 逐个 `onItemAdded` |
| 10 | 推进版本 | reload 成功路径 `EditSessionManager.bumpVersion()` |

## 4. 失败与边界

- **枚举资源/覆盖层目录失败** → `IllegalStateException`/`UncheckedIOException`，`applyReload` 捕获 `RuntimeException` 后**保留上一版索引**并返回 null，异常不外抛到 `/reload`。
- **单个坏文件/坏规则** → 拒载该文件/该条，其余继续（见 [issue-validation.md](../systems/issue-validation.md)）。
- **规则重载不清空已提交效果**，只重建检查/索引/缓存；已提交效果继续完成。
- **启动期的首次数据包加载**不作为重载处理（那时维度尚未创建），统一交给 `start`。
- **后加载的维度**由 `levelLoaded` 补一次回扫（`levelLoaded` → `rescan`）。

## 5. 相关

- 模块细节：[../modules/rule-loading.md](../modules/rule-loading.md)、[../modules/platform.md](../modules/platform.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 索引与缓存：[../systems/caching-indexing.md](../systems/caching-indexing.md)
