// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.config.ApiKeyConfig;
import com.haavk.relinkplugins.config.ConfigManager;
import com.haavk.relinkplugins.util.ApiResponse;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthFilter extends Filter {

    private final ConfigManager configManager;

    // ── IPs permanently exempt from ban ──────────────────────
    private static final java.util.Set<String> WHITELISTED_IPS = java.util.Set.of(
        "119.28.236.194",   // MCC-1 (whitelist backend)
        "127.0.0.1",        // localhost
        "0:0:0:0:0:0:0:1"  // IPv6 localhost
    );

    // ── IP ban on repeated wrong keys ────────────────────────────
    private static final int MAX_FAILS = 5;               // 5 wrong keys → ban
    private static final long BAN_DURATION_MS = 600_000L; // 10 minutes

    private final Map<String, Integer> failCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipBans = new ConcurrentHashMap<>();

    public AuthFilter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        String path = exchange.getRequestURI().getPath();

        // ── Bypass all IP bans for whitelisted IPs ────────────
        if (WHITELISTED_IPS.contains(clientIp)) {
            chain.doFilter(exchange);
            return;
        }

        // ── Check IP ban ────────────────────────────────────────
        Long bannedUntil = ipBans.get(clientIp);
        if (bannedUntil != null) {
            long now = System.currentTimeMillis();
            if (now < bannedUntil) {
                long remainSec = (bannedUntil - now) / 1000;
                writeResponse(exchange, ApiResponse.forbidden(
                    "IP 已被封禁，剩余 " + remainSec + " 秒"));
                return;
            } else {
                // Ban expired, clean up
                ipBans.remove(clientIp);
                failCounts.remove(clientIp);
            }
        }

        // ── Auth check ──────────────────────────────────────────
        if (requestKey == null || requestKey.isEmpty()) {
            recordFail(clientIp);
            writeResponse(exchange, ApiResponse.unauthorized("缺少 X-API-Key 请求头"));
            return;
        }

        ApiKeyConfig keyConfig = configManager.getKeyConfig(requestKey);
        if (keyConfig == null) {
            recordFail(clientIp);
            writeResponse(exchange, ApiResponse.unauthorized("X-API-Key 无效或已过期"));
            return;
        }

        // Check expiry (timed keys)
        if (configManager.isExpired(keyConfig)) {
            writeResponse(exchange, ApiResponse.unauthorized("X-API-Key 已过期"));
            return;
        }

        // Check one-time key usage
        if ("onetime".equals(keyConfig.getType()) && keyConfig.isUsed()) {
            writeResponse(exchange, ApiResponse.unauthorized("一次性 Key 已被使用"));
            return;
        }

        // Check IP whitelist
        if (!keyConfig.getIpWhitelist().isEmpty()) {
            if (!configManager.ipMatchesWhitelist(clientIp, keyConfig.getIpWhitelist())) {
                writeResponse(exchange, ApiResponse.forbidden("当前 IP 无权使用此 API Key"));
                return;
            }
        }

        // Check permission
        if (!configManager.hasPermission(keyConfig, path)) {
            writeResponse(exchange, ApiResponse.forbidden("当前 API Key 无权访问 " + path));
            return;
        }

        // Rate limiting (disabled — always passes)
        if (!configManager.getRateLimiter().allow(requestKey, keyConfig.getRateLimit())) {
            writeResponse(exchange, ApiResponse.rateLimited("请求过于频繁，请稍后重试"));
            return;
        }

        // Mark one-time key as used
        if ("onetime".equals(keyConfig.getType()) && !keyConfig.isUsed()) {
            keyConfig.setUsed(true);
        }

        // Auth passed — reset fail count for this IP
        failCounts.remove(clientIp);

        chain.doFilter(exchange);
    }

    /**
     * Record a failed auth attempt. If MAX_FAILS exceeded, ban the IP.
     */
    private void recordFail(String ip) {
        int count = failCounts.merge(ip, 1, Integer::sum);
        if (count >= MAX_FAILS) {
            ipBans.put(ip, System.currentTimeMillis() + BAN_DURATION_MS);
            failCounts.remove(ip); // reset counter for next ban window
        }
    }

    private void writeResponse(HttpExchange exchange, String json) throws IOException {
        int code = extractCode(json);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private int extractCode(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"code\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 200;
    }

    @Override
    public String description() {
        return "Multi-key authentication + IP ban on repeated wrong keys";
    }
}
