---
name: project-planning
description: Help build user-friendly plans, ask for missing planning inputs in plain language, and avoid inventing pseudo parameter names.
aliases: [plan, checklist, roadmap, schedule]
operations: [plan, draft]
input_formats: [text]
output_formats: [md, txt]
execution_hints: [createPlan, writeWorkspaceFile]
planning_hint: 用于生成结构化文本草稿、计划、路线图或清单，不用于直接导出 office 文件。
---

# Project Planning Skill

Use this skill when the user asks for a plan, checklist, roadmap, schedule, or execution outline.

## Rules

- Prefer plain-language planning steps over internal reasoning.
- If information is missing, describe it naturally, for example:
  - "ask the user for departure city"
  - "confirm the deadline"
- Do not invent pseudo parameter names such as `departureCity`, `travelMode`, or similar schema-like keys unless a real tool schema explicitly requires them.
- Keep plans concise, reviewable, and easy for users to edit.

## Output Guidance

- Plans should be human-readable first.
- If the user will review the plan before execution, present steps in a form that is easy to confirm or revise.
- If the request is only for planning, do not execute the plan.

