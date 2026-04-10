# 旅游计划工具集实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 11 个细粒度旅游工具，使用开源免费数据源，支持旅游计划生成

**Architecture:** 内置工具模式，工具类作为 Spring @Component 注册到 ToolRegistryService，通过 @Tool + @ToolDefinition 注解定义元数据，TravelApiClient 封装 HTTP 调用

**Tech Stack:** Spring Boot 3.4.3, Spring AI, Java HttpClient, Jackson, Overpass API, OSRM, Open-Meteo, Nager.Date, Frankfurter

---

## 文件结构

```
openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/
├── model/ToolMetadata.java                    # 修改：新增 Category
├── model/travel/
│   ├── GeoLocation.java                       # 新建
│   ├── POIItem.java                           # 新建
│   ├── RouteResult.java                       # 新建
│   ├── RouteStep.java                         # 新建
│   ├── WeatherInfo.java                       # 新建
│   ├── ForecastInfo.java                      # 新建
│   ├── BudgetEstimate.java                    # 新建
│   ├── HolidayInfo.java                       # 新建
│   ├── CurrencyRate.java                      # 新建
│   └── TravelConstants.java                   # 新建
├── tool/travel/
│   ├── TravelApiClient.java                   # 新建
│   ├── SearchAttractionsTool.java             # 新建
│   ├── SearchRestaurantsTool.java             # 新建
│   ├── SearchHotelsTool.java                  # 新建
│   ├── CalculateDistanceTool.java             # 新建
│   ├── PlanRouteTool.java                     # 新建
│   ├── QueryWeatherTool.java                  # 新建
│   ├── QueryForecastTool.java                 # 新建
│   ├── EstimateBudgetTool.java                # 新建
│   ├── GetTimezoneTool.java                   # 新建
│   ├── CheckHolidayTool.java                  # 新建
│   └── ConvertCurrencyTool.java               # 新建
└── agent/TravelAgentDefinition.java           # 新建

mcp-test-service/src/main/java/com/openmanus/mcptest/service/
├── TestMcpToolService.java                    # 删除
└── WeatherService.java                        # 删除
```

---

## Task 1: 扩展 ToolMetadata.Category

**Files:**
- Modify: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/ToolMetadata.java:27-37`

- [ ] **Step 1: 在 Category 枚举中新增旅游相关分类**

```java
public enum Category {
    FILE,           // 文件操作
    SHELL,          // Shell 命令
    MCP,            // MCP 工具
    RAG,            // 知识检索
    PLANNING,       // 计划管理
    BROWSER,        // 浏览器自动化
    SANDBOX,        // 沙箱执行
    SKILL,          // 技能调用
    TRAVEL_POI,     // POI 搜索工具
    TRAVEL_ROUTE,   // 路线规划工具
    WEATHER,        // 天气工具（通用）
    TRAVEL_INFO,    // 旅游信息工具
    OTHER           // 其他
}
```

- [ ] **Step 2: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/ToolMetadata.java
git commit -m "feat(tool): add TRAVEL_POI, TRAVEL_ROUTE, WEATHER, TRAVEL_INFO categories"
```

---

## Task 2: 创建数据模型

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/GeoLocation.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/POIItem.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/RouteResult.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/RouteStep.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/WeatherInfo.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/ForecastInfo.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/BudgetEstimate.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/HolidayInfo.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/CurrencyRate.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/TravelConstants.java`

- [ ] **Step 1: 创建 GeoLocation.java**

```java
package com.openmanus.saa.model.travel;

public record GeoLocation(double lat, double lon) {
    public String toCoordinateString() {
        return lat + "," + lon;
    }

    public static GeoLocation fromCoordinateString(String coord) {
        String[] parts = coord.split(",");
        return new GeoLocation(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
    }
}
```

- [ ] **Step 2: 创建 POIItem.java**

```java
package com.openmanus.saa.model.travel;

import java.util.List;

public record POIItem(
    String name,
    GeoLocation location,
    String address,
    String type,
    List<String> tags,
    String openingHours
) {}
```

- [ ] **Step 3: 创建 RouteStep.java**

```java
package com.openmanus.saa.model.travel;

public record RouteStep(
    String instruction,
    double distance,
    double duration
) {}
```

- [ ] **Step 4: 创建 RouteResult.java**

```java
package com.openmanus.saa.model.travel;

import java.util.List;

public record RouteResult(
    double distance,
    String distanceUnit,
    double duration,
    String durationUnit,
    String mode,
    List<RouteStep> steps
) {}
```

- [ ] **Step 5: 创建 WeatherInfo.java**

```java
package com.openmanus.saa.model.travel;

public record WeatherInfo(
    int temperature,
    String condition,
    int humidity,
    int windSpeed,
    boolean isDay,
    int weatherCode
) {}
```

- [ ] **Step 6: 创建 ForecastInfo.java**

```java
package com.openmanus.saa.model.travel;

import java.time.LocalDate;

public record ForecastInfo(
    LocalDate date,
    int day,
    int temperatureMax,
    int temperatureMin,
    String condition,
    int precipitationChance,
    int weatherCode
) {}
```

- [ ] **Step 7: 创建 BudgetEstimate.java**

```java
package com.openmanus.saa.model.travel;

import java.util.Map;

public record BudgetEstimate(
    String destination,
    int duration,
    String travelStyle,
    int groupSize,
    Map<String, Double> breakdown,
    double total,
    String currency
) {}
```

- [ ] **Step 8: 创建 HolidayInfo.java**

```java
package com.openmanus.saa.model.travel;

import java.time.LocalDate;
import java.util.List;

public record HolidayInfo(
    LocalDate date,
    String localName,
    String name,
    String countryCode,
    boolean fixed,
    List<String> types
) {}
```

- [ ] **Step 9: 创建 CurrencyRate.java**

```java
package com.openmanus.saa.model.travel;

import java.time.LocalDate;

public record CurrencyRate(
    String base,
    String target,
    double rate,
    double amount,
    double converted,
    LocalDate date
) {}
```

- [ ] **Step 10: 创建 TravelConstants.java**

```java
package com.openmanus.saa.model.travel;

public final class TravelConstants {
    private TravelConstants() {}

