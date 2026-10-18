# Issue tracker：本地 Markdown

工程技能使用 `.scratch/` 下的 Markdown 文件跟踪任务。

## 文件约定

- 每个功能使用 `.scratch/<feature-slug>/`。
- 技能生成的任务 spec 使用该目录下的 `spec.md`；项目正式文档不放在 `.scratch/` 内，规则与背景请链接 `docs/architecture/`、`docs/adr/` 或 `GLOSSARY.md`。
- 每张实施任务使用独立文件 `issues/<NN>-<slug>.md`，编号从 `01` 开始，禁止合并成单张总表。
- 计划落地后移出 `.scratch/`，按 `<feature-slug>` 归档到本地归档目录（`.local/` 下、被 git 忽略）；归档路径不得出现在现役文档的链接中。
- 分诊任务顶部使用 `Status:` 记录状态，名称见 `triage-labels.md`；使用 `Category:` 记录 `bug` 或 `enhancement`。
- 评论与讨论追加到文件末尾的 `## Comments` 下。

## 技能操作

- “发布到 issue tracker”：按文件约定创建本地文件。
- “获取相关 ticket”：读取指定任务文件；仅给编号时，在对应功能目录定位任务。
- 分诊标签变更：更新任务文件的 `Status:`。

## Wayfinder 操作

- 工作地图使用 `.scratch/<effort>/map.md`，子任务使用 `issues/<NN>-<slug>.md`。
- 子任务以 `Type:` 记录 `research`、`prototype`、`grilling` 或 `task`。
- Wayfinder 子任务以 `Status:` 记录 `open`、`claimed` 或 `resolved`；认领时同时写入 `Assignees: <认领名>`，本地无登录态，认领名只存在于该文件。
- 依赖使用 `Blocked by: NN, NN`；全部依赖为 `resolved` 才解除阻塞。
- 按编号选取未认领、未阻塞的开放任务；开始工作前保存 `Status: claimed`。
- 完成后将答案写入 `## Answer`，保存 `Status: resolved`，并在地图中追加结论与任务链接。
