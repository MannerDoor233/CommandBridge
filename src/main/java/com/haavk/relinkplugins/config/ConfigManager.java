// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.config;

import com.haavk.relinkplugins.Relink;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;

public class ConfigManager {

    private final Relink plugin;
    private int port;
    private String defaultKey;
    private final Map<String, ApiKeyConfig> keys = new LinkedHashMap<>();
    private final RateLimiter rateLimiter = new RateLimiter();

    /** Path-to-permission mapping for all endpoints. */
    private static final Map<String, String> ENDPOINT_PERMISSIONS = new LinkedHashMap<>();
    static {
        ENDPOINT_PERMISSIONS.put("/command", "command.execute");
        ENDPOINT_PERMISSIONS.put("/exec", "command.execute");
        ENDPOINT_PERMISSIONS.put("/broadcast", "broadcast");
        ENDPOINT_PERMISSIONS.put("/kick", "kick");
        ENDPOINT_PERMISSIONS.put("/teleport", "teleport");
        ENDPOINT_PERMISSIONS.put("/time", "time");
        ENDPOINT_PERMISSIONS.put("/weather", "weather");
        ENDPOINT_PERMISSIONS.put("/gamemode", "gamemode");
        ENDPOINT_PERMISSIONS.put("/give", "give");
        ENDPOINT_PERMISSIONS.put("/effect", "effect");
        ENDPOINT_PERMISSIONS.put("/schedule", "schedule");
        ENDPOINT_PERMISSIONS.put("/scheduled-tasks", "schedule");
        ENDPOINT_PERMISSIONS.put("/cancel-task", "schedule");
        ENDPOINT_PERMISSIONS.put("/config", "admin.config");
        ENDPOINT_PERMISSIONS.put("/config/reload", "admin.reload");
        ENDPOINT_PERMISSIONS.put("/status", "status");
        ENDPOINT_PERMISSIONS.put("/tps", "status");
        ENDPOINT_PERMISSIONS.put("/memory", "status");
        ENDPOINT_PERMISSIONS.put("/uptime", "status");
        ENDPOINT_PERMISSIONS.put("/diagnose", "status");
        ENDPOINT_PERMISSIONS.put("/players", "players");
        ENDPOINT_PERMISSIONS.put("/logs", "logs");
        ENDPOINT_PERMISSIONS.put("/plugins", "plugins");
        ENDPOINT_PERMISSIONS.put("/worlds", "worlds");
        ENDPOINT_PERMISSIONS.put("/chat", "chat");
        ENDPOINT_PERMISSIONS.put("/chat/latest", "chat");
        ENDPOINT_PERMISSIONS.put("/restart", "restart");
        ENDPOINT_PERMISSIONS.put("/keys", "admin.*");
        ENDPOINT_PERMISSIONS.put("/batch/command", "command.execute");
        ENDPOINT_PERMISSIONS.put("/batch/kick", "kick");
        ENDPOINT_PERMISSIONS.put("/batch/give", "give");
        ENDPOINT_PERMISSIONS.put("/error-test", "admin.*");
    }

    public ConfigManager(Relink plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.port = config.getInt("api.port", 9178);

        // Load keys section
        keys.clear();
        ConfigurationSection keysSection = config.getConfigurationSection("api.keys");
        if (keysSection != null) {
            for (String keyValue : keysSection.getKeys(false)) {
                ConfigurationSection ks = keysSection.getConfigurationSection(keyValue);
                if (ks == null) continue;
                ApiKeyConfig ak = new ApiKeyConfig(keyValue);
                ak.type = ks.getString("type", "static");
                ak.name = ks.getString("name", "");
                ak.permissions = ks.getStringList("permissions");
                ak.ipWhitelist = ks.getStringList("ipWhitelist");
                ak.rateLimit = ks.getInt("rateLimit", 60);
                ak.expireAt = ks.getString("expireAt", null);
                ak.used = ks.getBoolean("used", false);
                keys.put(keyValue, ak);
            }
        }

        // Also try the old single-key format for backward compatibility
        String oldKey = config.getString("api.key");
        if (oldKey != null && !oldKey.isEmpty() && !keys.containsKey(oldKey)) {
            ApiKeyConfig ak = new ApiKeyConfig(oldKey);
            ak.name = "默认密钥（旧格式）";
            ak.permissions = Collections.singletonList("admin.*");
            keys.put(oldKey, ak);
        }

        this.defaultKey = keys.isEmpty() ? "change-me-to-a-secure-key" : keys.keySet().iterator().next();

        if (keys.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, "No API keys configured! Using default.");
        }
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public int getPort() { return port; }

    /** Get the first available key (backward compatibility). */
    public String getApiKey() { return defaultKey; }

    /** Get all configured keys. */
    public Map<String, ApiKeyConfig> getKeys() { return Collections.unmodifiableMap(keys); }

    /** Get config for a specific key. */
    public ApiKeyConfig getKeyConfig(String key) { return keys.get(key); }

    /** Get the rate limiter instance. */
    public RateLimiter getRateLimiter() { return rateLimiter; }

    /**
     * Check if a key has permission for the given path.
     * Supports wildcard: "admin.*" matches everything.
     */
    public boolean hasPermission(ApiKeyConfig keyConfig, String path) {
        String required = ENDPOINT_PERMISSIONS.get(path);
        if (required == null) return false;

        List<String> perms = keyConfig.permissions;
        if (perms.contains("admin.*")) return true;

        // Direct match
        if (perms.contains(required)) return true;

        // Wildcard group match, e.g. "command.*" matches "command.execute"
        String group = required.contains(".") ? required.substring(0, required.indexOf('.')) : required;
        String groupWildcard = group + ".*";
        if (perms.contains(groupWildcard)) return true;

        // Also check "status.*" for specific status endpoints
        for (String perm : perms) {
            if (perm.endsWith(".*")) {
                String permGroup = perm.substring(0, perm.length() - 2);
                if (required.startsWith(permGroup + ".") || required.equals(permGroup)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a key has expired (timed keys only).
     */
    public boolean isExpired(ApiKeyConfig keyConfig) {
        if (!"timed".equals(keyConfig.type) || keyConfig.expireAt == null) return false;
        try {
            Instant expire = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(keyConfig.expireAt));
            return Instant.now().isAfter(expire);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if an IP matches an IP whitelist entry.
     * Supports exact, wildcard (192.168.1.*), and CIDR (192.168.1.0/24).
     */
    public boolean ipMatchesWhitelist(String ip, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) return true; // empty = allow all
        for (String entry : whitelist) {
            if (entry.equals(ip)) return true;
            // Wildcard: 192.168.1.*
            if (entry.endsWith(".*")) {
                String prefix = entry.substring(0, entry.length() - 2);
                if (ip.startsWith(prefix)) return true;
            }
            // CIDR: simple /24 only
            if (entry.contains("/")) {
                String[] parts = entry.split("/");
                if (parts.length == 2) {
                    try {
                        int mask = Integer.parseInt(parts[1]);
                        if (mask == 24) {
                            String netPrefix = parts[0].substring(0, parts[0].lastIndexOf('.'));
                            if (ip.startsWith(netPrefix + ".")) return true;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return false;
    }
}
