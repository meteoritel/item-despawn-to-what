# 不可变树编辑、树布局与画布原语（client/ui/kit）

> 层级：基础组件层；模块路径：`client/ui/kit`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 不可变树编辑、树布局与画布原语 | `client/ui/kit` | §2 |

## 2. 模块：不可变树编辑、树布局与画布原语

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.kit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit`
- 覆盖类型：8（本分组类型，不含 `package-info.java`；包级说明见 [UI kit 入口](README.md)）

本分组覆盖 `UiTreeEditor`、`UiTreePath`、`UiTreeCodec`、`UiTreeMoveInteraction`、`UiTidyTreeLayout`、`UiDotGrid`、`UiOrthoLink`、`UiRubberBand`。

### 2.2 职责

- `UiTreePath` 用「父节点子项下标」序列表示节点位置；空列表表示根，所有路径构造时复制并拒绝负下标。
- `UiTreeEditor` 绑定宿主的不可变节点，提供结构操作、多选、折叠与移动预览；配置变更与视图变化走两条独立事件。
- `UiTreeCodec` 由宿主注入树的外部表示编解码，kit 不解释配置格式与节点业务语义。
- `UiTreeMoveInteraction` 把一次拖动绑定到 `UiInputCapture`：预览不写绑定，正常释放提交一次，其余原因取消。
- `UiTidyTreeLayout` 计算通用 tidy 树布局：固定层高，水平方向按子树宽度装箱，父节点在自己的子树带内居中。
- `UiDotGrid` 在给定屏幕矩形内按缩放档位换算步长并逐点绘制 1 像素方格。
- `UiOrthoLink` 绘制父节点底部到子节点顶部的正交折线，按「竖 → 横 → 竖」三段绘制并取整到屏幕像素。
- `UiRubberBand` 记录空白拖拽的锚点与当前点，给出整数像素包围盒并按内容矩形判定相交。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiTreePath` | record | `UiTreePath(List<Integer>)`；`ROOT`；`child(int)`；`parent()`；`isAncestorOf(UiTreePath)` | 规范构造器复制列表并拒绝负下标；`parent()` 对空路径返回 `ROOT` |
| `UiTreeEditor` | final class | 构造 `(Adapter<N>, Supplier<N>, Consumer<Change<N>>, Function<N, Component>, Component, Component)`；`adapter()`；`root()`；`decode(UiTreeCodec, S)`；`encode(UiTreeCodec)`；`setBeforeEdit(Runnable)`；`setMoveErrors(MoveErrors)`；`setOnViewChanged(Consumer<ViewChange>)`；`selected()`；`collapsed()`；`selection()`；`select(UiTreePath)`；`setSelection(Collection<UiTreePath>, UiTreePath)`；`toggleSelection(UiTreePath)`；`normalizedSelection()`；`normalize(Collection<UiTreePath>)`；`setCollapsed(UiTreePath, boolean)`；`expandAll()`；`query(UiTreePath)`；`create`；`createMany`；`delete`；`deleteMany`；`update`；`accept(N, N, Kind)`；`beginMove`；`previewMove`；`commitMove`；`wrapMany` | 嵌套 `interface Adapter`、`enum Kind`、`enum DropPosition`、`record Change`、`record MoveErrors`、`record MovePreview`、`record Outcome`、`record ViewChange` |
| `UiTreeCodec<N, S>` | interface | `decode(@Nullable S)`；`encode(@Nullable N)`；嵌套 `record DecodeResult<N>(@Nullable N, @Nullable Component)` | `DecodeResult#succeeded()` 等价于 `error == null` |
| `UiTreeMoveInteraction<N>` | final class | `UiTreeMoveInteraction(UiTreeEditor<N>, Consumer<UiTreeEditor.Outcome>)`；`begin(UiTreePath)`；`begin(Collection<UiTreePath>)`；`isActive()`；`preview()`；`preview(UiTreePath, UiTreeEditor.DropPosition)`；`clearPreview()`；`end(UiInputCapture.EndReason)`；`captureEnded(UiInputCapture.EndReason)` | 实现 `UiInputCapture.Target` |
| `UiTidyTreeLayout<N>` | final class | `UiTidyTreeLayout(int levelPitch, int siblingGap)`；`layout(@Nullable N, @Nullable Source<N>)`；`x(N)`；`y(N)`；`widthOf(N)`；`contentWidth()`；`contentHeight()`；`clear()`；嵌套 `interface Source` | `levelPitch` 必须为正、`siblingGap` 不得为负；`Source`：`children(N)`、`width(N)` |
| `UiDotGrid` | final class | `draw(GuiGraphics, double scale, int x0, int y0, int x1, int y1, int baseStep, int color)` | 私有构造，静态工具 |
| `UiOrthoLink` | final class | `drawDown(GuiGraphics, int x1, int y1, int x2, int y2, int thickness, int color)` | 私有构造，静态工具 |
| `UiRubberBand` | final class | `begin(double, double)`；`update(double, double)`；`clear()`；`active()`；`minX()`；`minY()`；`maxX()`；`maxY()`；`intersects(int, int, int, int)`；`draw(GuiGraphics, int, int)` | 默认未激活；坐标非有限时 `begin`/`update` 被忽略 |

