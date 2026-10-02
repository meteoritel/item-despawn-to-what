# 前端重构实施契约（P0 冻结件）

> 本文件是 docs/plan/plan-frontend-rewrite.md（下称「规划书」）的**实施级契约**。
> 规划书说明「要什么」，本文件说明「写成什么样的代码」。多 agent 并行开发时以本文件为准；
> 本文件与规划书冲突时，**以本文件为准**并在完成后回报 Lead 修正规划书。
> 冻结日期：P0 阶段。任何改动本文件已冻结条款的行为，必须先由 Lead 更新本文件。

## 0. 通用开发规则（所有 agent 必须遵守）

- 类注释用 `/***/`，方法/字段注释用 `//`，**全部简体中文**。
- `common/` 不得 import 任何平台类（fabric/neoforge）；服务端代码（`core/**`）严禁引用 `client/**`。
- 优先已有 Event API，Mixin 只做入口。
- 不新增测试文件；测试由用户执行。
- **构建**：统一用 `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`（内部有命名互斥体串行化）。不要直接裸跑 `gradlew.bat`，多 agent 并发会互相踩 build 目录。构建约 40 秒，等待窗口 ≥180 秒。
  - 该脚本为 **纯 ASCII + UTF-8 BOM**，Windows PowerShell 5.1 与 pwsh 7 均可直接运行；请勿在其中加入中文，否则 PS 5.1 会按 GBK 读取而解析失败（已踩坑）。
- 只修改自己任务 write scope 内的文件。构建报错若位于他人 scope，**只报告，不修改**，报告格式：`文件:行 错误摘要 → 归属（谁）`。
- i18n：新增界面文本必须同时改 `common/src/main/resources/assets/itemdespawntowhat/lang/en_us.json` 与 `zh_cn.json`。

## 1. 目标包结构（新增代码的落点）

```
common/src/main/java/com/meteorite/itemdespawntowhat/
  core/model/          ConditionNode, ConditionExpression, ConditionTrees, ConditionLimits, Rule, RuleCodecs, RuleFields, ConditionType, SimpleConditionType
  core/api/            ConditionResult, Evaluability, ConditionEvaluator, ConditionContext
  core/runtime/        ExpressionEvaluator, ExpressionTreeEvaluator, RuleIndex
  core/service/        EditSessionManager, RuleEditService, RuleEditLock (见 §3)
  core/network/protocol/  RuleEdit, RuleSnapshot, RuleSnapshotEntry, RuleEditChangeSet, RuleIssue, RuleSaveStatus, RuleCatalog, RuleCatalogEntry
  core/network/transport/ 全部 *Payload + RuleEditServerHandler + RuleEditLimits + RuleEditChunkAccumulator
  client/ui/kit/       kit 复制件（见 §4）
  client/ui/theme/     UiTheme / UiPalette（灰色像素主题）
  client/ui/widget/    通用控件（输入框、列表、树、弹窗、按钮）
  client/ui/screen/    管理页与编辑页
  client/edit/         客户端工作区（协议视图、类型编辑器注册表、草稿、撤销栈）
  client/net/          客户端协议收发封装
```

---

## 2. 条件树契约（P2 冻结）

### 2.1 Java 类型

```java
/*** 递归条件节点：组合层（all_of/any_of/inverted）与具体条件叶（leaf）分离。 */
public sealed interface ConditionNode
        permits ConditionNode.AllOf, ConditionNode.AnyOf, ConditionNode.Inverted, ConditionNode.Leaf {
    record AllOf(List<ConditionNode> terms) implements ConditionNode {}
    record AnyOf(List<ConditionNode> terms) implements ConditionNode {}
    record Inverted(ConditionNode term) implements ConditionNode {}
    record Leaf(Condition condition) implements ConditionNode {}
}
```

四个 record 的紧凑构造器必须做防御性拷贝 `terms = List.copyOf(terms);` 并且 **不得** 在构造器里做「非空」校验（空组是编辑中间态，必须可构造、可序列化），非空性由 `isStructurallyValid()` 承担。

