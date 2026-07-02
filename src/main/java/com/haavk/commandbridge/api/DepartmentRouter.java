// SPDX-License-Identifier: MIT

package com.haavk.commandbridge.api;

import com.haavk.commandbridge.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 三部制 API 处理器
 * 控制部 — 规划、配置、调度
 * 执行部 — 操作、命令、执行
 * 审查部 — 监控、日志、审查
 */
public class DepartmentRouter implements HttpHandler {

    private final JavaPlugin plugin;

    // 定时任务存储
    private final Map<String, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

    public DepartmentRouter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath().toLowerCase();

        // Read body for POST requests
        String body = "";
        if ("POST".equals(method) || "PUT".equals(method)) {
            InputStream is = exchange.getRequestBody();
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try {
            // ======== 控制部 — 规划与管理 ========
            switch (path) {
                // 1. GET /config — 查看插件配置
                case "/config":
                    if ("GET".equals(method)) getConfig(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 2. POST /schedule — 创建定时任务
                case "/schedule":
                    if ("POST".equals(method)) createSchedule(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 3. GET /scheduled-tasks — 列出定时任务
                case "/scheduled-tasks":
                    if ("GET".equals(method)) listSchedules(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 4. POST /cancel-task — 取消定时任务
                case "/cancel-task":
                    if ("POST".equals(method)) cancelSchedule(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // ======== 执行部 — 操作与执行 ========

                // 5. POST /broadcast — 广播消息
                case "/broadcast":
                    if ("POST".equals(method)) broadcast(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 6. POST /kick — 踢出玩家
                case "/kick":
                    if ("POST".equals(method)) kickPlayer(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 7. POST /teleport — 传送
                case "/teleport":
                    if ("POST".equals(method)) teleport(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 8. POST /time — 设置时间
                case "/time":
                    if ("POST".equals(method)) setTime(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 9. POST /weather — 设置天气
                case "/weather":
                    if ("POST".equals(method)) setWeather(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 10. POST /gamemode — 设置游戏模式
                case "/gamemode":
                    if ("POST".equals(method)) setGamemode(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 11. POST /give — 给予物品
                case "/give":
                    if ("POST".equals(method)) giveItem(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // 12. POST /effect — 应用效果
                case "/effect":
                    if ("POST".equals(method)) applyEffect(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                // ======== 审查部 — 监控与日志 ========

                // 13. GET /logs — 获取日志
                case "/logs":
                    if ("GET".equals(method)) getLogs(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 14. GET /players — 在线玩家详情
                case "/players":
                    if ("GET".equals(method)) getPlayers(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 15. GET /plugins — 插件列表
                case "/plugins":
                    if ("GET".equals(method)) getPlugins(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 16. GET /worlds — 世界信息
                case "/worlds":
                    if ("GET".equals(method)) getWorlds(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 17. GET /memory — 内存详情
                case "/memory":
                    if ("GET".equals(method)) getMemoryDetail(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 18. GET /tps — TPS 历史
                case "/tps":
                    if ("GET".equals(method)) getTps(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 19. GET /uptime — 运行时间
                case "/uptime":
                    if ("GET".equals(method)) getUptime(exchange);
                    else methodNotAllowed(exchange);
                    return;

                // 20. POST /diagnose — 综合诊断
                case "/diagnose":
                    if ("POST".equals(method)) diagnose(exchange, body);
                    else methodNotAllowed(exchange);
                    return;

                default:
                    exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, path + " error: " + e.getMessage(), e);
            writeJson(exchange, 500, "{\"success\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ============================================================
    //  控制部
    // ============================================================

    /** 1. GET /config — 查看配置 */
    private void getConfig(HttpExchange exchange) throws IOException {
        String json = "{\"success\":true,\"port\":" + plugin.getConfig().getInt("port", 9178) +
            ",\"api_key\":\"" + plugin.getConfig().getString("api-key", "未设置").replaceAll("(?<=^.).*(?=.$)", "***") + "\"}";
        writeJson(exchange, 200, json);
    }

    /** 2. POST /schedule — 创建定时任务 */
    private void createSchedule(HttpExchange exchange, String body) throws IOException {
        String command = extractString(body, "command");
        long delay = extractLong(body, "delay_seconds", 60);
        long period = extractLong(body, "interval_seconds", -1);

        if (command.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'command' field\"}");
            return;
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        ScheduledTask task = new ScheduledTask(taskId, command);

        if (period > 0) {
            // 循环任务
            task.bukkitTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    task.runCount++;
                }
            }.runTaskTimer(plugin, delay * 20L, period * 20L).getTaskId();
            task.repeating = true;
        } else {
            // 一次性延时任务
            task.bukkitTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    task.runCount++;
                    scheduledTasks.remove(taskId);
                }
            }.runTaskLater(plugin, delay * 20L).getTaskId();
        }

        scheduledTasks.put(taskId, task);
        writeJson(exchange, 200, "{\"success\":true,\"task_id\":\"" + taskId + "\",\"command\":" + jsonEscape(command) + ",\"delay\":" + delay + ",\"interval\":" + period + "}");
    }

    /** 3. GET /scheduled-tasks — 列出定时任务 */
    private void listSchedules(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("{\"success\":true,\"tasks\":[");
        boolean first = true;
        for (ScheduledTask task : scheduledTasks.values()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":\"").append(task.id).append("\"");
            sb.append(",\"command\":").append(jsonEscape(task.command));
            sb.append(",\"repeating\":").append(task.repeating);
            sb.append(",\"run_count\":").append(task.runCount);
            sb.append(",\"task_id\":").append(task.bukkitTaskId);
            sb.append("}");
            first = false;
        }
        sb.append("],\"count\":").append(scheduledTasks.size()).append("}");
        writeJson(exchange, 200, sb.toString());
    }

    /** 4. POST /cancel-task — 取消定时任务 */
    private void cancelSchedule(HttpExchange exchange, String body) throws IOException {
        String taskId = extractString(body, "task_id");
        if (taskId.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'task_id' field\"}");
            return;
        }
        ScheduledTask task = scheduledTasks.remove(taskId);
        if (task == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"Task not found\"}");
            return;
        }
        Bukkit.getScheduler().cancelTask(task.bukkitTaskId);
        writeJson(exchange, 200, "{\"success\":true,\"cancelled\":\"" + taskId + "\",\"runs\":" + task.runCount + "}");
    }

    // ============================================================
    //  执行部
    // ============================================================

    /** 5. POST /broadcast — 广播消息 */
    private void broadcast(HttpExchange exchange, String body) throws IOException {
        String message = extractString(body, "message");
        if (message.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'message' field\"}");
            return;
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        writeJson(exchange, 200, "{\"success\":true,\"message\":" + jsonEscape(message) + ",\"players_reached\":" + Bukkit.getOnlinePlayers().size() + "}");
    }

    /** 6. POST /kick — 踢出玩家 */
    private void kickPlayer(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String reason = extractString(body, "reason", "Kicked via API");
        if (playerName.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'player' field\"}");
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"Player not found\"}");
            return;
        }
        player.kickPlayer(reason);
        writeJson(exchange, 200, "{\"success\":true,\"kicked\":\"" + playerName + "\",\"reason\":" + jsonEscape(reason) + "}");
    }

    /** 7. POST /teleport — 传送 */
    private void teleport(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String targetName = extractString(body, "target");
        if (playerName.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'player' field\"}");
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"Player " + playerName + " not found\"}");
            return;
        }
        if (!targetName.isEmpty()) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                writeJson(exchange, 404, "{\"success\":false,\"error\":\"Target " + targetName + " not found\"}");
                return;
            }
            player.teleport(target);
        } else {
            double x = extractDouble(body, "x", 0);
            double y = extractDouble(body, "y", 64);
            double z = extractDouble(body, "z", 0);
            String worldName = extractString(body, "world", player.getWorld().getName());
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                writeJson(exchange, 404, "{\"success\":false,\"error\":\"World " + worldName + " not found\"}");
                return;
            }
            player.teleport(new Location(world, x, y, z));
        }
        writeJson(exchange, 200, "{\"success\":true,\"teleported\":\"" + playerName + "\"}");
    }

    /** 8. POST /time — 设置时间 */
    private void setTime(HttpExchange exchange, String body) throws IOException {
        String worldName = extractString(body, "world", Bukkit.getWorlds().get(0).getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"World not found\"}");
            return;
        }
        String timeStr = extractString(body, "time", "day");
        switch (timeStr.toLowerCase()) {
            case "day": world.setTime(1000); break;
            case "noon": world.setTime(6000); break;
            case "night": world.setTime(13000); break;
            case "midnight": world.setTime(18000); break;
            default:
                try { world.setTime(Long.parseLong(timeStr)); }
                catch (NumberFormatException e) {
                    writeJson(exchange, 400, "{\"success\":false,\"error\":\"Invalid time value\"}");
                    return;
                }
        }
        writeJson(exchange, 200, "{\"success\":true,\"world\":\"" + worldName + "\",\"time\":" + world.getTime() + "}");
    }

    /** 9. POST /weather — 设置天气 */
    private void setWeather(HttpExchange exchange, String body) throws IOException {
        String worldName = extractString(body, "world", Bukkit.getWorlds().get(0).getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"World not found\"}");
            return;
        }
        String weather = extractString(body, "weather", "clear");
        switch (weather.toLowerCase()) {
            case "clear": case "sun":
                world.setStorm(false); world.setThundering(false); break;
            case "rain": case "storm":
                world.setStorm(true); world.setThundering(false); break;
            case "thunder":
                world.setStorm(true); world.setThundering(true); break;
            default:
                writeJson(exchange, 400, "{\"success\":false,\"error\":\"Invalid weather. Use: clear/rain/thunder\"}");
                return;
        }
        writeJson(exchange, 200, "{\"success\":true,\"world\":\"" + worldName + "\",\"weather\":\"" + weather + "\"}");
    }

    /** 10. POST /gamemode — 设置游戏模式 */
    private void setGamemode(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String mode = extractString(body, "mode", "survival");
        if (playerName.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'player' field\"}");
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"Player not found\"}");
            return;
        }
        GameMode gm;
        switch (mode.toLowerCase()) {
            case "survival": case "s": gm = GameMode.SURVIVAL; break;
            case "creative": case "c": gm = GameMode.CREATIVE; break;
            case "adventure": case "a": gm = GameMode.ADVENTURE; break;
            case "spectator": case "sp": gm = GameMode.SPECTATOR; break;
            default: writeJson(exchange, 400, "{\"success\":false,\"error\":\"Invalid gamemode\"}"); return;
        }
        player.setGameMode(gm);
        writeJson(exchange, 200, "{\"success\":true,\"player\":\"" + playerName + "\",\"gamemode\":\"" + mode + "\"}");
    }

    /** 11. POST /give — 给予物品 */
    private void giveItem(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String item = extractString(body, "item", "diamond");
        int count = (int) extractLong(body, "count", 1);
        if (playerName.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'player' field\"}");
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            writeJson(exchange, 404, "{\"success\":false,\"error\":\"Player not found\"}");
            return;
        }
        // Dispatch via command for item ID resolution
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + playerName + " " + item + " " + count);
        writeJson(exchange, 200, "{\"success\":true,\"player\":\"" + playerName + "\",\"item\":\"" + item + "\",\"count\":" + count + "}");
    }

    /** 12. POST /effect — 应用效果 */
    private void applyEffect(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String effect = extractString(body, "effect", "speed");
        int duration = (int) extractLong(body, "duration", 60);
        int amplifier = (int) extractLong(body, "amplifier", 1);
        if (playerName.isEmpty()) {
            writeJson(exchange, 400, "{\"success\":false,\"error\":\"Missing 'player' field\"}");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "effect give " + playerName + " " + effect + " " + duration + " " + amplifier);
        writeJson(exchange, 200, "{\"success\":true,\"player\":\"" + playerName + "\",\"effect\":\"" + effect + "\",\"duration\":" + duration + ",\"amplifier\":" + amplifier + "}");
    }

    // ============================================================
    //  审查部
    // ============================================================

    /** 13. GET /logs — 获取日志 */
    private void getLogs(HttpExchange exchange) throws IOException {
        String logPath = "logs/latest.log";
        java.io.File logFile = new java.io.File(logPath);
        if (!logFile.exists()) {
            writeJson(exchange, 200, "{\"success\":true,\"lines\":[]}");
            return;
        }
        int lines = Math.min((int) extractLong(extractQueryString(exchange), "lines", 50), 200);
        try {
            java.util.List<String> allLines = java.nio.file.Files.readAllLines(logFile.toPath());
            int start = Math.max(0, allLines.size() - lines);
            StringBuilder sb = new StringBuilder("{\"success\":true,\"count\":" + lines + ",\"total\":" + allLines.size() + ",\"lines\":[");
            for (int i = start; i < allLines.size(); i++) {
                if (i > start) sb.append(",");
                sb.append(jsonEscape(allLines.get(i)));
            }
            sb.append("]}");
            writeJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            writeJson(exchange, 500, "{\"success\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
        }
    }

    /** 14. GET /players — 在线玩家详情 */
    private void getPlayers(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("{\"success\":true,\"online\":" + Bukkit.getOnlinePlayers().size() +
            ",\"max\":" + Bukkit.getMaxPlayers() + ",\"players\":[");
        boolean first = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!first) sb.append(",");
            sb.append("{\"name\":\"").append(jsonEscape(p.getName())).append("\"");
            sb.append(",\"uuid\":\"").append(p.getUniqueId()).append("\"");
            sb.append(",\"gamemode\":\"").append(p.getGameMode().name().toLowerCase()).append("\"");
            sb.append(",\"health\":").append(String.format("%.1f", p.getHealth()));
            sb.append(",\"food\":").append(p.getFoodLevel());
            sb.append(",\"level\":").append(p.getLevel());
            sb.append(",\"xp\":").append(p.getExp());
            sb.append(",\"world\":\"").append(p.getWorld().getName()).append("\"");
            sb.append(",\"x\":").append(String.format("%.1f", p.getLocation().getX()));
            sb.append(",\"y\":").append(String.format("%.1f", p.getLocation().getY()));
            sb.append(",\"z\":").append(String.format("%.1f", p.getLocation().getZ()));
            sb.append(",\"ping\":").append(p.getPing());
            sb.append(",\"ip\":\"").append(p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown").append("\"");
            sb.append("}");
            first = false;
        }
        sb.append("]}");
        writeJson(exchange, 200, sb.toString());
    }

