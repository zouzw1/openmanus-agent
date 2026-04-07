package com.openmanus.saa.tool.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.travel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class TravelApiClient {

    private static final Logger log = LoggerFactory.getLogger(TravelApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // API 端点
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final String OSRM_URL = "https://router.project-osrm.org";
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String HOLIDAY_URL = "https://date.nager.at/api/v3";
    private static final String CURRENCY_URL = "https://api.frankfurter.app";

    // ==================== Geocoding ====================

    public GeoLocation geocode(String city) {
        Objects.requireNonNull(city, "城市名称不能为空");
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = String.format("%s?name=%s&count=1&language=zh&format=json",
                    GEOCODING_URL, encodedCity);
            log.info("调用地理编码API: 城市={}", city);
            JsonNode root = getJson(url, "Geocoding");
            JsonNode results = root.get("results");
            if (results == null || results.isEmpty()) {
                throw new TravelApiException("Geocoding", "未找到城市: " + city);
            }
            double lat = results.get(0).get("latitude").asDouble();
            double lon = results.get(0).get("longitude").asDouble();
            log.info("地理编码成功: 城市={}, 坐标=({}, {})", city, lat, lon);
            return new GeoLocation(lat, lon);
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("Geocoding", "地理编码失败: " + city, e);
        }
    }

    // ==================== POI Search ====================

    public List<POIItem> searchPoi(String query, GeoLocation location, int radius, int limit) {
        Objects.requireNonNull(query, "查询条件不能为空");
        Objects.requireNonNull(location, "位置信息不能为空");
        if (radius <= 0) {
            throw new IllegalArgumentException("搜索半径必须大于0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("返回数量限制必须大于0");
        }
        try {
            String overpassQuery = String.format(
                    "[out:json][timeout:25];(node[%s](around:%d,%f,%f););out center %d;",
                    query, radius, location.lat(), location.lon(), limit);

            log.info("调用POI搜索API: query={}, 位置=({}, {}), 半径={}m", query, location.lat(), location.lon(), radius);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OVERPASS_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
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
            log.info("POI搜索完成: 找到{}个结果", results.size());
            return results;
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("Overpass", "POI搜索失败: " + query, e);
        }
    }

    // ==================== Route ====================

    public RouteResult getRoute(GeoLocation origin, GeoLocation destination, TravelConstants.TransportMode mode) {
        Objects.requireNonNull(origin, "起点位置不能为空");
        Objects.requireNonNull(destination, "终点位置不能为空");
        Objects.requireNonNull(mode, "交通方式不能为空");
        try {
            String coords = origin.toCoordinateString() + ";" + destination.toCoordinateString();
            String url = String.format("%s/route/v1/%s/%s?steps=true&overview=false",
                    OSRM_URL, mode.toOsrmString(), coords);

            log.info("调用路线规划API: 模式={}, 起点=({}, {}), 终点=({}, {})",
                    mode, origin.lat(), origin.lon(), destination.lat(), destination.lon());

            JsonNode root = getJson(url, "OSRM");
            JsonNode routes = root.get("routes");
            if (routes == null || routes.isEmpty()) {
                throw new TravelApiException("OSRM", "未找到可行路线");
            }
            JsonNode route = routes.get(0);

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

            log.info("路线规划完成: 距离={}km, 时长={}min, 步骤数={}", distance, duration, steps.size());
            return new RouteResult(distance, "km", duration, "min", mode.toOsrmString(), steps);
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("OSRM", "路线规划失败", e);
        }
    }

    // ==================== Weather ====================

    public WeatherInfo getCurrentWeather(GeoLocation location) {
        Objects.requireNonNull(location, "位置信息不能为空");
        try {
            String url = String.format("%s?latitude=%f&longitude=%f&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m&timezone=auto",
                    WEATHER_URL, location.lat(), location.lon());

            log.info("调用天气API: 位置=({}, {})", location.lat(), location.lon());

            JsonNode root = getJson(url, "Weather");
            JsonNode current = root.get("current");
            if (current == null) {
                throw new TravelApiException("Weather", "天气数据不可用");
            }

            int temp = current.get("temperature_2m").asInt();
            int humidity = current.get("relative_humidity_2m").asInt();
            int windSpeed = current.get("wind_speed_10m").asInt();
            boolean isDay = current.get("is_day").asInt() == 1;
            int weatherCode = current.get("weather_code").asInt();
            String condition = interpretWeatherCode(weatherCode, isDay);

            log.info("天气查询成功: 温度={}°C, 天气={}", temp, condition);
            return new WeatherInfo(temp, condition, humidity, windSpeed, isDay, weatherCode);
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("Weather", "天气查询失败", e);
        }
    }

    public List<ForecastInfo> getForecast(GeoLocation location, int days) {
        Objects.requireNonNull(location, "位置信息不能为空");
        if (days <= 0 || days > TravelConstants.MAX_FORECAST_DAYS) {
            throw new IllegalArgumentException("预报天数必须在1-" + TravelConstants.MAX_FORECAST_DAYS + "之间");
        }
        try {
            int effectiveDays = Math.min(days, TravelConstants.MAX_FORECAST_DAYS);
            String url = String.format("%s?latitude=%f&longitude=%f&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=%d",
                    WEATHER_URL, location.lat(), location.lon(), effectiveDays);

            log.info("调用天气预报API: 位置=({}, {}), 天数={}", location.lat(), location.lon(), effectiveDays);

            JsonNode root = getJson(url, "Weather");
            JsonNode daily = root.get("daily");
            if (daily == null) {
                throw new TravelApiException("Weather", "天气预报数据不可用");
            }

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
            log.info("天气预报查询成功: {}天预报", forecasts.size());
            return forecasts;
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("Weather", "天气预报查询失败", e);
        }
    }

    // ==================== Holiday ====================

    public List<HolidayInfo> getHolidays(int year, String countryCode) {
        Objects.requireNonNull(countryCode, "国家代码不能为空");
        if (year < 1900 || year > 2100) {
            throw new IllegalArgumentException("年份必须在1900-2100之间");
        }
        try {
            String url = String.format("%s/PublicHolidays/%d/%s", HOLIDAY_URL, year, countryCode);

            log.info("调用节假日API: 年份={}, 国家={}", year, countryCode);

            JsonNode root = getJson(url, "Holiday");

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
            log.info("节假日查询成功: {}年{}共{}个节假日", year, countryCode, holidays.size());
            return holidays;
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("Holiday", "节假日查询失败: " + year + "/" + countryCode, e);
        }
    }

    // ==================== Currency ====================

    public CurrencyRate getExchangeRate(String from, String to, double amount) {
        Objects.requireNonNull(from, "源货币不能为空");
        Objects.requireNonNull(to, "目标货币不能为空");
        if (amount < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        try {
            String encodedAmount = String.format("%.2f", amount);
            String url = String.format("%s/%s?from=%s&to=%s", CURRENCY_URL, encodedAmount, from, to);

            log.info("调用汇率API: {} {} → {}", amount, from, to);

            JsonNode root = getJson(url, "Currency");
            JsonNode rates = root.get("rates");
            if (rates == null || !rates.has(to)) {
                throw new TravelApiException("Currency", "无法获取汇率: " + from + " → " + to);
            }
            double rate = rates.get(to).asDouble();
            LocalDate date = LocalDate.parse(root.get("date").asText());

            log.info("汇率查询成功: 1 {} = {} {}", from, rate, to);
            return new CurrencyRate(from, to, rate, amount, rate, date);
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException("Currency", "汇率查询失败: " + from + " → " + to, e);
        }
    }

    // ==================== Helper Methods ====================

    private JsonNode getJson(String url, String apiName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new TravelApiException(apiName, response.statusCode(),
                        "HTTP错误: " + response.statusCode() + " from " + url);
            }
            return objectMapper.readTree(response.body());
        } catch (TravelApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TravelApiException(apiName, "API调用失败: " + url, e);
        }
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