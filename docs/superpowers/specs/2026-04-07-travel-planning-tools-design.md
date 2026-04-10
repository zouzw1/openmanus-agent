---
name: Travel Planning Tools Design
description: 细粒度旅游计划工具集设计，使用开源免费数据源，输出结构化 JSON
type: project
---

# 旅游计划工具集设计

## 一、概述

在主模块 `openmanus-spring-ai-alibaba` 中实现细粒度旅游工具集，用于旅游计划生成。

**核心决策：**
- 工具位置：主模块 `tool/travel/` 包（内置工具）
- 工具粒度：细粒度（每个独立操作一个工具）
- 数据源：开源/免费 API（无需注册）
- 输出格式：结构化 JSON
- Agent 模式：独立 TravelAgent + 可复用通用工具

---

## 二、工具清单（11 个工具）

**位置：** `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/`

### 2.1 POI 搜索工具（3 个）

| 工具类 | 功能 | 数据源 | 输入参数 |
|--------|------|--------|----------|
| `SearchAttractionsTool` | 搜索景点、地标、博物馆 | Overpass API | query, location, radius, limit |
| `SearchRestaurantsTool` | 搜索餐厅、咖啡馆 | Overpass API | query, location, radius, limit |
| `SearchHotelsTool` | 搜索住宿 | Overpass API | query, location, radius, limit |

**输入参数说明：**
- `query`: 搜索关键词（可选）
- `location`: 经纬度坐标或城市名
- `radius`: 搜索半径（米），默认 5000
- `limit`: 返回数量，默认 20

**输出结构：**
```json
{
  "success": true,
  "count": 5,
  "results": [
    {
      "name": "故宫博物院",
      "location": {"lat": 39.9163, "lon": 116.3972},
      "address": "北京市东城区景山前街4号",
      "type": "museum",
      "tags": ["tourism", "museum", "attraction"],
      "openingHours": null
    }
  ],
  "source": "OpenStreetMap/Overpass"
}
```

### 2.2 路线规划工具（2 个）

| 工具类 | 功能 | 数据源 | 输入参数 |
|--------|------|--------|----------|
| `CalculateDistanceTool` | 计算两点距离/时间 | OSRM API | origin, destination, mode |
| `PlanRouteTool` | 多点路线规划 | OSRM API | waypoints, mode |

**输入参数说明：**
- `origin`: 起点坐标或地址
- `destination`: 终点坐标或地址
- `waypoints`: 途经点列表（坐标）
- `mode`: 交通方式（driving/walking/cycling）

**输出结构：**
```json
{
  "success": true,
  "distance": 12.5,
  "distanceUnit": "km",
  "duration": 35,
  "durationUnit": "min",
  "mode": "driving",
  "steps": [
    {"instruction": "Head north on Main St", "distance": 0.5, "duration": 2}
  ]
}
```

### 2.3 天气工具（2 个）- 通用工具

| 工具类 | 功能 | 数据源 | 输入参数 |
|--------|------|--------|----------|
| `QueryWeatherTool` | 当前天气 | Open-Meteo | city 或 coordinates |
| `QueryForecastTool` | 天气预报 | Open-Meteo | city 或 coordinates, days |

**输入参数说明：**
- `city`: 城市名（自动解析坐标）
- `coordinates`: 经纬度
- `days`: 预报天数（1-7）

**输出结构：**
```json
{
  "success": true,
  "city": "北京",
  "coordinates": {"lat": 39.9042, "lon": 116.4074},
  "current": {
    "temperature": 18,
    "condition": "晴",
    "humidity": 45,
    "windSpeed": 12
  },
  "source": "Open-Meteo"
}
```

### 2.4 费用与信息工具（4 个）

| 工具类 | 功能 | 数据源 | 类型 |
|--------|------|--------|------|
| `EstimateBudgetTool` | 费用估算 | 本地计算模型 | 专用 |
| `GetTimezoneTool` | 时区查询 | Java TimeZone | 通用 |
| `CheckHolidayTool` | 节假日查询 | Nager.Date API | 专用 |
| `ConvertCurrencyTool` | 汇率转换 | frankfurter.app | 通用 |

