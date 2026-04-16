# LLM 响应格式预处理设计

## 问题

当前 `PlanningService.parseWorkflowPlan()` 假设 LLM 返回纯 JSON，但不同模型返回格式差异导致解析失败：

- GLM 模型返回 `<!--...?-->`, `<!--...?-->` 等 thinking 标签前缀
- 行解析器将含 XML 标签的内容按换行分割为大量碎片步骤
- 每个碎片步骤触发一次 enrichment LLM 调用
- 20 个碎片 → 20 次 enrichment 调用 → API 限流（HTTP 429）
- 最终生成 20 个破碎步骤而非预期的 2-5 个

## 解决思路

两层防御：
- **第一层（Prompt）**：指导模型只输出 JSON，消除格式污染根源
- **第二层（预处理）**：清洗残留的 thinking/reasoning 标签，作为兜底防护

## 改动 2：预处理层

**文件**：`PlanningService.java`

**位置**：`stripMarkdownCodeFence()` 方法（约行 774）

增强现有方法，增加清洗逻辑：

```java
private String preprocessLlmResponse(String content) {
    if (content == null) return "";

    // 1. 剥离 XML thinking 标签
    //    匹配 <!--...-->, <??>...</??>, <thinking>...</thinking> 等模式
    content = content.replaceAll("<!--[\\s\\S]*?-->", "");
    content = content.replaceAll("(?s)<[\\w]+>[\\s\\S]*?</[\\w]+>", "");

    // 2. 剥离 markdown code fence
    content = content.replace("```json", "").replace("```", "").replace("`", "");

    // 3. 提取纯 JSON 数组（首个 [ 到末个 ]）
    int jsonStart = content.indexOf('[');
    int jsonEnd = content.lastIndexOf(']');
    if (jsonStart >= 0 && jsonEnd > jsonStart) {
        content = content.substring(jsonStart, jsonEnd + 1);
    }

    return content.trim();
}
```

**注意**：将原 `stripMarkdownCodeFence()` 方法改为 `preprocessLlmResponse()`，调用点保持不变（方法名替换）。

## 改动 3：日志增强

在 JSON 解析失败回退到行解析器时，增加日志输出清洗后的内容片段，便于调试：

```java
log.warn("Failed to parse workflow plan as JSON, falling back to line parser. Cleaned content preview: {}",
    normalizedContent.length() > 200 ? normalizedContent.substring(0, 200) + "..." : normalizedContent);
```

## 影响范围

- 仅修改 `PlanningService.java`
- 三处改动，互相独立
- 不影响现有业务逻辑，只改进解析鲁棒性

## 测试验证

重新运行北京 7 天行程请求，验证：
- [ ] JSON 解析成功率接近 100%
- [ ] 步骤数量恢复到 2-5 个
- [ ] enrichment 调用降到 0 次
- [ ] 最终生成 20 步的问题不再复现
