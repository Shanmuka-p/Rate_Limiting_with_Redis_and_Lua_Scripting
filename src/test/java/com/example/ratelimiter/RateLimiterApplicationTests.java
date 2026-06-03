package com.example.ratelimiter;

import com.example.ratelimiter.dto.RateLimitConfigDto;
import com.example.ratelimiter.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RateLimiterApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        // Clear all Redis keys used in tests to ensure isolation
        redisTemplate.delete("rate-limit-configs");
        redisTemplate.delete(redisTemplate.keys("rate-limit:log:*"));
    }

    @Test
    public void testDefaultRateLimitSuccessAndFailure() throws Exception {
        String apiKey = "default-test-key";

        // Default limit is 10 requests per minute
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/data")
                            .header("X-API-Key", apiKey))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "10"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(9 - i)))
                    .andExpect(header().exists("X-RateLimit-Reset"))
                    .andExpect(jsonPath("$.status").value("success"));
        }

        // 11th request should be rate limited (429)
        mockMvc.perform(post("/api/data")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    public void testCustomRateLimitConfiguration() throws Exception {
        String apiKey = "custom-test-key";
        int customLimit = 3;

        RateLimitConfigDto config = new RateLimitConfigDto(apiKey, customLimit);

        // Configure custom limit
        mockMvc.perform(post("/api/rate-limit/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.apiKey").value(apiKey))
                .andExpect(jsonPath("$.requestsPerMinute").value(customLimit));

        // First 3 requests should pass
        for (int i = 0; i < customLimit; i++) {
            mockMvc.perform(post("/api/data")
                            .header("X-API-Key", apiKey))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", String.valueOf(customLimit)))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(customLimit - 1 - i)));
        }

        // 4th request should fail
        mockMvc.perform(post("/api/data")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    public void testRateLimitStatusEndpoint() throws Exception {
        String apiKey = "status-test-key";

        // Make 2 requests
        mockMvc.perform(post("/api/data").header("X-API-Key", apiKey)).andExpect(status().isOk());
        mockMvc.perform(post("/api/data").header("X-API-Key", apiKey)).andExpect(status().isOk());

        // Get status
        mockMvc.perform(get("/api/rate-limit/status/" + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.remaining").value(8))
                .andExpect(jsonPath("$.reset").exists());
    }

    @Test
    public void testRateLimitResetEndpoint() throws Exception {
        String apiKey = "reset-test-key";

        // Exceed the limit (make 10 requests, plus 1 to fail)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/data").header("X-API-Key", apiKey)).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/data").header("X-API-Key", apiKey)).andExpect(status().isTooManyRequests());

        // Reset the rate limit
        mockMvc.perform(delete("/api/rate-limit/reset/" + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Request should pass now and start counting down from default limit again
        mockMvc.perform(post("/api/data")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }
}
