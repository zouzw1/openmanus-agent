package com.openmanus.saa.tool.travel;

import com.openmanus.saa.model.travel.HolidayInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 节假日查询工具
 * 查询指定国家指定年份的节假日信息
 */
@Component
public class CheckHolidayTool {

    private final TravelApiClient apiClient;

    public CheckHolidayTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 查询节假日
     *
     * @param year        年份
     * @param countryCode 国家代码（如CN、US、JP）
     * @return 节假日信息的JSON字符串
     */
    @Tool(description = "查询指定国家某年的节假日信息。参数：year-年份(必填如2025)，countryCode-国家代码(必填如CN中国、US美国、JP日本)")
    public String checkHoliday(
            @ToolParam(description = "年份，例如2025") Integer year,
            @ToolParam(description = "国家代码，例如CN(中国)、US(美国)、JP(日本)") String countryCode) {
        try {
            if (year == null || year < 1900 || year > 2100) {
                return "{\"success\":false,\"error\":\"年份必须在1900-2100之间\"}";
            }
            if (countryCode == null || countryCode.isBlank()) {
                return "{\"success\":false,\"error\":\"国家代码不能为空\"}";
            }

            List<HolidayInfo> holidays = apiClient.getHolidays(year, countryCode.toUpperCase());

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"year\":").append(year);
            sb.append(",\"country\":\"").append(escapeJson(countryCode.toUpperCase())).append("\"");
            sb.append(",\"count\":").append(holidays.size());
            sb.append(",\"holidays\":[");

            for (int i = 0; i < holidays.size(); i++) {
                HolidayInfo holiday = holidays.get(i);
                sb.append("{");
                sb.append("\"date\":\"").append(holiday.date()).append("\"");
                sb.append(",\"name\":\"").append(escapeJson(holiday.name())).append("\"");
                sb.append(",\"localName\":\"").append(escapeJson(holiday.localName())).append("\"");
                sb.append(",\"fixed\":").append(holiday.fixed());
                sb.append(",\"types\":").append(holiday.types());
                sb.append("}");
                if (i < holidays.size() - 1) sb.append(",");
            }

            sb.append("]}");
            return sb.toString();
        } catch (TravelApiException e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 检查指定日期是否为节假日
     *
     * @param date        日期（YYYY-MM-DD格式）
     * @param countryCode 国家代码
     * @return 是否为节假日的JSON字符串
     */
    @Tool(description = "检查指定日期是否为节假日。参数：date-日期(必填YYYY-MM-DD格式)，countryCode-国家代码(必填如CN)")
    public String isHoliday(
            @ToolParam(description = "日期，格式YYYY-MM-DD，例如2025-01-01") String date,
            @ToolParam(description = "国家代码，例如CN(中国)") String countryCode) {
        try {
            if (date == null || date.isBlank()) {
                return "{\"success\":false,\"error\":\"日期不能为空\"}";
            }

            LocalDate checkDate;
            try {
                checkDate = LocalDate.parse(date);
            } catch (Exception e) {
                return "{\"success\":false,\"error\":\"日期格式错误，请使用YYYY-MM-DD格式\"}";
            }

            int year = checkDate.getYear();
            List<HolidayInfo> holidays = apiClient.getHolidays(year, countryCode.toUpperCase());

            boolean isHoliday = false;
            String holidayName = null;
            for (HolidayInfo holiday : holidays) {
                if (holiday.date().equals(checkDate)) {
                    isHoliday = true;
                    holidayName = holiday.name();
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"date\":\"").append(date).append("\"");
            sb.append(",\"country\":\"").append(escapeJson(countryCode.toUpperCase())).append("\"");
            sb.append(",\"isHoliday\":").append(isHoliday);
            if (isHoliday) {
                sb.append(",\"holidayName\":\"").append(escapeJson(holidayName)).append("\"");
            }
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