```java
/*** 规则级与效果级共用的条件表达式：空表示无条件（恒真）。 */
public record ConditionExpression(@Nullable ConditionNode root) {
    public static final ConditionExpression EMPTY = new ConditionExpression(null);
    public boolean isEmpty();              // root == null
    public int leafCount();                // 递归叶数，复杂度口径
    public int nodeCount();                // 递归节点总数（含叶）
    public int depth();                    // 根深度 = 1；空表达式 = 0
    public boolean isStructurallyValid();  // all_of/any_of terms 非空；inverted 有 term；深度/节点/叶限额内
}
```

**保留的现有名字**：`ConditionExpression`（下游引用多）。**删除**：`ConditionGroup`。
`ConditionExpression` 不再有 `groups()`。所有旧调用点必须改写。

### 2.2 树遍历工具

```java
/*** 条件树遍历与统计工具，条件树相关的算法集中在此。 */
public final class ConditionTrees {
    public static void forEachLeaf(@Nullable ConditionNode node, Consumer<Condition> visitor);
    public static int leafCount(@Nullable ConditionNode node);
    public static int nodeCount(@Nullable ConditionNode node);
    public static int depth(@Nullable ConditionNode node);
}
```

### 2.3 限额常量

```java
/*** 条件树与规则规模限额。 */
public final class ConditionLimits {
    public static final int MAX_LEAVES = 128;
    public static final int MAX_NODES = 256;
    public static final int MAX_DEPTH = 16;          // 根深度定义为 1
    public static final int MAX_EFFECTS = 32;
    public static final int MAX_SOURCE_ENTRIES = 256;
    public static final int MAX_DISPLAY_NAME_CODEPOINTS = 128;
}
```
`RuleValidation` 中原本写死的 128 / 32 必须改为引用这些常量。

### 2.4 叶取反契约变更

**叶级 `negated` 被删除**：`Condition.negated()` 方法移除，`Condition` 只剩 `ResourceLocation type()`。
取反只能通过 `ConditionNode.Inverted` 表达。
- `CommonFields.negated(getter)` 删除；`DEFAULT_NEGATED` 常量删除。
- 10 个内置条件 record 的 `boolean negated` 字段删除，改为 `static MapCodec<X> codec()`（不再接 expressionCodec 的那些保持不变）。
- `RuleFields.NEGATED` 保留常量但不用于解码；遇到叶内出现 `negated` 字段必须**报错**（提示改用 inverted 节点），不得静默忽略。

### 2.5 规范 JSON 形状

条件表达式序列化为 `conditions` 字段（无条件时**省略字段**，不得输出 `null`）：

```json
{"op":"all_of","terms":[{...},{...}]}
{"op":"any_of","terms":[{...},{...}]}
{"op":"inverted","term":{...}}
{"op":"leaf","condition":{"type":"itemdespawntowhat:dimension","dimensions":["minecraft:overworld"]}}
```

- `all_of` / `any_of`：`terms` 至少 1 项（解码期不足即报错）。
- `inverted`：恰好 1 个 `term`。
- `leaf`：`condition` 内是现有扁平类型分发对象（`type` 与类型专属字段同层，`TypeDispatch.flat`）。
- 未知 `op` 值 → 报错并列出允许值。
- **旧格式必须被拒绝**：`conditions` 为 JSON 数组（旧二维 DNF）、或对象含 `groups` 字段、或叶内出现 `negated` → 返回明确错误信息，禁止静默兼容。
- 保持硬约束：**codec 层不得产出 null**，可选字段用 `Optional` 承载并在 `apply` 末尾 `orElse(null)`。

### 2.6 求值契约

```java
/*** 条件求值结果。UNAVAILABLE 表示当前上下文无法判定（例如区块未加载）。 */
public enum ConditionResult { MATCH, NO_MATCH, UNAVAILABLE, ERROR;
    public boolean isMatch();       // == MATCH
}
```

```java
/*** 条件在当前上下文中的可求值性前置检查结果。 */
public enum Evaluability { AVAILABLE, UNAVAILABLE }
```

`ConditionType` 增加默认方法（第三方类型不实现即为恒可求值）：
```java
// 当前上下文是否足以判定该条件；默认恒可求值。
default Evaluability evaluability(P params, ConditionContext context) { return Evaluability.AVAILABLE; }
```

