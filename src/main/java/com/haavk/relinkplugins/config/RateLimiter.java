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
     * Rate limiting disabled — always returns true.
     */
    public boolean allow(String key, int maxPerMinute) {
        return true;
    }

    private static class RateEntry {
        long window = System.currentTimeMillis() / 60000;
        AtomicInteger count = new AtomicInteger(0);
    }
}
