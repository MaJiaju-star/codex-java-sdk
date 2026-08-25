# Codex Java SDK + Solon SSE 完整示例

这个示例演示如何用 JDK 25、Maven 和 Solon 4.0.3 构建一个可直接打开浏览器使用的 Codex 对话服务。它包含：

- 一个会话内的增量回答；
- 多会话创建、恢复和即时切换；
- 强类型 Codex Thread 搜索、游标分页、归档和取消归档；
- Personality、Service Tier、Reasoning Effort/Summary 页面配置；
- 按 `threadId` 读取持久 Turn 历史，刷新页面后与实时事件合并；
- 持久 Web SSE 连接与有界事件回放；
- 活动 Turn 中断；
- PowerShell、CMD、Bash 等命令、MCP、文件修改、Web Search 与 Token 用量监听；
- 命令标准输出/错误输出实时推送到浏览器；
- 浏览器内“允许一次 / 本会话允许 / 拒绝 / 取消”异步审批；
- Shell 命令路径的尽力解析；
- 工作目录递归变化监听；
- 细分 SDK 异常的 HTTP 状态、JSON-RPC code 和 retryable 响应；
- 明确过载错误的指数退避自动重试；
- 无第三方前端依赖的管理页面。

## 运行

先把同仓库 Java SDK 安装到本机 Maven 仓库：

```powershell
cd D:\code\claude-code-project\codex-main\sdk\java
mvn clean install
```

然后构建并启动示例：

```powershell
cd examples\solon-sse-chat
$env:CODEX_EXAMPLE_WORKSPACE_ROOT = "D:\code"
$env:CODEX_EXAMPLE_MODEL = "gpt-5.5"
mvn clean package
java -jar target\codex-solon-sse-chat.jar
```

打开 <http://localhost:8080/>。创建会话时，工作目录必须位于 `CODEX_EXAMPLE_WORKSPACE_ROOT` 下。若未设置该变量，允许的根目录是服务启动目录。

模型默认使用 `gpt-5.5`；如需切换，可通过 `CODEX_EXAMPLE_MODEL` 覆盖。

页面左侧“Codex 历史”读取 app-server 持久 Thread，而“会话”只表示当前 Solon
进程中的活动 `ChatSession`。归档会关闭关联的网页会话；取消归档后点击“恢复”才会
重新建立 SSE、目录监听和内存状态。

## 新增 Thread 管理 API

```text
GET  /api/codex/threads?archived=false&limit=20&searchTerm=...
POST /api/codex/threads/archive
POST /api/codex/threads/unarchive
```

归档和取消归档请求体：

```json
{"threadId":"019..."}
```

创建/恢复会话还可以传递：

```json
{
  "name": "支付模块",
  "workspace": "codex-main",
  "threadId": "",
  "personality": "pragmatic",
  "serviceTier": "priority"
}
```

发送消息可以覆盖本 Turn 的推理配置：

```json
{
  "sessionId": "网页会话 ID",
  "message": "检查并修复测试",
  "reasoningEffort": "high",
  "reasoningSummary": "detailed"
}
```

SDK 异常被转换为 `error/type/code/retryable` 四个字段。`retryable=true` 只是上层重试
提示；对创建、发送消息等操作仍应结合幂等键和业务状态判断，不能盲目重复提交。

完整讲解见：[Solon SSE 多会话完整案例](../../docs/14-solon-sse-complete-example.md)。

## 重要安全说明

示例使用 `workspace-write` 沙箱和 `on-request` 审批策略。审批请求通过 SSE 发到当前
网页会话，服务端 `ApprovalHandler` 会异步等待用户决定，9 分钟未处理则自动取消。
`BashCommandMonitor` 仅用于跨 Shell 的尽力展示，不能替代 Codex 沙箱、操作系统权限、
容器隔离或服务端 `ToolInterceptor` 策略。示例还会截断单个超大输出增量并遮蔽常见
`token/password/secret` 赋值，但这只是降低误泄露风险，不是完整的数据防泄漏方案。
