package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.ForecastInfo;
import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.TravelConstants;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 天气预报工具
 * 查询指定城市的未来几天天气预报
 */
@Component
public class QueryForecastTool {

    private final TravelApiClient apiClient;

    public QueryForecastTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 查询天气预报
     *
     * @param city 城市名称
     * @param days 预报天数（1-7天）
     * @return 天气预报信息的JSON字符串
     */
    @Tool(description = "查询指定城市未来几天的天气预报。参数：city-城市名称(必填)，days-预报天数(可选默认7，范围1-7)，countryCode-国家代码如CN/US(可选)")
    public String queryForecast(
            @ToolParam(description = "城市名称，例如北京、上海") String city,
            @ToolParam(description = "预报天数，范围1-7，默认7天") Integer days,
            @ToolParam(description = "国家代码如CN/US/JP，用于精确地理编码") String countryCode) {
        try {
            GeoLocation location = apiClient.geocode(city, countryCode);
            int effectiveDays = days != null ? Math.min(Math.max(days, 1), TravelConstants.MAX_FORECAST_DAYS) : TravelConstants.MAX_FORECAST_DAYS;

            List<ForecastInfo> forecasts = apiClient.getForecast(location, effectiveDays);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"city\":\"").append(escapeJson(city)).append("\"");
            sb.append(",\"location\":{\"lat\":").append(location.lat()).append(",\"lon\":").append(location.lon()).append("}");
            sb.append(",\"days\":").append(forecasts.size());
            sb.append(",\"forecasts\":[");

            for (int i = 0; i < forecasts.size(); i++) {
                ForecastInfo forecast = forecasts.get(i);
                sb.append("{");
                sb.append("\"date\":\"").append(forecast.date()).append("\"");
                sb.append(",\"day\":").append(forecast.day());
                sb.append(",\"temperatureMax\":").append(forecast.temperatureMax());
                sb.append(",\"temperatureMin\":").append(forecast.temperatureMin());
                sb.append(",\"condition\":\"").append(escapeJson(forecast.condition())).append("\"");
                sb.append(",\"precipitationChance\":").append(forecast.precipitationChance());
                sb.append(",\"weatherCode\":").append(forecast.weatherCode());
                sb.append("}");
                if (i < forecasts.size() - 1) sb.append(",");
            }

            sb.append("]}");
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