    public static final int DEFAULT_RADIUS = 5000;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_FORECAST_DAYS = 7;

    public enum TransportMode {
        DRIVING, WALKING, CYCLING;

        public String toOsrmString() {
            return name().toLowerCase();
        }
    }

    public enum TravelStyle {
        BUDGET, MID_RANGE, LUXURY
    }
}
```

- [ ] **Step 11: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 12: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/travel/
git commit -m "feat(model): add travel data models (GeoLocation, POIItem, RouteResult, WeatherInfo, etc.)"
```

---

## Task 3: 创建 TravelApiClient

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/TravelApiClient.java`

- [ ] **Step 1: 创建 TravelApiClient.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.travel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class TravelApiClient {

    private static final Logger log = LoggerFactory.getLogger(TravelApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // API 端点
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final String OSRM_URL = "https://router.project-osrm.org";
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String HOLIDAY_URL = "https://date.nager.at/api/v3";
    private static final String CURRENCY_URL = "https://api.frankfurter.app";

    // ==================== Geocoding ====================

    public GeoLocation geocode(String city) throws Exception {
        String url = String.format("%s?name=%s&count=1&language=zh&format=json",
                GEOCODING_URL, city.replace(" ", "%20"));
        JsonNode root = getJson(url);
        JsonNode results = root.get("results");
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("City not found: " + city);
        }
        double lat = results.get(0).get("latitude").asDouble();
        double lon = results.get(0).get("longitude").asDouble();
        return new GeoLocation(lat, lon);
    }

    // ==================== POI Search ====================

    public List<POIItem> searchPoi(String query, GeoLocation location, int radius, int limit) throws Exception {
        String overpassQuery = String.format(
                "[out:json][timeout:25];(node[%s](around:%d,%f,%f););out center %d;",
                query, radius, location.lat(), location.lon(), limit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OVERPASS_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("data=" + overpassQuery))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode elements = root.get("elements");

        List<POIItem> results = new ArrayList<>();
        if (elements != null) {
            for (JsonNode element : elements) {
                String name = element.path("tags").path("name").asText("");
                if (name.isEmpty()) continue;

                double lat = element.path("center").path("lat").asDouble(element.path("lat").asDouble());
                double lon = element.path("center").path("lon").asDouble(element.path("lon").asDouble());
                String address = element.path("tags").path("addr:street").asText("") + " " +
                                 element.path("tags").path("addr:housenumber").asText("");
                address = address.trim();
                if (address.isEmpty()) address = null;

                String type = element.path("tags").path(query.split("=")[0]).asText("");
                List<String> tags = new ArrayList<>();
                element.path("tags").fieldNames().forEachRemaining(tags::add);

                String openingHours = element.path("tags").path("opening_hours").asText(null);

                results.add(new POIItem(name, new GeoLocation(lat, lon), address, type, tags, openingHours));
            }
        }
        return results;
    }

    // ==================== Route ====================

    public RouteResult getRoute(GeoLocation origin, GeoLocation destination, TravelConstants.TransportMode mode) throws Exception {
        String coords = origin.toCoordinateString() + ";" + destination.toCoordinateString();
        String url = String.format("%s/route/v1/%s/%s?steps=true&overview=false",
                OSRM_URL, mode.toOsrmString(), coords);

        JsonNode root = getJson(url);
        JsonNode route = root.get("routes").get(0);

        double distance = route.get("distance").asDouble() / 1000.0;
        double duration = route.get("duration").asDouble() / 60.0;

        List<RouteStep> steps = new ArrayList<>();
        for (JsonNode leg : route.get("legs")) {
            for (JsonNode step : leg.get("steps")) {
                String instruction = step.path("name").asText("");
                if (instruction.isEmpty()) {
                    instruction = step.path("maneuver").path("type").asText("continue");
                }
                double stepDist = step.get("distance").asDouble() / 1000.0;
                double stepDur = step.get("duration").asDouble() / 60.0;
                steps.add(new RouteStep(instruction, stepDist, stepDur));
            }
        }

        return new RouteResult(distance, "km", duration, "min", mode.toOsrmString(), steps);
    }

    // ==================== Weather ====================

    public WeatherInfo getCurrentWeather(GeoLocation location) throws Exception {
        String url = String.format("%s?latitude=%f&longitude=%f&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m&timezone=auto",
                WEATHER_URL, location.lat(), location.lon());
        JsonNode root = getJson(url);
        JsonNode current = root.get("current");

        int temp = current.get("temperature_2m").asInt();
        int humidity = current.get("relative_humidity_2m").asInt();
        int windSpeed = current.get("wind_speed_10m").asInt();
        boolean isDay = current.get("is_day").asInt() == 1;
        int weatherCode = current.get("weather_code").asInt();
        String condition = interpretWeatherCode(weatherCode, isDay);

        return new WeatherInfo(temp, condition, humidity, windSpeed, isDay, weatherCode);
    }

    public List<ForecastInfo> getForecast(GeoLocation location, int days) throws Exception {
        int effectiveDays = Math.min(days, TravelConstants.MAX_FORECAST_DAYS);
        String url = String.format("%s?latitude=%f&longitude=%f&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=%d",
                WEATHER_URL, location.lat(), location.lon(), effectiveDays);
        JsonNode root = getJson(url);
        JsonNode daily = root.get("daily");

        List<ForecastInfo> forecasts = new ArrayList<>();
        JsonNode times = daily.get("time");
        JsonNode maxTemps = daily.get("temperature_2m_max");
        JsonNode minTemps = daily.get("temperature_2m_min");
        JsonNode codes = daily.get("weather_code");
        JsonNode precipProbs = daily.get("precipitation_probability_max");

        for (int i = 0; i < times.size(); i++) {
            LocalDate date = LocalDate.parse(times.get(i).asText());
            int maxTemp = maxTemps.get(i).asInt();
            int minTemp = minTemps.get(i).asInt();
            int code = codes.get(i).asInt();
            int precipProb = precipProbs.get(i).asInt();
            String condition = interpretWeatherCode(code, true);

            forecasts.add(new ForecastInfo(date, i + 1, maxTemp, minTemp, condition, precipProb, code));
        }
        return forecasts;
    }

    // ==================== Holiday ====================

    public List<HolidayInfo> getHolidays(int year, String countryCode) throws Exception {
        String url = String.format("%s/PublicHolidays/%d/%s", HOLIDAY_URL, year, countryCode);
        JsonNode root = getJson(url);

        List<HolidayInfo> holidays = new ArrayList<>();
        for (JsonNode node : root) {
            LocalDate date = LocalDate.parse(node.get("date").asText());
            String localName = node.get("localName").asText();
            String name = node.get("name").asText();
            boolean fixed = node.get("fixed").asBoolean();
            List<String> types = new ArrayList<>();
            node.get("types").forEach(t -> types.add(t.asText()));

            holidays.add(new HolidayInfo(date, localName, name, countryCode, fixed, types));
        }
        return holidays;
    }

    // ==================== Currency ====================

    public CurrencyRate getExchangeRate(String from, String to, double amount) throws Exception {
        String url = String.format("%s/%.2f?from=%s&to=%s", CURRENCY_URL, amount, from, to);
        JsonNode root = getJson(url);
        double rate = root.get("rates").get(to).asDouble();
        LocalDate date = LocalDate.parse(root.get("date").asText());

        return new CurrencyRate(from, to, rate, amount, rate, date);
    }

    // ==================== Helper Methods ====================

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("API error: " + response.statusCode() + " from " + url);
        }
        return objectMapper.readTree(response.body());
    }

    private String interpretWeatherCode(int code, boolean isDay) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2, 3 -> isDay ? "多云" : "晴夜";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知";
        };
    }
}
```

- [ ] **Step 2: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/TravelApiClient.java
git commit -m "feat(tool): add TravelApiClient for OpenStreetMap, OSRM, Open-Meteo, Nager.Date, Frankfurter APIs"
```

---

## Task 4: 创建 POI 搜索工具

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/SearchAttractionsTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/SearchRestaurantsTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/SearchHotelsTool.java`

- [ ] **Step 1: 创建 SearchAttractionsTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.POIItem;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchAttractionsTool {

    private static final Logger log = LoggerFactory.getLogger(SearchAttractionsTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public SearchAttractionsTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "搜索指定位置附近的景点、地标、博物馆等旅游景点")
    @ToolDefinition(
        category = Category.TRAVEL_POI,
        tags = {"travel", "poi", "attraction"},
        dangerous = false
    )
    public String searchAttractions(
        @ToolParam(description = "位置：城市名称或经纬度坐标（格式：lat,lon），如：北京 或 39.9163,116.3972", required = true)
        String location,
        @ToolParam(description = "搜索关键词（可选），如：博物馆、公园", required = false)
        String query,
        @ToolParam(description = "搜索半径（米），默认 5000", required = false)
        Integer radius,
        @ToolParam(description = "返回数量上限，默认 20", required = false)
        Integer limit
    ) {
        try {
            GeoLocation geo = resolveLocation(location);
            int effectiveRadius = radius != null ? radius : TravelConstants.DEFAULT_RADIUS;
            int effectiveLimit = limit != null ? limit : TravelConstants.DEFAULT_LIMIT;

            String overpassQuery = query != null && !query.isBlank()
                    ? "tourism=" + query
                    : "tourism=attraction";

            log.info("Searching attractions near {}, query={}", location, overpassQuery);
            List<POIItem> results = apiClient.searchPoi(overpassQuery, geo, effectiveRadius, effectiveLimit);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "count", results.size(),
                    "results", results,
                    "source", "OpenStreetMap/Overpass"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to search attractions: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private GeoLocation resolveLocation(String location) throws Exception {
        if (location.contains(",")) {
            return GeoLocation.fromCoordinateString(location);
        }
        return apiClient.geocode(location);
    }
}
```

- [ ] **Step 2: 创建 SearchRestaurantsTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.POIItem;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchRestaurantsTool {

    private static final Logger log = LoggerFactory.getLogger(SearchRestaurantsTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public SearchRestaurantsTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "搜索指定位置附近的餐厅、咖啡馆、美食等")
    @ToolDefinition(
        category = Category.TRAVEL_POI,
        tags = {"travel", "poi", "restaurant"},
        dangerous = false
    )
    public String searchRestaurants(
        @ToolParam(description = "位置：城市名称或经纬度坐标（格式：lat,lon），如：北京 或 39.9163,116.3972", required = true)
        String location,
        @ToolParam(description = "搜索关键词（可选），如：咖啡馆、中餐", required = false)
        String query,
        @ToolParam(description = "搜索半径（米），默认 5000", required = false)
        Integer radius,
        @ToolParam(description = "返回数量上限，默认 20", required = false)
        Integer limit
    ) {
        try {
            GeoLocation geo = resolveLocation(location);
            int effectiveRadius = radius != null ? radius : TravelConstants.DEFAULT_RADIUS;
            int effectiveLimit = limit != null ? limit : TravelConstants.DEFAULT_LIMIT;

            String overpassQuery = query != null && !query.isBlank()
                    ? "amenity=" + query
                    : "amenity=restaurant";

            log.info("Searching restaurants near {}, query={}", location, overpassQuery);
            List<POIItem> results = apiClient.searchPoi(overpassQuery, geo, effectiveRadius, effectiveLimit);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "count", results.size(),
                    "results", results,
                    "source", "OpenStreetMap/Overpass"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to search restaurants: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private GeoLocation resolveLocation(String location) throws Exception {
        if (location.contains(",")) {
            return GeoLocation.fromCoordinateString(location);
        }
        return apiClient.geocode(location);
    }
}
```

- [ ] **Step 3: 创建 SearchHotelsTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.POIItem;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchHotelsTool {

    private static final Logger log = LoggerFactory.getLogger(SearchHotelsTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public SearchHotelsTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "搜索指定位置附近的酒店、旅馆、民宿等住宿")
    @ToolDefinition(
        category = Category.TRAVEL_POI,
        tags = {"travel", "poi", "hotel"},
        dangerous = false
    )
    public String searchHotels(
        @ToolParam(description = "位置：城市名称或经纬度坐标（格式：lat,lon），如：北京 或 39.9163,116.3972", required = true)
        String location,
        @ToolParam(description = "搜索关键词（可选），如：酒店、民宿", required = false)
        String query,
        @ToolParam(description = "搜索半径（米），默认 5000", required = false)
        Integer radius,
        @ToolParam(description = "返回数量上限，默认 20", required = false)
        Integer limit
    ) {
        try {
            GeoLocation geo = resolveLocation(location);
            int effectiveRadius = radius != null ? radius : TravelConstants.DEFAULT_RADIUS;
            int effectiveLimit = limit != null ? limit : TravelConstants.DEFAULT_LIMIT;

            String overpassQuery = query != null && !query.isBlank()
                    ? "tourism=" + query
                    : "tourism=hotel";

            log.info("Searching hotels near {}, query={}", location, overpassQuery);
            List<POIItem> results = apiClient.searchPoi(overpassQuery, geo, effectiveRadius, effectiveLimit);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "count", results.size(),
                    "results", results,
                    "source", "OpenStreetMap/Overpass"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to search hotels: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private GeoLocation resolveLocation(String location) throws Exception {
        if (location.contains(",")) {
            return GeoLocation.fromCoordinateString(location);
        }
        return apiClient.geocode(location);
    }
}
```

- [ ] **Step 4: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/Search*.java
git commit -m "feat(tool): add SearchAttractionsTool, SearchRestaurantsTool, SearchHotelsTool"
```

---

## Task 5: 创建路线规划工具

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/CalculateDistanceTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/PlanRouteTool.java`

- [ ] **Step 1: 创建 CalculateDistanceTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.RouteResult;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CalculateDistanceTool {

    private static final Logger log = LoggerFactory.getLogger(CalculateDistanceTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public CalculateDistanceTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "计算两个位置之间的距离和预计通行时间")
    @ToolDefinition(
        category = Category.TRAVEL_ROUTE,
        tags = {"travel", "route", "distance"},
        dangerous = false
    )
    public String calculateDistance(
        @ToolParam(description = "起点：城市名称或经纬度坐标（格式：lat,lon），如：北京 或 39.9163,116.3972", required = true)
        String origin,
        @ToolParam(description = "终点：城市名称或经纬度坐标（格式：lat,lon），如：上海 或 31.2304,121.4737", required = true)
        String destination,
        @ToolParam(description = "交通方式：driving（驾车）、walking（步行）、cycling（骑行），默认 driving", required = false)
        String mode
    ) {
        try {
            GeoLocation originGeo = resolveLocation(origin);
            GeoLocation destGeo = resolveLocation(destination);
            TravelConstants.TransportMode transportMode = parseMode(mode);

            log.info("Calculating distance from {} to {} via {}", origin, destination, transportMode);
            RouteResult result = apiClient.getRoute(originGeo, destGeo, transportMode);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "origin", origin,
                    "destination", destination,
                    "distance", result.distance(),
                    "distanceUnit", result.distanceUnit(),
                    "duration", result.duration(),
                    "durationUnit", result.durationUnit(),
                    "mode", result.mode()
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to calculate distance: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private GeoLocation resolveLocation(String location) throws Exception {
        if (location.contains(",")) {
            return GeoLocation.fromCoordinateString(location);
        }
        return apiClient.geocode(location);
    }

    private TravelConstants.TransportMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) return TravelConstants.TransportMode.DRIVING;
        return switch (mode.toLowerCase()) {
            case "walking" -> TravelConstants.TransportMode.WALKING;
            case "cycling" -> TravelConstants.TransportMode.CYCLING;
            default -> TravelConstants.TransportMode.DRIVING;
        };
    }
}
```

- [ ] **Step 2: 创建 PlanRouteTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.RouteResult;
import com.openmanus.saa.model.travel.RouteStep;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PlanRouteTool {

    private static final Logger log = LoggerFactory.getLogger(PlanRouteTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public PlanRouteTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "规划多点路线，计算总距离、总时间和详细步骤")
    @ToolDefinition(
        category = Category.TRAVEL_ROUTE,
        tags = {"travel", "route", "planning"},
        dangerous = false
    )
    public String planRoute(
        @ToolParam(description = "途经点列表（JSON数组），如：[{\"name\":\"故宫\",\"location\":\"39.9163,116.3972\"},{\"name\":\"天坛\",\"location\":\"39.8822,116.4066\"}]", required = true)
        String waypoints,
        @ToolParam(description = "交通方式：driving（驾车）、walking（步行）、cycling（骑行），默认 driving", required = false)
        String mode
    ) {
        try {
            TravelConstants.TransportMode transportMode = parseMode(mode);
            List<GeoLocation> locations = parseWaypoints(waypoints);

            if (locations.size() < 2) {
                return "{\"success\": false, \"error\": \"至少需要2个途经点\"}";
            }

            log.info("Planning route with {} waypoints via {}", locations.size(), transportMode);

            double totalDistance = 0;
            double totalDuration = 0;
            List<RouteStep> allSteps = new ArrayList<>();

            for (int i = 0; i < locations.size() - 1; i++) {
                RouteResult segment = apiClient.getRoute(locations.get(i), locations.get(i + 1), transportMode);
                totalDistance += segment.distance();
                totalDuration += segment.duration();
                allSteps.addAll(segment.steps());
            }

            Map<String, Object> response = Map.of(
                    "success", true,
                    "waypointCount", locations.size(),
                    "totalDistance", totalDistance,
                    "distanceUnit", "km",
                    "totalDuration", totalDuration,
                    "durationUnit", "min",
                    "mode", transportMode.toOsrmString(),
                    "steps", allSteps
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to plan route: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private List<GeoLocation> parseWaypoints(String waypoints) throws Exception {
        List<GeoLocation> locations = new ArrayList<>();
        var array = objectMapper.readTree(waypoints);
        for (var node : array) {
            String loc = node.path("location").asText();
            if (loc.contains(",")) {
                locations.add(GeoLocation.fromCoordinateString(loc));
            } else {
                locations.add(apiClient.geocode(loc));
            }
        }
        return locations;
    }

    private TravelConstants.TransportMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) return TravelConstants.TransportMode.DRIVING;
        return switch (mode.toLowerCase()) {
            case "walking" -> TravelConstants.TransportMode.WALKING;
            case "cycling" -> TravelConstants.TransportMode.CYCLING;
            default -> TravelConstants.TransportMode.DRIVING;
        };
    }
}
```

- [ ] **Step 3: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/CalculateDistanceTool.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/PlanRouteTool.java
git commit -m "feat(tool): add CalculateDistanceTool and PlanRouteTool using OSRM API"
```

---

## Task 6: 创建天气工具

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/QueryWeatherTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/QueryForecastTool.java`

- [ ] **Step 1: 创建 QueryWeatherTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.WeatherInfo;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryWeatherTool {

    private static final Logger log = LoggerFactory.getLogger(QueryWeatherTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public QueryWeatherTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "查询指定城市的当前天气信息，返回温度、天气状况、湿度、风速等实时数据")
    @ToolDefinition(
        category = Category.WEATHER,
        tags = {"general", "weather"},
        dangerous = false
    )
    public String queryWeather(
        @ToolParam(description = "城市名称，如：北京、上海、Tokyo、Paris", required = true)
        String city
    ) {
        try {
            log.info("Querying weather for city: {}", city);
            GeoLocation location = apiClient.geocode(city);
            WeatherInfo weather = apiClient.getCurrentWeather(location);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "city", city,
                    "coordinates", Map.of("lat", location.lat(), "lon", location.lon()),
                    "current", Map.of(
                            "temperature", weather.temperature(),
                            "condition", weather.condition(),
                            "humidity", weather.humidity(),
                            "windSpeed", weather.windSpeed()
                    ),
                    "source", "Open-Meteo"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to query weather for {}: {}", city, e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 2: 创建 QueryForecastTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.ForecastInfo;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QueryForecastTool {

    private static final Logger log = LoggerFactory.getLogger(QueryForecastTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public QueryForecastTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "查询指定城市的未来天气预报，返回每日最高/最低温度、天气状况、降水概率")
    @ToolDefinition(
        category = Category.WEATHER,
        tags = {"general", "weather", "forecast"},
        dangerous = false
    )
    public String queryForecast(
        @ToolParam(description = "城市名称，如：北京、上海、Tokyo、Paris", required = true)
        String city,
        @ToolParam(description = "预报天数（1-7），默认 3", required = false)
        Integer days
    ) {
        try {
            int effectiveDays = (days == null || days < 1 || days > TravelConstants.MAX_FORECAST_DAYS)
                    ? 3 : days;

            log.info("Querying {}-day forecast for city: {}", effectiveDays, city);
            GeoLocation location = apiClient.geocode(city);
            List<ForecastInfo> forecasts = apiClient.getForecast(location, effectiveDays);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "city", city,
                    "coordinates", Map.of("lat", location.lat(), "lon", location.lon()),
                    "days", effectiveDays,
                    "forecast", forecasts,
                    "source", "Open-Meteo"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to query forecast for {}: {}", city, e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 3: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/QueryWeatherTool.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/QueryForecastTool.java
git commit -m "feat(tool): add QueryWeatherTool and QueryForecastTool using Open-Meteo API"
```

---

## Task 7: 创建信息工具

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/GetTimezoneTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/ConvertCurrencyTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/CheckHolidayTool.java`
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/EstimateBudgetTool.java`

- [ ] **Step 1: 创建 GetTimezoneTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class GetTimezoneTool {

    private static final Logger log = LoggerFactory.getLogger(GetTimezoneTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "查询指定城市的时区信息，返回当前时间、UTC偏移量、时区ID")
    @ToolDefinition(
        category = Category.WEATHER,
        tags = {"general", "timezone"},
        dangerous = false
    )
    public String getTimezone(
        @ToolParam(description = "城市名称，如：北京、上海、Tokyo、Paris", required = true)
        String city
    ) {
        try {
            log.info("Getting timezone for city: {}", city);

            Map<String, String> cityTimezoneMap = Map.ofEntries(
                    Map.entry("北京", "Asia/Shanghai"),
                    Map.entry("上海", "Asia/Shanghai"),
                    Map.entry("东京", "Asia/Tokyo"),
                    Map.entry("巴黎", "Europe/Paris"),
                    Map.entry("伦敦", "Europe/London"),
                    Map.entry("纽约", "America/New_York"),
                    Map.entry("悉尼", "Australia/Sydney"),
                    Map.entry("新加坡", "Asia/Singapore"),
                    Map.entry("首尔", "Asia/Seoul"),
                    Map.entry("迪拜", "Asia/Dubai"),
                    Map.entry("Beijing", "Asia/Shanghai"),
                    Map.entry("Shanghai", "Asia/Shanghai"),
                    Map.entry("Tokyo", "Asia/Tokyo"),
                    Map.entry("Paris", "Europe/Paris"),
                    Map.entry("London", "Europe/London"),
                    Map.entry("New York", "America/New_York"),
                    Map.entry("Sydney", "Australia/Sydney"),
                    Map.entry("Singapore", "Asia/Singapore"),
                    Map.entry("Seoul", "Asia/Seoul"),
                    Map.entry("Dubai", "Asia/Dubai")
            );

            String timezoneId = cityTimezoneMap.getOrDefault(city, "UTC");
            ZoneId zoneId = ZoneId.of(timezoneId);
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "city", city,
                    "timezone", timezoneId,
                    "currentTime", now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                    "utcOffset", zoneId.getRules().getOffset(now.toInstant()).getId()
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to get timezone for {}: {}", city, e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 2: 创建 ConvertCurrencyTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.CurrencyRate;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConvertCurrencyTool {

    private static final Logger log = LoggerFactory.getLogger(ConvertCurrencyTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public ConvertCurrencyTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "货币汇率转换，支持主要货币之间的实时汇率查询和金额转换")
    @ToolDefinition(
        category = Category.WEATHER,
        tags = {"general", "currency"},
        dangerous = false
    )
    public String convertCurrency(
        @ToolParam(description = "源货币代码，如：CNY、USD、EUR、JPY", required = true)
        String from,
        @ToolParam(description = "目标货币代码，如：CNY、USD、EUR、JPY", required = true)
        String to,
        @ToolParam(description = "转换金额，默认 1", required = false)
        Double amount
    ) {
        try {
            double effectiveAmount = (amount != null && amount > 0) ? amount : 1.0;

            log.info("Converting {} {} to {}", effectiveAmount, from, to);
            CurrencyRate rate = apiClient.getExchangeRate(from, to, effectiveAmount);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "from", from,
                    "to", to,
                    "amount", effectiveAmount,
                    "rate", rate.rate(),
                    "converted", rate.converted(),
                    "date", rate.date().toString(),
                    "source", "Frankfurter (European Central Bank)"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to convert currency: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 3: 创建 CheckHolidayTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.HolidayInfo;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.List;
import java.util.Map;

@Component
public class CheckHolidayTool {

    private static final Logger log = LoggerFactory.getLogger(CheckHolidayTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelApiClient apiClient;

    public CheckHolidayTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "查询指定国家的公共节假日信息，用于旅行规划避开或利用节假日")
    @ToolDefinition(
        category = Category.TRAVEL_INFO,
        tags = {"travel", "holiday"},
        dangerous = false
    )
    public String checkHoliday(
        @ToolParam(description = "国家代码，如：CN（中国）、US（美国）、JP（日本）、KR（韩国）", required = true)
        String countryCode,
        @ToolParam(description = "年份，默认当前年份", required = false)
        Integer year
    ) {
        try {
            int effectiveYear = (year != null && year > 2000) ? year : Year.now().getValue();

            log.info("Checking holidays for country={}, year={}", countryCode, effectiveYear);
            List<HolidayInfo> holidays = apiClient.getHolidays(effectiveYear, countryCode);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "countryCode", countryCode,
                    "year", effectiveYear,
                    "count", holidays.size(),
                    "holidays", holidays,
                    "source", "Nager.Date"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to check holidays: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 4: 创建 EstimateBudgetTool.java**

```java
package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.travel.BudgetEstimate;
import com.openmanus.saa.model.travel.TravelConstants;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EstimateBudgetTool {

    private static final Logger log = LoggerFactory.getLogger(EstimateBudgetTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 基础日均费用模型（USD）
    private static final Map<String, Map<String, Double>> BUDGET_MODEL = Map.ofEntries(
            Map.entry("BUDGET", Map.of("accommodation", 30.0, "food", 15.0, "transport", 10.0, "attraction", 10.0)),
            Map.entry("MID_RANGE", Map.of("accommodation", 100.0, "food", 40.0, "transport", 25.0, "attraction", 25.0)),
            Map.entry("LUXURY", Map.of("accommodation", 300.0, "food", 100.0, "transport", 60.0, "attraction", 50.0))
    );

    @Tool(description = "估算旅行费用，根据目的地、天数、旅行风格和人数计算每日和总费用")
    @ToolDefinition(
        category = Category.TRAVEL_INFO,
        tags = {"travel", "budget", "estimate"},
        dangerous = false
    )
    public String estimateBudget(
        @ToolParam(description = "目的地城市名称", required = true)
        String destination,
        @ToolParam(description = "旅行天数", required = true)
        Integer duration,
        @ToolParam(description = "旅行风格：BUDGET（经济）、MID_RANGE（中等）、LUXURY（豪华），默认 MID_RANGE", required = false)
        String travelStyle,
        @ToolParam(description = "人数，默认 1", required = false)
        Integer groupSize
    ) {
        try {
            int effectiveDuration = (duration != null && duration > 0) ? duration : 3;
            String effectiveStyle = (travelStyle != null && !travelStyle.isBlank()) ? travelStyle.toUpperCase() : "MID_RANGE";
            int effectiveGroup = (groupSize != null && groupSize > 0) ? groupSize : 1;

            log.info("Estimating budget for {} days in {} (style={}, group={})",
                    effectiveDuration, destination, effectiveStyle, effectiveGroup);

            Map<String, Double> dailyCosts = BUDGET_MODEL.getOrDefault(effectiveStyle, BUDGET_MODEL.get("MID_RANGE"));
            double dailyTotal = dailyCosts.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalPerPerson = dailyTotal * effectiveDuration;
            double totalGroup = totalPerPerson * effectiveGroup;

            Map<String, Double> breakdown = Map.of(
                    "accommodation", dailyCosts.get("accommodation") * effectiveDuration * effectiveGroup,
                    "food", dailyCosts.get("food") * effectiveDuration * effectiveGroup,
                    "transport", dailyCosts.get("transport") * effectiveDuration * effectiveGroup,
                    "attraction", dailyCosts.get("attraction") * effectiveDuration * effectiveGroup
            );

            BudgetEstimate estimate = new BudgetEstimate(
                    destination, effectiveDuration, effectiveStyle, effectiveGroup,
                    breakdown, totalGroup, "USD"
            );

            Map<String, Object> response = Map.of(
                    "success", true,
                    "estimate", estimate,
                    "dailyPerPerson", dailyTotal,
                    "note", "费用为估算值，实际费用因地区和个人消费习惯而异"
            );
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to estimate budget: {}", e.getMessage());
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 5: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/GetTimezoneTool.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/ConvertCurrencyTool.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/CheckHolidayTool.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/travel/EstimateBudgetTool.java
git commit -m "feat(tool): add GetTimezoneTool, ConvertCurrencyTool, CheckHolidayTool, EstimateBudgetTool"
```

---

## Task 8: 创建 TravelAgent

**Files:**
- Create: `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/agent/TravelAgentDefinition.java`

- [ ] **Step 1: 查看现有 Agent 实现模式**

```bash
grep -r "implements AgentConfigSource" openmanus-spring-ai-alibaba/src/main/java/
```

- [ ] **Step 2: 创建 TravelAgentDefinition.java**

```java
package com.openmanus.saa.agent;

import com.openmanus.saa.model.ToolMetadata.Category;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TravelAgentDefinition implements AgentConfigSource {

    @Override
    public AgentDefinition getDefinition() {
        return AgentDefinition.builder()
                .id("travel")
                .name("TravelAgent")
                .description("旅游规划专家，帮助用户规划行程、搜索景点、查询天气、估算费用")
                .systemPrompt(buildSystemPrompt())
                .localTools(new IdAccessPolicy(List.of(
                        // POI 搜索
                        "searchAttractions",
                        "searchRestaurants",
                        "searchHotels",
                        // 路线规划
                        "calculateDistance",
                        "planRoute",
                        // 天气
                        "queryWeather",
                        "queryForecast",
                        // 信息工具
                        "getTimezone",
                        "convertCurrency",
                        "checkHoliday",
                        "estimateBudget"
                ), List.of()))
                .build();
    }

    private String buildSystemPrompt() {
        return """
                你是一个专业的旅游规划助手，帮助用户规划完美的旅行体验。

                你可以：
                1. 搜索景点、餐厅、酒店等 POI
                2. 规划路线和计算距离
                3. 查询天气和预报
                4. 估算旅行费用
                5. 查询节假日和汇率信息

                在规划行程时：
                - 考虑天气因素，避免恶劣天气安排户外活动
                - 优化路线顺序，减少往返时间
                - 合理安排时间，每个景点预留充足时间
                - 提供费用估算，帮助用户控制预算

                使用工具获取真实数据，不要编造信息。
                所有输出使用结构化 JSON 格式。
                """;
    }
}
```

- [ ] **Step 3: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/agent/TravelAgentDefinition.java
git commit -m "feat(agent): add TravelAgentDefinition with travel tool access policy"
```

---

## Task 9: 删除旧 MCP 工具

**Files:**
- Delete: `mcp-test-service/src/main/java/com/openmanus/mcptest/service/TestMcpToolService.java`
- Delete: `mcp-test-service/src/main/java/com/openmanus/mcptest/service/WeatherService.java`

- [ ] **Step 1: 删除 TestMcpToolService.java**

```bash
rm mcp-test-service/src/main/java/com/openmanus/mcptest/service/TestMcpToolService.java
```

- [ ] **Step 2: 删除 WeatherService.java**

```bash
rm mcp-test-service/src/main/java/com/openmanus/mcptest/service/WeatherService.java
```

- [ ] **Step 3: 检查是否有其他引用**

```bash
grep -r "TestMcpToolService\|WeatherService" openmanus-spring-ai-alibaba/ mcp-test-service/ --include="*.java" | grep -v ".class"
```

- [ ] **Step 4: 运行编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git rm mcp-test-service/src/main/java/com/openmanus/mcptest/service/TestMcpToolService.java mcp-test-service/src/main/java/com/openmanus/mcptest/service/WeatherService.java
git commit -m "refactor(mcp): remove TestMcpToolService and WeatherService, replaced by travel tools"
```

---

## Task 10: 集成验证

**Files:**
- None (verification only)

- [ ] **Step 1: 运行完整编译**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行测试**

```bash
cd openmanus-spring-ai-alibaba && mvn test -q
```

Expected: Tests passed

- [ ] **Step 3: 验证工具注册**

启动应用并检查日志中工具注册情况：

```bash
cd openmanus-spring-ai-alibaba && mvn spring-boot:run 2>&1 | grep -i "travel\|tool"
```

Expected: 看到 TravelAgent 和 travel tools 注册日志

- [ ] **Step 4: 提交最终验证**

```bash
git add -A
git commit -m "verify: integration verification for travel tools"
```

---

## 总结

| 任务 | 内容 | 文件数 |
|------|------|--------|
| Task 1 | 扩展 Category | 1 修改 |
| Task 2 | 数据模型 | 10 新建 |
| Task 3 | TravelApiClient | 1 新建 |
| Task 4 | POI 搜索工具 | 3 新建 |
| Task 5 | 路线规划工具 | 2 新建 |
| Task 6 | 天气工具 | 2 新建 |
| Task 7 | 信息工具 | 4 新建 |
| Task 8 | TravelAgent | 1 新建 |
| Task 9 | 删除旧代码 | 2 删除 |
| Task 10 | 集成验证 | - |
| **合计** | | **26 文件** |