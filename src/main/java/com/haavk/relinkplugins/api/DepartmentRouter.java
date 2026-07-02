// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.config.ConfigManager;
import com.haavk.relinkplugins.util.ApiResponse;
import com.haavk.relinkplugins.util.JsonUtil;
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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Multi-department API handler
 * 控制部 — 规划、配置、调度
 * 执行部 — 操作、命令、执行
 * 审查部 — 监控、日志、审查
 */
public class DepartmentRouter implements HttpHandler {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    // Scheduled tasks
    private final Map<String, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

    public DepartmentRouter(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath().toLowerCase();

        String body = "";
        if ("POST".equals(method) || "PUT".equals(method)) {
            InputStream is = exchange.getRequestBody();
            body = new String(JsonUtil.readAll(is), StandardCharsets.UTF_8);
        }

        try {
            switch (path) {
                // ======== 控制部 ========
                case "/config":
                    if ("GET".equals(method)) getConfig(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/schedule":
                    if ("POST".equals(method)) createSchedule(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/scheduled-tasks":
                    if ("GET".equals(method)) listSchedules(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/cancel-task":
                    if ("POST".equals(method)) cancelSchedule(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                // ======== 执行部 ========
                case "/broadcast":
                    if ("POST".equals(method)) broadcast(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/kick":
                    if ("POST".equals(method)) kickPlayer(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/teleport":
                    if ("POST".equals(method)) teleport(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/time":
                    if ("POST".equals(method)) setTime(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/weather":
                    if ("POST".equals(method)) setWeather(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/gamemode":
                    if ("POST".equals(method)) setGamemode(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/give":
                    if ("POST".equals(method)) giveItem(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/effect":
                    if ("POST".equals(method)) applyEffect(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                // ======== 审查部 ========
                case "/logs":
                    if ("GET".equals(method)) getLogs(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/players":
                    if ("GET".equals(method)) getPlayers(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/plugins":
                    if ("GET".equals(method)) getPlugins(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/worlds":
                    if ("GET".equals(method)) getWorlds(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/memory":
                    if ("GET".equals(method)) getMemoryDetail(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/tps":
                    if ("GET".equals(method)) getTps(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/uptime":
                    if ("GET".equals(method)) getUptime(exchange);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                case "/diagnose":
                    if ("POST".equals(method)) diagnose(exchange, body);
                    else send(exchange, ApiResponse.methodNotAllowed());
                    return;

                default:
                    send(exchange, ApiResponse.notFound("端点不存在: " + path));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, path + " error: " + e.getMessage(), e);
            send(exchange, ApiResponse.internalError(e.getMessage()));
        }
    }

    // ============================================================
    //  控制部
    // ============================================================

    private void getConfig(HttpExchange exchange) throws IOException {
        StringBuilder data = new StringBuilder();
        data.append("{\"port\":").append(configManager.getPort());
        data.append(",\"keys\":[");
        boolean first = true;
        for (String key : configManager.getKeys().keySet()) {
            if (!first) data.append(",");
            first = false;
            String masked = key.length() > 8 ? key.substring(0, 4) + "****" + key.substring(key.length() - 4) : "****";
            data.append("{\"key\":\"").append(JsonUtil.escapeJson(masked)).append("\"}");
        }
        data.append("]}");
        send(exchange, ApiResponse.success(data.toString(), "配置信息"));
    }

    private void createSchedule(HttpExchange exchange, String body) throws IOException {
        String command = extractString(body, "command");
        long delay = extractLong(body, "delay_seconds", 60);
        long period = extractLong(body, "interval_seconds", -1);

        if (command.isEmpty()) {
            send(exchange, ApiResponse.missingParam("command"));
            return;
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        ScheduledTask task = new ScheduledTask(taskId, command);

        if (period > 0) {
            task.bukkitTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    task.runCount++;
                }
            }.runTaskTimer(plugin, delay * 20L, period * 20L).getTaskId();
            task.repeating = true;
        } else {
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
        String data = "{\"task_id\":\"" + taskId + "\",\"command\":"
            + JsonUtil.escapeJson(command) + ",\"delay_seconds\":" + delay
            + ",\"interval_seconds\":" + period + "}";
        send(exchange, ApiResponse.success(data, "定时任务已创建"));
    }

    private void listSchedules(HttpExchange exchange) throws IOException {
        StringBuilder tasks = new StringBuilder("[");
        boolean first = true;
        for (ScheduledTask t : scheduledTasks.values()) {
            if (!first) tasks.append(",");
            tasks.append("{\"id\":\"").append(t.id).append("\"");
            tasks.append(",\"command\":").append(JsonUtil.escapeJson(t.command));
            tasks.append(",\"repeating\":").append(t.repeating);
            tasks.append(",\"run_count\":").append(t.runCount);
            tasks.append(",\"bukkit_task_id\":").append(t.bukkitTaskId);
            tasks.append("}");
            first = false;
        }
        tasks.append("]");
        String data = "{\"tasks\":" + tasks.toString() + ",\"count\":" + scheduledTasks.size() + "}";
        send(exchange, ApiResponse.success(data, "定时任务列表"));
    }

    private void cancelSchedule(HttpExchange exchange, String body) throws IOException {
        String taskId = extractString(body, "task_id");
        if (taskId.isEmpty()) {
            send(exchange, ApiResponse.missingParam("task_id"));
            return;
        }
        ScheduledTask task = scheduledTasks.remove(taskId);
        if (task == null) {
            send(exchange, ApiResponse.notFound("定时任务 " + taskId + " 不存在"));
            return;
        }
        Bukkit.getScheduler().cancelTask(task.bukkitTaskId);
        String data = "{\"cancelled\":\"" + taskId + "\",\"runs\":" + task.runCount + "}";
        send(exchange, ApiResponse.success(data, "定时任务已取消"));
    }

    // ============================================================
    //  执行部
    // ============================================================

    private void broadcast(HttpExchange exchange, String body) throws IOException {
        String message = extractString(body, "message");
        if (message.isEmpty()) {
            send(exchange, ApiResponse.missingParam("message"));
            return;
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        String data = "{\"message\":" + JsonUtil.escapeJson(message)
            + ",\"players_reached\":" + Bukkit.getOnlinePlayers().size() + "}";
        send(exchange, ApiResponse.success(data, "广播已发送"));
    }

    private void kickPlayer(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String reason = extractString(body, "reason", "Kicked via API");
        if (playerName.isEmpty()) {
            send(exchange, ApiResponse.missingParam("player"));
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            send(exchange, ApiResponse.notFound("玩家 " + playerName + " 不在线"));
            return;
        }
        player.kickPlayer(reason);
        String data = "{\"player\":\"" + JsonUtil.escapeJson(playerName)
            + "\",\"reason\":" + JsonUtil.escapeJson(reason) + "}";
        send(exchange, ApiResponse.success(data, "玩家 " + playerName + " 已被踢出"));
    }

    private void teleport(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String targetName = extractString(body, "target");
        if (playerName.isEmpty()) {
            send(exchange, ApiResponse.missingParam("player"));
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            send(exchange, ApiResponse.notFound("玩家 " + playerName + " 不在线"));
            return;
        }
        if (!targetName.isEmpty()) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                send(exchange, ApiResponse.notFound("目标 " + targetName + " 不在线"));
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
                send(exchange, ApiResponse.notFound("世界 " + worldName + " 不存在"));
                return;
            }
            player.teleport(new Location(world, x, y, z));
        }
        String data = "{\"player\":\"" + JsonUtil.escapeJson(playerName) + "\"}";
        send(exchange, ApiResponse.success(data, "已传送 " + playerName));
    }

    private void setTime(HttpExchange exchange, String body) throws IOException {
        String worldName = extractString(body, "world", Bukkit.getWorlds().get(0).getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            send(exchange, ApiResponse.notFound("世界 " + worldName + " 不存在"));
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
                    send(exchange, ApiResponse.badRequest("参数 time 格式错误"));
                    return;
                }
        }
        String data = "{\"world\":\"" + worldName + "\",\"time\":" + world.getTime() + "}";
        send(exchange, ApiResponse.success(data, "时间已设置"));
    }

    private void setWeather(HttpExchange exchange, String body) throws IOException {
        String worldName = extractString(body, "world", Bukkit.getWorlds().get(0).getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            send(exchange, ApiResponse.notFound("世界 " + worldName + " 不存在"));
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
                send(exchange, ApiResponse.badRequest("无效天气。可用: clear/rain/thunder"));
                return;
        }
        String data = "{\"world\":\"" + worldName + "\",\"weather\":\"" + weather + "\"}";
        send(exchange, ApiResponse.success(data, "天气已设置"));
    }

    private void setGamemode(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String mode = extractString(body, "mode", "survival");
        if (playerName.isEmpty()) {
            send(exchange, ApiResponse.missingParam("player"));
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            send(exchange, ApiResponse.notFound("玩家 " + playerName + " 不在线"));
            return;
        }
        GameMode gm;
        switch (mode.toLowerCase()) {
            case "survival": case "s": gm = GameMode.SURVIVAL; break;
            case "creative": case "c": gm = GameMode.CREATIVE; break;
            case "adventure": case "a": gm = GameMode.ADVENTURE; break;
            case "spectator": case "sp": gm = GameMode.SPECTATOR; break;
            default:
                send(exchange, ApiResponse.badRequest("无效游戏模式"));
                return;
        }
        player.setGameMode(gm);
        String data = "{\"player\":\"" + JsonUtil.escapeJson(playerName) + "\",\"gamemode\":\"" + mode + "\"}";
        send(exchange, ApiResponse.success(data, "游戏模式已设置"));
    }

    private void giveItem(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String item = extractString(body, "item", "diamond");
        int count = (int) extractLong(body, "count", 1);
        if (playerName.isEmpty()) {
            send(exchange, ApiResponse.missingParam("player"));
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            send(exchange, ApiResponse.notFound("玩家 " + playerName + " 不在线"));
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + playerName + " " + item + " " + count);
        String data = "{\"player\":\"" + JsonUtil.escapeJson(playerName)
            + "\",\"item\":\"" + item + "\",\"count\":" + count + "}";
        send(exchange, ApiResponse.success(data, "物品已给予"));
    }

    private void applyEffect(HttpExchange exchange, String body) throws IOException {
        String playerName = extractString(body, "player");
        String effect = extractString(body, "effect", "speed");
        int duration = (int) extractLong(body, "duration", 60);
        int amplifier = (int) extractLong(body, "amplifier", 1);
        if (playerName.isEmpty()) {
            send(exchange, ApiResponse.missingParam("player"));
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "effect give " + playerName + " " + effect + " " + duration + " " + amplifier);
        String data = "{\"player\":\"" + JsonUtil.escapeJson(playerName)
            + "\",\"effect\":\"" + effect + "\",\"duration\":" + duration + ",\"amplifier\":" + amplifier + "}";
        send(exchange, ApiResponse.success(data, "效果已应用"));
    }

    // ============================================================
    //  审查部
    // ============================================================

    private void getLogs(HttpExchange exchange) throws IOException {
        String logPath = "logs/latest.log";
        java.io.File logFile = new java.io.File(logPath);
        if (!logFile.exists()) {
            send(exchange, ApiResponse.success("{\"lines\":[],\"total\":0}", "日志文件不存在"));
            return;
        }
        int lines = Math.min((int) extractLong(extractQueryString(exchange), "lines", 50), 200);
        try {
            java.util.List<String> allLines = java.nio.file.Files.readAllLines(logFile.toPath());
            int start = Math.max(0, allLines.size() - lines);
            StringBuilder sb = new StringBuilder("{\"count\":").append(lines).append(",\"total\":").append(allLines.size()).append(",\"lines\":[");
            for (int i = start; i < allLines.size(); i++) {
                if (i > start) sb.append(",");
                sb.append(JsonUtil.escapeJson(allLines.get(i)));
            }
            sb.append("]}");
            send(exchange, ApiResponse.success(sb.toString(), "日志记录"));
        } catch (Exception e) {
            send(exchange, ApiResponse.internalError(e.getMessage()));
        }
    }

    private void getPlayers(HttpExchange exchange) throws IOException {
        StringBuilder players = new StringBuilder("[");
        boolean first = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!first) players.append(",");
            players.append("{\"name\":\"").append(JsonUtil.escapeJson(p.getName())).append("\"");
            players.append(",\"uuid\":\"").append(p.getUniqueId()).append("\"");
            players.append(",\"gamemode\":\"").append(p.getGameMode().name().toLowerCase()).append("\"");
            players.append(",\"health\":").append(String.format("%.1f", p.getHealth()));
            players.append(",\"food\":").append(p.getFoodLevel());
            players.append(",\"level\":").append(p.getLevel());
            players.append(",\"xp\":").append(p.getExp());
            players.append(",\"world\":\"").append(p.getWorld().getName()).append("\"");
            players.append(",\"x\":").append(String.format("%.1f", p.getLocation().getX()));
            players.append(",\"y\":").append(String.format("%.1f", p.getLocation().getY()));
            players.append(",\"z\":").append(String.format("%.1f", p.getLocation().getZ()));
            players.append(",\"ping\":").append(p.getPing());
            players.append(",\"ip\":\"").append(p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown").append("\"");
            players.append("}");
            first = false;
        }
        players.append("]");
        String data = "{\"online\":" + Bukkit.getOnlinePlayers().size()
            + ",\"max\":" + Bukkit.getMaxPlayers()
            + ",\"players\":" + players.toString() + "}";
        send(exchange, ApiResponse.success(data, "在线玩家"));
    }

    private void getPlugins(HttpExchange exchange) throws IOException {
        StringBuilder plugins = new StringBuilder("[");
        boolean first = true;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (!first) plugins.append(",");
            plugins.append("{\"name\":\"").append(JsonUtil.escapeJson(p.getName())).append("\"");
            plugins.append(",\"version\":\"").append(JsonUtil.escapeJson(p.getDescription().getVersion())).append("\"");
            plugins.append(",\"enabled\":").append(p.isEnabled());
            plugins.append(",\"main\":\"").append(JsonUtil.escapeJson(p.getDescription().getMain())).append("\"");
            plugins.append("}");
            first = false;
        }
        plugins.append("]");
        String data = "{\"total\":" + Bukkit.getPluginManager().getPlugins().length
            + ",\"plugins\":" + plugins.toString() + "}";
        send(exchange, ApiResponse.success(data, "插件列表"));
    }

    private void getWorlds(HttpExchange exchange) throws IOException {
        StringBuilder worlds = new StringBuilder("[");
        boolean first = true;
        for (World w : Bukkit.getWorlds()) {
            if (!first) worlds.append(",");
            worlds.append("{\"name\":\"").append(w.getName()).append("\"");
            worlds.append(",\"environment\":\"").append(w.getEnvironment().name().toLowerCase()).append("\"");
            worlds.append(",\"players\":").append(w.getPlayers().size());
            worlds.append(",\"time\":").append(w.getTime());
            worlds.append(",\"storm\":").append(w.hasStorm());
            worlds.append(",\"thunder\":").append(w.isThundering());
            worlds.append(",\"difficulty\":\"").append(w.getDifficulty().name().toLowerCase()).append("\"");
            worlds.append(",\"seed\":").append(w.getSeed());
            worlds.append("}");
            first = false;
        }
        worlds.append("]");
        String data = "{\"count\":" + Bukkit.getWorlds().size() + ",\"worlds\":" + worlds.toString() + "}";
        send(exchange, ApiResponse.success(data, "世界信息"));
    }

    private void getMemoryDetail(HttpExchange exchange) throws IOException {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
        Runtime rt = Runtime.getRuntime();
        String data = "{\"heap\":{\"used_mb\":" + (heap.getUsed() / 1048576L)
            + ",\"max_mb\":" + (heap.getMax() / 1048576L)
            + ",\"committed_mb\":" + (heap.getCommitted() / 1048576L)
            + ",\"init_mb\":" + (heap.getInit() / 1048576L) + "}"
            + ",\"non_heap\":{\"used_mb\":" + (nonHeap.getUsed() / 1048576L)
            + ",\"max_mb\":" + (nonHeap.getMax() / 1048576L) + "}"
            + ",\"runtime\":{\"total_mb\":" + (rt.totalMemory() / 1048576L)
            + ",\"free_mb\":" + (rt.freeMemory() / 1048576L)
            + ",\"used_mb\":" + ((rt.totalMemory() - rt.freeMemory()) / 1048576L) + "}}";
        send(exchange, ApiResponse.success(data, "内存信息"));
    }

    private void getTps(HttpExchange exchange) throws IOException {
        try {
            Method getTpsMethod = Bukkit.class.getMethod("getTPS");
            double[] tps = (double[]) getTpsMethod.invoke(null);
            String data = "{\"tps\":{\"1m\":" + String.format("%.2f", tps[0])
                + ",\"5m\":" + String.format("%.2f", tps[1])
                + ",\"15m\":" + String.format("%.2f", tps[2]) + "}}";
            send(exchange, ApiResponse.success(data, "TPS 信息"));
        } catch (ReflectiveOperationException e) {
            String data = "{\"tps\":{\"1m\":-1,\"5m\":-1,\"15m\":-1}}";
            send(exchange, ApiResponse.success(data, "TPS 信息（不可用）"));
        }
    }

    private void getUptime(HttpExchange exchange) throws IOException {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        long days = uptime / 86400;
        long hours = (uptime % 86400) / 3600;
        long minutes = (uptime % 3600) / 60;
        long seconds = uptime % 60;
        String data = "{\"uptime_seconds\":" + uptime
            + ",\"formatted\":\"" + days + "天" + hours + "时" + minutes + "分" + seconds + "秒\"}";
        send(exchange, ApiResponse.success(data, "运行时间"));
    }

    private void diagnose(HttpExchange exchange, String body) throws IOException {
        String target = extractString(body, "target", "server");
        StringBuilder diag = new StringBuilder("{");

        if ("server".equals(target) || "all".equals(target)) {
            double[] tps = {-1, -1, -1};
            try {
                Method getTpsMethod = Bukkit.class.getMethod("getTPS");
                tps = (double[]) getTpsMethod.invoke(null);
            } catch (Exception ignored) {}
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            diag.append("\"server\":{\"tps_1m\":").append(String.format("%.2f", tps[0]));
            diag.append(",\"players\":").append(Bukkit.getOnlinePlayers().size());
            diag.append(",\"memory_usage_pct\":").append(String.format("%.1f", (double) heap.getUsed() / heap.getMax() * 100));
            diag.append(",\"worlds\":").append(Bukkit.getWorlds().size());
            diag.append(",\"plugins\":").append(Bukkit.getPluginManager().getPlugins().length);
            diag.append("}");
        }
        if ("player".equals(target) || "all".equals(target)) {
            String playerName = extractString(body, "player", "");
            if (!playerName.isEmpty()) {
                Player p = Bukkit.getPlayerExact(playerName);
                if (p != null) {
                    if (diag.charAt(diag.length()-1) != '{') diag.append(",");
                    diag.append("\"player\":{\"name\":\"").append(JsonUtil.escapeJson(p.getName())).append("\"");
                    diag.append(",\"health\":").append(String.format("%.1f", p.getHealth()));
                    diag.append(",\"food\":").append(p.getFoodLevel());
                    diag.append(",\"ping\":").append(p.getPing());
                    diag.append(",\"location\":\"").append(p.getWorld().getName()).append(" ")
                        .append(String.format("%.0f", p.getLocation().getX())).append(" ")
                        .append(String.format("%.0f", p.getLocation().getY())).append(" ")
                        .append(String.format("%.0f", p.getLocation().getZ())).append("\"");
                    diag.append("}");
                }
            }
        }
        if ("performance".equals(target) || "all".equals(target)) {
            if (diag.charAt(diag.length()-1) != '{') diag.append(",");
            double[] tps;
            try {
                Method m = Bukkit.class.getMethod("getTPS");
                tps = (double[]) m.invoke(null);
            } catch (Exception e) {
                tps = new double[]{-1, -1, -1};
            }
            diag.append("\"performance\":{\"tps_1m\":").append(String.format("%.2f", tps[0]));
            diag.append(",\"tps_5m\":").append(String.format("%.2f", tps[1]));
            diag.append(",\"tps_15m\":").append(String.format("%.2f", tps[2]));
            diag.append(",\"free_memory_mb\":").append(Runtime.getRuntime().freeMemory() / 1048576);
            diag.append(",\"max_memory_mb\":").append(Runtime.getRuntime().maxMemory() / 1048576);
            diag.append("}");
        }
        diag.append("}");
        send(exchange, ApiResponse.success(diag.toString(), "诊断信息"));
    }

    // ============================================================
    //  Utility methods
    // ============================================================

    private void send(HttpExchange exchange, String json) throws IOException {
        int code = extractHttpCode(json);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private int extractHttpCode(String json) {
        Pattern p = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 200;
    }

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
