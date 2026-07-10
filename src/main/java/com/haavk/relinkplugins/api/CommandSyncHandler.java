// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.crypto.CryptoUtil;
import com.haavk.relinkplugins.util.ApiResponse;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandSyncHandler implements HttpHandler {

    private final JavaPlugin plugin;
    private final CryptoUtil crypto;

    public CommandSyncHandler(JavaPlugin plugin, CryptoUtil crypto) {
        this.plugin = plugin;
        this.crypto = crypto;
    }

    private String decryptIfNeeded(String body) throws Exception {
        Pattern eP = Pattern.compile("\"encrypted\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher eM = eP.matcher(body);
        if (eM.find() && crypto.isReady()) {
            String enc = unescape(eM.group(1));
            String dec = crypto.rsaDecrypt(enc);
            return dec;
        }
        return body;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, ApiResponse.methodNotAllowed());
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(JsonUtil.readAll(is), StandardCharsets.UTF_8);

            // Decrypt if encrypted request
            String decryptedBody;
            try {
                decryptedBody = decryptIfNeeded(body);
            } catch (SecurityException e) {
                writeJson(exchange, ApiResponse.forbidden(e.getMessage()));
                return;
            } catch (Exception e) {
                writeJson(exchange, ApiResponse.forbidden("Decryption failed: " + e.getMessage()));
                return;
            }

            List<String> commands = extractCommands(decryptedBody);
            if (commands.isEmpty()) {
                writeJson(exchange, ApiResponse.missingParam("command"));
                return;
            }

            List<String> resultEntries = new ArrayList<>();
            boolean allSuccess = true;

            for (String cmd : commands) {
                AtomicBoolean dispatchOk = new AtomicBoolean(false);
                CountDownLatch latch = new CountDownLatch(1);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        dispatchOk.set(ok);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Command failed: " + cmd, e);
                    } finally {
                        latch.countDown();
                    }
                });

                boolean completed = latch.await(30, TimeUnit.SECONDS);
                boolean success = completed && dispatchOk.get();

                StringBuilder entry = new StringBuilder();
                entry.append("{\"command\":").append(JsonUtil.escapeJson(cmd));
                entry.append(",\"success\":").append(success);
                if (!completed) {
                    entry.append(",\"error\":\"Timeout after 30s\"");
                }
                entry.append("}");
                resultEntries.add(entry.toString());
                if (!success) allSuccess = false;
            }

            String data = "{\"executed\":[" + String.join(",", resultEntries)
                + "],\"count\":" + commands.size() + ",\"all_success\":" + allSuccess + "}";
            writeJson(exchange, ApiResponse.success(data, "命令同步执行完成"));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "CommandSyncHandler error", e);
            writeJson(exchange, ApiResponse.internalError(e.getMessage()));
        }
    }

    private List<String> extractCommands(String body) {
        List<String> commands = new ArrayList<>();
        Pattern singlePattern = Pattern.compile("\"command\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher singleMatcher = singlePattern.matcher(body);
        if (singleMatcher.find()) {
            commands.add(unescape(singleMatcher.group(1)));
        }
        Pattern arrayPattern = Pattern.compile("\"commands\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher arrayMatcher = arrayPattern.matcher(body);
        if (arrayMatcher.find()) {
            String arrayContent = arrayMatcher.group(1).trim();
            if (!arrayContent.isEmpty()) {
                Pattern itemPattern = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
                Matcher itemMatcher = itemPattern.matcher(arrayContent);
                while (itemMatcher.find()) {
                    String cmd = unescape(itemMatcher.group(1));
                    if (!cmd.isEmpty()) commands.add(cmd);
                }
            }
        }
        return commands;
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

    private void writeJson(HttpExchange exchange, String json) throws IOException {
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
