# UI kit：通用不可变树编辑

> 事实来源：`client/ui/kit/` 的 `UiTreePath`、`UiTreeEditor`、`UiTreeMoveInteraction`、`UiTreeCodec`；宿主 `ConditionTreeNodes`、`UiConditionTreeEditor`、`ConditionTreeOverlay`、`ConditionTreeJsonCodec`。
> 上承 [UI kit 入口](README.md)；输入捕获与结束原因见 [input-routing.md](input-routing.md) §3。

## 1. UiTreePath 与 UiTreeEditor

`UiTreePath(List<Integer> indices)` 使用父节点子项下标表示位置，空路径 `ROOT` 为根；`child`、`parent`、`isAncestorOf` 用于路径组合与祖先判断。构造复制列表并拒绝负下标。

`UiTreeEditor<N>` 直接绑定宿主节点，不创建第二份业务树。构造注入 `Adapter<N>`、根 `Supplier<N>`、接受变更的 `Consumer<Change<N>>`、候选根校验函数及非法路径/父节点提示；空根允许为 null。Kit 不读取注册表、不持有配置历史。适配器通过 `children`、`withChildren` 读写不可变子项，并用 `childCapacity` 声明容量；`UNLIMITED_CHILDREN` 表示不限制，`acceptsChildren(node, count)` 检查候选数量。

| 入口 | 行为 |
| --- | --- |
| `root()` / `query(UiTreePath)` | 读取当前绑定根/节点，路径不存在返回 null |
| `create(parent, index, node)` | 插入父节点子项；parent 为 null 仅用于创建空树根；父容量和插入下标先检查 |
| `delete(path)` | 删除指定节点；删除根产生空树，父重建由适配器决定 |
| `update(path, updater)` | 不可变替换节点；updater 返回 null 表示删除 |
| `accept(before, after, Kind)` | 校验并接受候选根；无变化与校验失败不触发绑定 |
| `setBeforeEdit(Runnable)` | 操作读取根之前完成宿主合法输入；宿主不能将非法文本写入配置 |
| `select(path)` / `selected()` | 选择真实节点或清空选择；不存在的路径忽略 |
| `setCollapsed(path, value)` / `collapsed()` / `expandAll()` | 管理只读可查询的折叠状态，不修改绑定树 |
| `setOnViewChanged(listener)` | 接收 `ViewChange(selected, collapsed, selection)`，与配置操作事件分开 |

`Outcome(changed, error)` 区分已接受、无变化和错误。配置操作仅在实际改变时产生一次 `Change(before, after, Kind, paths, removed)`；Kind 为 CREATE、DELETE、UPDATE、MOVE。三参数与四参数构造保留，默认路径映射和移除集合为空。宿主在接受回调内写历史，回填根不调用修改入口。节点与子项列表由宿主适配器保持不可变，不得修改既有节点。

实际接入：`ConditionTreeNodes` 适配现有 ConditionNode；`UiConditionTreeEditor` 的创建、删除、更新、查询、选择和折叠均调用 Kit。空 NOT 由宿主重建为 `Inverted(null)`；NOT 的容量为1，叶容量为0。逐表达式限额校验与本地化提示由宿主注入。`ConditionTreeOverlay` 将配置操作与输入路径迁移放入同一 EditSession 事务；JSON、参数表单、服务端保存均留在宿主。

## 2. 单节点移动与捕获

`UiTreeEditor.DropPosition` 为 BEFORE、INSIDE、AFTER；前后落点插入锚点的父节点，内部落点追加到锚点末尾。`beginMove(source)` 先执行 `beforeEdit`，随后读取更新后的树；`previewMove(before, source, target, position)` 只构造候选，不写绑定或历史。候选按移除后的父容量检查，自身及后代落点、根的同级落点、叶父节点和超容量拒绝，错误文本可由 `setMoveErrors(MoveErrors)` 注入；整个候选仍经过宿主 validator。

`MovePreview<N>` 包含前后根、源路径、锚点、落点方位、移动后的路径、完整旧路径→新路径映射和 Outcome。映射包含所有原节点及不变路径，不依赖值相等或对象身份。`commitMove(preview)` 只接受基准仍相同的候选，合法且有变化时产生一次 MOVE 事件，迁移选择与折叠；错误、取消、无变化不触发绑定。宿主把 `Change.paths` 转成业务字段路径，并与草稿写入放在同一历史事务内；前后字段路径须分别用对应树转换。

`UiTreeMoveInteraction<N>(editor, ended)` 提供真实输入生命周期：`begin(source)` 在 `beforeEdit` 后捕获基准，`preview(target, position)` 缓存当前落点候选，`clearPreview()` 清空落点，`isActive()`/`preview()` 查询状态，`end(reason)` 统一结束。仅 `UiInputCapture.EndReason.RELEASE` 提交最近展示的候选，其余原因取消；结束回调收到一次 Outcome。宿主须把按下/拖动/释放路由给捕获的同一个控件，并在失焦、隐藏、禁用、卸载和历史回填时结束交互。

