package com.openmanus.saa.tool.travel;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 汇率转换工具
 * 查询货币汇率并进行金额转换
 */
@Component
public class ConvertCurrencyTool {

    private final TravelApiClient apiClient;

    public ConvertCurrencyTool(TravelApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 转换货币
     *
     * @param from   源货币代码（如USD、EUR、CNY）
     * @param to     目标货币代码
     * @param amount 金额
     * @return 转换结果的JSON字符串
     */
    @Tool(description = "查询货币汇率并进行金额转换。参数：from-源货币代码(必填如USD)，to-目标货币代码(必填如CNY)，amount-金额(必填)")
    public String convertCurrency(
            @ToolParam(description = "源货币代码，例如USD、EUR、CNY") String from,
            @ToolParam(description = "目标货币代码，例如USD、EUR、CNY") String to,
            @ToolParam(description = "要转换的金额") Double amount) {
        try {
            if (from == null || from.isBlank()) {
                return "{\"success\":false,\"error\":\"源货币代码不能为空\"}";
            }
            if (to == null || to.isBlank()) {
                return "{\"success\":false,\"error\":\"目标货币代码不能为空\"}";
            }
            if (amount == null || amount < 0) {
                return "{\"success\":false,\"error\":\"金额必须为非负数\"}";
            }

            var rate = apiClient.getExchangeRate(from.toUpperCase(), to.toUpperCase(), amount);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"from\":\"").append(rate.base()).append("\"");
            sb.append(",\"to\":\"").append(rate.target()).append("\"");
            sb.append(",\"amount\":").append(rate.amount());
            sb.append(",\"rate\":").append(rate.rate());
            sb.append(",\"converted\":").append(rate.converted());
            sb.append(",\"date\":\"").append(rate.date()).append("\"");
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