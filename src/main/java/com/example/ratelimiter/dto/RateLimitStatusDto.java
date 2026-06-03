package com.example.ratelimiter.dto;

public class RateLimitStatusDto {
    private int limit;
    private int remaining;
    private long reset;

    public RateLimitStatusDto() {
    }

    public RateLimitStatusDto(int limit, int remaining, long reset) {
        this.limit = limit;
        this.remaining = remaining;
        this.reset = reset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public long getReset() {
        return reset;
    }

    public void setReset(long reset) {
        this.reset = reset;
    }
}