**求值流程（`core/runtime/ExpressionEvaluator`）**，按序：
1. 表达式为空 → `MATCH`。
2. `leaf`：类型未注册 → `UNAVAILABLE`（加载期已拒绝，运行期兜底）。
3. `evaluability(...)` 返回 `UNAVAILABLE` → `UNAVAILABLE`。
4. `evaluator().test(...)` 返回 true/false → `MATCH`/`NO_MATCH`。
5. `evaluability` 或 `test` 抛异常 → log（含条件类型与栈）+ `ERROR`。
6. `inverted`：子结果 `MATCH`→`NO_MATCH`、`NO_MATCH`→`MATCH`、`UNAVAILABLE`/`ERROR` **原样保留**。
7. `all_of`：按序求值；遇 `NO_MATCH` 立即返回 `NO_MATCH`；遇 `UNAVAILABLE` 记录标记后继续；遇 `ERROR` 记录标记后继续；全部 `MATCH` → `MATCH`；否则有 `UNAVAILABLE` → `UNAVAILABLE`，仅有 `ERROR` → `ERROR`。
8. `any_of`：按序求值；遇 `MATCH` 立即返回 `MATCH`；遇 `UNAVAILABLE`/`ERROR` 记录标记后继续；全部 `NO_MATCH` → `NO_MATCH`；否则有 `UNAVAILABLE` → `UNAVAILABLE`，仅有 `ERROR` → `ERROR`。
9. `UNAVAILABLE` 优先于 `ERROR` 作为组合节点的聚合结果。
10. 空 `all_of`/`any_of`（编辑中间态，运行期不应出现）→ `MATCH`/`NO_MATCH` 分别作为单位元，不抛异常。

保留便捷入口（供旧调用点过渡）：
```java
public static boolean matches(@Nullable ConditionExpression expression, ConditionContext context, TypeRegistry<ConditionType<?>> types);
// 等价于 evaluate(...) == ConditionResult.MATCH
public static ConditionResult evaluate(@Nullable ConditionExpression expression, ConditionContext context, TypeRegistry<ConditionType<?>> types);
```

**根能通过的唯一条件是 `MATCH`**；`UNAVAILABLE`/`ERROR` 一律不触发效果（`NOT(UNAVAILABLE)` 与 `NOT(ERROR)` 绝不通过）。

**可求值性门禁迁移**：`SurroundingBlocksCondition` 现有硬编码 `LoadedChunks.containsArea(level, pos, 1)` 必须从 `ExpressionEvaluator` 移到该条件的 `evaluability(...)` 实现。

### 2.7 Rule 增加 display_name

```java
public record Rule(ResourceLocation id, boolean enabled, int priority,
                   @Nullable String displayName, @Nullable String notes,
                   SourceMatcher source, ConditionExpression conditions,
                   int triggerAfterSeconds, List<Effect> effects)
```

- JSON 字段名 `display_name`（`RuleFields.DISPLAY_NAME`），加入 `KNOWN_RULE_FIELDS`。
- 规范化：``trim 后为空串 → 视为未命名（`null`）；长度按 **Unicode code point** 计，> 128 → 校验失败（`RuleValidation`）。
- `Rule.complexity()` = `conditions.leafCount()`（语义不变，实现改走树）。

---

## 3. 独占编辑会话与协议契约（P3 冻结）

### 3.1 状态机与时钟

```
FREE --(指令 /idtw config edit 成功取锁)--> OPENING --(客户端确认)--> ACTIVE
ACTIVE --(提交变更集)--> APPLYING --(应用结束)--> ACTIVE
ACTIVE/OPENING --(释放/断线/租约到期/强制释放)--> FREE
```

**独占目标是全局规则编辑目标（单一 targetId `itemdespawntowhat:rules`）**，不再按玩家。同一时刻至多 1 个 `ACTIVE`/`OPENING`/`APPLYING` 会话。

```java
/*** 编辑会话常量。时间一律使用服务端活动时钟（MinecraftServer.getTickCount()），暂停时不流逝。 */
public final class RuleEditSessionLimits {
    public static final int OPEN_CONFIRM_WINDOW_SECONDS = 15;  // OPENING 确认窗口
    public static final int HEARTBEAT_INTERVAL_SECONDS = 10;   // 客户端心跳周期
    public static final int LEASE_SECONDS = 60;                // 客户端静默租约
    public static final String TARGET_ID = "itemdespawntowhat:rules";
    public static final int REQUIRED_PERMISSION_LEVEL = 2;
}
```

