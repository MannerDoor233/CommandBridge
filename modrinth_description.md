# RelinkPlugins — HTTP API 远程控制你的 Minecraft 服务器

> 一个通过 HTTP API 远程控制 Minecraft 服务器的 Paper/Spigot 插件，兼容 1.8.9 ~ 最新版。

**A Minecraft Paper/Spigot plugin that controls your server via HTTP API. Compatible 1.8.9 ~ latest.**

---

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
# 执行命令
curl -X POST http://localhost:9178/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: your-key' \
  -d '{"command":"say Hello"}'

# 批量踢出
curl -X POST http://localhost:9178/batch/kick \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: your-key' \
  -d '{"players":["Notch","Steve"],"reason":"维护中"}'

# 查看状态
curl http://localhost:9178/status -H 'X-API-Key: your-key'
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
