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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class StatusHandler implements HttpHandler {

    private final JavaPlugin plugin;

    public StatusHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Build status response
            StringBuilder json = new StringBuilder();
            json.append("{");

            // Online players
            json.append("\"players\":{");
            json.append("\"online\":" + Bukkit.getOnlinePlayers().size());
            json.append(",\"max\":" + Bukkit.getMaxPlayers());
            json.append(",\"list\":[");
            List<String> names = Bukkit.getOnlinePlayers().stream()
                .map(p -> "\"" + JsonUtil.escapeJson(p.getName()) + "\"")
                .collect(Collectors.toList());
            json.append(String.join(",", names));
            json.append("]");
            json.append("},");

            // TPS (Paper/Leaves specific)
            double[] tps = Bukkit.getTPS();
            json.append("\"tps\":{");
            json.append("\"1m\":" + String.format("%.2f", tps[0]));
            json.append(",\"5m\":" + String.format("%.2f", tps[1]));
            json.append(",\"15m\":" + String.format("%.2f", tps[2]));
            json.append("},");

            // Memory
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            Runtime rt = Runtime.getRuntime();
            json.append("\"memory\":{");
            json.append("\"heap_used_mb\":" + (heap.getUsed() / 1048576));
            json.append(",\"heap_max_mb\":" + (heap.getMax() / 1048576));
            json.append(",\"total_mb\":" + (rt.totalMemory() / 1048576));
            json.append(",\"free_mb\":" + (rt.freeMemory() / 1048576));
            json.append("},");

            // Uptime - use ManagementFactory for cross-version compatibility
            json.append("\"uptime_seconds\":" + ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

            // Server info - strip any embedded quotes from version strings
            json.append(",\"server\":{");
            String ver = Bukkit.getVersion().replace("\"", "").replace("\\", "");
            String bVer = Bukkit.getBukkitVersion().replace("\"", "").replace("\\", "");
            json.append("\"version\":\"" + ver + "\"");
            json.append(",\"bukkit_version\":\"" + bVer + "\"");
            json.append("}");

            // Plugin info
            json.append(",\"plugins\":{");
            json.append("\"total\":" + plugin.getServer().getPluginManager().getPlugins().length);
            json.append(",\"enabled\":" + plugin.getServer().getPluginManager().getPlugins().length);
            json.append("}");

            // Server tick (is the server running)
            json.append(",\"tick\":{");
            json.append("\"lagging\":" + (Bukkit.isPrimaryThread() ? "false" : "true"));
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