- 会话持有：`ownerUuid`、不可预测的 `sessionId`（`UUID.randomUUID()`，**不得**由玩家名或时间戳推导）、`state`、`lastHeartbeatTick`、`openedTick`。
- 服务端每 20 tick 检查：OPENING 超确认窗口 → FREE；ACTIVE 超租约 → FREE（并通知客户端 `SESSION_EXPIRED`）。
- 服务端不得引用客户端类；时钟不得使用 `System.currentTimeMillis()`。

### 3.2 协议版本

```java
/*** 编辑协议版本；不匹配的客户端在握手阶段被拒绝。 */
public final class RuleEditProtocol { public static final int VERSION = 2; }
```
`RuleEditLimits.MAX_SNAPSHOT_BYTES` 从 `1_000_000` 提升到 `4 * 1024 * 1024`（与规划书一致）。其余常量不变。

### 3.3 Payload 一览（全部为 record，服务端→客户端标 S2C）

| 名称 | 方向 | 字段 |
|---|---|---|
| `OpenRuleEditorPayload` | S2C | `String sessionId, String targetId, int protocolVersion, int contextRevision, String statusCode, List<String> messageArgs, String fallbackMessage` |
| `ConfirmRuleEditorPayload` | C2S | `String sessionId, int protocolVersion` |
| `HeartbeatRuleEditorPayload` | C2S | `String sessionId` |
| `CloseRuleEditorPayload` | C2S | `String sessionId, String reasonCode` |
| `RequestRuleSnapshotPayload` | C2S | `String sessionId, String requestId` |
| `RuleSnapshotPayload` | S2C | `String sessionId, String requestId, String json`（`json` = `RuleSnapshot.serialize()`） |
| `SaveRuleChangeSetPayload` | C2S | `String sessionId, String operationId, String json` |
| `SaveRuleChangeSetChunkPayload` | C2S | `String sessionId, String operationId, String transferId, int index, int count, String chunk` |
| `RuleSaveResultPayload` | S2C | `String sessionId, String operationId, String statusCode, String messageCode, List<String> messageArgs, int resultVersion, boolean writtenToDisk, boolean reloaded, List<RuleIssue> issues` |
| `RequestRuleCatalogPayload` | C2S | `String sessionId, String requestId, String catalogType, String filter, int page, int pageSize` |
| `RuleCatalogPayload` | S2C | `String sessionId, String requestId, String catalogType, int revision, String json, boolean lastPage` |

- 所有长度受限字段必须先按 UTF-8 校验，超限直接以 **请求不合法** 回执拒绝，不得截断。
- `operationId` 由客户端生成（UUID 字符串），服务端对同一会话保留最近 16 个已处理 `operationId` 用于**幂等**：重复提交同一 `operationId` 直接返回上次回执，不重复写盘。
- 分片：`transferId` 标识一次传输，`index` 从 0 递增，`count` 为总分片数；同一玩家最多 2 个在途传输；60 秒无进展丢弃。
- **授权检查点**：心跳、快照请求、目录请求、变更集提交、分片任一步骤，都必须校验 `sessionId` 与状态匹配，否则回执 `SESSION_EXPIRED`/`LOCK_NOT_OWNED`。

#### 3.3.1 新增 S2C payload：`RuleSnapshotChunkPayload`（Lead 批准，session-dev 申请）

| 名称 | 方向 | 字段 |
|---|---|---|
| `RuleSnapshotChunkPayload` | S2C | `String sessionId, String requestId, String transferId, int index, int count, String chunk` |

- 快照序列化后 ≤ `RuleEditLimits.MAX_SNAPSHOT_BYTES` → 仍走整包 `RuleSnapshotPayload`。
- 超限 → 改发 N 片，同一 `transferId`、`index` 0..count-1；客户端收齐拼接后按 `RuleSnapshot.parse` 处理。
- 在途上限（每玩家 2 个传输）与 60 秒无进展丢弃沿用 §3.3 分片规则。

### 3.4 回执状态码（S2C 传给客户端，客户端只认这些）

