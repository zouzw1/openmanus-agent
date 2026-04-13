package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.RouteResult;
import com.openmanus.saa.model.travel.RouteStep;
import com.openmanus.saa.model.travel.TravelConstants;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 路线规划工具
 * 提供详细的导航路线信息，包括每一步的指引
 */
@Component
public class PlanRouteTool {

    private final TravelApiClient apiClient;

    public PlanRouteTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 规划详细路线
     *
     * @param fromCity       起点城市
     * @param toCity         终点城市
     * @param transportMode  交通方式
     * @return 详细路线规划的JSON字符串
     */
    @Tool(description = "规划两个城市之间的详细导航路线。参数：fromCity-起点城市(必填)，toCity-终点城市(必填)，transportMode-交通方式(可选默认driving)，countryCode-国家代码如CN/US(可选)")
    public String planRoute(
            @ToolParam(description = "起点城市名称") String fromCity,
            @ToolParam(description = "终点城市名称") String toCity,
            @ToolParam(description = "交通方式：driving(驾车)、walking(步行)、cycling(骑行)") String transportMode,
            @ToolParam(description = "国家代码如CN/US/JP，用于精确地理编码") String countryCode) {
        try {
            GeoLocation from = apiClient.geocode(fromCity, countryCode);
            GeoLocation to = apiClient.geocode(toCity, countryCode);

            TravelConstants.TransportMode mode = parseTransportMode(transportMode);
            RouteResult route = apiClient.getRoute(from, to, mode);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"from\":\"").append(escapeJson(fromCity)).append("\"");
            sb.append(",\"to\":\"").append(escapeJson(toCity)).append("\"");
            sb.append(",\"mode\":\"").append(mode.toOsrmString()).append("\"");
            sb.append(",\"summary\":{");
            sb.append("\"distance\":").append(route.distance());
            sb.append(",\"distanceUnit\":\"").append(route.distanceUnit()).append("\"");
            sb.append(",\"duration\":").append(route.duration());
            sb.append(",\"durationUnit\":\"").append(route.durationUnit()).append("\"");
            sb.append("}");
            sb.append(",\"fromLocation\":{\"lat\":").append(from.lat()).append(",\"lon\":").append(from.lon()).append("}");
            sb.append(",\"toLocation\":{\"lat\":").append(to.lat()).append(",\"lon\":").append(to.lon()).append("}");
            sb.append(",\"steps\":[");

            for (int i = 0; i < route.steps().size(); i++) {
                RouteStep step = route.steps().get(i);
                sb.append("{");
                sb.append("\"step\":").append(i + 1).append(",");
                sb.append("\"instruction\":\"").append(escapeJson(step.instruction())).append("\"");
                sb.append(",\"distance\":").append(step.distance());
                sb.append(",\"duration\":").append(step.duration());
                sb.append("}");
                if (i < route.steps().size() - 1) sb.append(",");
            }

            sb.append("]}");
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