    /** 15. GET /plugins — 插件列表 */
    private void getPlugins(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("{\"success\":true,\"total\":" + Bukkit.getPluginManager().getPlugins().length + ",\"plugins\":[");
        boolean first = true;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (!first) sb.append(",");
            sb.append("{\"name\":\"").append(jsonEscape(p.getName())).append("\"");
            sb.append(",\"version\":\"").append(jsonEscape(p.getDescription().getVersion())).append("\"");
            sb.append(",\"enabled\":").append(p.isEnabled());
            sb.append(",\"main\":\"").append(jsonEscape(p.getDescription().getMain())).append("\"");
            sb.append("}");
            first = false;
        }
        sb.append("]}");
        writeJson(exchange, 200, sb.toString());
    }

    /** 16. GET /worlds — 世界信息 */
    private void getWorlds(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("{\"success\":true,\"worlds\":[");
        boolean first = true;
        for (World w : Bukkit.getWorlds()) {
            if (!first) sb.append(",");
            sb.append("{\"name\":\"").append(w.getName()).append("\"");
            sb.append(",\"environment\":\"").append(w.getEnvironment().name().toLowerCase()).append("\"");
            sb.append(",\"players\":").append(w.getPlayers().size());
            sb.append(",\"time\":").append(w.getTime());
            sb.append(",\"storm\":").append(w.hasStorm());
            sb.append(",\"thunder\":").append(w.isThundering());
            sb.append(",\"difficulty\":\"").append(w.getDifficulty().name().toLowerCase()).append("\"");
            sb.append(",\"seed\":").append(w.getSeed());
            sb.append("}");
            first = false;
        }
        sb.append("],\"count\":").append(Bukkit.getWorlds().size()).append("}");
        writeJson(exchange, 200, sb.toString());
    }

    /** 17. GET /memory — 内存详情 */
    private void getMemoryDetail(HttpExchange exchange) throws IOException {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
        Runtime rt = Runtime.getRuntime();
        String json = "{\"success\":true," +
            "\"heap\":{\"used_mb\":" + (heap.getUsed() / 1048576L) +
            ",\"max_mb\":" + (heap.getMax() / 1048576L) +
            ",\"committed_mb\":" + (heap.getCommitted() / 1048576L) +
            ",\"init_mb\":" + (heap.getInit() / 1048576L) + "}," +
            "\"non_heap\":{\"used_mb\":" + (nonHeap.getUsed() / 1048576L) +
            ",\"max_mb\":" + (nonHeap.getMax() / 1048576L) + "}," +
            "\"runtime\":{\"total_mb\":" + (rt.totalMemory() / 1048576L) +
            ",\"free_mb\":" + (rt.freeMemory() / 1048576L) +
            ",\"used_mb\":" + ((rt.totalMemory() - rt.freeMemory()) / 1048576L) + "}}";
        writeJson(exchange, 200, json);
    }

    /** 18. GET /tps — TPS */
    private void getTps(HttpExchange exchange) throws IOException {
        double[] tps = Bukkit.getTPS();
        String json = "{\"success\":true,\"tps\":{\"1m\":" + String.format("%.2f", tps[0]) +
            ",\"5m\":" + String.format("%.2f", tps[1]) +
            ",\"15m\":" + String.format("%.2f", tps[2]) + "}}";
        writeJson(exchange, 200, json);
    }

    /** 19. GET /uptime */
    private void getUptime(HttpExchange exchange) throws IOException {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        long days = uptime / 86400;
        long hours = (uptime % 86400) / 3600;
        long minutes = (uptime % 3600) / 60;
        long seconds = uptime % 60;
        String json = "{\"success\":true,\"uptime_seconds\":" + uptime +
            ",\"formatted\":\"" + days + "天" + hours + "时" + minutes + "分" + seconds + "秒\"}";
        writeJson(exchange, 200, json);
    }

    /** 20. POST /diagnose — 综合诊断 */
    private void diagnose(HttpExchange exchange, String body) throws IOException {
        String target = extractString(body, "target", "server");
        StringBuilder sb = new StringBuilder("{\"success\":true,\"diagnosis\":{");

        if ("server".equals(target) || "all".equals(target)) {
            double[] tps = Bukkit.getTPS();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            sb.append("\"server\":{\"tps_1m\":" + String.format("%.2f", tps[0]));
            sb.append(",\"players\":" + Bukkit.getOnlinePlayers().size());
            sb.append(",\"memory_usage_pct\":" + String.format("%.1f", (double) heap.getUsed() / heap.getMax() * 100));
            sb.append(",\"worlds\":" + Bukkit.getWorlds().size());
            sb.append(",\"plugins\":" + Bukkit.getPluginManager().getPlugins().length);
            sb.append("}");
        }
        if ("player".equals(target) || "all".equals(target)) {
            String playerName = extractString(body, "player", "");
            if (!playerName.isEmpty()) {
                Player p = Bukkit.getPlayerExact(playerName);
                if (p != null) {
                    if (sb.charAt(sb.length()-1) != '{') sb.append(",");
                    sb.append("\"player\":{\"name\":\"" + jsonEscape(p.getName()) + "\"");
                    sb.append(",\"health\":" + String.format("%.1f", p.getHealth()));
                    sb.append(",\"food\":" + p.getFoodLevel());
                    sb.append(",\"ping\":" + p.getPing());
                    sb.append(",\"location\":\"" + p.getWorld().getName() + " " +
                        String.format("%.0f", p.getLocation().getX()) + " " +
                        String.format("%.0f", p.getLocation().getY()) + " " +
                        String.format("%.0f", p.getLocation().getZ()) + "\"");
                    sb.append("}");
                }
            }
        }
        if ("performance".equals(target) || "all".equals(target)) {
            if (sb.charAt(sb.length()-1) != '{') sb.append(",");
            sb.append("\"performance\":{\"tps_1m\":" + String.format("%.2f", Bukkit.getTPS()[0]));
            sb.append(",\"tps_5m\":" + String.format("%.2f", Bukkit.getTPS()[1]));
            sb.append(",\"tps_15m\":" + String.format("%.2f", Bukkit.getTPS()[2]));
            sb.append(",\"free_memory_mb\":" + (Runtime.getRuntime().freeMemory() / 1048576));
            sb.append(",\"max_memory_mb\":" + (Runtime.getRuntime().maxMemory() / 1048576));
            sb.append("}");
        }
        sb.append("}}");
        writeJson(exchange, 200, sb.toString());
    }

    // ============================================================
    //  工具方法
    // ============================================================

    private void methodNotAllowed(HttpExchange exchange) throws IOException {
        writeJson(exchange, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
    }

    private void writeJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String jsonEscape(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // 简单 JSON 字段提取（正则，支持嵌套）
    private String extractString(String json, String key) {
        return extractString(json, key, "");
    }

    private String extractString(String json, String key, String def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return unescapeJson(m.group(1));
        return def;
    }

    private long extractLong(String json, String key, long def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) return Long.parseLong(m.group(1));
        return def;
    }

    private double extractDouble(String json, String key, double def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+\\.?\\d*)");
        Matcher m = p.matcher(json);
        if (m.find()) return Double.parseDouble(m.group(1));
        return def;
    }

    private String extractQueryString(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        return query != null ? query : "";
    }

    private String unescapeJson(String s) {
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

    private static class ScheduledTask {
        final String id;
        final String command;
        int bukkitTaskId;
        boolean repeating;
        int runCount;

        ScheduledTask(String id, String command) {
            this.id = id;
            this.command = command;
        }
    }
}
