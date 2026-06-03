package com.example.ratelimiter.controller;

import com.example.ratelimiter.dto.RateLimitConfigDto;
import com.example.ratelimiter.dto.RateLimitStatusDto;
import com.example.ratelimiter.service.RateLimiterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    public RateLimiterController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/api/data")
    public ResponseEntity<?> processData(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", "Missing or empty X-API-Key header"));
        }

        RateLimiterService.RateLimitResult result = rateLimiterService.checkRateLimit(apiKey);
        RateLimitStatusDto status = result.getStatus();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(status.getLimit()));
        headers.add("X-RateLimit-Remaining", String.valueOf(status.getRemaining()));
        headers.add("X-RateLimit-Reset", String.valueOf(status.getReset()));

        if (!result.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(Collections.singletonMap("error", "Too Many Requests"));
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Request processed successfully.");

        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    @PostMapping("/api/rate-limit/configure")
    public ResponseEntity<?> configureLimit(@RequestBody RateLimitConfigDto config) {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "apiKey cannot be null or empty"));
        }
        if (config.getRequestsPerMinute() <= 0) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "requestsPerMinute must be greater than 0"));
        }

        rateLimiterService.configureLimit(config.getApiKey(), config.getRequestsPerMinute());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Rate limit configured successfully");
        response.put("apiKey", config.getApiKey());
        response.put("requestsPerMinute", config.getRequestsPerMinute());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/rate-limit/status/{apiKey}")
    public ResponseEntity<?> getStatus(@PathVariable("apiKey") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "apiKey cannot be empty"));
        }

        RateLimitStatusDto status = rateLimiterService.getStatus(apiKey);
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/api/rate-limit/reset/{apiKey}")
    public ResponseEntity<?> resetLimit(@PathVariable("apiKey") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "apiKey cannot be empty"));
        }

        rateLimiterService.resetLimit(apiKey);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Rate limit reset successfully for apiKey: " + apiKey);

        return ResponseEntity.ok(response);
    }
}
