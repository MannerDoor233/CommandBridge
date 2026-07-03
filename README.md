# RelinkPlugins

一个通过 HTTP API 远程控制 Minecraft 服务器的 Paper/Spigot 插件。兼容 Paper/Spigot 1.8.9 ~ 最新版，Java 8+。

---

## 功能

- **命令执行** — 执行任意控制台指令（异步或同步带结果）
- **批量操作** — 一次请求执行多条命令、踢出多个玩家、给予多个物品
- **服务器状态** — TPS、内存、在线玩家、世界、运行时间、插件列表
- **时间与天气** — 设置世界时间和天气
- **玩家管理** — 踢出、传送、游戏模式、给予、效果
- **聊天系统** — 向游戏内广播消息，获取游戏内聊天记录
- **定时任务** — 延时执行或定时循环执行指令
- **API Key 管理** — 多 Key 鉴权，支持细粒度权限、IP 白名单、频率限制
- **日志** — 通过 API 浏览服务端日志
- **诊断** — 综合服务器诊断
- **统一返回值** — 所有端点返回 `{success, code, message, data}` 标准格式

## 全部 API 端点

### 管理与配置

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/config` | GET | 查看插件配置 | `admin.config` |
| `/keys` | GET | 列出 API Key（脱敏） | `admin.*` |
| `/keys` | POST | 创建新的 API Key | `admin.*` |
| `/keys/{key}` | DELETE | 删除指定 Key | `admin.*` |
| `/keys/{key}/renew` | POST | 续期过期 Key | `admin.*` |
| `/error-test` | GET | 测试错误响应 | `admin.*` |

### 命令执行

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/command` | POST | 异步执行指令 | `command.execute` |
| `/exec` | POST | 同步执行指令（返回结果） | `command.execute` |
| `/batch/command` | POST | 批量执行指令 | `command.execute` |

### 玩家操作

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/broadcast` | POST | 全服广播 | `broadcast` |
| `/kick` | POST | 踢出玩家 | `kick` |
| `/teleport` | POST | 传送玩家 | `teleport` |
| `/gamemode` | POST | 设置游戏模式 | `gamemode` |
| `/give` | POST | 给予物品 | `give` |
| `/effect` | POST | 应用效果 | `effect` |
| `/batch/kick` | POST | 批量踢出 | `kick` |
| `/batch/give` | POST | 批量给予 | `give` |

### 世界与时间

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/time` | POST | 设置世界时间 | `time` |
| `/weather` | POST | 设置天气 | `weather` |
| `/worlds` | GET | 世界信息 | `worlds` |

### 状态与监控

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/status` | GET | 服务器状态 | `status` |
| `/tps` | GET | TPS 报告 | `status` |
| `/memory` | GET | 内存详情 | `status` |
| `/uptime` | GET | 运行时间 | `status` |
| `/diagnose` | POST | 综合诊断 | `status` |
| `/players` | GET | 在线玩家列表 | `players` |
| `/plugins` | GET | 插件列表 | `plugins` |
| `/logs` | GET | 服务端日志 | `logs` |

### 聊天

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/chat` | GET | 获取增量聊天消息 | `chat` |
| `/chat` | POST | 向游戏内发送消息 | `chat` |
| `/chat/latest` | GET | 获取最新消息 ID | `chat` |

### 定时任务与服务器

| 端点 | 方法 | 说明 | 所需权限 |
|------|------|------|----------|
| `/schedule` | POST | 创建定时任务 | `schedule` |
| `/scheduled-tasks` | GET | 列出定时任务 | `schedule` |
| `/cancel-task` | POST | 取消定时任务 | `schedule` |
| `/restart` | POST | 重启服务器 | `restart` |

## 安装

1. 将 `RelinkPlugins-*.jar` 放入服务端 `plugins/` 目录
2. 启动服务器，配置文件自动生成于 `plugins/RelinkPlugins/config.yml`
3. 编辑 `config.yml` 配置端口和 API Key：

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

4. 执行 `/relink reload` 或重启服务器

## 调用示例

