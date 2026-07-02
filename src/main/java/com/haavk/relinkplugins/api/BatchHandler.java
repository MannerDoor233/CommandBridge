// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.config.ConfigManager;
import com.haavk.relinkplugins.util.ApiResponse;
import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Batch operations handler.
 * Routes based on path prefix.
 */
public class BatchHandler implements HttpHandler {

    private final JavaPlugin plugin;
    @SuppressWarnings("unused")
    private final ConfigManager configManager;

    public BatchHandler(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(ApiResponse.methodNotAllowed(), exchange);
                return;
            }

            String path = exchange.getRequestURI().getPath().toLowerCase();
            InputStream is = exchange.getRequestBody();
            String body = new String(JsonUtil.readAll(is), StandardCharsets.UTF_8);

            switch (path) {
                case "/batch/command":
                    handleBatchCommand(exchange, body);
                    break;
                case "/batch/kick":
                    handleBatchKick(exchange, body);
                    break;
                case "/batch/give":
                    handleBatchGive(exchange, body);
                    break;
                default:
                    writeJson(ApiResponse.notFound("批量端点不存在"), exchange);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "BatchHandler error", e);
            writeJson(ApiResponse.internalError(e.getMessage()), exchange);
        }
    }

    /** POST /batch/command — execute multiple commands */
    private void handleBatchCommand(HttpExchange exchange, String body) throws IOException {
        boolean stopOnError = extractBoolean(body, "stopOnError", false);
        List<String> commands = extractStringArray(body, "commands");
        if (commands.isEmpty()) {
            writeJson(ApiResponse.missingParam("commands"), exchange);
            return;
        }

        List<String> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (String cmd : commands) {
            AtomicBoolean dispatchOk = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    dispatchOk.set(ok);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Batch command failed: " + cmd, e);
                } finally {
                    latch.countDown();
                }
            });

            boolean completed;
            try {
                completed = latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                completed = false;
            }
            boolean success = completed && dispatchOk.get();

            StringBuilder entry = new StringBuilder();
            entry.append("{\"command\":").append(JsonUtil.escapeJson(cmd));
            entry.append(",\"status\":").append(success ? "\"success\"" : "\"failed\"");
            if (!success) {
                entry.append(",\"error\":\"").append(completed ? "命令执行返回false" : "执行超时(30s)").append("\"");
            }
            entry.append("}");
            results.add(entry.toString());

            if (success) successCount++;
            else failedCount++;

            if (!success && stopOnError) break;
        }

        String data = "{\"total\":" + (successCount + failedCount)
            + ",\"success\":" + successCount
            + ",\"failed\":" + failedCount
            + ",\"results\":[" + String.join(",", results) + "]}";
        writeJson(ApiResponse.success(data, "批量命令执行完成"), exchange);
    }

    /** POST /batch/kick — kick multiple players */
    private void handleBatchKick(HttpExchange exchange, String body) throws IOException {
        String reason = extractString(body, "reason", "服务器维护");
        boolean stopOnError = extractBoolean(body, "stopOnError", false);
        List<String> players = extractStringArray(body, "players");
        if (players.isEmpty()) {
            writeJson(ApiResponse.missingParam("players"), exchange);
            return;
        }

        List<String> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (String playerName : players) {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                player.kickPlayer(reason);
                results.add("{\"player\":" + JsonUtil.escapeJson(playerName) + ",\"status\":\"success\"}");
                successCount++;
            } else {
                results.add("{\"player\":" + JsonUtil.escapeJson(playerName) + ",\"status\":\"failed\",\"error\":\"玩家不在线\"}");
                failedCount++;
                if (stopOnError) break;
            }
        }

        String data = "{\"total\":" + (successCount + failedCount)
            + ",\"success\":" + successCount
            + ",\"failed\":" + failedCount
            + ",\"results\":[" + String.join(",", results) + "]}";
        writeJson(ApiResponse.success(data, "批量踢出完成"), exchange);
    }

    /** POST /batch/give — give items to multiple players */
    private void handleBatchGive(HttpExchange exchange, String body) throws IOException {
        boolean stopOnError = extractBoolean(body, "stopOnError", false);
        List<String> itemsRaw = extractStringArray(body, "items");
        if (itemsRaw.isEmpty()) {
            writeJson(ApiResponse.missingParam("items"), exchange);
            return;
        }

        List<String> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        // items is an array of {"player":"...","material":"...","amount":N}
        // We parse each item entry from the raw array strings
        for (String itemEntry : itemsRaw) {
            String playerName = extractString(itemEntry, "player");
            String material = extractString(itemEntry, "material", "diamond");
            int amount = (int) extractLong(itemEntry, "amount", 1);

            if (playerName.isEmpty()) {
                results.add("{\"status\":\"failed\",\"error\":\"缺少 player 字段\"}");
                failedCount++;
                if (stopOnError) break;
                continue;
            }

            Player player = Bukkit.getPlayerExact(playerName);
            if (player == null) {
                results.add("{\"player\":\"" + JsonUtil.escapeJson(playerName) + "\",\"material\":\"" + material + "\",\"status\":\"failed\",\"error\":\"玩家不在线\"}");
                failedCount++;
                if (stopOnError) break;
                continue;
            }

            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + playerName + " " + material + " " + amount);
                results.add("{\"player\":\"" + JsonUtil.escapeJson(playerName) + "\",\"material\":\"" + material + "\",\"amount\":" + amount + ",\"status\":\"success\"}");
                successCount++;
            } catch (Exception e) {
                results.add("{\"player\":\"" + JsonUtil.escapeJson(playerName) + "\",\"status\":\"failed\",\"error\":" + JsonUtil.escapeJson(e.getMessage()) + "}");
                failedCount++;
                if (stopOnError) break;
            }
        }

        String data = "{\"total\":" + (successCount + failedCount)
            + ",\"success\":" + successCount
            + ",\"failed\":" + failedCount
            + ",\"results\":[" + String.join(",", results) + "]}";
        writeJson(ApiResponse.success(data, "批量给予完成"), exchange);
    }

    // ============ Utility methods ============

    private List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String content = m.group(1).trim();
            if (!content.isEmpty()) {
                // Extract individual quoted strings or JSON objects
                Pattern itemPattern = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"|(\\{[^}]+\\})");
                Matcher im = itemPattern.matcher(content);
                while (im.find()) {
                    if (im.group(1) != null) {
                        result.add(unescape(im.group(1)));
                    } else if (im.group(2) != null) {
                        result.add(im.group(2));
                    }
                }
            }
        }
        return result;
    }

    private String extractString(String json, String key) {
        return extractString(json, key, "");
    }

    private String extractString(String json, String key, String def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescape(m.group(1)) : def;
    }

    private long extractLong(String json, String key, long def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : def;
    }

    private boolean extractBoolean(String json, String key, boolean def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : def;
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
