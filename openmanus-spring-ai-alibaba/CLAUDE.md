# CLAUDE.md

## 语言
请始终使用简体中文与我对话，并在回答时保持专业、简洁。

## 项目背景
当前项目是基于 spring-ai-alibaba-graph 开发，核心目的是完成一个通用的对话类 agent。

## 日志配置

### 请求日志
每个 HTTP 请求都会自动生成一个唯一的 `requestId`（8位 UUID），并记录到 MDC 中。

- **主日志文件**: `logs/openmanus.log` (按日期滚动)
- **请求日志文件**: `logs/request-{requestId}.log` (每个请求一个独立文件)

### 分析指定 request_id 的问题
当输入请求id，并要求分析问题时，直接读取对应的日志文件：

```bash
# 直接查看指定 request_id 的日志
cat logs/request-abc12345.log
```

### 日志格式
```
2026-04-10 14:30:15.123 [http-nio-8080-exec-1] INFO  c.o.saa.service.ManusAgentService - 处理请求...
```

### 请求 ID 来源
- 如果请求头中包含 `X-Request-ID`，则使用该值
- 否则自动生成 8 位 UUID

## 设计规范
- 设计流程和规则匹配时，优先采用通用、可扩展的方式（如配置驱动、策略模式），避免针对单个案例的硬编码特化处理。
