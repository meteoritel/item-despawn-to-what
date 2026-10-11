<!-- 以下是项目 Agent 提示词 -->

## 基本环境
- 项目背景：minecraft 1.21.1 neoforge/fabric mod项目
- 语言：java(jdk21)
- 反混淆映射：mojang 官方映射

## 文档
- 开发者文档写入 `docs/dev/`，说明架构、模块职责、API 与实现机制。
- 文档入口：[开发者文档索引](docs/dev/README.md)；文档阅读与维护约定见 [conventions.md](docs/dev/conventions.md)。
- 指针方向：本文件只允许定义**单向**指向其它文档的引用；其他任何文档不得以链接、路径指针或反向依赖的方式指回本文件。

## 核心原则
按任务复杂度分级执行：

- 简单任务：单文件或少量文件小改动，不涉及跨子系统契约，无需新增架构决策 → 立刻执行。
- 中等复杂任务：涉及多个文件或局部设计选择，但不改变跨子系统接口 → 先做规划再执行。
- 复杂任务：涉及跨子系统改动、平台差异新增、Mixin 侵入性方案、API 不确定需外部查询或架构契约调整 → 先询问用户是否拆成小任务。
- 特殊：若改动仅涉及json文件，则仅进行json合法性校验；若改动仅涉及markdown文档，则无需格式校验。

分级争议时按高一级别处理；信息足够时直接按上述方式执行。

## 基本开发准则
- 平台隔离：`common/` 不得 import 任何平台类；平台专属代码只能写在 `fabric/` 或 `neoforge/` 中。
- 端隔离：服务端严格禁止引用客户端类。
- 性能：选择实现方案前需考虑性能占用。
- 复用：为拓展性考虑，将可复用方法提取到公共代码类中。
- 平台差异：允许 NeoForge 端与 Fabric 端使用不同方案实现相同或相似效果。
- 验证顺序：先通过 IDEA MCP（`http://127.0.0.1:64342/stream` ）git 工作区报错/警告（忽略 markdown 格式问题），再执行 `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`（内部串行互斥，勿裸跑 `gradlew`）；测试交由用户，不再使用 test 文件。
- 失败处理：若 IDEA MCP 检查或构建失败，先修复并重新验证；原因不明确或修复需改变设计时，停止并向用户说明后再继续。
- 构建等待：Gradle 验证等待不得短于 120 秒；若返回运行中任务 ID，持续等待至结束；未确认上一次构建结束前不得重复启动，避免并发占用 `build` 目录导致误报失败。

## Mixin 准则

**优先使用已有 Event API，尽可能少用 Mixin。**

Fabric 平台原生事件较少，可能需使用 Mixin，必须遵循：
- 不在 Mixin 中写大量业务逻辑，Mixin 仅作为入口
- 若已有 Event API 能表达同等或相近行为，不得新写 Mixin
- 若必须新增 Mixin，应尽量缩小注入范围，并保持业务逻辑位于普通类中

牢记 **Mixin 是侵入式方案**，谨慎使用。

## 原版或 neoforge/fabric API 获取准则
当涉及原版 API 或 NeoForge/fabric API 时，必须遵循以下**分级策略**：

### Level 1（默认）  
-  优先基于**已有上下文 + 常识性 API 使用方式**完成任务：若 API 用法明确，应使用上下文与常识性判断。
---

### Level 2（不确定时）

触发条件（满足任一）：

- API 参数或返回值不确定
- 编译报错（方法调用不正确）
- 方法名/类名存在歧义

允许操作：
- 调用 mc-developing-mcp 查询 API 源码或官方文档
- 通过 IDEA MCP 反编译原版/NeoForge/Fabric 类（实测可用，mc-developing-mcp 取不到原版类时的首选替代）：
- **定位类**：`search_symbol(include_external=true, q="<类名>")` → 返回 merged jar 内的 `net/minecraft/.../<类>.class` 路径。
- 同一类通常有 3 份：vanilla、neoforge、loom-cache。优先访问 vanilla 类；仅当目标符号涉及平台补丁、平台特有行为或 vanilla 中不存在时，再访问对应平台类。
  - `common/build/moddev/artifacts/vanilla-*-minecraft-merged.jar!`
  - `neoforge/build/moddev/artifacts/neoforge-*-merged.jar!`
  - `.gradle/loom-cache/minecraftMaven/**/-merged.jar!`
- **读源码**：`read_file(file_path="<绝对路径>\\...vanilla-*-minecraft-merged.jar!\\net\\minecraft\\world\\entity\\LivingEntity.class")` → 直接返回 Fernflower 反编译后的完整 `.java` 源码，支持 `limit` / `offset` 取片段
- **跳转定义**：对**项目内源码**里的原版符号调用 `get_symbol_info`（给 file/line/column）→ 返回声明片段 + documentation，`declarationFile` 指向 merged jar
- **限制**：`get_symbol_info` 的 `filePath` 不认 jar 的 `!` 路径（报 File not found），只能对项目内源码用；`search_regex` / `search_text` 不索引 jar 内部（返回空）。要读原版整类只能走 `read_file` + jar 路径

---

### Level 3（必须用户确认）
**执行前必须说明理由并询问用户**

触发条件：
- 需要源码级理解且 Level 2 无法解决（如数据库中不存在目标 API）

允许：源码反编译查询，使用 fetch 查阅网络获取 API 用法。

---

## 注释风格约束
- 注释应使用简体中文

---

## i18n 规范

所有 HUD / GUI 文本都必须使用本地化 key

必须同步更新：
- `en_us.json`
- `zh_cn.json`

## 禁止滥用魔法字符串与魔法数字

在编写、修改、重构或审查 Java 代码时，**必须主动识别并合理消除魔法字符串（Magic String）和魔法数字（Magic Number）**。

原则如下：

- 禁止在业务逻辑中直接使用缺乏明确语义的硬编码字符串或数字。
- 优先使用具有业务含义的命名常量、枚举、配置项或专用值对象替代魔法值。
- 优先复用项目中已有的常量、枚举和配置定义，避免重复定义。
- 不得机械地将所有字面量提取为常量，应结合业务语义、使用范围、可维护性和代码可读性进行判断。
- 所有修改必须保持原有业务行为和接口兼容性。

## Agent skills

### Issue tracker

任务使用本地 Markdown；创建、读取或拆分任务时，先读 `docs/agents/issue-tracker.md`。

### Triage labels

使用五个默认 triage 标签；分诊任务时，先读 `docs/agents/triage-labels.md`。

### Domain docs

采用 single-context 布局；探索代码、讨论领域术语或架构决策前，先读 `docs/agents/domain.md`。

### 文档要求

- 文档结论必须能回到源码；
- 动手前先确认改动落在哪一层；跨子系统改动按上文「核心原则」先拆分再实施。
- 只做被要求的改动，不改动无关文件与无关文档。

<!-- 项目 Agent 提示词到此结束 -->
