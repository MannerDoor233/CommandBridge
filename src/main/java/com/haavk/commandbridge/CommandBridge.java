package com.haavk.commandbridge;

import com.haavk.commandbridge.api.ApiServer;
import com.haavk.commandbridge.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class CommandBridge extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private ApiServer apiServer;

    // 聊天消息缓冲区（线程安全，上限200条）
    private final List<ChatMessage> chatBuffer = new ArrayList<>();
    private int nextId = 0;

    /** 聊天消息记录 */
    public static class ChatMessage {
        public final int id;
        public final long timestamp;
        public final String player;
        public final String message;

        public ChatMessage(int id, long timestamp, String player, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.player = player;
            this.message = message;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);

        int port = configManager.getPort();
        String apiKey = configManager.getApiKey();

        if (apiKey == null || apiKey.isEmpty() || "change-me-to-a-secure-key".equals(apiKey)) {
            getLogger().log(Level.WARNING, "API key is set to the default value! Please change it in config.yml");
        }

        // 注册聊天事件监听
        getServer().getPluginManager().registerEvents(this, this);

        apiServer = new ApiServer(this, configManager);
        try {
            apiServer.start(port);
            getLogger().log(Level.INFO, "CommandBridge HTTP API started on port " + port);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start HTTP API server on port " + port, e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (apiServer != null) {
            apiServer.stop();
            getLogger().log(Level.INFO, "CommandBridge HTTP API stopped.");
        }
        synchronized (chatBuffer) {
            chatBuffer.clear();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage();
        long now = System.currentTimeMillis();

        synchronized (chatBuffer) {
            int id = nextId++;
            chatBuffer.add(new ChatMessage(id, now, player.getName(), msg));
            // 保留最近 200 条
            while (chatBuffer.size() > 200) {
                chatBuffer.remove(0);
            }
        }
    }

    /**
     * 获取自 sinceId 之后的新聊天消息（包含 sinceId 自身）。
     * 若 sinceId < 0 则返回全部。
     */
    public List<ChatMessage> getChatMessagesSince(int sinceId) {
        synchronized (chatBuffer) {
            if (chatBuffer.isEmpty()) return List.of();
            List<ChatMessage> result = new ArrayList<>();
            for (ChatMessage cm : chatBuffer) {
                if (cm.id > sinceId) {
                    result.add(cm);
                }
            }
            return result;
        }
    }

    /** 获取最新聊天消息ID（用于下次轮询） */
    public int getLatestChatId() {
        synchronized (chatBuffer) {
            return nextId - 1;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("commandbridge")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                configManager.reload();

                if (apiServer != null) {
                    apiServer.stop();
                }

                int port = configManager.getPort();
                try {
                    apiServer = new ApiServer(this, configManager);
                    apiServer.start(port);
                    sender.sendMessage("§a[CommandBridge] Config reloaded. API server restarted on port " + port);
                } catch (Exception e) {
                    sender.sendMessage("§c[CommandBridge] Failed to restart API server: " + e.getMessage());
                }
                return true;
            }
            sender.sendMessage("§6[CommandBridge] Usage: /commandbridge reload");
            return true;
        }
        return false;
    }
}
