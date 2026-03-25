# openmanus-spring-ai-alibaba

SDK 与主服务模块，提供 OpenManus 风格的 Spring AI Alibaba Agent 运行时。

它既可以：
- 作为独立 Spring Boot 服务启动
- 也可以作为 SDK 被其他 Spring Boot 项目引入

## 模块职责

该模块主要提供以下能力：
- Agent 注册与聚合加载
- 聊天路由与工作流执行
- MCP 工具桥接
- Skills 加载与 `read_skill`
- 本地 `@Tool` 自动发现
- 会话与执行状态管理
- Human-in-the-loop 反馈处理
- 意图识别扩展 SPI
- 产物追踪与响应封装

## 内置能力

### 内置基础 agent
SDK 自带两个基础 agent，打包在 classpath 中：
- `manus`
- `data_analysis`

这意味着业务项目即使不额外提供 agent，也能直接运行。

### 可聚合的 agent source
SDK 会聚合多个 `AgentConfigSource`：
- SDK 内置 classpath agents
- 外部 `./agents` 目录中的 agent 定义
- 业务项目自己声明的 `AgentConfigSource`

业务项目可以追加 agent，也可以用相同 id 覆盖默认 agent。

### 本地工具自动发现
SDK 会扫描任意 Spring Bean 上的 `@Tool` 方法，不再要求工具类必须放在 SDK 固定包路径下。

### 意图识别扩展层
SDK 提供可扩展意图识别 SPI：
- `IntentRecognizer`
- `IntentResolution`
- `IntentResolutionService`

业务项目可以插入自己的识别器，影响：
- `DIRECT_CHAT / PLAN_EXECUTE`
- 首选 agent
- planning hints
- 结构化意图属性

## 默认配置

主配置文件位于：
- [application.yml](./src/main/resources/application.yml)

默认运行端口：`8080`

主要默认行为：
- 默认聊天 agent：`manus`
- 默认 workspace：`./workspace`
- Skills 默认开启
- MCP 默认开启并连接 `demo-sse`
- Browser / Sandbox / Shell 默认关闭或偏保守

## 作为独立服务运行

### 启动

```powershell
mvn -pl openmanus-spring-ai-alibaba spring-boot:run
```

### 典型接口

#### 聊天

```http
POST /api/agent/chat
```

#### 显式执行

```http
POST /api/agent/execute
```

#### 反馈续跑

```http
POST /api/agent/feedback
```

#### MCP 管理

```http
GET /api/mcp/servers
POST /api/mcp/tools/call
```

#### Skills 管理

```http
GET /api/skills
POST /api/skills/reload
```

## 作为 SDK 接入

业务项目至少需要：
- 依赖本模块
- 提供模型配置，例如 DashScope API Key
- 如有需要，声明自己的 agent、本地工具、意图识别器

### 示例依赖

```xml
<dependency>
    <groupId>com.openmanus</groupId>
    <artifactId>openmanus-spring-ai-alibaba</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 扩展自己的 agent

通过 `AgentConfigSource` 追加：
- 新的业务 agent
- 自定义本地工具权限
- 自定义 MCP 访问策略
- 自定义 skill 访问策略

### 扩展自己的本地工具

```java
@Component
public class MyBusinessTools {

    @Tool(description = "Generate a business checklist")
    public String generateChecklist(String topic) {
        return "Checklist for " + topic;
    }
}
```

### 扩展自己的意图识别

```java
@Component
@Order(10)
public class MyIntentRecognizer implements IntentRecognizer {
    @Override
    public Optional<IntentResolution> recognize(String prompt, SessionState session) {
        return Optional.empty();
    }
}
```

## 工作流设计要点

当前工作流规划与执行遵循一些通用约束：
- `collect / compose / export` 分层
- 导出型步骤前应有草案或源内容步骤
- 缺用户信息优先进入人工反馈，而不是盲目失败
- 产物统一进入 `artifacts` 字段，并返回绝对路径

## 推荐阅读顺序

- [root README](../README.md)
- [MCP 测试服务 README](../mcp-test-service/README.md)
- [study-plan-demo README](../study-plan-demo/README.md)
