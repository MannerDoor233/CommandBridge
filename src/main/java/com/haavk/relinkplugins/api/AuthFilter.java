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

public class AuthFilter extends Filter {

    private final ConfigManager configManager;

    public AuthFilter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        String path = exchange.getRequestURI().getPath();

        if (requestKey == null || requestKey.isEmpty()) {
            writeResponse(exchange, ApiResponse.unauthorized("缺少 X-API-Key 请求头"));
            return;
        }

        ApiKeyConfig keyConfig = configManager.getKeyConfig(requestKey);
        if (keyConfig == null) {
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
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
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

        // Rate limiting
        if (!configManager.getRateLimiter().allow(requestKey, keyConfig.getRateLimit())) {
            writeResponse(exchange, ApiResponse.rateLimited("请求过于频繁，请稍后重试"));
            return;
        }

        // Mark one-time key as used
        if ("onetime".equals(keyConfig.getType()) && !keyConfig.isUsed()) {
            keyConfig.setUsed(true);
        }

        chain.doFilter(exchange);
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
        return "Multi-key authentication, permission, IP whitelist, and rate limiting";
    }
}
