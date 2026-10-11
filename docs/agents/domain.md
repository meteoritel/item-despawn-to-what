# 领域文档

本项目采用 single-context 布局：根目录术语表 `GLOSSARY.md` 与 `docs/adr/`（索引见 [ADR 目录](../adr/README.md)）。

## 读取规则

探索代码前，读取根目录术语表，并读取与任务相关的 ADR。
相关开发者文档按 [开发者文档索引](../dev/README.md) 查阅。

术语表与 ADR 均已存在；遇到术语缺口时先核对现有用语，再由 `domain-modeling` 确认后写入 `GLOSSARY.md`。

## 术语与决策

- 输出中的领域概念沿用术语表定义。
- 遇到术语缺口，核对现有项目用语，再交由 `domain-modeling` 明确。
- 方案与已有 ADR 冲突时，明确指出具体 ADR 和冲突原因，交由用户决定是否调整。