```bash
# 执行指令
curl -X POST http://localhost:9178/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: my-admin-key' \
  -d '{"command":"say Hello"}'

# 批量执行
curl -X POST http://localhost:9178/batch/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: my-admin-key' \
  -d '{"commands":["say 重启中","save-all"],"stopOnError":false}'

# 查看状态
curl http://localhost:9178/status -H 'X-API-Key: my-admin-key'

# 踢出玩家
curl -X POST http://localhost:9178/kick \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: my-admin-key' \
  -d '{"player":"Notch","reason":"维护中"}'
```

## 返回值格式

所有端点返回统一 JSON 格式：

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

错误示例：
```json
{
  "success": false,
  "code": 400,
  "message": "缺少必要参数: command",
  "data": null
}
```

## 权限节点

| 权限节点 | 覆盖端点 |
|----------|----------|
| `admin.*` | 所有端点 |
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

通配符：`admin.*` 授予所有权限，`command.*` 授予所有命令相关端点。

## 构建

```bash
mvn clean package
# 输出: target/RelinkPlugins-*.jar
```

## 依赖要求

- Paper 或 Spigot 1.8.9+
- Java 8+
- Maven 3.8+

## 开发者

HAAVK Group / 哈夫克集团

## 许可证

MIT。详见 [LICENSE](LICENSE)。

---

# RelinkPlugins

**A Minecraft Paper/Spigot plugin that controls your server via HTTP API.**

Compatible with Paper/Spigot **1.8.9 ~ latest**, Java 8+.

## Features

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

## All API Endpoints

### Admin & Config

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/config` | GET | View plugin config | `admin.config` |
| `/keys` | GET | List API keys (masked) | `admin.*` |
| `/keys` | POST | Create a new API key | `admin.*` |
| `/keys/{key}` | DELETE | Delete an API key | `admin.*` |
| `/keys/{key}/renew` | POST | Renew an expired key | `admin.*` |
| `/error-test` | GET | Test error responses | `admin.*` |

### Command Execution

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/command` | POST | Execute command (async) | `command.execute` |
| `/exec` | POST | Execute command (sync, with result) | `command.execute` |
| `/batch/command` | POST | Execute multiple commands | `command.execute` |

### Player Operations

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/broadcast` | POST | Broadcast message | `broadcast` |
| `/kick` | POST | Kick a player | `kick` |
| `/teleport` | POST | Teleport a player | `teleport` |
| `/gamemode` | POST | Set gamemode | `gamemode` |
| `/give` | POST | Give item(s) | `give` |
| `/effect` | POST | Apply effect | `effect` |
| `/batch/kick` | POST | Kick multiple players | `kick` |
| `/batch/give` | POST | Give items to multiple players | `give` |

### World & Time

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/time` | POST | Set world time | `time` |
| `/weather` | POST | Set weather | `weather` |
| `/worlds` | GET | World information | `worlds` |

### Status & Monitoring

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/status` | GET | Server status | `status` |
| `/tps` | GET | TPS report | `status` |
| `/memory` | GET | Memory details | `status` |
| `/uptime` | GET | Server uptime | `status` |
| `/diagnose` | POST | Server diagnosis | `status` |
| `/players` | GET | Online players | `players` |
| `/plugins` | GET | Plugin list | `plugins` |
| `/logs` | GET | Server logs | `logs` |

### Chat

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/chat` | GET | Get incremental chat messages | `chat` |
| `/chat` | POST | Send message to game chat | `chat` |
| `/chat/latest` | GET | Get latest message ID | `chat` |

### Scheduling & Server

| Endpoint | Method | Description | Required Permission |
|----------|--------|-------------|---------------------|
| `/schedule` | POST | Create scheduled task | `schedule` |
| `/scheduled-tasks` | GET | List scheduled tasks | `schedule` |
| `/cancel-task` | POST | Cancel a task | `schedule` |
| `/restart` | POST | Restart server | `restart` |

## Installation

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

## Examples

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

## Response Format

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

## Permission Nodes

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

## Build

```bash
mvn clean package
# Output: target/RelinkPlugins-*.jar
```

## Requirements

- Paper or Spigot 1.8.9+
- Java 8+
- Maven 3.8+

## Developer

HAAVK Group / 哈夫克集团

## License

MIT. See [LICENSE](LICENSE).
