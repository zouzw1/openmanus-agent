---
name: docx-format
description: 使用 python-docx 精确读取、分析、修改 Word 文档（.docx）格式。当用户需要分析文档格式、批量修改格式、统一排版规范、处理中英文混排字体、mardDown转docx、修改交叉引用样式时使用此 skill。
aliases: [docx, word]
operations: [read, edit, export, format, convert]
input_formats: [md, txt, html, docx]
output_formats: [docx]
execution_hints: [runPowerShell, readWorkspaceFile, writeWorkspaceFile, listWorkspaceFiles]
planning_hint: 用于 Word 文档的导出、读取、编辑或格式处理。若目标是 .docx，可优先选择该 skill。
---

# Word 文档格式处理

使用 python-docx 库精确操作 Word 文档格式，适用于格式分析、格式规范化、批量修改。

## 核心规则

**被调用时立即执行**：

1. **确认文件路径**：询问用户 Word 文档的完整路径
2. **明确需求**：确认是分析格式还是修改格式，目标规范是什么
3. **选择模板**：根据需求选择合适的代码模板或脚本
4. **生成脚本**：创建独立的 Python 脚本文件
5. **执行验证**：使用 `uv run` 执行并检查结果

**强制性约束**：

- ✓ **必须使用** `uv run --with python-docx python script.py`
- ✓ **必须处理** `doc.paragraphs` 和 `doc.tables`（文档正文常在表格内）
- ✓ **必须分设** 中英文字体（使用 `qn('w:eastAsia')`）
- ✓ **必须询问** 用户文件路径和输出路径
- ✓ **在 Windows 环境下优先使用** `python`，不要使用 `python3`
- ✗ **禁止使用** 脱离 `uv run --with python-docx` 的直接 `python` 命令
- ✗ **禁止遗漏** 表格内容处理（常见错误）

## 何时使用

**触发场景**：
- 分析 Word 文档格式（字体/字号/缩进/行距）
- 批量修改文档格式
- 统一排版规范（学术论文、报告等）
- 处理中英文混排字体
- 修改交叉引用/参考文献样式

**触发关键词**：修改 Word 格式、统一字体、调整缩进、分析文档格式、参考文献格式

## 决策树