```java
/*** 保存/请求回执状态码。客户端按码分支，禁止解析文案。 */
public enum RuleSaveStatus {
    SUCCESS,               // 成功
    NO_CHANGES,            // 无修改
    NO_PERMISSION,         // 无权限
    LOCK_NOT_OWNED,        // 编辑锁不属于当前会话
    LOCK_BUSY,             // 锁被他人持有
    SESSION_EXPIRED,       // 会话失效
    VERSION_CONFLICT,      // 版本冲突
    VALIDATION_FAILED,     // 校验失败
    WRITE_FAILED,          // 写盘失败
    SAVED_NOT_RELOADED,    // 已写盘但重载失败
    INVALID_REQUEST,       // 请求不合法
    UNAVAILABLE            // 传输或运行时不可用
}
```

### 3.5 结构化问题 DTO

```java
/*** 结构化问题：跨网络传输，客户端不得解析中文日志或字符串拼接来决定状态。 */
public record RuleIssue(String severity, @Nullable String ruleId, String origin,
                        String fieldPath, String messageCode, List<String> messageArgs,
                        String fallbackMessage) {}
```
- `severity` 取值 `error` / `warning` / `info`。
- `origin` 取值 `overlay` / `datapack` / `runtime` / `network`。
- `messageCode` 是全限定翻译 key，例如 `itemdespawntowhat.edit.error.leaf_limit`；`messageArgs` 供 `Component.translatable` 使用。
- 所有 messageCode 必须同时进 `en_us.json` 与 `zh_cn.json`。

### 3.6 快照与目录 DTO

```java
/*** 单条规则的来源视图。 */
public record RuleSnapshotEntry(ResourceLocation id, String origin, String status,
                                boolean editable, @Nullable JsonObject effective,
                                @Nullable JsonObject base, @Nullable JsonObject overlay,
                                List<RuleIssue> issues) {}
// origin: overlay | datapack | mixed
// status: active | disabled | masked | invalid
```

**`editable` 语义（Lead 裁决，2026-xx 冻结）**：`editable = 该条目存在可编辑的规则内容（`base != null || overlay != null`）`。
- 纯数据包规则（只有 base）**必须为 true**——需求 5 要求数据包规则可「自定义覆盖/停用/屏蔽/恢复原始版本」，首次编辑即由保存流程在覆盖层建同 id 副本；
- 覆盖层副本、`mixed`、被停用或被屏蔽但有 base 的条目同样为 true；
- 只有「仅控制条目、无任何实体内容」（如屏蔽一个不存在的 id）才为 false；
- `status = invalid`（解码失败）的条目仍可为 true：客户端可保留只读 JSON 回退展示，是否允许表单编辑由客户端按 `status`/`issues` 自行决定，不通过 `editable` 表达。
- 已废弃：`RuleSourceIndex.Entry.writable()`（无调用方，语义与本节不一致，已删除）。`RuleSourceLayer.writable()`（仅 OVERLAY 为 true 的层可写性）**不受影响**，继续保留。

`RuleSnapshot` 扩展为：`record RuleSnapshot(int version, int contextRevision, List<RuleSnapshotEntry> entries, List<RuleIssue> issues)`（**`rules: List<JsonObject>` 与 `issues: List<String>` 被替换**，序列化保持 JSON 字符串形态）。
`RuleSnapshot.parse(String)` 保持 `DataResult<RuleSnapshot>`。

```java
/*** 选择目录类型：物品/方块/实体/战利品表/群系/维度/标签/流体/状态效果。 */
public enum RuleCatalogType { ITEM, BLOCK, ENTITY, LOOT_TABLE, BIOME, DIMENSION, TAG, FLUID, MOB_EFFECT }
/*** 目录条目：id 为注册表 id 或标签 id，label 为展示名（已本地化 key 或原文）。 */
public record RuleCatalogEntry(String id, String label, String subLabel, String icon) {}
/*** 目录分页结果。 */
public record RuleCatalog(RuleCatalogType type, int revision, List<RuleCatalogEntry> entries, boolean lastPage) {}
```

### 3.6.1 目录数据源接口（P4b 冻结，tree-dev 实现 / session-dev 消费）

冻结形状（**双方都不得单方面改动；要改先找 Lead**）：

