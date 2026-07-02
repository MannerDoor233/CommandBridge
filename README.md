# CommandBridge

一个 Minecraft Paper 服务端插件，通过 HTTP API 远程控制服务器。

## 功能

| 端点 | 方法 | 说明 |
|------|------|------|
| `/command` | POST | 执行任意指令（异步） |
| `/exec` | POST | 执行指令并返回 dispatch 状态 |
| `/status` | GET | 服务器状态（TPS、内存、玩家数） |
| `/chat` | POST | 向游戏内发送消息 |
| `/chat/latest` | GET | 获取最新聊天消息 |
| `/restart` | POST | 重启服务器 |
| `/logs` | GET | 查看控制台日志 |
| `/players` | GET | 在线玩家列表 |
| `/tps` | GET | TPS 报告 |

## 安装

1. 将 `CommandBridge.jar` 放入 `plugins/` 目录
2. 启动服务器，生成 `plugins/CommandBridge/config.yml`
3. 修改 `config.yml` 中的端口和密钥：

```yaml
api:
  port: 9178           # HTTP 监听端口
  key: "your-secure-key"  # X-API-Key 鉴权
```

4. 执行 `/commandbridge reload` 或重启服务器

## 调用示例

```bash
# 执行指令
curl -X POST http://localhost:9178/command \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: your-secure-key' \
  -d '{"command":"say Hello"}'

# 查询状态
curl http://localhost:9178/status \
  -H 'X-API-Key: your-secure-key'

# 发送消息
curl -X POST http://localhost:9178/chat \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: your-secure-key' \
  -d '{"message":"你好，世界","target":"@a"}'
```

## 构建

```bash
mvn clean package
# 输出在 target/CommandBridge-*.jar
```

## 依赖

- Paper 1.21+（或兼容的 Folia / Leaves 服务端）
- Java 21+
- Maven 3.8+

## 许可证

AGPL v3。详见 [LICENSE](LICENSE)。
