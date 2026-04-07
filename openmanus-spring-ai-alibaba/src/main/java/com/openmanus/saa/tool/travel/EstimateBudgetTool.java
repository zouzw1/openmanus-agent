package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.TravelConstants;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 旅行预算估算工具
 * 根据目的地、天数、旅行风格估算旅行预算
 */
@Component
public class EstimateBudgetTool {

    private final TravelApiClient apiClient;

    public EstimateBudgetTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 估算旅行预算
     *
     * @param destination 目的地城市
     * @param duration    旅行天数
     * @param travelStyle 旅行风格（budget/mid_range/luxury）
     * @param groupSize   人数
     * @return 预算估算的JSON字符串
     */
    @Tool(description = "估算旅行预算。参数：destination-目的地城市(必填)，duration-天数(必填)，travelStyle-风格(可选默认mid_range，可选值budget/mid_range/luxury)，groupSize-人数(可选默认1)")
    public String estimateBudget(
            @ToolParam(description = "目的地城市名称") String destination,
            @ToolParam(description = "旅行天数") Integer duration,
            @ToolParam(description = "旅行风格：budget(经济型)、mid_range(中档)、luxury(豪华)") String travelStyle,
            @ToolParam(description = "出行人数") Integer groupSize) {
        try {
            if (destination == null || destination.isBlank()) {
                return "{\"success\":false,\"error\":\"目的地不能为空\"}";
            }
            if (duration == null || duration <= 0) {
                return "{\"success\":false,\"error\":\"旅行天数必须大于0\"}";
            }

            int effectiveGroupSize = groupSize != null && groupSize > 0 ? groupSize : 1;
            String effectiveStyle = travelStyle != null && !travelStyle.isBlank() ? travelStyle.toLowerCase() : "mid_range";

            // 基础预算（每人每天，单位：美元）
            Map<String, Double> baseCosts = getBaseCosts(effectiveStyle);

            // 计算各项费用
            Map<String, Double> breakdown = new LinkedHashMap<>();
            double total = 0;

            for (Map.Entry<String, Double> entry : baseCosts.entrySet()) {
                double cost = entry.getValue() * duration * effectiveGroupSize;
                breakdown.put(entry.getKey(), cost);
                total += cost;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"destination\":\"").append(escapeJson(destination)).append("\"");
            sb.append(",\"duration\":").append(duration);
            sb.append(",\"travelStyle\":\"").append(effectiveStyle).append("\"");
            sb.append(",\"groupSize\":").append(effectiveGroupSize);
            sb.append(",\"currency\":\"USD\"");
            sb.append(",\"breakdown\":{");

            int i = 0;
            for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":").append(String.format("%.2f", entry.getValue()));
                i++;
            }

            sb.append("}");
            sb.append(",\"total\":").append(String.format("%.2f", total));
            sb.append(",\"perPerson\":").append(String.format("%.2f", total / effectiveGroupSize));
            sb.append(",\"perDay\":").append(String.format("%.2f", total / duration));
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 获取不同旅行风格的基础费用（每人每天，美元）
     */
    private Map<String, Double> getBaseCosts(String style) {
        Map<String, Double> costs = new LinkedHashMap<>();

        switch (style) {
            case "budget":
                costs.put("accommodation", 30.0);
                costs.put("food", 15.0);
                costs.put("transport", 10.0);
                costs.put("activities", 10.0);
                costs.put("misc", 5.0);
                break;
            case "luxury":
                costs.put("accommodation", 250.0);
                costs.put("food", 100.0);
                costs.put("transport", 50.0);
                costs.put("activities", 80.0);
                costs.put("misc", 30.0);
                break;
            case "mid_range":
            default:
                costs.put("accommodation", 80.0);
                costs.put("food", 40.0);
                costs.put("transport", 20.0);
                costs.put("activities", 30.0);
                costs.put("misc", 10.0);
                break;
        }

        return costs;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}