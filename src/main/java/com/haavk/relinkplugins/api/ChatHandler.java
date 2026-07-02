// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.Relink;
import com.haavk.relinkplugins.Relink.ChatMessage;
import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/**
 * GET /chat?since=<id> — 获取 sinceId 之后的新聊天消息
 * GET /chat/latest — 获取最新消息ID（用于下次轮询起点）
 */
public class ChatHandler implements HttpHandler {

    private final Relink plugin;

    public ChatHandler(Relink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equals(method) && path.equals("/chat/latest")) {
                handleLatest(exchange);
            } else if ("GET".equals(method) && path.equals("/chat")) {
                handleChatPoll(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            String err = "{\"success\":false,\"error\":\"" + JsonUtil.escapeJson(e.getMessage()) + "\"}";
            byte[] bytes = err.getBytes("UTF-8");
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }
    }

    /** GET /chat?since=<id> */
    private void handleChatPoll(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        int sinceId = -1;
        if (query != null && query.startsWith("since=")) {
            try {
                sinceId = Integer.parseInt(query.substring(6));
            } catch (NumberFormatException ignored) {}
        }

        List<ChatMessage> msgs = plugin.getChatMessagesSince(sinceId);
        int latestId = plugin.getLatestChatId();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"latest_id\":").append(latestId);
        sb.append(",\"count\":").append(msgs.size());
        sb.append(",\"messages\":[");
        boolean first = true;
        for (ChatMessage cm : msgs) {
            if (!first) sb.append(",");
            sb.append("{\"id\":").append(cm.id);
            sb.append(",\"time\":").append(cm.timestamp);
            sb.append(",\"player\":\"").append(JsonUtil.escapeJson(cm.player)).append("\"");
            sb.append(",\"message\":\"").append(JsonUtil.escapeJson(cm.message)).append("\"}");
            first = false;
        }
        sb.append("]}");

        byte[] bytes = sb.toString().getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    /** GET /chat/latest */
    private void handleLatest(HttpExchange exchange) throws IOException {
        int id = plugin.getLatestChatId();
        String json = "{\"success\":true,\"latest_id\":" + id + "}";
        byte[] bytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }
}
