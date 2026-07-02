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