```java
package com.meteorite.itemdespawntowhat.core.catalog;

/*** 单类候选数据源：分页由消费方切，本接口只保证全量与稳定顺序。 */
public interface RuleCatalogSource {
    RuleCatalogType type();
    int revision();                 // 内容修订号；内容未变时必须返回相同值
    List<RuleCatalogEntry> all();   // 全量候选，按 id 字典序稳定排序
}

/*** 内置数据源集合。 */
public final class RuleCatalogSources {
    public static List<RuleCatalogSource> builtin(MinecraftServer server);
}
```

- 落点：tree-dev 只写 `core/catalog/**`（必要时 `core/registry/**`）；session-dev 只写 `core/network/**`、`core/load/**`、`client/net/**`。
- 基础 DTO `RuleCatalogType` / `RuleCatalogEntry` / `RuleCatalog` 位于 `core/network/protocol`，**双方只读**。
- `all()` 必须可缓存（按 server 实例 + 重载失效），不得每次请求重建全部字符串。
- `RuleCatalogEntry.icon` 为物品/方块贴图 id 字符串，客户端解析失败须走缺省渲染，不得抛异常。
- 客户端「按名字挑 ID」只能走本目录，**不允许要求玩家手输注册表 id**（最高验收标准的支撑点）。
- **2026-10-XX Lead 追加裁决（增量批准）**：`RuleCatalogType` 允许**追加**枚举常量 `MOB_EFFECT`（状态效果，供 `arrow_rain` 效果的 `potion_effects` 子列表选择器使用）。理由：契约本行禁止要求玩家手输注册表 id，而该字段原先在目录里没有对应类型，玩家只能手打 `minecraft:poison`。追加规则：只允许**在末尾追加**，不得改名、不得重排既有 8 个常量（线上按枚举名序列化，`fromId` 宽松解析）；`core/catalog` 增加对应 `RegistryCatalogSource`（`BuiltInRegistries.MOB_EFFECT`）；客户端 `client/ui/screen/form/CatalogSuggestions.fromRegistry` 增加 `minecraft:mob_effect` 映射。不改协议版本（双端同包发布）。

### 3.7 管理指令

`/idtw config edit-lock status`（无需权限即可查看自身会话状态；有权限时显示持有者）与 `/idtw config edit-lock release`（需权限等级 ≥2，强制释放）。
「打开编辑器」的指令路径必须**先取锁成功**才向客户端发 `OpenRuleEditorPayload`；取锁失败发 `statusCode=LOCK_BUSY` + 持有者信息。

---

## 4. kit 引入契约（P1）

