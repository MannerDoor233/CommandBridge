package com.haavk.commandbridge.config;

import com.haavk.commandbridge.CommandBridge;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final CommandBridge plugin;
    private int port;
    private String apiKey;

    public ConfigManager(CommandBridge plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.port = config.getInt("api.port", 8080);
        this.apiKey = config.getString("api.key", "change-me-to-a-secure-key");
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public int getPort() {
        return port;
    }

    public String getApiKey() {
        return apiKey;
    }
}
