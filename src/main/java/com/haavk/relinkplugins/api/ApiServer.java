// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.Relink;
import com.haavk.relinkplugins.config.ConfigManager;
import com.haavk.relinkplugins.crypto.CryptoUtil;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ApiServer {

    private final Relink plugin;
    private final ConfigManager configManager;
    private final CryptoUtil crypto;
    private HttpServer server;

    public ApiServer(Relink plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.crypto = new CryptoUtil();
    }

    public void start(int port) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 10);
        server.setExecutor(Executors.newCachedThreadPool());

        AuthFilter authFilter = new AuthFilter(configManager);

        // Register all routes with authentication
        server.createContext("/command", new CommandHandler(plugin, crypto))
              .getFilters().add(authFilter);

        server.createContext("/exec", new CommandSyncHandler(plugin, crypto))
              .getFilters().add(authFilter);

        server.createContext("/status", new StatusHandler(plugin))
              .getFilters().add(authFilter);

        server.createContext("/restart", new RestartHandler(plugin))
              .getFilters().add(authFilter);

        // Chat endpoints
        server.createContext("/chat", new ChatHandler(plugin))
              .getFilters().add(authFilter);
        server.createContext("/chat/latest", new ChatHandler(plugin))
              .getFilters().add(authFilter);

        // Multi-department router — 控制/执行/审查 全部20+端点
        DepartmentRouter deptRouter = new DepartmentRouter(plugin, configManager);
        String[] deptPaths = {
            "/config", "/schedule", "/scheduled-tasks", "/cancel-task",
            "/broadcast", "/kick", "/teleport", "/time", "/weather",
            "/gamemode", "/give", "/effect",
            "/logs", "/players", "/plugins", "/worlds",
            "/memory", "/tps", "/uptime", "/diagnose"
        };
        for (String path : deptPaths) {
            server.createContext(path, deptRouter)
                  .getFilters().add(authFilter);
        }

        // Key management
        KeyManagerHandler keyHandler = new KeyManagerHandler(plugin, configManager);
        server.createContext("/keys", keyHandler)
              .getFilters().add(authFilter);

        // Batch operations
        BatchHandler batchHandler = new BatchHandler(plugin, configManager);
        server.createContext("/batch/command", batchHandler)
              .getFilters().add(authFilter);
        server.createContext("/batch/kick", batchHandler)
              .getFilters().add(authFilter);
        server.createContext("/batch/give", batchHandler)
              .getFilters().add(authFilter);

        // Error test (admin only)
        server.createContext("/error-test", new ErrorTestHandler())
              .getFilters().add(authFilter);

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