### 2.4 依赖关系

- 依赖本模组：无。本分组 8 个文件均不 import 其它模组包；同包类型直接引用，不产生 import。
- 依赖外部：`java.util`（4 个文件 import，共 20 处）、`java.util.function`（2 个文件，5 处）、`net.minecraft.client.gui`（3 个文件，3 处，`GuiGraphics`）、`net.minecraft.network.chat`（2 个文件，2 处，`Component`）、`org.jetbrains.annotations`（3 个文件，3 处，`Nullable`）。
- 被依赖：`client/ui/widget`（4 个文件 import，共 11 处）、`client/edit`（2 个文件，3 处）、`client/ui/screen/form`（1 个文件，2 处）；合计 7 个文件、16 处。

### 2.5 关键实现位置

- `UiTreePath#child`/`#parent`/`#isAncestorOf`：路径组合与祖先判断；`isAncestorOf` 要求严格更短且前缀相同。
- `UiTreeEditor#normalize`：按树顺序去重，并去掉「已有祖先被选中」的后代；`normalizedSelection` 使用当前选择集合。
- `UiTreeEditor#accept`：前后根相等直接返回无变化；否则交给宿主 `validator`，通过后以一次 `Change` 提交。
- `UiTreeEditor#createMany`：根分支只在空树上接受单个节点；非根分支校验父节点、插入下标与适配器容量后一次提交。
- `UiTreeEditor#deleteMany`：以最高选中子树为单位逆序移除，父节点由适配器重建，一次 `DELETE` 提交。
- `UiTreeEditor#previewMove`：先移除全部源，再按移除后的父节点检查容量与边界；拒绝原因按 `MoveErrors` 的五类文案返回。
- `UiTreeEditor#commitMove`：候选的 `before` 必须仍等于当前根，否则以 `MoveErrors#stale` 拒绝；提交后重映射折叠路径并迁移选择。
- `UiTreeEditor#wrapMany`：只接受同父且连续的选中节点，包装节点由宿主提供；仅选中根时包装整个根。
- `UiTreeEditor#removedPath`/`#insertedPath`/`#relocate`：在旧路径与新路径之间做下标平移，供 `Change#paths` 使用。
- `UiTreeEditor#collectPaths`：遍历整棵树生成「旧路径 → 新路径」映射，映射返回 `null` 的路径不写入结果。
- `UiTreeMoveInteraction#captureEnded`：只有 `EndReason.RELEASE` 且存在最近预览时调用 `commitMove`，其余原因回报未变化。
- `UiTidyTreeLayout#subtreeWidth`：先记录自身宽度再递归子节点，结构含环时回边读到已记账值，递归必然终止。
- `UiTidyTreeLayout#place`：节点水平居中于自己的子树带，`y` 取「层号 × 层高」，子节点从子带总宽居中的游标开始排列。
- `UiDotGrid#draw`：取矩形两端点的 `min`/`max`，按 `max(1, round(baseStep * scale))` 得步长，点数超限则放弃绘制。
- `UiOrthoLink#drawDown`：横段高度取两端中点，竖段各画到横段边界；`y1 > y2` 时自动对调起止端。
- `UiRubberBand#minX`/`#minY`/`#maxX`/`#maxY`：左上 `floor`、右下 `ceil`；`intersects` 使用半开区间判定。

### 2.6 代码现状（可核对）