- 源目录：`D:/system_default/desktop/mc_mod_project/unsuspiciousBlock-1.21.1-multi/common/src/main/java/com/meteorite/unsuspiciousblock/client/ui/kit/`
- 源 commit：`625747e1f34ea957841d7136be4635469687c227`
- 目标目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/`
- 目标包名：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 复制 23 个文件，**只改** package 声明与 javadoc 中的全限定名；不改逻辑、不改类名、不加字段。
- 必须新增 `NOTICE-kit.md`（放在 kit 目录或 `common/src/main/java/.../client/ui/kit/NOTICE-kit.md`）记录：源仓库路径、commit、23 个文件清单与行数、重命名规则、已知差异（无）。
- kit 不得引入对 `core.model` 的依赖（保持通用）。

---

## 5. 客户端 UI 契约（P1/P5）

- 最低验收尺寸 **320×240**（GUI 逻辑坐标）；小尺寸页签可滚动、参数转单列；大屏扩宽但不留大片空白。
- 主题：灰色像素风（像素边框、槽位、原版字体、按钮按压层次、少量状态色）；禁止现代网页式圆角卡片与大面积留白。颜色必须辅以文字或图标。
- 所有界面文本走本地化 key，`en_us.json` 与 `zh_cn.json` 同步。
- **禁止任何绕过独占锁的生产入口**：客户端打开编辑器只能由服务端 `OpenRuleEditorPayload` 触发；快捷键在不持有会话时只能提示「请先用指令 /idtw config edit 打开」。
- 条件编辑器与效果编辑器共用同一「条件树控件」；`ConditionExpression` 是唯一数据模型。
- 第三方条件/效果类型：客户端 `ConditionEditorRegistry` / `EffectEditorRegistry` 未注册编辑器时，展示只读 JSON 摘要并**原样保留未识别字段**。

---


### 5.1 i18n 命名裁决（冻结，所有 agent 遵守）

现有 lang 文件里两套前缀并存，按其既有用途分工，**不要混用**：

| 用途 | 前缀 | 例 |
|---|---|---|
| 界面文本（按钮/标签/字段名/枚举名/提示/工具提示） | `gui.itemdespawntowhat.edit.*` | `gui.itemdespawntowhat.edit.field.dimension.dimensions` |
| 条件/效果字段标签 | `gui.itemdespawntowhat.edit.field.<condition|effect>.<field>` | `gui.itemdespawntowhat.edit.field.y_level.min` |
| 枚举取值名 | `gui.itemdespawntowhat.edit.enum.<enum>.<value>` | `gui.itemdespawntowhat.edit.enum.weather_kind.thunder` |
| 协议回执 / 校验错误 messageCode（走网络，见 §3.5） | `itemdespawntowhat.edit.*` | `itemdespawntowhat.edit.error.leaf_limit` |
| 条件/效果类型显示名 | `gui.itemdespawntowhat.edit.condition.<type>` / `...effect.<type>` | `gui.itemdespawntowhat.edit.condition.y_level` |

`en_us.json` 与 `zh_cn.json` 必须键集合完全一致。新增 key 一律**追加**到对应前缀段末尾，不删除、不重命名既有 key。

### 5.2 取值域与产品裁决（冻结）

- `delay_ticks`：后端不限，UI 输入范围 0..72000（1 游戏小时），0 显示为「立即」。
- `chance`：0..1，UI 以百分比输入（0..100，保留 1 位小数），写回除以 100。
- `y_level` / `light_level`：允许两端全空（= 恒真），编辑器给黄色提示「未设置任何边界」；不强制填写。
- `ClimateRange`（biome 的 6 个气候维度）：单维两端全空 = **该维度不构成约束**；UI 提示「留空 = 不约束」。`mode=climate` 而 6 维全空 → 校验失败。
- `spawn_entity.age`：UI 范围 -24000..24000，默认 0；提供「幼年」预设 -24000、「成年」预设 0。
- `notes`：UI 上限 1024 个 Unicode code point（后端不校验）。
- `amplifier`（药水效果）：后端 0 起算，UI 显示罗马数字（等级 = amplifier + 1），输入范围 1..255，写回减 1。
- 限额是**逐表达式**的：规则级条件树与每个效果级条件树各自受 128 叶 / 256 节点 / 深度 16 限制，**不跨表达式累加**。这是规划书 §5.3 的原意，按此实现。
- 条件树编辑器层级引导：不额外硬限；深度超过 6 时在节点旁显示「层级较深」提示。
- **编辑器保存的覆盖文件形态：顶层 JSON 对象，一个文件一条规则**；文件名由规则 id 生成（`:` 与 `/` 替换为 `_`，加 `.json`）。顶层数组形态只用于只读的数据包原始文件。

### 5.3 界面层与协议层的接缝（P5 冻结）

`client/net`（session-dev）负责会话与协议，**不得 import `client/ui/**` 或 `client/edit/**`**；界面层由 P5（kit-dev）实现，并通过下列注册接口反挂到协议层。`client/net` 只能定义接口与注册槽，不能引用具体界面类。

```java
package com.meteorite.itemdespawntowhat.client.net;

/*** 打开编辑器界面的请求：client/net 收到服务端授权后构造，界面层只读。 */
public record EditorOpenRequest(String sessionId, String targetId, int contextRevision,
                                int protocolVersion, String statusCode,
                                List<String> messageArgs, String fallbackMessage) {}

/*** 界面层实现的回调；client/net 只负责在客户端主线程调用。 */
public interface EditorScreenOpener {
    void open(EditorOpenRequest request);   // 客户端主线程
    void close();                           // 会话失效 / 被强制释放 / 断线时关闭界面
}

