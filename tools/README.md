---
title: 仓库工具入口
layer: 5
audience: 运行机械校验、构建和开发工具的开发者
authority:
  - tools/toolslib/check_suite.py
  - tools/toolslib/checks.py
  - tools/toolslib/repo.py
  - tools/dsh-build.ps1
  - build.gradle
---

# 仓库工具入口

## TL;DR

- `python tools/check-all.py` 按固定顺序执行源码机械校验，首个失败立即停止。
- `tools/dsh-build.ps1 -Tasks build` 串行运行完整构建，包含配置参考与发布产物门禁。
- 游戏验证、资源生成和日志分析分别使用下方对应入口。

## 适用对象

需要选择校验范围、运行构建，或定位工具实现的开发者。源码机械校验使用 Python 标准库，默认从工具位置定位仓库。

## 源码机械校验

在仓库根运行：

```powershell
python tools/check-all.py
python tools/check-all.py --only docs --only i18n
python tools/check-all.py --root D:/path/to/repository
```

顺序固定为 `docs → isolation → i18n → ui-kit → gui-cases → probe-catalog`。`--only` 可重复，选择会去重，执行顺序仍由清单决定。各项输出实时显示，并附耗时和退出码；全部通过返回 `0`，失败原样返回首个非零退出码。未知选择、无效根目录和必需输入缺失返回 `2`，规则违规返回 `1`。

默认检查只读取源码、资源、用例声明和文档。GUI 用例及后端序列检查分别使用只读参数 `--check`、`--check-docs`。单项脚本保留独立入口；UI kit 的显式路径和包前缀等高级参数见[依赖规则](../docs/architecture/dependency-rules.md)。

## 构建门禁

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks build
```

`dsh-build.ps1` 通过命名 Mutex 串行执行 Gradle，透传 Gradle 退出码；`-TimeoutSeconds` 控制取得 Mutex 的等待时间。

根 `checkSourceRepository` 调用源码顺序入口；`checkRepository` 汇总它和配置参考校验。配置参考先由 `:common:exportConfigReference` 导出 schema，发布产物检查先依赖各端 `assemble`。同一任务图中配置参考检查排在源码校验后，打包检查排在仓库门禁后；前置编译与 assemble 由 Gradle 调度。

完整 `build` 还执行迁移验证、UI kit 有效公开面核对、纯样例与开发源码编译。现有单项 Gradle 检查任务仍可按需调用。

### UI kit Interface

`common:exportUiKitInterface` 使用 JDK 21 读取 kit 的真实符号与有效 public/protected 成员，比较 `tools/ui-kit-interface/api.txt`。外部依赖白名单集中在 `tools/ui-kit-interface/dependencies.txt`；宿主资源引用及 JavaDoc 检查随同执行。该任务和 `compileUiKitSamples` 均接入 common 的 `check`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks ':common:exportUiKitInterface'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks ':common:exportUiKitInterface -PkitInterfaceMode=write'
```

默认 `check` 模式在差异时失败，并输出 `api.txt.actual` 供核对；批准 Interface 变更后使用 `write` 更新基线，再执行标准 build。`-PkitInterfaceOutput=相对仓库路径` 可指定输出位置。工具源码与任务定义分别位于 `tools/ui-kit-interface/ExportInterface.java` 和 `common/build.gradle`；签名范围与依赖政策见[依赖规则](../docs/architecture/dependency-rules.md#有效公开面与符号检查)。

## 按用途选择工具

| 用途 | 入口 | 契约与用法 |
|---|---|---|
| 源码机械校验 | `check-all.py` | 本页与 [check_suite.py](toolslib/check_suite.py) |
| 后端运行验证与离线报告 | `run-probes.ps1`、`probe-catalog.py`、`probe_artifacts.py`、`probe-performance-report.py` | [后端探针指南](../docs/guides/backend-probes.md) |
| GUI 运行验证与用例契约 | `gui-probes/` | [GUI 工具说明](gui-probes/README.md) |
| 双端连接与独立服务端冒烟 | `connection_smoke.py` | [连接冒烟说明](connection-smoke.md) |
| 编辑器图集生成 | `image_generator/` | [图集说明](image_generator/README.md) |
| 配置参考生成与检查 | `gen-config-reference.py`、`config-docs/` | [配置维护流程](../docs/ops/configuration.md) |
| 调试日志离线分析 | `analyze-debug-log.py` | `python tools/analyze-debug-log.py --help` |

## 实现归属

根脚本提供命令 Interface，`toolslib` 集中仓库定位、检查结果呈现、执行清单和共享 Java 引用识别。后端序列与报告实现放在 `toolslib/backend_probes/`，其根 CLI 保持已有调用路径。

GUI 的 `lib/case_files.py` 负责离线输入，`case_contract.py` 负责声明语义；文档校验和运行驱动共同消费。图集生成使用自身的 `atlas.py`，绘图库依赖见图集说明。

## 相关文档

- [文档体系](../docs/README.md)
- [依赖规则](../docs/architecture/dependency-rules.md)
- [后端探针](../docs/guides/backend-probes.md)
- [GUI 探针](../docs/guides/gui-probes.md)
