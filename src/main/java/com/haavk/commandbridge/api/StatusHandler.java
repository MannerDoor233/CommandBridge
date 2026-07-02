// SPDX-License-Identifier: MIT

package com.haavk.commandbridge.api;

import com.haavk.commandbridge.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StatusHandler implements HttpHandler {

    private final JavaPlugin plugin;
    private Method getTpsMethod = null;

    public StatusHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        // Reflection for getTPS() — Paper 1.16+ only, silently absent on Spigot/older Paper
        try {
            getTpsMethod = Bukkit.class.getMethod("getTPS");
        } catch (NoSuchMethodException ignored) {
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{");

            // Online players — cross-version: array (pre-1.12) or Collection (1.12+)
            json.append("\"players\":{");
            Object onlineRaw = Bukkit.class.getMethod("getOnlinePlayers").invoke(null);
            int onlineCount;
            List<String> playerNames = new ArrayList<>();
            if (onlineRaw instanceof Object[]) {
                Object[] arr = (Object[]) onlineRaw;
                onlineCount = arr.length;
                for (Object p : arr) {
                    playerNames.add("\"" + JsonUtil.escapeJson(p.getClass().getMethod("getName").invoke(p).toString()) + "\"");
                }
            } else {
                @SuppressWarnings("unchecked")
                Iterable<Object> iter = (Iterable<Object>) onlineRaw;
                java.util.Iterator<Object> it = iter.iterator();
                onlineCount = 0;
                while (it.hasNext()) {
                    onlineCount++;
                    Object p = it.next();
                    playerNames.add("\"" + JsonUtil.escapeJson(p.getClass().getMethod("getName").invoke(p).toString()) + "\"");
                }
            }
            json.append("\"online\":").append(onlineCount);
            json.append(",\"max\":").append(Bukkit.getMaxPlayers());
            json.append(",\"list\":[");
            json.append(String.join(",", playerNames));
            json.append("]},");

            // TPS — Paper exclusive, via reflection
            json.append("\"tps\":{");
            if (getTpsMethod != null) {
                double[] tps = (double[]) getTpsMethod.invoke(null);
                json.append("\"1m\":").append(String.format("%.2f", tps[0]));
                json.append(",\"5m\":").append(String.format("%.2f", tps[1]));
                json.append(",\"15m\":").append(String.format("%.2f", tps[2]));
            } else {
                json.append("\"1m\":-1,\"5m\":-1,\"15m\":-1");
            }
            json.append("},");

            // Memory
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            Runtime rt = Runtime.getRuntime();
            json.append("\"memory\":{");
            json.append("\"heap_used_mb\":").append(heap.getUsed() / 1048576);
            json.append(",\"heap_max_mb\":").append(heap.getMax() / 1048576);
            json.append(",\"total_mb\":").append(rt.totalMemory() / 1048576);
            json.append(",\"free_mb\":").append(rt.freeMemory() / 1048576);
            json.append("},");

            // Uptime
            json.append("\"uptime_seconds\":").append(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

            // Server info
            json.append(",\"server\":{");
            json.append("\"version\":\"").append(JsonUtil.escapeJson(Bukkit.getVersion())).append("\"");
            json.append(",\"bukkit_version\":\"").append(JsonUtil.escapeJson(Bukkit.getBukkitVersion())).append("\"");
            json.append("}");

            // Plugin info
            json.append(",\"plugins\":{");
            json.append("\"total\":").append(plugin.getServer().getPluginManager().getPlugins().length);
            json.append("}");

            json.append("}");

            byte[] responseBytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error handling status request: " + e.getMessage());
            String error = "{\"success\":false,\"error\":\"Internal server error\"}";
            byte[] responseBytes = error.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(500, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
