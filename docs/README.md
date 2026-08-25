# Codex Java SDK 中文教程

这套教程面向希望在 Java 应用、服务端任务系统、IDE 插件或自动化平台中嵌入 Codex 的开发者。SDK 基于 Maven 和 JDK 25，通过标准输入输出连接本地 `codex app-server`，使用 JSONL 承载双向 JSON-RPC v2 消息。

## 阅读路线

第一次使用时，建议按顺序阅读前六章：

1. [概览、环境准备与安装](01-overview-and-installation.md)
2. [快速入门：运行第一个 Codex 任务](02-quickstart.md)
3. [客户端、线程与 Turn 配置](03-configuration.md)
4. [Thread 生命周期管理](04-thread-lifecycle.md)
5. [执行 Turn 与处理结果](05-turns-and-results.md)
6. [流式事件与 Flow API](06-streaming-events.md)

需要进阶能力时按主题阅读：

7. [文本、图片、Skill 与结构化输出](07-inputs-and-structured-output.md)
8. [审批、安全与沙箱](08-approvals-and-security.md)
9. [异步调用、虚拟线程与并发](09-async-and-concurrency.md)
10. [使用原始 JSON-RPC 扩展 SDK](10-raw-json-rpc.md)
11. [异常处理与故障排查](11-errors-and-troubleshooting.md)
12. [生产环境最佳实践](12-production-practices.md)
13. [公开 API 速查](13-api-reference.md)
14. [Solon Web SSE 多会话完整案例](14-solon-sse-complete-example.md)
15. [从 app-server Schema 生成 Java 类型](15-schema-code-generation.md)

## 当前版本能力

当前 Java SDK 已提供：

- 启动、初始化和关闭 `codex app-server` 子进程；
- JDK 25 虚拟线程驱动的读写、事件与审批处理；
- 同步 JSON-RPC 请求和 `CompletableFuture` 异步请求；
- Thread 的创建、恢复、分叉、强类型分页列表、归档、取消归档、读取、命名和压缩；
- Turn 的普通执行、流式事件、追加指令和中断；
- 完整的 v2 Thread/Turn 配置以及细粒度沙箱、审批配置；
- 从仓库 app-server Schema 自动生成的 Java record/枚举；
- JSON-RPC 细分异常和带抖动指数退避的过载自动重试；
- 已知通知的 `CodexEventType` 枚举和未知事件兜底；
- 文本、远程图片、本地图片、Skill 和 Mention 输入；
- JSON Schema 结构化输出；
- 强类型命令/文件审批、工具前后拦截器和生命周期观察器；
- 面向 Web UI 的跨平台命令事件、实时终端输出和异步人工审批案例；
- 原始 JSON-RPC 入口，用于尚未添加便捷方法的 app-server API。

生成层目前覆盖 Java SDK 公开便捷 API 使用的核心 v2 类型，而不是把 500 多个 Schema
定义全部暴露为公共 API。登录、账户等尚未包装的方法仍可通过
`CodexClient.request(...)` 和 `requestAsync(...)` 调用；需要扩展生成范围时参见第 15 章。

## 示例约定

教程默认使用以下 import：

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.*;
import io.github.majiajustar.codex.event.*;
import io.github.majiajustar.codex.exception.*;
import io.github.majiajustar.codex.generated.v2.*;
import io.github.majiajustar.codex.model.*;
import io.github.majiajustar.codex.sandbox.*;
import io.github.majiajustar.codex.thread.*;
import io.github.majiajustar.codex.tool.*;
import io.github.majiajustar.codex.turn.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
```

示例中的 `/path/to/project` 应替换成实际项目的绝对路径。Windows 用户可以使用：

```java
Path.of("D:/code/my-project")
```

除非章节明确说明，示例都假设已经安装并登录 Codex CLI。

---

下一章：[概览、环境准备与安装](01-overview-and-installation.md)
