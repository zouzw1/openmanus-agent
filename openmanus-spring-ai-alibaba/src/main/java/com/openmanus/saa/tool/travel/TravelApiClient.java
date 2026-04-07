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
