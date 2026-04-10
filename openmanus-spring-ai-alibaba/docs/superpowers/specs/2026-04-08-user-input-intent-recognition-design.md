# 用户输入意图自动识别设计

## 问题背景

当前系统在处理用户反馈时存在两个问题：

1. **Feedback 路径断裂**：`continuePausedWorkflow()` 只是简单调用 `chat()`，没有真正处理反馈
2. **用户输入类型未识别**：无法区分补充信息、继续执行、新任务

## 设计目标

实现用户输入意图的自动识别，让系统能正确处理：
1. **补充信息**：用户回答之前的问题（如 "是4月8"）
2. **继续执行**：用户明确要求继续执行（如 "继续"）
3. **新任务**：完全独立的新请求

## 实现方案

### 方案 A：增强 SessionContextIntentRecognizer

#### 1. 新增 UserInputIntent 枚举

```java
public enum UserInputIntent {
    /** 补充信息：用户回答之前的问题 */
    SUPPLEMENT_INFO,
    /** 继续执行：用户明确要求继续 */
    CONTINUE,
    /** 新任务：完全独立的请求 */
    NEW_TASK,
    /** 不确定：需要进一步分析 */
    UNCERTAIN
}
```

#### 2. 扩展 IntentResolution.attributes

新增以下属性：
- `userInputIntent`: 用户输入意图类型
- `pendingFeedbackId`: 待处理反馈的步骤索引
- `extractedParams`: 从用户输入中提取的参数

#### 3. 增强 SessionContextIntentRecognizer

```java
// 新增方法：分类用户输入意图
public UserInputIntent classifyUserInput(
    String prompt,
    HumanFeedbackRequest pendingFeedback
) {
    // 1. 检查是否明确要求继续
    if (isExplicitContinue(prompt)) {
        return UserInputIntent.CONTINUE;
    }

    // 2. 检查是否补充参数（回应之前的问题）
    if (canExtractParameters(prompt, pendingFeedback)) {
        return UserInputIntent.SUPPLEMENT_INFO;
    }

    // 3. 检查是否新任务（通过 LLM 判断）
    if (isNewTask(prompt, pendingFeedback)) {
        return UserInputIntent.NEW_TASK;
    }

    return UserInputIntent.UNCERTAIN;
}
```

#### 4. 完善 continuePausedWorkflow

```java
private AgentResponse continuePausedWorkflow(
    String sessionId,
    String prompt,
    IntentResolution resolution
) {
    // 1. 从 attributes 获取意图分类结果
    UserInputIntent intent = resolution.getUserInputIntent();

    return switch (intent) {
        case SUPPLEMENT_INFO -> handleSupplementInfo(sessionId, prompt, resolution);
        case CONTINUE -> handleContinue(sessionId, resolution);
        case NEW_TASK -> handleNewTask(sessionId, prompt, resolution);
        case UNCERTAIN -> handleUncertain(sessionId, prompt, resolution);
    };
}
```

### 处理流程

#### 场景 1：补充信息（推荐）

**输入：** `"是4月8，其它没有特别的"`

**处理：**
1. `classifyUserInput()` 识别为 `SUPPLEMENT_INFO`
2. 从 `pendingFeedback` 提取缺失参数：`departureDate=4月8`
3. 合并参数，重新执行失败的步骤
4. 执行完成后生成完整行程计划

**关键优势：**
- 系统智能合并用户提供的参数
- 自动恢复工作流继续执行
- 最终输出完整的7日行程计划

#### 场景 2：继续执行

**输入：** `"继续执行"`

**处理：**
- 直接使用默认参数继续执行
- 不等待更多用户输入

#### 场景 3：新任务

**输入：** `"帮我查一下天气"`

**处理：**
- 清除 `WorkflowState`
- 正常路由到 chat/plan-execute 模式
- 旧任务可以由系统记录或询问是否恢复

### 关键技术实现

#### 1. 参数提取（基于规则 + LLM）

```java
private Map<String, Object> extractParameters(
    String prompt,
    HumanFeedbackRequest pendingFeedback
) {
    // 1. 从 pendingFeedback 获取缺失参数
    List<String> missingFields = pendingFeedback.getMissingFields();

    // 2. 尝试从用户输入中提取
    //    - 日期格式检测
    //    - 布尔值检测（"是/否"）
    //    - 枚举值检测（新街口/老门东）
    // 3. 对于复杂参数，使用 LLM 提取

    return extractedParams;
}
```

#### 2. 意图分类规则

```java
private boolean isExplicitContinue(String prompt) {
    List<String> continueKeywords = List.of(
        "继续", "继续执行", "继续吧", "go on", "continue",
        "用默认的", "不用管", "继续用", "跳过"
    );
    return continueKeywords.stream()
        .anyMatch(prompt::contains);
}

private boolean isNewTask(String prompt, HumanFeedbackRequest pending) {
    // 使用 LLM 判断输入是否与之前任务相关
    String prompt = String.format("""
        判断以下用户输入是否是在回答之前的问题。
        之前的问题：%s
        用户的输入：%s

        如果用户输入与之前的问题无关，是一个新的独立任务，返回 YES。
        如果用户输入是在回答之前的问题，返回 NO。
        """, pending.getErrorMessage(), prompt);
    // LLM 调用...
}
```

### 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `UserInputIntent.java` | 创建 | 用户输入意图枚举 |
| `SessionContextIntentRecognizer.java` | 修改 | 添加意图分类逻辑 |
| `IntentResolution.java` | 修改 | 添加意图分类属性 |
| `ManusAgentService.java` | 修改 | 完善 continuePausedWorkflow |
| `HumanFeedbackResolutionService.java` | 修改 | 添加参数提取方法 |

### 测试场景

| # | 场景 | 输入 | 期望输出 |
|---|------|------|----------|
| 1 | 补充日期 | "是4月8" | 继续执行，生成行程 |
| 2 | 补充多个参数 | "4月8日，新街口购物，没特别要求" | 继续执行 |
| 3 | 明确继续 | "继续" | 直接继续执行 |
| 4 | 新任务 | "帮我查天气" | 清除旧状态，正常处理 |
| 5 | 不确定 | "等等" | 询问用户意图 |

## 验证方式

1. 单元测试：意图分类逻辑
2. 集成测试：完整反馈流程
3. 手动测试：模拟真实用户交互