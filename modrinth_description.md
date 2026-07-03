# RelinkPlugins — HTTP API 远程控制你的 Minecraft 服务器

一个通过 HTTP API 远程控制 Minecraft 服务器的 Paper/Spigot 插件，兼容 1.8.9 ~ 最新版。

## 功能亮点

- **统一返回值格式** — 所有端点返回标准 JSON `{success, code, message, data}`
- **精细权限管理** — 多 API Key，每种 Key 独立配置权限、IP 白名单、频率限制
- **批量操作** — 一次请求执行多条命令、踢出多个玩家、批量给予物品
- **定时任务** — 延时/循环执行指令
- **实时状态** — TPS、内存、在线玩家、延迟等
- **玩家管理** — 踢出、传送、游戏模式、给予、效果
- **聊天系统** — 向游戏内发消息，获取聊天记录
- **全异步** — 命令执行不阻塞 HTTP 线程
- **跨版本兼容** — Paper/Spigot 1.8.9 ~ 最新版，Java 8+

## 30+ 端点

| 类别 | 端点 |
|------|------|
| 执行 | `/command`, `/exec`, `/broadcast`, `/kick`, `/teleport` |
| 世界 | `/time`, `/weather`, `/worlds` |
| 玩家 | `/players`, `/gamemode`, `/give`, `/effect` |
| 状态 | `/status`, `/tps`, `/memory`, `/uptime`, `/diagnose` |
| 日志 | `/logs`, `/plugins` |
| 配置 | `/config`, `/keys` |
| 聊天 | `/chat`, `/chat/latest` |
| 调度 | `/schedule`, `/scheduled-tasks`, `/cancel-task` |
| 批量 | `/batch/command`, `/batch/kick`, `/batch/give` |
| 管理 | `/keys`, `/restart`, `/error-test` |

## 调用示例

```bash
curl -X POST http://localhost:9178/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: your-key' \
  -d '{"command":"say Hello"}'
```

## 安装

1. 下载 jar 放入 `plugins/`
2. 启动服务器，编辑 `plugins/RelinkPlugins/config.yml`
3. 配置你的 API Key
4. 执行 `/relink reload`

## 技术细节

- 编译目标 Java 8，兼容 Java 8 ~ 21+
- 零外部依赖，通过 JDK 内置 `com.sun.net.httpserver` 实现 HTTP
- 命令同步端点（`/exec`）使用 `CountDownLatch` + Bukkit Scheduler
- Chat 消息轮询基于环形缓冲区，上限 200 条
- TPS 通过反射获取，Spigot 1.12 以下优雅降级

## 开发者

HAAVK Group / 哈夫克集团

---

# RelinkPlugins

**HTTP API remote control for your Minecraft server.**

A Paper/Spigot plugin for remote Minecraft server control via HTTP API, compatible with 1.8.9 ~ latest, Java 8+.

## Highlights

- **Unified JSON Response** — All endpoints return `{success, code, message, data}`
- **Granular Permission System** — Multi-key auth with independent permissions, IP whitelist, rate limiting
- **Batch Operations** — Execute commands, kick players, give items in a single request
- **Scheduled Tasks** — Delayed or recurring command execution
- **Real-time Status** — TPS, memory, online players, uptime, etc.
- **Player Management** — Kick, teleport, gamemode, give, effects
- **Chat System** — Send messages to game chat, poll chat history
- **Fully Async** — Command execution never blocks HTTP threads
- **Cross-Version Compatible** — Paper/Spigot 1.8.9 ~ latest, Java 8+

## 30+ Endpoints

| Category | Endpoints |
|----------|-----------|
| Execution | `/command`, `/exec`, `/broadcast`, `/kick`, `/teleport` |
| World | `/time`, `/weather`, `/worlds` |
| Player | `/players`, `/gamemode`, `/give`, `/effect` |
| Status | `/status`, `/tps`, `/memory`, `/uptime`, `/diagnose` |
| Logs | `/logs`, `/plugins` |
| Config | `/config`, `/keys` |
| Chat | `/chat`, `/chat/latest` |
| Schedule | `/schedule`, `/scheduled-tasks`, `/cancel-task` |
| Batch | `/batch/command`, `/batch/kick`, `/batch/give` |
| Admin | `/keys`, `/restart`, `/error-test` |

## Installation

1. Drop the jar into `plugins/`
2. Start the server, edit `plugins/RelinkPlugins/config.yml`
3. Configure your API keys
4. Run `/relink reload`

## Tech Details

- Java 8 bytecode target, compatible Java 8 ~ 21+
- Zero external dependencies, uses JDK built-in `com.sun.net.httpserver`
- Sync endpoint (`/exec`) uses `CountDownLatch` + Bukkit Scheduler
- Chat polling via ring buffer (200 cap)
- TPS fetched via reflection, gracefully degrades on Spigot <1.12

## Developer

HAAVK Group / 哈夫克集团
