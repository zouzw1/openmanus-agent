package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.GeoLocation;
import com.openmanus.saa.model.travel.POIItem;
import com.openmanus.saa.model.travel.TravelConstants;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 酒店搜索工具
 * 在指定位置附近搜索酒店、旅馆等住宿场所
 */
@Component
public class SearchHotelsTool {

    private final TravelApiClient apiClient;

    public SearchHotelsTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 搜索酒店
     *
     * @param city   城市名称
     * @param radius 搜索半径（米），默认5000米
     * @param limit  返回数量上限，默认20
     * @return 酒店列表的JSON字符串
     */
    @Tool(description = "在指定城市搜索酒店和住宿场所。参数：city-城市名称(必填)，radius-搜索半径米数(可选默认5000)，limit-返回数量上限(可选默认20)")
    public String searchHotels(
            @ToolParam(description = "城市名称，例如北京、上海") String city,
            @ToolParam(description = "搜索半径（米），默认5000") Integer radius,
            @ToolParam(description = "返回数量上限，默认20") Integer limit) {
        try {
            int effectiveRadius = radius != null ? radius : TravelConstants.DEFAULT_RADIUS;
            int effectiveLimit = limit != null ? limit : TravelConstants.DEFAULT_LIMIT;

            GeoLocation location = apiClient.geocode(city);
            // 搜索住宿相关POI: tourism=hotel, tourism=hostel, tourism=guest_house
            String query = "tourism~\"hotel|hostel|guest_house|apartment|motel|chalet\"";
            List<POIItem> hotels = apiClient.searchPoi(query, location, effectiveRadius, effectiveLimit);

            return formatResult(city, hotels, "酒店");
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