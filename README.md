# RelinkPlugins

**A Minecraft Paper/Spigot plugin that controls your server via HTTP API.**

**一个通过 HTTP API 远程控制 Minecraft 服务器的 Paper/Spigot 插件。**

Compatible with Paper/Spigot **1.8.9 ~ latest**, Java 8+.
兼容 **Paper/Spigot 1.8.9 ~ 最新版**，Java 8+。

## Features / 功能

- **Command Execution** — Execute any console command (async or sync with result)
- **Batch Operations** — Execute multiple commands, kick players, or give items in one request
- **Server Status** — TPS, memory, players, worlds, uptime, plugins
- **Time & Weather Control** — Set world time and weather
- **Player Management** — Kick, teleport, gamemode, give, effects
- **Chat** — Broadcast messages, ingest in-game chat
- **Scheduled Tasks** — Run commands after a delay or on a timer
- **API Key Management** — Multi-key with granular permissions, IP whitelist, rate limiting
- **Logs** — Browse server logs via API
- **Diagnose** — Comprehensive server diagnosis
- **Unified JSON Response** — All endpoints return `{success, code, message, data}`

## Endpoints / API 端点

| Endpoint | Method | Description / 说明 | Required Permission / 所需权限 |
|----------|--------|--------------------|-----------------------|
| `/status` | GET | Server status (TPS, memory, players) | `status` |
| `/players` | GET | Online player list | `players` |
| `/tps` | GET | TPS report | `status` |
| `/memory` | GET | Memory details | `status` |
| `/uptime` | GET | Server uptime | `status` |
| `/diagnose` | POST | Server diagnosis | `status` |
| `/logs` | GET | Server logs (query: `?lines=N`) | `logs` |
| `/plugins` | GET | Plugin list | `plugins` |
| `/worlds` | GET | World info | `worlds` |
| `/config` | GET | Plugin configuration | `admin.config` |
| `/command` | POST | Execute command (async) | `command.execute` |
| `/exec` | POST | Execute command (sync, with result) | `command.execute` |
| `/broadcast` | POST | Broadcast message | `broadcast` |
| `/kick` | POST | Kick a player | `kick` |
| `/teleport` | POST | Teleport player | `teleport` |
| `/time` | POST | Set world time | `time` |
| `/weather` | POST | Set weather | `weather` |
| `/gamemode` | POST | Set gamemode | `gamemode` |
| `/give` | POST | Give item | `give` |
| `/effect` | POST | Apply effect | `effect` |
| `/chat` | GET/POST | Send/receive chat messages | `chat` |
| `/chat/latest` | GET | Latest chat ID | `chat` |
| `/restart` | POST | Restart server | `restart` |
| `/schedule` | POST | Create scheduled task | `schedule` |
| `/scheduled-tasks` | GET | List scheduled tasks | `schedule` |
| `/cancel-task` | POST | Cancel a task | `schedule` |
| `/batch/command` | POST | Execute multiple commands | `command.execute` |
| `/batch/kick` | POST | Kick multiple players | `kick` |
| `/batch/give` | POST | Give items to multiple players | `give` |
| `/keys` | GET/POST | List/create API keys | `admin.*` |
| `/keys/{key}` | DELETE | Delete API key | `admin.*` |
| `/keys/{key}/renew` | POST | Renew expired key | `admin.*` |
| `/error-test` | GET | Test error responses (?type=400/401/403/...) | `admin.*` |

## Installation / 安装

1. Download `RelinkPlugins-*.jar` into your server's `plugins/` directory
2. Start the server — config is auto-generated at `plugins/RelinkPlugins/config.yml`
3. Edit `config.yml` to configure keys:

```yaml
api:
  port: 9178
  keys:
    my-admin-key:
      name: "管理员"
      type: static
      permissions: ["admin.*"]
      ipWhitelist: []
      rateLimit: 100
```

4. Run `/relink reload` or restart the server

## Examples / 调用示例

```bash
# Execute a command
curl -X POST http://localhost:9178/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: my-admin-key' \
  -d '{"command":"say Hello"}'

# Batch execute
curl -X POST http://localhost:9178/batch/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: my-admin-key' \
  -d '{"commands":["say 重启中","save-all"],"stopOnError":false}'

# Server status
curl http://localhost:9178/status -H 'X-API-Key: my-admin-key'

# Kick player
curl -X POST http://localhost:9178/kick \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: my-admin-key' \
  -d '{"player":"Notch","reason":"维护中"}'
```

## Response Format / 返回值格式

All endpoints return unified JSON:

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

Error example:
```json
{
  "success": false,
  "code": 400,
  "message": "缺少必要参数: command",
  "data": null
}
```

## Permission Nodes / 权限节点

| Permission | Covered Endpoints |
|------------|-------------------|
| `admin.*` | All endpoints |
| `admin.config` | `/config` |
| `admin.reload` | `/config/reload` |
| `command.*` | `/command`, `/exec`, `/batch/command` |
| `command.execute` | `/command`, `/exec`, `/batch/command` |
| `broadcast` | `/broadcast` |
| `kick` | `/kick`, `/batch/kick` |
| `teleport` | `/teleport` |
| `time` | `/time` |
| `weather` | `/weather` |
| `gamemode` | `/gamemode` |
| `give` | `/give`, `/batch/give` |
| `effect` | `/effect` |
| `schedule` | `/schedule`, `/scheduled-tasks`, `/cancel-task` |
| `restart` | `/restart` |
| `status` | `/status`, `/tps`, `/memory`, `/uptime`, `/diagnose` |
| `players` | `/players` |
| `logs` | `/logs` |
| `plugins` | `/plugins` |
| `worlds` | `/worlds` |
| `chat` | `/chat`, `/chat/latest` |

Wildcards: `admin.*` grants all, `command.*` grants all command-related endpoints.

## Build / 构建

```bash
mvn clean package
# Output: target/RelinkPlugins-*.jar
```

## Requirements / 依赖

- Paper or Spigot 1.8.9+
- Java 8+
- Maven 3.8+

## Developer / 开发者

HAAVK Group / 哈夫克集团

## License / 许可证

MIT。详见 [LICENSE](LICENSE)。
