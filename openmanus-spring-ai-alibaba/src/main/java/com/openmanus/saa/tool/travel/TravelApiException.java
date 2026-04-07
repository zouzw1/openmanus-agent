package com.openmanus.saa.tool.travel;

/**
 * 旅游API调用异常
 */
public class TravelApiException extends RuntimeException {

    private final String apiName;
    private final int statusCode;

    public TravelApiException(String apiName, String message) {
        super(String.format("[%s] %s", apiName, message));
        this.apiName = apiName;
        this.statusCode = -1;
    }

    public TravelApiException(String apiName, int statusCode, String message) {
        super(String.format("[%s] HTTP %d: %s", apiName, statusCode, message));
        this.apiName = apiName;
        this.statusCode = statusCode;
    }

    public TravelApiException(String apiName, String message, Throwable cause) {
        super(String.format("[%s] %s", apiName, message), cause);
        this.apiName = apiName;
        this.statusCode = -1;
    }

    public String getApiName() {
        return apiName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}