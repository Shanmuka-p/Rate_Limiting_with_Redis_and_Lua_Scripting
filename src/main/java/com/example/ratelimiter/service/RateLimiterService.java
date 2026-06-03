package com.example.ratelimiter.service;

import com.example.ratelimiter.dto.RateLimitStatusDto;

public interface RateLimiterService {
    
    class RateLimitResult {
        private final boolean allowed;
        private final RateLimitStatusDto status;

        public RateLimitResult(boolean allowed, RateLimitStatusDto status) {
            this.allowed = allowed;
            this.status = status;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public RateLimitStatusDto getStatus() {
            return status;
        }
    }

    /**
     * Checks if the request for the given API key is allowed.
     * Updates the request log and returns the decision along with the updated status.
     */
    RateLimitResult checkRateLimit(String apiKey);

    /**
     * Retrieves the current rate limit status for the given API key.
     */
    RateLimitStatusDto getStatus(String apiKey);

    /**
     * Configures a custom rate limit for the given API key.
     */
    void configureLimit(String apiKey, int requestsPerMinute);

    /**
     * Manually resets the rate limit log and configuration for the given API key.
     */
    void resetLimit(String apiKey);
}
