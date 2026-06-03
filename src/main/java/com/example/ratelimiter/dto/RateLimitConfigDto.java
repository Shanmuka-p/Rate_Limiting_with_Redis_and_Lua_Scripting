package com.example.ratelimiter.dto;

public class RateLimitConfigDto {
    private String apiKey;
    private int requestsPerMinute;

    public RateLimitConfigDto() {
    }

    public RateLimitConfigDto(String apiKey, int requestsPerMinute) {
        this.apiKey = apiKey;
        this.requestsPerMinute = requestsPerMinute;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }
}
