// SPDX-License-Identifier: MIT

package com.haavk.commandbridge.api;

import com.haavk.commandbridge.CommandBridge;
import com.haavk.commandbridge.config.ConfigManager;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class ApiServer {

    private final CommandBridge plugin;
    private final ConfigManager configManager;
    private HttpServer server;

    public ApiServer(CommandBridge plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start(int port) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 10);
        server.setExecutor(Executors.newCachedThreadPool());

        // Register routes with authentication
        server.createContext("/command", new CommandHandler(plugin))
              .getFilters()
              .add(new AuthFilter(configManager.getApiKey()));

        // Synchronous command endpoint (returns dispatch success/failure)
        server.createContext("/exec", new CommandSyncHandler(plugin))
              .getFilters()
              .add(new AuthFilter(configManager.getApiKey()));

        // Status endpoint (also authenticated)
        server.createContext("/status", new StatusHandler(plugin))
              .getFilters()
              .add(new AuthFilter(configManager.getApiKey()));

        // Restart endpoint (also authenticated)
        server.createContext("/restart", new RestartHandler(plugin))
              .getFilters()
              .add(new AuthFilter(configManager.getApiKey()));

        // 聊天轮询端点（免 SSH 尬聊用）
        server.createContext("/chat", new ChatHandler(plugin))
              .getFilters()
              .add(new AuthFilter(configManager.getApiKey()));
        server.createContext("/chat/latest", new ChatHandler(plugin))
              .getFilters()
              .add(new AuthFilter(configManager.getApiKey()));

        // 三部制路由 — 控制部/执行部/审查部 全部20个端点
        String[] deptPaths = {
            "/config", "/schedule", "/scheduled-tasks", "/cancel-task",      // 控制部
            "/broadcast", "/kick", "/teleport", "/time", "/weather",          // 执行部
            "/gamemode", "/give", "/effect",                                  // 执行部
            "/logs", "/players", "/plugins", "/worlds",                       // 审查部
            "/memory", "/tps", "/uptime", "/diagnose"                         // 审查部
        };
        DepartmentRouter deptRouter = new DepartmentRouter(plugin);
        for (String path : deptPaths) {
            server.createContext(path, deptRouter)
                  .getFilters()
                  .add(new AuthFilter(configManager.getApiKey()));
        }

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }
}
