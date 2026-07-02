/*
 * CommandBridge - HTTP API remote control for Minecraft Paper servers.
 * Copyright (C) 2026 MannerDoor233
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.haavk.commandbridge.api;

import com.haavk.commandbridge.util.JsonUtil;
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
