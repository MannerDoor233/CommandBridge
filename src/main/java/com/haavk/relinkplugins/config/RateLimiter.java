// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter per API key.
 * Tracks request count in the current minute window.
 */
public class RateLimiter {

    private final Map<String, RateEntry> counters = new ConcurrentHashMap<>();

    /**
     * Check if a request is allowed for the given key.
     * @param key the API key
     * @param maxPerMinute maximum allowed requests per minute
     * @return true if allowed, false if rate limited
     */
    public boolean allow(String key, int maxPerMinute) {
        long now = System.currentTimeMillis() / 60000; // minute window
        RateEntry entry = counters.computeIfAbsent(key, k -> new RateEntry());
        synchronized (entry) {
            if (entry.window != now) {
                entry.window = now;
                entry.count.set(0);
            }
            return entry.count.incrementAndGet() <= maxPerMinute;
        }
    }

    private static class RateEntry {
        long window = System.currentTimeMillis() / 60000;
        AtomicInteger count = new AtomicInteger(0);
    }
}
