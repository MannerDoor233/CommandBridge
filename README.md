# RelinkPlugins

一个 Minecraft Paper/Spigot 服务端插件，通过 HTTP API 远程控制服务器。

兼容 **Paper/Spigot 1.8.9 ~ 最新版**，Java 8+。

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
| `/diagnose` | GET | 诊断信息 |
| `/config` | GET | 配置文件（控制部） |
| `/broadcast` | POST | 全服广播（执行部） |
| 等多达 20+ 端点 | | 三部制路由 |

## 安装

1. 下载 `RelinkPlugins-1.0.2.jar` 放入 `plugins/` 目录
2. 启动服务器，自动生成 `plugins/RelinkPlugins/config.yml`
3. 编辑 `config.yml` 设置端口和 API 密钥：

```yaml
api:
  port: 9178           # HTTP 监听端口
  key: "your-secure-key"  # X-API-Key 鉴权
```

4. 执行 `/relink reload` 或重启服务器

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
# 输出在 target/RelinkPlugins-*.jar
```

## 依赖

- Paper 或 Spigot 1.8.9+ 
- Java 8+
- Maven 3.8+

## 开发者

HAAVK Group / 哈夫克集团

## 许可证

MIT。详见 [LICENSE](LICENSE)。