**EstimateBudgetTool 输入：**
- `destination`: 目的地
- `duration`: 旅行天数
- `travelStyle`: budget/mid_range/luxury
- `groupSize`: 人数

**CheckHolidayTool 输入：**
- `countryCode`: 国家代码（如 CN, US, JP）
- `year`: 年份
- `month`: 月份（可选）

**ConvertCurrencyTool 输入：**
- `from`: 源货币代码
- `to`: 目标货币代码
- `amount`: 金额

---

## 三、数据模型

**位置：** `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/`

```
model/travel/
├── POIItem.java              # POI 结果项
├── GeoLocation.java          # 经纬度坐标
├── RouteResult.java          # 路线结果
├── RouteStep.java            # 路线步骤
├── WeatherInfo.java          # 天气信息
├── ForecastInfo.java         # 预报信息
├── BudgetEstimate.java       # 费用估算
├── HolidayInfo.java          # 节假日信息
├── CurrencyRate.java         # 汇率信息
└── TravelConstants.java      # 常量定义
```

### 3.1 核心模型定义

**GeoLocation.java**
```java
public record GeoLocation(double lat, double lon) {
    public String toCoordinateString() {
        return lat + "," + lon;
    }
}
```

**POIItem.java**
```java
public record POIItem(
    String name,
    GeoLocation location,
    String address,
    String type,
    List<String> tags,
    String openingHours
) {}
```

**WeatherInfo.java**
```java
public record WeatherInfo(
    int temperature,
    String condition,
    int humidity,
    int windSpeed,
    boolean isDay,
    int weatherCode
) {}
```

**TravelConstants.java**
```java
public final class TravelConstants {
    public static final int DEFAULT_RADIUS = 5000;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_FORECAST_DAYS = 7;

    public enum TransportMode {
        DRIVING, WALKING, CYCLING
    }

    public enum TravelStyle {
        BUDGET, MID_RANGE, LUXURY
    }
}
```

---

## 四、工具分类与注册

### 4.1 Category 扩展

修改 `ToolMetadata.Category`，新增：

```java
public enum Category {
    // 现有分类...
    TRAVEL_POI,      // POI 搜索工具
    TRAVEL_ROUTE,    // 路线规划工具
    WEATHER,         // 天气工具（通用）
    TRAVEL_INFO      // 旅游信息工具
}
```

### 4.2 Tag 标记

- 通用工具：`tags: {"general", "weather", "currency", "timezone"}`
- 专用工具：`tags: {"travel", "poi", "route", "budget"}`

### 4.3 工具注册示例

```java
@Component
public class QueryWeatherTool {

    @Tool(description = "查询指定城市的当前天气信息，返回温度、天气状况、湿度、风速等")
    @ToolDefinition(
        category = Category.WEATHER,
        tags = {"general", "weather"},
        dangerous = false
    )
    public String queryWeather(
        @ToolParam(description = "城市名称，如北京、上海、Tokyo、Paris", required = true)
        String city
    ) {
        // 实现...
    }
}
```

---

## 五、TravelAgent 定义

**位置：** `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/agent/`

### 5.1 Agent 定义

```java
@Component
public class TravelAgentDefinition implements AgentConfigSource {

    @Override
    public AgentDefinition getDefinition() {
        return AgentDefinition.builder()
            .name("TravelAgent")
            .description("旅游规划专家，帮助用户规划行程、搜索景点、查询天气、估算费用")
            .systemPrompt(buildSystemPrompt())
            .categories(List.of(
                Category.TRAVEL_POI,
                Category.TRAVEL_ROUTE,
                Category.WEATHER,
                Category.TRAVEL_INFO
            ))
            .intentKeywords(List.of(
                "旅游", "旅行", "行程", "景点", "酒店", "餐厅",
                "天气", "路线", "预算", "机票", "签证"
            ))
            .build();
    }

    private String buildSystemPrompt() {
        return """
            你是一个专业的旅游规划助手，帮助用户规划完美的旅行体验。

            你可以：
            1. 搜索景点、餐厅、酒店
            2. 规划路线和计算距离
            3. 查询天气和预报
            4. 估算旅行费用
            5. 查询节假日和汇率信息

            在规划行程时：
            - 考虑天气因素
            - 优化路线顺序
            - 合理安排时间
            - 提供费用估算

            使用工具获取真实数据，不要编造信息。
            """;
    }
}
```

