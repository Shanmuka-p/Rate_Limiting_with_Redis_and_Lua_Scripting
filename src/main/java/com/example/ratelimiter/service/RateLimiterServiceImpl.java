package com.example.ratelimiter.service;

import com.example.ratelimiter.dto.RateLimitStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterServiceImpl.class);
    
    private static final String CONFIG_HASH_KEY = "rate-limit-configs";
    private static final String LOG_KEY_PREFIX = "rate-limit:log:";
    private static final int DEFAULT_LIMIT = 10;
    private static final long WINDOW_SIZE_MS = 60000; // 1 minute

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    public RateLimiterServiceImpl(RedisTemplate<String, String> redisTemplate, RedisScript<Long> rateLimitScript) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
    }

    @Override
    public RateLimitResult checkRateLimit(String apiKey) {
        long currentTime = System.currentTimeMillis();
        int limit = getLimitForApiKey(apiKey);
        String logKey = LOG_KEY_PREFIX + apiKey;

        // Execute Lua script: ARGV[1]=key, ARGV[2]=currentTime, ARGV[3]=windowSize, ARGV[4]=limit
        Long scriptResult = redisTemplate.execute(
                rateLimitScript,
                Collections.emptyList(),
                logKey,
                String.valueOf(currentTime),
                String.valueOf(WINDOW_SIZE_MS),
                String.valueOf(limit)
        );

        boolean allowed = scriptResult != null && scriptResult == 1;

        // Calculate current status (limit, remaining, reset time)
        RateLimitStatusDto status = calculateStatus(apiKey, logKey, limit, currentTime);

        return new RateLimitResult(allowed, status);
    }

    @Override
    public RateLimitStatusDto getStatus(String apiKey) {
        long currentTime = System.currentTimeMillis();
        int limit = getLimitForApiKey(apiKey);
        String logKey = LOG_KEY_PREFIX + apiKey;
        
        return calculateStatus(apiKey, logKey, limit, currentTime);
    }

    @Override
    public void configureLimit(String apiKey, int requestsPerMinute) {
        redisTemplate.opsForHash().put(CONFIG_HASH_KEY, apiKey, String.valueOf(requestsPerMinute));
        log.info("Configured custom rate limit for apiKey: {}: {} requests/min", apiKey, requestsPerMinute);
    }

    @Override
    public void resetLimit(String apiKey) {
        String logKey = LOG_KEY_PREFIX + apiKey;
        redisTemplate.delete(logKey);
        redisTemplate.opsForHash().delete(CONFIG_HASH_KEY, apiKey);
        log.info("Reset rate limit logs and configurations for apiKey: {}", apiKey);
    }

    private int getLimitForApiKey(String apiKey) {
        try {
            String limitStr = (String) redisTemplate.opsForHash().get(CONFIG_HASH_KEY, apiKey);
            if (limitStr != null) {
                return Integer.parseInt(limitStr);
            }
        } catch (Exception e) {
            log.error("Failed to fetch limit from Redis for apiKey: {}. Using default.", apiKey, e);
        }
        return DEFAULT_LIMIT;
    }

    private RateLimitStatusDto calculateStatus(String apiKey, String logKey, int limit, long currentTime) {
        Long currentCount = 0L;
        long resetTime = currentTime + WINDOW_SIZE_MS;

        try {
            currentCount = redisTemplate.opsForZSet().zCard(logKey);
            if (currentCount == null) {
                currentCount = 0L;
            }

            Set<TypedTuple<String>> range = redisTemplate.opsForZSet().rangeWithScores(logKey, 0, 0);
            if (range != null && !range.isEmpty()) {
                Double score = range.iterator().next().getScore();
                if (score != null) {
                    resetTime = score.longValue() + WINDOW_SIZE_MS;
                }
            }
        } catch (Exception e) {
            log.error("Error calculating rate limit status for apiKey: {}", apiKey, e);
        }

        int remaining = Math.max(0, limit - currentCount.intValue());
        return new RateLimitStatusDto(limit, remaining, resetTime);
    }
}
