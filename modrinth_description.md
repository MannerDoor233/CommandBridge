# RelinkPlugins

一个通过 HTTP API 远程控制 Minecraft 服务器的 Paper/Spigot 插件。内嵌 JDK 原生 HttpServer，零外部依赖，单 jar 即装即用。安装后通过 HTTP 请求即可执行游戏指令、查询状态、管理玩家、调度定时任务，无需 SSH、RCON 或第三方面板。

---

### 功能

- 异步/同步执行服务器指令（支持批量）
- 服务器状态查询：TPS、内存、玩家、版本、运行时间
- 在线玩家详情：坐标、血量、饱食度、Ping、IP
- 控制台日志查看
- 全服广播（支持颜色代码）
- 踢出、传送、设置时间/天气/游戏模式
- 给予物品、应用状态效果
- 定时任务调度（延时单次 + 循环）
- 聊天消息轮询
- 重启服务器
- 20+ API 端点
- 单 jar 覆盖 Paper/Spigot 1.8.9 ~ 最新版，Java 8+

### 安装

放入 plugins/，启动服务器，编辑 config.yml 设置端口和 API 密钥。执行 `/relink reload` 生效。

### 技术细节

详见 GitHub：https://github.com/MannerDoor233/Relink

### 许可证

MIT

### 开发者

HAAVK Group / 哈夫克集团

---

---

# RelinkPlugins

A Paper/Spigot plugin that lets you control your Minecraft server entirely over HTTP. Built on JDK's built-in HttpServer with zero external dependencies — a single JAR, drop-in installation, no SSH/RCON/panel needed.

---

### Features

- Async & sync command execution (batch support)
- Server status: TPS, memory, online players, version, uptime
- Player details: location, health, food, ping, IP
- Console logs
- Broadcast with color code support
- Kick, teleport, time/weather/gamemode control
- Give items, apply potion effects
- Scheduled tasks (delayed + repeating)
- Chat message polling
- Server restart
- 20+ API endpoints
- Single JAR, Paper/Spigot 1.8.9 ~ latest, Java 8+

### Installation

Drop into plugins/, start server, edit config.yml for port & API key. Run `/relink reload` to apply.

### Technical Details

See GitHub: https://github.com/MannerDoor233/Relink

### License

MIT

### Developer

HAAVK Group
