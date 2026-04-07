package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.WeatherInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具
 * 查询指定城市的当前天气状况
 */
@Component
public class QueryWeatherTool {

    private final TravelApiClient apiClient;

    public QueryWeatherTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 查询当前天气
     *
     * @param city 城市名称
     * @return 当前天气信息的JSON字符串
     */
    @Tool(description = "查询指定城市的当前天气状况。参数：city-城市名称(必填)")
    public String queryWeather(
            @ToolParam(description = "城市名称，例如北京、上海") String city) {
        try {
            GeoLocation location = apiClient.geocode(city);
            WeatherInfo weather = apiClient.getCurrentWeather(location);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"city\":\"").append(escapeJson(city)).append("\"");
            sb.append(",\"location\":{\"lat\":").append(location.lat()).append(",\"lon\":").append(location.lon()).append("}");
            sb.append(",\"temperature\":").append(weather.temperature());
            sb.append(",\"condition\":\"").append(escapeJson(weather.condition())).append("\"");
            sb.append(",\"humidity\":").append(weather.humidity());
            sb.append(",\"windSpeed\":").append(weather.windSpeed());
            sb.append(",\"isDay\":").append(weather.isDay());
            sb.append(",\"weatherCode\":").append(weather.weatherCode());
            sb.append("}");
            return sb.toString();
        } catch (TravelApiException e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}