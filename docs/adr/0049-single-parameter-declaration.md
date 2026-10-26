# ADR-0049：类型参数只声明一次，编解码、校验与编辑器共同消费

- 状态：已实施。
- 依据：逐类型核对五个写点后确认同一事实重复（以 `lightning` 为例：默认值与边界常量、Codec 区间、校验区间、客户端描述符的字段区间、客户端新建初值），且 `core/type` 下散落 `Codec.intRange` / `Codec.doubleRange` 字面量。
- 相关决策：[ADR-0013](0013-dfu-codec-and-flat-type-dispatch.md)（用 DFU Codec 统一序列化并按扁平字段分发；本 ADR 只把字段的形状与默认值收进一处声明，分发方式不变）。

## 背景

内置条件与效果的类型参数原本分散在五个地方：类型内的默认值与边界常量、`codec()` 里的区间与 `optionalFieldOf`、`validateParams` 里的区间判断、客户端描述符里手写的 `EditorField` 字段表、客户端的新建初值表。五份事实靠人工同步，改默认值或改窗口时容易漏改，客户端字段表还要自行维护标签 key、枚举分组名、单位、窗口与提示。

`CommonFields` 只提供延迟与概率的编解码片段，无法为编辑器提供字段形状；客户端描述符因此逐类型手写，`core` 侧的区间与客户端侧的区间是两份独立事实。参数校验中的单字段值域判断与 Codec 的区间也各自书写。

## 决策

1. **声明落在 `core/api`**：新增 `core/api/param` 包，`ParamSpec` 为 sealed 族（`IntSpec`、`FloatSpec`、`DoubleSpec`、`BoolSpec`、`EnumSpec`、`RefSpec`、`RefListSpec`、`ResLocSpec`、`ResLocListSpec`、`MapSpec`、`NestedSpec`、`NestedListSpec`），`ParamList` 承载有序不可变声明；`TypeDefinition` 增加 `params()`（默认 `ParamList.EMPTY`）。放契约包是因为 `core/type` 与 `client/edit` 都允许依赖 `core/api`，无需扩任何允许清单；放 `core/model` 会与 `TypeDefinition` 形成反向依赖。
2. **一个声明同时承载**：字段名、wire 存在性（必填、有默认值的可选、可空可选）、值域、默认值、新建初值、单位、编辑器投影元数据（标签后缀、枚举分组名、目录注册表、常用窗口、精度、提示、是否在表单显示）与声明顺序。
3. **四个读点由声明派生**：`fieldFor` / `fieldForNullable` 生成 `MapCodec` 的字段构件（必填用 `fieldOf`，可选保留 `optionalFieldOf`，可空字段仍产出 `Optional`），`check` 生成单字段校验，客户端 `ParamProjection` 投影成 `EditorField`，新建条目用 `initialValues()` 写初值。
4. **声明不承载跨字段校验**：模式相关判断、字段间引用、动态引用校验、运行期上限与执行器/产出声明仍写在各自的 `validateParams`、`validateRuntimeLimits` 与类型定义里；`ParamSpec` 只见单字段，跨字段逻辑塞进声明会让它变成校验脚本。
5. **通用效果字段由 `CommonFields` 声明**：延迟与概率以 spec 常量（`DELAY_TICKS`、`CHANCE`）加 `EFFECT_PARAMS` 提供，各效果的 `PARAMS` 引用同一常量，不再各自拼 Codec 片段。
6. **历史 wire 字段保留但可隐藏**：`catalyst_present.count`、`fluid_present.fluid` 之类的历史兼容字段仍在声明与 JSON 契约中，只是标记为不参与表单显示，避免表单重复出行。
7. **客户端侧不再逐类型手写**：内置描述符遍历客户端类型表按 `params()` 投影注册，新建初值按 `initialValues()` 派生；`place_block` 的 `limit` 依赖同对象的半径，是唯一保留的显式动态初值。
8. **两侧复用 SPI 装配**：服务端启动与客户端首次使用类型表时分别调用 `BuiltinTypeRegistries.create()`；客户端按已安装模组的 `RuleTypeProvider` 发现类型，非空 `params()` 自动投影表单，初值、默认值与条件叶 Codec 共用已冻结类型表。手动预注册的描述符优先，内置无参类型保留说明行。
9. **单字段校验共用值域**：通用效果字段与 Y 高度走 spec 的 `check`，调用方可保留业务错误文案与来源；浮点区间校验同时拒绝非有限数。气候端点与交互步长由 `ClimateDomain` 供后端和编辑器共用，端点顺序仍由类型校验。`delay_ticks` 的 1200 是 UI 常用窗口，合法域为非负 int。

## 考虑过的替代方案

- **声明放 `core/model`**：`TypeDefinition` 在 `core/api`，契约无法依赖模型，会形成反向依赖。否决。
- **声明放 `core/type`**：`client/edit` 的允许清单不含 `core/type`，需要扩依赖边界，并让编辑数据包认识类型实现。否决。
- **用注解处理器或反射从 record 生成整段 Codec**：DFU 的 `MapCodec` 需要显式记录组合，代码生成会引入构建期依赖，并让字段顺序与 JSON 契约脱钩。否决。
- **只在 core 侧声明、客户端继续手写描述符表**：字段表与初值表仍是第二份事实，正是本次要消除的重复。否决。
- **用声明表达全部校验（含跨字段与引用）**：`ParamSpec` 只见单字段，跨字段、模式相关与注册表存在性无法表达，强行塞入后声明会变成校验脚本。否决。
- **用注解或字符串表达式书写标签与枚举组**：与现有标签 key 的派生规则不一致，也会把界面文案散进 core。否决。
- **把 `EditorFieldType` 的逐字段谓词（是否列表、是否注释行等）并入声明**：属于编辑器内部控件选择，与「参数只有一份事实」正交，另行收敛。否决。

## 后果

- 新增内置类型只需写参数 record、声明常量与 `PARAMS`，Codec、单字段校验、表单字段与新建初值自动一致；`core/type` 不再出现区间与默认值字面量。
- 字段的 wire 存在性与表单必填标记同源：声明必填的字段在表单即必填，声明可空或有默认值的字段在表单即可选。
- 描述符顺序等于声明顺序；调整参数顺序会同时改变 JSON 字段顺序与表单顺序，这是有意保留的单一事实。
- 标记为不显示的参数仍参与编解码与校验，只在编辑器中隐藏，因此可继续读写历史字段。
- 客户端安装并通过 SPI 发现第三方类型后，非空 `params()` 自动获得表单支持；必填字段的新建预填由声明的 `initialValue` 提供。空声明的第三方类型与本地未发现的类型走只读 JSON 回退。
- provider 在服务端与客户端分别装配，注册逻辑只建立类型定义，使用两侧均可加载的类，并独立于当前世界与服务端实例。
- 跨字段与引用校验仍由类型自己承担，新增这类规则时不能期待声明层代劳。
