// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.util.ApiResponse;
import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.haavk.relinkplugins.Relink;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler implements HttpHandler {

    private final Relink plugin;

    // Static pattern for JSON int extraction
    private static final Pattern SINCE_PATTERN = Pattern.compile("\"since_id\"\\s*:\\s*(\\d+)");

    public ChatHandler(Relink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath().toLowerCase();

            if ("GET".equals(method) && path.endsWith("/latest")) {
                getLatestId(exchange);
            } else if ("GET".equals(method)) {
                getMessages(exchange);
            } else if ("POST".equals(method)) {
                sendChat(exchange);
            } else {
                writeJson(ApiResponse.methodNotAllowed(), exchange);
            }
        } catch (Exception e) {
            writeJson(ApiResponse.internalError(e.getMessage()), exchange);
        }
    }

    /** GET /chat/latest — 获取最新消息ID */
    private void getLatestId(HttpExchange exchange) throws IOException {
        String data = "{\"latest_id\":" + plugin.getLatestChatId() + "}";
        writeJson(ApiResponse.success(data, "最新消息ID"), exchange);
    }

    /** GET /chat?since_id=N — 获取增量消息 */
    private void getMessages(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int sinceId = -1;
        if (query != null) {
            Matcher m = SINCE_PATTERN.matcher(query);
            if (m.find()) {
                try { sinceId = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            }
        }

        List<Relink.ChatMessage> messages = plugin.getChatMessagesSince(sinceId);
        StringBuilder data = new StringBuilder("{\"since_id\":");
        data.append(sinceId).append(",\"latest_id\":").append(plugin.getLatestChatId());
        data.append(",\"count\":").append(messages.size()).append(",\"messages\":[");
        boolean first = true;
        for (Relink.ChatMessage cm : messages) {
            if (!first) data.append(",");
            data.append("{\"id\":").append(cm.id);
            data.append(",\"timestamp\":").append(cm.timestamp);
            data.append(",\"player\":").append(JsonUtil.escapeJson(cm.player));
            data.append(",\"message\":").append(JsonUtil.escapeJson(cm.message));
            data.append("}");
            first = false;
        }
        data.append("]}");
        writeJson(ApiResponse.success(data.toString(), "聊天消息"), exchange);
    }

    /** POST /chat — 以控制台身份发送聊天消息 */
    private void sendChat(HttpExchange exchange) throws IOException {
        String body = new String(JsonUtil.readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
        String message = extractString(body, "message");
        if (message.isEmpty()) {
            writeJson(ApiResponse.missingParam("message"), exchange);
            return;
        }
        Bukkit.broadcastMessage("[API] " + message);
        String data = "{\"message\":" + JsonUtil.escapeJson(message) + "}";
        writeJson(ApiResponse.success(data, "消息已发送"), exchange);
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescape(m.group(1)) : "";
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
