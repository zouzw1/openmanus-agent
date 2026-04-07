package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.GeoLocation;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时区查询工具
 * 查询指定城市的时区和当前时间
 */
@Component
public class GetTimezoneTool {

    private final TravelApiClient apiClient;

    public GetTimezoneTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 查询城市时区
     *
     * @param city 城市名称
     * @return 时区信息的JSON字符串
     */
    @Tool(description = "查询指定城市的时区和当前当地时间。参数：city-城市名称(必填)")
    public String getTimezone(
            @ToolParam(description = "城市名称，例如北京、纽约、伦敦") String city) {
        try {
            GeoLocation location = apiClient.geocode(city);

            // 使用经纬度估算时区
            ZoneId zoneId = estimateZoneId(location);
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"city\":\"").append(escapeJson(city)).append("\"");
            sb.append(",\"location\":{\"lat\":").append(location.lat()).append(",\"lon\":").append(location.lon()).append("}");
            sb.append(",\"timezone\":\"").append(zoneId.getId()).append("\"");
            sb.append(",\"currentTime\":\"").append(now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)).append("\"");
            sb.append(",\"utcOffset\":").append(zoneId.getRules().getOffset(now.toInstant()).getTotalSeconds() / 3600);
            sb.append(",\"isDST\":").append(zoneId.getRules().isDaylightSavings(now.toInstant()));
            sb.append("}");
            return sb.toString();
        } catch (TravelApiException e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 根据经纬度估算时区
     */
    private ZoneId estimateZoneId(GeoLocation location) {
        // 基于经度的简单时区估算（每15度一个时区）
        int offsetHours = (int) Math.round(location.lon() / 15.0);

        // 常用时区映射
        if (location.lat() >= 18 && location.lat() <= 54 && offsetHours >= 7 && offsetHours <= 13) {
            // 中国/东亚区域
            if (offsetHours == 8) return ZoneId.of("Asia/Shanghai");
            if (offsetHours == 9) return ZoneId.of("Asia/Tokyo");
        }
        if (location.lat() >= 25 && location.lat() <= 50 && offsetHours >= -5 && offsetHours <= -4) {
            // 美国东部
            return ZoneId.of("America/New_York");
        }
        if (location.lat() >= 25 && location.lat() <= 50 && offsetHours >= -8 && offsetHours <= -7) {
            // 美国西部
            return ZoneId.of("America/Los_Angeles");
        }
        if (location.lat() >= 35 && location.lat() <= 60 && offsetHours >= 0 && offsetHours <= 1) {
            // 欧洲西部（英国/法国等）
            return ZoneId.of("Europe/London");
        }
        if (location.lat() >= 35 && location.lat() <= 60 && offsetHours >= 1 && offsetHours <= 2) {
            // 欧洲中部
            return ZoneId.of("Europe/Paris");
        }

        // 默认使用 UTC 偏移
        return ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(Math.max(-12, Math.min(12, offsetHours))));
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}