- `UiTreeEditor.Kind` 只有 `CREATE`、`DELETE`、`UPDATE`、`MOVE` 四个取值；`wrapMany` 以 `Kind.UPDATE` 提交，没有独立的包装种类。
- `UiTreeEditor#accept` 的公开重载只有三参数 `(before, after, kind)`；带 `paths`、`removed`、`added` 的重载均为 `private`。
- `UiTreePath#child(int)` 不做非负校验，负下标要等到 `UiTreePath` 规范构造器才抛 `IllegalArgumentException`。
- `UiTreePath#parent()` 在空路径上返回 `ROOT` 本身，不抛异常。
- `UiTreeEditor#beginMove` 先执行 `beforeEdit` 再归一化并检查路径存在；`#previewMove` 与 `#commitMove` 不执行 `beforeEdit`。
- `UiTreeEditor#createMany` 的根分支要求当前根为 `null` 且节点数为 1，否则返回 `invalidParent`；非根分支用 `invalidPath` 报告非法下标。
- `UiTreeEditor` 三类批量操作成功后都会改写选择：`deleteMany` 清空选择，`createMany` 选中新插入节点，`wrapMany` 选中新包组路径。
- `UiTreeEditor` 构造期用四个 `invalidParent` 与一个 `invalidPath` 组成默认 `MoveErrors`：`cycle`、`rootSibling`、`noChildren`、`fullParent` 取 `invalidParent`，`stale` 取 `invalidPath`。
- `UiTreeEditor#previewMove` 的容量检查发生在移除全部源之后，边界按「同父且位于插入点之前」的源数量回退。
- `UiTidyTreeLayout#x`/`#y`/`#widthOf` 没有 JavaDoc；未参与本次布局的节点返回 `0`，与合法坐标 `0` 不可区分。
- `UiTidyTreeLayout` 全部索引表使用 `IdentityHashMap`，结构相等但不同对象的节点不会互相覆盖。
- `UiTidyTreeLayout#contentHeight` 为「层数 × 层高」，节点自身高度不由本类决定；`#contentWidth` 为根子树总宽。
- `UiDotGrid` 的步长公式为 `max(1, round(baseStep * scale))`；`MAX_DOTS = 4_000_000L` 是私有常量，超限时静默放弃绘制。
- `UiOrthoLink` 只有 `drawDown` 一个公开方法，没有向上或左右方向的折线变体；横段高度取两端中点，线宽至少 1 像素。
- `UiRubberBand` 不提供锚点或当前点的读取入口，只暴露包围盒四边与 `active()`；包围盒以「向外取整」定义。
- `UiTreeCodec.DecodeResult#succeeded()` 只判断 `error == null`；成功且 `root` 为 `null` 表示空树，而失败时 `root` 也可能为 `null`，两种情况只靠 `error` 区分。
- `UiTreeMoveInteraction#preview` 以「目标路径 + 落点位置」为缓存键，二者都不变时复用上一次候选。
- `UiTreeEditor` 的多数 getter 与 setter 使用 `//` 行注释而不是 JavaDoc；同分组的 `UiTidyTreeLayout#x`/`#y`/`#widthOf`、`UiOrthoLink#drawDown`、`UiRubberBand#minX` 等公开成员同样没有 JavaDoc。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `UiTreeEditor` | 不可变树的结构操作、选择与移动 | `createMany`、`deleteMany`、`wrapMany`、`previewMove`、`commitMove`、`normalize` |
| `UiTreePath` | 不可变节点路径 | `ROOT`、`child`、`parent`、`isAncestorOf` |
| `UiTreeCodec` | 宿主注入的树编解码 | `decode`、`encode`、`DecodeResult` |
| `UiTreeMoveInteraction` | 移动的捕获与一次提交 | `begin`、`preview`、`end`、`captureEnded` |
| `UiTidyTreeLayout` | tidy 树布局 | `layout`、`x`、`y`、`contentWidth`、`contentHeight` |
| `UiDotGrid` | 画布点阵网格 | `draw` |
| `UiOrthoLink` | 正交折线连接 | `drawDown` |
| `UiRubberBand` | 框选矩形 | `begin`、`update`、`intersects`、`draw` |

`package-info.java` 承载包级公开面说明，见 [UI kit 入口](README.md)。

## 4. 相关文档

- [UI kit 入口](README.md)
- [输入路由、捕获与值交互](input-routing.md)
- [数值策略、滑块与区间](value-controls.md)
- [文本历史与文本测量](text-controls.md)
- [internals 索引](../README.md)