条件树控件接入四像素拖动阈值，行上四分之一为 BEFORE，中间为 INSIDE，下四分之一为 AFTER；空分组/NOT 的添加占位映射到父节点内部。原树在拖动中保留，显示节点浮影、落点线及具体拒绝原因；滚轮可滚动选择屏外落点。释放使用同一落点投影并提交候选，Esc 仅取消手势，规则历史快捷键先取消再交给宿主。`UiConditionTreeEditor.isDragging()` 和 `endInteractions(reason)` 供页面暂停参数重建并取消捕获；JSON 视图切换、历史回填和卸载均取消手势。移动 NOT 唯一子项时保留空 NOT，未知叶节点与其原文按原节点移动。

## 3. 树编解码注入与只读源码

`UiTreeCodec<N,S>` 由宿主实现：`decode(S)` 返回 `DecodeResult<N>(root,error)`，成功且 root 为 null 表示空树，error 非空表示无法解析，宿主不得将其当空树覆盖。`encode(N)` 返回宿主外部表示，允许用 null 表示省略。Kit 不要求 JSON，也不依赖 Gson。

`UiTreeEditor.decode(codec, source)` 调用注入解码，返回结果由宿主回填，不提交操作事件；`encode(codec)` 编码当前绑定根。实际条件页注入 `ConditionTreeJsonCodec`，读取条件草稿、结构写回及源码展示均使用该能力。未知叶由宿主保留完整 JSON；未修改的已接受根保留原有字段。

宿主 `ConditionTreeOverlay` 默认显示树，顶部切换到当前表达式的缩进 JSON；无根时保持字段省略。`UiTextArea.setSelectableWhenReadOnly(true)` 配合 `setEditable(false)` 允许选中、滚动、Ctrl+A/C 和复制按钮；字符、删除、剪切与粘贴不修改文本。只读文本不消费局部撤销，Ctrl+Z 交给当前规则历史。切换视图完成合法字段、保留未完成输入，不产生配置历史；源码展示已接受值并提示未完成输入。

## 4. 多选与批量结构操作

`UiTreeEditor.selection()` 返回选择集合，`selected()` 是用于逐节点编辑的主节点。`setSelection(paths, primary)` 与 `toggleSelection(path)` 只发 ViewChange，原有 `select(path)` 切回单选；ViewChange 增加 selection，并保留原双参数构造。`normalizedSelection()` / 静态 `normalize(paths)` 按树顺序去重，移除已选祖先覆盖的后代；复制、删除、移动与包组均以这些最高子树为单位，不重复处理后代。

| API | 批量契约 |
| --- | --- |
| `deleteMany(paths)` | 按路径逆序移除最高子树，适配器保留空父；一次 DELETE 事件 |
| `createMany(parent, index, nodes)` | 在明确位置插入全部节点；空根仅允许一个节点，不自动建组或替换既有根；一次 CREATE 事件 |
| `wrapMany(paths, wrapper, invalidSelection)` | 同父连续节点可包装，宿主提供 AND/OR 等 wrapper；仅选中根时包住整个根；一次 UPDATE 事件 |
| `beginMove(paths)` / `previewMove(before, paths, target, position)` | 捕获后保持原树顺序，先移除全部源，再检查目标容量与整体候选；任一非法目标整批拒绝 |
| `UiTreeMoveInteraction.begin(paths)` | 使用同一移动生命周期；MovePreview.sources 保存归一化的最高源子树，正常释放一次 MOVE 事件 |

批量事件的 `Change.paths` 包含所有存活原节点的旧→新路径，`removed` 记录被删除的最高子树。宿主先按前树路径清除删除子树的输入缓冲，再完整迁移存活字段；两者与 JSON 写入在同一事务内完成。复制粘贴只携带已接受业务节点，不复制非法文本缓冲；粘贴节点由宿主解码成新节点，原节点输入仍按映射保留。容量、整树限额与无变化判断沿用同一核心，错误与无变化不产生绑定事件。

条件页面实际接入 Ctrl 点击切换、Shift 点击可见行范围、Ctrl+A 选择可见真实节点；点击已选节点先保留多选以允许拖动，未拖动释放后转为单选。Ctrl+C/V 与工具栏复制/粘贴使用宿主系统剪贴板；剪贴板交换格式为单个节点 JSON 或节点 JSON 数组，正式配置格式保持不变。剪贴板读取检查节点/叶/深度限额及草稿结构；候选整树仍由宿主 validator 验证。粘贴在选中分组末尾、选中叶之后或空占位对应父内插入，拒绝已有子项的 NOT，不自动替换或合并。源码只读页只分发文本键盘事件，不调用树粘贴。

工具栏的包 AND／OR 对应 wrapMany；不同父或不连续选择给出具体错误。参数表单只绑定主节点，批量结构操作不覆盖多个条件参数。未知叶复制直接从共享草稿读取完整原文，移动与包装复用原条件节点；剪贴板和配置 JSON、系统剪贴板、条件限额均不进入 Kit。
