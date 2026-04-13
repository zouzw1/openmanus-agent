package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.POIItem;
import com.openmanus.saa.model.travel.TravelConstants;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 餐厅搜索工具
 * 在指定位置附近搜索餐厅、咖啡馆等餐饮场所
 */
@Component
public class SearchRestaurantsTool {

    private final TravelApiClient apiClient;

    public SearchRestaurantsTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 搜索餐厅
     *
     * @param city   城市名称
     * @param radius 搜索半径（米），默认5000米
     * @param limit  返回数量上限，默认20
     * @return 餐厅列表的JSON字符串
     */
    @Tool(description = "在指定城市搜索餐厅和餐饮场所。参数：city-城市名称(必填)，radius-搜索半径米数(可选默认5000)，limit-返回数量上限(可选默认20)，countryCode-国家代码如CN/US(可选)")
    public String searchRestaurants(
            @ToolParam(description = "城市名称，例如北京、上海") String city,
            @ToolParam(description = "搜索半径（米），默认5000") Integer radius,
            @ToolParam(description = "返回数量上限，默认20") Integer limit,
            @ToolParam(description = "国家代码如CN/US/JP，用于精确地理编码") String countryCode) {
        try {
            int effectiveRadius = radius != null ? radius : TravelConstants.DEFAULT_RADIUS;
            int effectiveLimit = limit != null ? limit : TravelConstants.DEFAULT_LIMIT;

            GeoLocation location = apiClient.geocode(city, countryCode);
            // 使用精确 tag 匹配搜索餐饮（比正则匹配更稳定）
            List<String> foodTypes = List.of("restaurant", "cafe", "fast_food", "bar", "pub", "biergarten", "food_court");
            List<POIItem> restaurants = apiClient.searchPoi("amenity", foodTypes, location, effectiveRadius, effectiveLimit);

            return formatResult(city, restaurants, "餐厅");
        } catch (TravelApiException e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String formatResult(String city, List<POIItem> items, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"city\":\"").append(escapeJson(city)).append("\"");
        sb.append(",\"type\":\"").append(type).append("\"");
        sb.append(",\"count\":").append(items.size());
        sb.append(",\"results\":[");

        for (int i = 0; i < items.size(); i++) {
            POIItem item = items.get(i);
            sb.append("{\"name\":\"").append(escapeJson(item.name())).append("\"");
            sb.append(",\"latitude\":").append(item.location().lat());
            sb.append(",\"longitude\":").append(item.location().lon());
            if (item.address() != null) {
                sb.append(",\"address\":\"").append(escapeJson(item.address())).append("\"");
            }
            if (item.type() != null && !item.type().isEmpty()) {
                sb.append(",\"category\":\"").append(escapeJson(item.type())).append("\"");
            }
            if (item.openingHours() != null) {
                sb.append(",\"openingHours\":\"").append(escapeJson(item.openingHours())).append("\"");
            }
            sb.append("}");
            if (i < items.size() - 1) sb.append(",");
        }

        sb.append("]}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}