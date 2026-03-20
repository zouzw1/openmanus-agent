package com.openmanus.mcptest.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    
    // 使用 Open-Meteo 免费天气 API（无需 API Key）
    private static final String WEATHER_API_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_API_URL = "https://geocoding-api.open-meteo.com/v1/search";

    /**
     * 获取城市坐标
     */
    public Map<String, Double> getCityCoordinates(String cityName) throws Exception {
        log.info("Searching coordinates for city: {}", cityName);
        
        String url = String.format("%s?name=%s&count=1&language=en&format=json", 
                GEOCODING_API_URL, cityName.replace(" ", "%20"));
        
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Geocoding API error: " + response.statusCode());
        }
        
        JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.body());
        
        JsonNode results = root.get("results");
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("City not found: " + cityName);
        }
        
        double latitude = results.get(0).get("latitude").asDouble();
        double longitude = results.get(0).get("longitude").asDouble();
        
        log.info("Found coordinates for {}: latitude={}, longitude={}", 
                cityName, latitude, longitude);
        
        return Map.of("latitude", latitude, "longitude", longitude);
    }

    /**
     * 获取当前天气数据
     */
    public Map<String, Object> getCurrentWeather(double latitude, double longitude) throws Exception {
        log.info("Fetching current weather for coordinates: {}, {}", latitude, longitude);
        
        String url = String.format(
                "%s?latitude=%s&longitude=%s&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m&timezone=auto",
                WEATHER_API_URL, latitude, longitude
        );
        
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Weather API error: " + response.statusCode());
        }
        
        JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.body());
        
        JsonNode current = root.get("current");
        if (current == null) {
            throw new RuntimeException("No current weather data available");
        }
        
        int temperature = current.get("temperature_2m").asInt();
        int humidity = current.get("relative_humidity_2m").asInt();
        int windSpeed = current.get("wind_speed_10m").asInt();
        boolean isDay = current.get("is_day").asInt() == 1;
        int weatherCode = current.get("weather_code").asInt();
        
        String condition = interpretWeatherCode(weatherCode, isDay);
        
        log.info("Retrieved weather: temp={}°C, condition={}, humidity={}%, wind={}km/h",
                temperature, condition, humidity, windSpeed);
        
        return Map.of(
                "temperature", temperature,
                "condition", condition,
                "humidity", humidity,
                "windSpeed", windSpeed,
                "isDay", isDay,
                "weatherCode", weatherCode
        );
    }

    /**
     * 获取天气预报数据
     */
    public List<Map<String, Object>> getWeatherForecast(double latitude, double longitude, int days) throws Exception {
        log.info("Fetching {}-day weather forecast for coordinates: {}, {}", days, latitude, longitude);
        
        String url = String.format(
                "%s?latitude=%s&longitude=%s&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=%d",
                WEATHER_API_URL, latitude, longitude, Math.min(days, 7)
        );
        
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Weather forecast API error: " + response.statusCode());
        }
        
        JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.body());
        
        JsonNode daily = root.get("daily");
        if (daily == null) {
            throw new RuntimeException("No forecast data available");
        }
        
        JsonNode times = daily.get("time");
        JsonNode maxTemps = daily.get("temperature_2m_max");
        JsonNode minTemps = daily.get("temperature_2m_min");
        JsonNode codes = daily.get("weather_code");
        JsonNode precipProbs = daily.get("precipitation_probability_max");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault());
        
        java.util.List<Map<String, Object>> forecast = new java.util.ArrayList<>();
        for (int i = 0; i < times.size() && i < days; i++) {
            String date = times.get(i).asText();
            int maxTemp = maxTemps.get(i).asInt();
            int minTemp = minTemps.get(i).asInt();
            int code = codes.get(i).asInt();
            int precipProb = precipProbs.get(i).asInt();
            
            String condition = interpretWeatherCode(code, true);
            
            Map<String, Object> dayForecast = Map.of(
                    "date", date,
                    "day", i + 1,
                    "temperatureMax", maxTemp,
                    "temperatureMin", minTemp,
                    "condition", condition,
                    "precipitation_chance", precipProb,
                    "weatherCode", code
            );
            
            forecast.add(dayForecast);
        }
        
        log.info("Retrieved {} days of forecast", forecast.size());
        
        return forecast;
    }

    /**
     * 根据 WMO 天气代码解释天气状况
     */
    private String interpretWeatherCode(int code, boolean isDay) {
        // WMO Weather interpretation codes (WW)
        return switch (code) {
            case 0 -> "Clear";
            case 1, 2, 3 -> isDay ? "Partly Cloudy" : "Clear Night";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing Drizzle";
            case 61, 63, 65 -> "Rainy";
            case 66, 67 -> "Freezing Rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow Grains";
            case 80, 81, 82 -> "Showers";
            case 85, 86 -> "Snow Showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with Hail";
            default -> "Unknown";
        };
    }
}
