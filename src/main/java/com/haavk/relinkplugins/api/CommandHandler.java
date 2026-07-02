// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandHandler implements HttpHandler {

    private final JavaPlugin plugin;

    public CommandHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Only accept POST
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
                return;
            }

            // Read request body
            InputStream is = exchange.getRequestBody();
            String body = new String(JsonUtil.readAll(is), StandardCharsets.UTF_8);

            // Collect commands from body
            List<String> commands = extractCommands(body);

            if (commands.isEmpty()) {
                writeResponse(exchange, 400, "{\"success\":false,\"error\":\"Missing 'command' or 'commands' field\"}");
                return;
            }

            // Execute all commands sequentially
            List<String> results = new ArrayList<>();
            for (String cmd : commands) {
                String finalCmd = cmd;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                    });
                    results.add("{\"command\":" + JsonUtil.escapeJson(cmd) + ",\"status\":\"queued\"}");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to execute command: " + cmd, e);
                    results.add("{\"command\":" + JsonUtil.escapeJson(cmd) + ",\"status\":\"error\",\"error\":" + JsonUtil.escapeJson(e.getMessage()) + "}");
                }
            }

            String response = "{\"success\":true,\"executed\":[" + String.join(",", results) + "],\"count\":" + commands.size() + "}";
            writeResponse(exchange, 200, response);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling command request", e);
            writeResponse(exchange, 500, "{\"success\":false,\"error\":\"Internal server error\"}");
        }
    }

    /**
     * Extract commands from JSON body using regex (avoids JsonUtil's inability to parse arrays).
     * Supports:
     * - {"command":"say hi"} (single)
     * - {"commands":["say hi","say ho"]} (array)
     * - {"command":"say hi","commands":["say ho"]} (both merged)
     */
    private List<String> extractCommands(String body) {
        List<String> commands = new ArrayList<>();

        // Extract single "command" value via regex
        Pattern singlePattern = Pattern.compile("\"command\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher singleMatcher = singlePattern.matcher(body);
        if (singleMatcher.find()) {
            String cmd = unescape(singleMatcher.group(1));
            if (!cmd.isEmpty()) {
                commands.add(cmd);
            }
        }

        // Extract "commands" array values via regex
        Pattern arrayPattern = Pattern.compile("\"commands\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher arrayMatcher = arrayPattern.matcher(body);
        if (arrayMatcher.find()) {
            String arrayContent = arrayMatcher.group(1).trim();
            if (!arrayContent.isEmpty()) {
                // Split by comma at top level (doesn't handle nested arrays, but good enough for string arrays)
                // Find quoted strings separated by commas
                Pattern itemPattern = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
                Matcher itemMatcher = itemPattern.matcher(arrayContent);
                while (itemMatcher.find()) {
                    String cmd = unescape(itemMatcher.group(1));
                    if (!cmd.isEmpty()) {
                        commands.add(cmd);
                    }
                }
            }
        }

        return commands;
    }

    /** Unescape JSON string escapes. */
    private String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i++;
                switch (s.charAt(i)) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
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

    private void writeResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
