// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RestartHandler implements HttpHandler {

    private final JavaPlugin plugin;

    public RestartHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String response;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Restart triggered via HTTP API");
                Bukkit.spigot().restart();
            });
            response = "{\"success\":true,\"message\":\"Server restarting...\"}";
        } catch (Exception e) {
            response = "{\"success\":false,\"error\":\"" + JsonUtil.escapeJson(e.getMessage()) + "\"}";
        }
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
