// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.util.ApiResponse;
import com.haavk.relinkplugins.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatusHandler implements HttpHandler {

    private final JavaPlugin plugin;

    public StatusHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(ApiResponse.methodNotAllowed(), exchange);
                return;
            }

            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            Runtime rt = Runtime.getRuntime();

            double[] tps = {-1, -1, -1};
            try {
                Method m = Bukkit.class.getMethod("getTPS");
                tps = (double[]) m.invoke(null);
            } catch (Exception ignored) {}

            String data = "{\"server\":\"" + plugin.getServer().getName() + "\""
                + ",\"version\":\"" + plugin.getServer().getVersion() + "\""
                + ",\"bukkit_version\":\"" + plugin.getServer().getBukkitVersion() + "\""
                + ",\"online_players\":" + Bukkit.getOnlinePlayers().size()
                + ",\"max_players\":" + Bukkit.getMaxPlayers()
                + ",\"tps_1m\":" + String.format("%.2f", tps[0])
                + ",\"tps_5m\":" + String.format("%.2f", tps[1])
                + ",\"tps_15m\":" + String.format("%.2f", tps[2])
                + ",\"used_memory_mb\":" + ((rt.totalMemory() - rt.freeMemory()) / 1048576)
                + ",\"free_memory_mb\":" + (rt.freeMemory() / 1048576)
                + ",\"total_memory_mb\":" + (rt.totalMemory() / 1048576)
                + ",\"max_memory_mb\":" + (rt.maxMemory() / 1048576)
                + ",\"heap_used_mb\":" + (mem.getHeapMemoryUsage().getUsed() / 1048576)
                + ",\"worlds\":" + Bukkit.getWorlds().size()
                + ",\"plugins\":" + Bukkit.getPluginManager().getPlugins().length
                + "}";

            writeJson(ApiResponse.success(data, "服务器状态"), exchange);

        } catch (Exception e) {
            writeJson(ApiResponse.internalError(e.getMessage()), exchange);
        }
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
