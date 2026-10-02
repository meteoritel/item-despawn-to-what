# 用 DFU Codec 统一序列化，并按扁平字段做类型分发

## 背景
旧链路用 Gson + `@SerializedName` 手写序列化：61 个文件、两处独立 Gson 实例、三套并存的校验策略（未知字段拒整文件 / 启动逐条丢弃 / reload 一条非法即整体失败），并且 Gson 默认静默忽略未知字段，配置错误会"静默退化"而不是报错。同时效果与条件是多态类型，需要一个能按 `type` 查注册表的解码入口。

## 决策
1. **一套 DFU Codec**：规则、效果、条件叶全部用 `RecordCodecBuilder` / `MapCodec` + `RegistryOps`；效果与条件叶经注册表按 `type` 分发。
2. **扁平分发**：`core/api/TypeDispatch.flat(registry, typeIdGetter)` 让类型专属字段与通用字段处在**同一个 JSON 对象**中，不产生 DFU 默认 `dispatch` 的嵌套 `value` 字段。
3. **类型定义自带编解码与校验**：`TypeDefinition<P>` = `id()` + `codec()` + `validateParams()`；`EffectType` 附加 `executor()`，`ConditionType` 附加 `evaluator()`，一次查表同时拿到三者。
4. **可空字段经 Optional 中转**：DFU 的 `DataResult` 内部用 `Optional.of`，任何 codec 产出 null 都会在解码期抛 NPE；可空字段一律以 `Optional` 承载，在记录构造的最后一步 `orElse(null)`。
5. **统一严格校验**：文件解析失败 → 整文件拒载；单条语义非法 → 该条拒载，其余照常；启动与 reload 行为一致；不做任何隐式写回。
6. 加载层的注入边界是 `JsonObject`（`core/api/RuleDecoder`），因为它要在解码前补齐推导 id、剔除 `disabled`/`delete` 控制字段；模型与运行时仍全程 DFU Codec。

## 考虑过的替代方案
- **沿用 Gson + 手写分支**：改动小，但注册表分发、注册表感知的类型校验（物品/方块/流体引用是否存在）都要自己重写一遍，且无法复用原版 Codec 的持有人解析。否决。
- **DFU 自带 `dispatch`**：写法最标准，但会在 JSON 里多出一层 `value` 包装，数据包作者要写 `"type": "...", "value": { ... }`。可读性与手写成本都变差。否决。
- **未知字段一律拒载**：最严格，但第三方数据包带注释字段就会被整条拒掉；改为顶层未知字段 WARN。否决。

## 后果
- 数据包作者获得注册表级校验（引用不存在即报错），错误信息带来源与字段路径。
- 类型新增/变更只影响自己的 Codec 与校验器，`TypeDispatch` 与注册表不动。
- 编码未注册类型会抛 `IllegalStateException`（编程错误），GUI 保存路径必须捕获并转成用户可读错误。
- 类型参数级未知字段检测需要各类型暴露字段集合，尚未实现（记入阶段⑤/⑥）。
