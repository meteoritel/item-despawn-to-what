# ADR-0021：display_name 与稳定技术 ID 的分工

- 状态：已实施（P2，2026-10-02）；字段与校验见 `core/model/Rule.java`、`core/model/RuleFields.java`、`core/model/RuleValidation.java`。
- 依据：[实施契约 §2.7](../plan/plan-frontend-rewrite-contract.md)（Rule 增加 display_name）、[契约 §5.2](../plan/plan-frontend-rewrite-contract.md)（覆盖文件命名）、[P2 表单规格 §2](../plan/plan-frontend-rewrite-forms.md)。
- 相关术语：`CONTEXT.md` 中的「规则 (Rule)」「规则 id (Rule Id)」。

## 背景

规则的稳定标识是 `ResourceLocation` 形式的 id（例如 `mypack:stone_to_diamond`）：它是**覆盖、删除、日志与命令引用**的依据，也被用来推导覆盖层文件名。但 id 必须唯一且格式受限（小写、命名空间、路径），直接用作用户界面的标题会牺牲可读性；而如果让界面标题参与身份识别，改一次标题就会变成「删除旧规则 + 新建规则」，破坏覆盖关系与引用。

## 决策

1. **Rule 增加可选展示名**：`public record Rule(ResourceLocation id, boolean enabled, int priority, @Nullable String displayName, @Nullable String notes, SourceMatcher source, ConditionExpression conditions, int triggerAfterSeconds, List<Effect> effects)` —— `displayName` 紧跟在 `priority` 之后、`notes` 之前（字段顺序冻结，见契约 §2.7）。
2. **JSON 字段名 `display_name`**（`RuleFields.DISPLAY_NAME`），加入 `KNOWN_RULE_FIELDS`，因此未知字段检查不会把它当成噪声。
3. **规范化**：`trim` 后为空串 → 视为未命名（`null`）；长度按 **Unicode code point** 计，超过 `ConditionLimits.MAX_DISPLAY_NAME_CODEPOINTS`（128）即校验失败（`RuleValidation`）。
4. **技术 id 仍是唯一身份**：覆盖、删除、索引、命令、日志与覆盖文件名一律按 id 匹配；`display_name` **不参与**任何身份判定，也不影响规则排序（排序键为优先级 desc → 条件叶数 desc → 定义序）。
5. **覆盖文件命名由 id 派生**：`:` 与 `/` 替换为 `_`，追加 `.json`；一个文件一条规则（契约 §5.2）。
6. **多语言不进 id**：`display_name` 是用户自由文本，界面固定文案（字段标签、枚举名、类型显示名）走 i18n 前缀分工（契约 §5.1：界面 `gui.itemdespawntowhat.edit.*`，网络 messageCode `itemdespawntowhat.edit.*`）。
7. **创建/复制语义**：新建或复制规则时由用户给出新 id；复制不改变原 id 对应关系；重命名 id 等价于「新建 + 删除」，界面必须按此提示。

## 理由

- 身份与展示分离，才能让「改标题」是无副作用的编辑操作；否则每次改标题都会断开覆盖关系、丢失 in-place 修改历史。
- 只允许改 `display_name`、不允许改 id，让覆盖层的「同 ID 覆盖」语义保持可预测，也避免了文件名与 id 不一致带来的重名冲突。
- 按 code point 而不是字节或 UTF-16 单元计数，是因为中文/emoji 在多字节编码下字节数远大于显示宽度；用 code point 才能让「128 个字符」对中英文用户是同一个直觉。
- `trim` 后为空视为 `null`，避免「一串空格」被当成已命名规则，保证 `isEmpty` 语义（是否有展示名）稳定。
- 字段插入位置紧跟 `priority` 并在 `notes` 之前，是因为 `notes` 的 JSON 顺序与显示顺序已经是「优先级 → 说明」，`display_name` 放在两者之间即可自然被 UI 读出而无需额外映射。

## 后果

- 旧 JSON 没有 `display_name` 字段时解析为 `null`，无需迁移。
- 所有 `new Rule(...)` 调用点必须按新字段顺序更新（当前唯一构造点在 `RuleCodecs`）。
- 界面把 `display_name` 作为标题、把 id 作为副标题/技术标识展示；保存不改 id。
- 超过 128 code point 的回显走校验错误，不得截断后写入。
- 覆盖文件名与 id 的映射关系固化，改名操作必须显式提示其「新建 + 删除」后果（与 [ADR-0020](0020-source-catalog-and-overlay-control-entries.md) 的覆盖语义配合）。
