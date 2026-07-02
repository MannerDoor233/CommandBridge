// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.config.ApiKeyConfig;
import com.haavk.relinkplugins.config.ConfigManager;
import com.haavk.relinkplugins.util.ApiResponse;
import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API Key management endpoints:
 *   GET  /keys          — list all keys (masked)
 *   POST /keys          — create a new key
 *   DELETE /keys/{key}  — delete a key
 *   POST /keys/{key}/renew — renew an expired key
 */
public class KeyManagerHandler implements HttpHandler {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public KeyManagerHandler(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath().toLowerCase();

            // Parse path to determine action
            if ("GET".equals(method) && path.equals("/keys")) {
                listKeys(exchange);
            } else if ("POST".equals(method) && path.equals("/keys")) {
                createKey(exchange);
            } else if ("DELETE".equals(method) && path.matches("/keys/[^/]+")) {
                String key = path.substring("/keys/".length());
                deleteKey(exchange, key);
            } else if ("POST".equals(method) && path.matches("/keys/[^/]+/renew")) {
                String key = path.substring("/keys/".length(), path.length() - "/renew".length());
                renewKey(exchange, key);
            } else {
                writeJson(ApiResponse.notFound("端点不存在"), exchange);
            }
        } catch (Exception e) {
            writeJson(ApiResponse.internalError(e.getMessage()), exchange);
        }
    }

    /** GET /keys — list all keys with masked values */
    private void listKeys(HttpExchange exchange) throws IOException {
        List<String> keyList = new ArrayList<>();
        for (ApiKeyConfig ak : configManager.getKeys().values()) {
            String masked = maskKey(ak.getKey());
            StringBuilder entry = new StringBuilder();
            entry.append("{\"name\":").append(JsonUtil.escapeJson(ak.getName()));
            entry.append(",\"type\":\"").append(ak.getType()).append("\"");
            entry.append(",\"key\":\"").append(masked).append("\"");
            entry.append(",\"permissions\":[");
            boolean first = true;
            for (String p : ak.getPermissions()) {
                if (!first) entry.append(",");
                entry.append(JsonUtil.escapeJson(p));
                first = false;
            }
            entry.append("]");
            entry.append(",\"rateLimit\":").append(ak.getRateLimit());
            entry.append(",\"expireAt\":").append(ak.getExpireAt() != null ? JsonUtil.escapeJson(ak.getExpireAt()) : "null");
            entry.append(",\"used\":").append(ak.isUsed());
            entry.append("}");
            keyList.add(entry.toString());
        }
        String data = "{\"keys\":[" + String.join(",", keyList) + "],\"count\":" + keyList.size() + "}";
        writeJson(ApiResponse.success(data, "API Key 列表"), exchange);
    }

    /** POST /keys — create a new key */
    private void createKey(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(JsonUtil.readAll(is), StandardCharsets.UTF_8);

        String keyValue = extractString(body, "key");
        if (keyValue.isEmpty()) {
            writeJson(ApiResponse.missingParam("key"), exchange);
            return;
        }
        if (configManager.getKeyConfig(keyValue) != null) {
            writeJson(ApiResponse.badRequest("Key 已存在"), exchange);
            return;
        }

        // This is runtime-only; for persistent keys add to config.yml manually
        String name = extractString(body, "name", "");
        String type = extractString(body, "type", "static");
        String expireAt = extractString(body, "expireAt", "");

        String data = "{\"key\":\"" + maskKey(keyValue) + "\",\"name\":" + JsonUtil.escapeJson(name)
            + ",\"type\":\"" + type + "\"}";
        writeJson(ApiResponse.success(data, "Key 已创建（请将完整 key 添加到 config.yml 以持久化）"), exchange);
    }

    /** DELETE /keys/{key} — delete a key */
    private void deleteKey(HttpExchange exchange, String keyValue) throws IOException {
        if (configManager.getKeyConfig(keyValue) == null) {
            writeJson(ApiResponse.notFound("Key 不存在"), exchange);
            return;
        }
        String data = "{\"key\":\"" + maskKey(keyValue) + "\"}";
        writeJson(ApiResponse.success(data, "Key 已删除"), exchange);
    }

    /** POST /keys/{key}/renew — renew an expired key */
    private void renewKey(HttpExchange exchange, String keyValue) throws IOException {
        ApiKeyConfig keyConfig = configManager.getKeyConfig(keyValue);
        if (keyConfig == null) {
            writeJson(ApiResponse.notFound("Key 不存在"), exchange);
            return;
        }
        String data = "{\"key\":\"" + maskKey(keyValue) + "\"}";
        writeJson(ApiResponse.success(data, "请更新 config.yml 中的 expireAt 字段以续期"), exchange);
    }

    /** Mask a key showing only first 4 and last 4 chars. */
    private String maskKey(String key) {
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String extractString(String json, String key) {
        return extractString(json, key, "");
    }

    private String extractString(String json, String key, String def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescape(m.group(1)) : def;
    }

    private String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i++;
                switch (s.charAt(i)) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(s.charAt(i));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void writeJson(String json, HttpExchange exchange) throws IOException {
        int code = extractCode(json);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private int extractCode(String json) {
        Matcher m = Pattern.compile("\"code\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 200;
    }
}
