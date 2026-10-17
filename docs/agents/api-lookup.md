# API 查询：原版与平台源码

改动涉及原版、NeoForge 或 Fabric API、且用法无法从已有上下文判断时，按本页取源码。先判断是否真的需要查，再选查询工具，最后才申请源码级深挖。

## 判定：是否需要查

- API 用法明确：直接实现，不查。
- 命中任一情况才进入下一级：参数或返回值不确定；编译报错且不是笔误；方法名、类名存在歧义。

## Level 2：工具查询

1. `mc-developing-mcp`：查本地数据库中的 API 源码或官方文档。
2. IDEA MCP 反编译：`mc-developing-mcp` 取不到原版类时的首选替代。merged jar 内每个类通常有 vanilla、neoforge、loom-cache 三份，优先 vanilla；仅当目标符号涉及平台补丁、平台特有行为或 vanilla 中不存在时，才读对应平台类。
   - 三份分别位于 `common/build/moddev/artifacts/vanilla-*-minecraft-merged.jar!`、`neoforge/build/moddev/artifacts/neoforge-*-merged.jar!`、`.gradle/loom-cache/minecraftMaven/**/-merged.jar!`。
   - 定位类：`search_symbol(include_external=true, q="<类名>")`，返回 jar 内 `net/minecraft/.../<类>.class` 路径。
   - 读源码：`read_file(file_path="<绝对路径>\\...vanilla-*-minecraft-merged.jar!\\net\\minecraft\\<包>\\<类>.class")`，直接返回反编译后的完整 `.java`，可用 `limit` / `offset` 取片段。
   - 跳转定义：对项目内源码中的原版符号调用 `get_symbol_info`（给 file/line/column），返回声明片段与 documentation，`declarationFile` 指向 merged jar。
   - 已知限制：`get_symbol_info` 的 `filePath` 不认 jar 的 `!` 路径（报 File not found），只能对项目内源码用；`search_regex` / `search_text` 不索引 jar 内部，返回空。读原版整类只能走 `read_file` + jar 路径。

## Level 3：源码级深挖，先取得同意

- 触发条件：需要源码级理解，且 Level 2 无法解决（例如目标 API 不在已索引的库中）。
- 执行前向用户说明理由与计划，取得同意后再查网络资料、访问github仓库或进一步反编译。