/*** 注册槽：客户端初始化时由界面层注册；未注册时 client/net 只记日志，不得抛异常。 */
public final class EditorScreenHooks {
    public static void setOpener(EditorScreenOpener opener) { }
    public static EditorScreenOpener opener() { return null; }
}
```

- 具体类名允许 P5 落地时微调，但**方向不得改变**：协议层定义接口与注册槽，界面层实现并注册。
- 界面层必须在客户端初始化时（Fabric `ItemDespawnToWhatClient.onInitializeClient` / NeoForge 客户端构造）调用 `EditorScreenHooks.setOpener(...)`。初始化接线文件归 Lead（`client/key/**`、两平台 `client/event/**`、`client/**/ItemDespawnToWhatClient.java`），kit-dev 只管提供可注册的实现。
- 快捷键在没有会话时**只提示**「请先用指令打开」（key `gui.itemdespawntowhat.edit.hint.use_command`），不得自行发起会话。

---

## 6. 分工与写入范围（write scope）

| 任务 | 负责方 | 写入范围 |
|---|---|---|
| P1 kit 引入 + 主题 + 通用控件 + 原型 | kit-dev | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit/**`、`.../client/ui/theme/**`、`.../client/ui/widget/**`、`.../client/ui/prototype/**`、kit 的 NOTICE |
| P2 条件树 + display_name + 内置条件/效果 + 样本 + Debug | tree-dev | `core/model/**`、`core/api/**`（仅条件相关）、`core/runtime/ExpressionEvaluator.java`、`core/type/condition/**`、`core/type/effect/**`、`core/type/Builtin*.java`、`core/service/BuiltinTypeRegistries.java`、`core/service/RuleReferenceValidator.java`、`core/debug/**`、`common/src/main/resources/data/itemdespawntowhat/idtw/**`、`core/command/RuleConvertService.java` |
| P3 独占会话 + 协议 | session-dev | `core/service/EditSessionManager.java`、`core/service/RuleEditService.java`、`core/network/**`、`core/command/RuleConfigCommands.java`、`fabric/**/network/**`、`neoforge/**/network/**` |
| P4 来源目录 + 客户端工作区 | pending（P2/P3 后） | `core/load/**`、`core/service/RuleSnapshotAssembler.java`、`client/edit/**`、`client/net/**` |
| P5 完整编辑器 | kit-dev | `client/ui/screen/**`（含 `client/ui/screen/form/**`）、`client/ui/widget/**`、`client/edit/**`、lang 文件 |
| P6 草稿持久化 + 撤销/重做 | kit-dev（task-9，blocked_by task-6） | `client/edit/**`（含 `client/edit/draft/**`）、lang 文件 |
| P4b 目录数据源 | tree-dev（task-8） | `core/catalog/**`、`core/registry/**` |
| P7 文档与 ADR | tree-dev（task-7） | `docs/**` |
| 入口接线 / 退役占位屏 / 平台客户端初始化 | **Lead** | `client/key/**`、`fabric/**/client/event/**`、`neoforge/**/client/event/**`、`fabric+neoforge/**/ItemDespawnToWhatClient.java` |

**P6 前置约束（P5 必须遵守）**：P5 的每一个编辑入口都必须走统一的「可撤销操作」门面，禁止绕过历史直接改 `RuleDraft`；P5 内可以先只维护当前值与脏标记，但调用点必须已收敛到一处，P6 只替换门面内部实现。

**冲突热点与规则**：`core/model/RuleValidation.java` 归 tree-dev；session-dev 若需改它，先只读、再通过 Lead 协调。
两个 agent 同时需要改同一文件时，**由 Lead 裁决**，禁止各自直接改。
`core/catalog/RuleCatalogSource.java` 与 `RuleCatalogSources.java` 是 session-dev 依契约 §3.6.1 建的骨架，**实现体归 tree-dev，接口形状双方都不得单方面改动**。

## 7. 验收基线（P0 冻结，P7 复验）

- 单人世界 + 无权限玩家：不出现编辑入口。
- 权限等级 <2 的玩家执行编辑指令：拒绝且不取锁。
- A 打开编辑器后 B 执行编辑指令：B 收到 `LOCK_BUSY` 并显示持有者；A 退出后 B 可打开。
- 暂停游戏（单人）时租约不流逝；恢复后按活动秒继续计时。
- 断线：会话在租约到期后释放；重连后需重新执行指令。
- 停服：全部会话清空。
- 空条件组、超过限额、旧格式 JSON：均在保存前被校验拦截并定位到具体字段，客户端显示可读错误且不写盘。
- 单条规则 100 个叶节点、深度 16 的树：可编辑、可保存、可重载。
- 中英文切换、GUI 缩放 1x/2x/3x、窗口 320×240 与 1920×1080：无文本溢出、无控件重叠。
