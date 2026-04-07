package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.RouteResult;
import com.openmanus.saa.model.travel.TravelConstants;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 距离计算工具
 * 计算两个位置之间的距离和预估行程时间
 */
@Component
public class CalculateDistanceTool {

    private final TravelApiClient apiClient;

    public CalculateDistanceTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 计算两点之间的距离
     *
     * @param fromCity       起点城市
     * @param toCity         终点城市
     * @param transportMode  交通方式（driving/walking/cycling）
     * @return 距离和时间信息的JSON字符串
     */
    @Tool(description = "计算两个城市之间的距离和预估行程时间。参数：fromCity-起点城市(必填)，toCity-终点城市(必填)，transportMode-交通方式(可选默认driving，可选值driving/walking/cycling)")
    public String calculateDistance(
            @ToolParam(description = "起点城市名称") String fromCity,
            @ToolParam(description = "终点城市名称") String toCity,
            @ToolParam(description = "交通方式：driving(驾车)、walking(步行)、cycling(骑行)") String transportMode) {
        try {
            GeoLocation from = apiClient.geocode(fromCity);
            GeoLocation to = apiClient.geocode(toCity);

            TravelConstants.TransportMode mode = parseTransportMode(transportMode);
            RouteResult route = apiClient.getRoute(from, to, mode);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"from\":\"").append(escapeJson(fromCity)).append("\"");
            sb.append(",\"to\":\"").append(escapeJson(toCity)).append("\"");
            sb.append(",\"mode\":\"").append(mode.toOsrmString()).append("\"");
            sb.append(",\"distance\":").append(route.distance());
            sb.append(",\"distanceUnit\":\"").append(route.distanceUnit()).append("\"");
            sb.append(",\"duration\":").append(route.duration());
            sb.append(",\"durationUnit\":\"").append(route.durationUnit()).append("\"");
            sb.append(",\"fromLocation\":{\"lat\":").append(from.lat()).append(",\"lon\":").append(from.lon()).append("}");
            sb.append(",\"toLocation\":{\"lat\":").append(to.lat()).append(",\"lon\":").append(to.lon()).append("}");
            sb.append("}");
            return sb.toString();
        } catch (TravelApiException e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private TravelConstants.TransportMode parseTransportMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return TravelConstants.TransportMode.DRIVING;
        }
        return switch (mode.toLowerCase()) {
            case "walking", "步行" -> TravelConstants.TransportMode.WALKING;
            case "cycling", "骑行", "自行车" -> TravelConstants.TransportMode.CYCLING;
            default -> TravelConstants.TransportMode.DRIVING;
        };
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}