---

## 六、开源 API 端点

| 服务 | 端点 | 说明 | 调用方式 |
|------|------|------|----------|
| Overpass API | `https://overpass-api.de/api/interpreter` | POI 搜索 | POST JSON body |
| OSRM Route | `https://router.project-osrm.org/route/v1/{mode}/{coords}` | 路线规划 | GET |
| OSRM Table | `https://router.project-osrm.org/table/v1/{mode}/{coords}` | 距离矩阵 | GET |
| Open-Meteo | `https://api.open-meteo.com/v1/forecast` | 天气预报 | GET |
| Geocoding | `https://geocoding-api.open-meteo.com/v1/search` | 城名转坐标 | GET |
| Nager.Date | `https://date.nager.at/api/v3/PublicHolidays/{year}/{country}` | 节假日 | GET |
| Frankfurter | `https://api.frankfurter.app/latest?from={from}&to={to}` | 汇率 | GET |
| Frankfurter | `https://api.frankfurter.app/{amount}?from={from}&to={to}` | 汇率转换 | GET |

### 6.1 Overpass API 查询示例

```json
[data]
out json;
(
  node["tourism"="attraction"](around:5000,39.9163,116.3972);
  node["tourism"="museum"](around:5000,39.9163,116.3972);
);
out center;
```

### 6.2 OSRM API 示例

```
https://router.project-osrm.org/route/v1/driving/116.3972,39.9163;116.4074,39.9042?steps=true
```

### 6.3 Open-Meteo API 示例

```
https://api.open-meteo.com/v1/forecast?latitude=39.9042&longitude=116.4074&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto
```

---

## 七、需要删除的旧代码

### 7.1 完全删除

1. `mcp-test-service/src/main/java/com/openmanus/mcptest/service/TestMcpToolService.java`
2. `mcp-test-service/src/main/java/com/openmanus/mcptest/service/WeatherService.java`

### 7.2 清理配置

- 移除 MCP 配置中与 `TestMcpToolService` 相关的 server 配置
- 如整个 `mcp-test-service` 不再需要，可考虑删除整个模块

---

## 八、文件结构总览

```
openmanus-spring-ai-alibaba/
└── src/main/java/com/openmanus/saa/
    ├── tool/
    │   └── travel/
    │       ├── SearchAttractionsTool.java
    │       ├── SearchRestaurantsTool.java
    │       ├── SearchHotelsTool.java
    │       ├── CalculateDistanceTool.java
    │       ├── PlanRouteTool.java
    │       ├── QueryWeatherTool.java
    │       ├── QueryForecastTool.java
    │       ├── EstimateBudgetTool.java
    │       ├── GetTimezoneTool.java
    │       ├── CheckHolidayTool.java
    │       ├── ConvertCurrencyTool.java
    │       └── TravelApiClient.java        # API 调用封装
    ├── model/
    │   └── travel/
    │       ├── POIItem.java
    │       ├── GeoLocation.java
    │       ├── RouteResult.java
    │       ├── RouteStep.java
    │       ├── WeatherInfo.java
    │       ├── ForecastInfo.java
    │       ├── BudgetEstimate.java
    │       ├── HolidayInfo.java
    │       ├── CurrencyRate.java
    │       └── TravelConstants.java
    ├── agent/
    │   └── TravelAgentDefinition.java
    └── model/
        └── ToolMetadata.java              # 修改：新增 Category
```

---

## 九、实施优先级

1. **Phase 1: 基础设施**
   - 数据模型定义
   - TravelApiClient API 调用封装
   - Category 扩展

2. **Phase 2: 通用工具**
   - QueryWeatherTool
   - QueryForecastTool
   - GetTimezoneTool
   - ConvertCurrencyTool

3. **Phase 3: 专用工具**
   - SearchAttractionsTool
   - SearchRestaurantsTool
   - SearchHotelsTool
   - CalculateDistanceTool
   - PlanRouteTool

4. **Phase 4: 信息工具**
   - EstimateBudgetTool
   - CheckHolidayTool

5. **Phase 5: Agent 集成**
   - TravelAgentDefinition
   - 意图路由配置
   - 清理旧代码