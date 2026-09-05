# 01. 概览、环境准备与安装

[返回目录](README.md) · [下一章：快速入门](02-quickstart.md)

## 1. SDK 如何工作

Java SDK 不直接调用远程模型 HTTP API。它启动本地 Codex CLI 的 app-server 模式，然后通过进程标准输入输出交换 JSONL 消息：

```text
Java 应用
   │
   │ CodexClient / JSON-RPC 请求
   ▼
codex app-server 子进程
   │
   ├── 读取项目文件
   ├── 执行命令和修改文件
   ├── 调用模型与工具
   └── 持久化 Thread 会话
```

每行都是一个独立 JSON 对象。连接建立后，SDK 自动发送 `initialize` 请求，再发送 `initialized` 通知。应用通常不需要手工处理握手。

SDK 中的核心概念如下：

| 概念 | Java 类型 | 含义 |
|---|---|---|
| Client | `CodexClient` | 一个 app-server 子进程和一条 JSON-RPC 连接 |
| Thread | `CodexThread` | 可持续多轮交互的会话 |
| Turn | `CodexTurn` | Thread 中的一次用户输入及其完整执行过程 |
| Item | `JsonNode` | 消息、推理、命令、文件修改等 Turn 内容 |
| Event | `CodexEvent` | app-server 推送的流式通知 |
| Result | `TurnResult` | Turn 完成后汇总的状态、回答、Items 和用量 |

## 2. 环境要求

本模块要求：

- JDK 25；
- Maven 3.9 或更高版本；
- 可用的 Codex CLI；
- Codex 已完成登录，或环境中存在可用的认证配置。

检查版本：

```shell
java -version
javac -version
mvn -version
codex --version
```

`pom.xml` 使用 Maven Enforcer 将 Java 版本限制为 `[25,26)`。如果 Maven 实际使用了其他 JDK，即使终端中的 `java` 是 25，构建也会失败。请以 `mvn -version` 输出中的 `Java version` 为准。

## 3. 从当前源码构建 SDK

Java SDK 当前位于仓库的 `sdk/java`：

```shell
cd sdk/java
mvn clean test
```

安装到本机 Maven 仓库：

```shell
mvn clean install
```

安装完成后，其他 Maven 项目可以引用：

```xml
<dependency>
  <groupId>io.github.majiajustar</groupId>
  <artifactId>codex-java-sdk</artifactId>
  <version>0.0.5-SNAPSHOT</version>
</dependency>
```

这是源码开发版本号，并不表示已经发布到 Maven Central。如果没有先执行 `mvn install`，独立项目将无法解析这个依赖。

## 4. 准备 Codex CLI

默认情况下，SDK 会执行：

- Windows：`cmd.exe /d /c codex app-server`
- Linux/macOS：`codex app-server`

先检查命令能否被找到：

```shell
codex app-server --help
```

如果 CLI 不在 `PATH`，在 Java 中传入明确路径：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .codexExecutable(Path.of("D:/tools/codex.exe"))
        .build();

try (CodexClient codex = CodexClient.create(config)) {
    System.out.println(codex.metadata());
}
```

也可以完全覆盖启动命令。例如通过一个包装脚本启动：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .command(List.of(
                "powershell.exe",
                "-NoProfile",
                "-File",
                "D:/tools/start-codex.ps1"))
        .build();
```

自定义命令最终必须启动兼容的 app-server，并使用 stdin/stdout 交换逐行 JSON。不要让包装脚本向 stdout 输出普通日志，否则 SDK 会把日志当作 JSON-RPC 消息解析。日志应写入 stderr。

## 5. 认证准备

默认使用内置 OpenAI Provider 时，Java SDK 会复用 Codex CLI 已有的认证状态。最简单的方式是在
终端中先完成登录，然后再启动 Java 应用。

如果应用连接内网 OpenAI-compatible 服务，不应依赖本机登录态，也不要认为单独设置
`OPENAI_API_KEY` 必然覆盖 `auth.json`。应注册一个明确从环境变量读取密钥、且不要求 OpenAI
登录的 Provider：

```java
OpenAiCompatibleProviderConfig internal = OpenAiCompatibleProviderConfig.builder("internal")
        .name("Internal Codex")
        .baseUrl(URI.create("https://codex-api.internal/v1"))
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .openAiCompatibleProvider(internal)
        .apiKey(System.getenv("INTERNAL_CODEX_API_KEY"))
        .build();
```

该 API 固定生成 `wire_api = "responses"`、`env_key = "OPENAI_API_KEY"` 和
`requires_openai_auth = false`。本机登录态仍可供其他账户相关能力使用，但不会参与这个
Provider 的模型请求认证。完整配置、定制环境变量名和优先级见[第三章](03-configuration.md)。

SDK 不会读取或打印 API Key。不要把密钥直接写入源码、日志或提交到版本库；生产环境应从
密钥管理系统注入。

## 6. 验证连接

下面的最小程序只启动并初始化 app-server，不执行 Turn：

```java
import io.github.majiajustar.codex.CodexClient;

public final class ConnectionCheck {
    public static void main(String[] args) {
        try (CodexClient codex = CodexClient.create()) {
            System.out.println("app-server metadata:");
            System.out.println(codex.metadata().toPrettyString());
        }
    }
}
```

`metadata()` 是 `initialize` 响应，可能包含 `userAgent`、`codexHome`、`platformFamily` 和 `platformOs`。具体字段取决于 app-server 版本，因此返回类型是 `JsonNode`。

## 7. 版本兼容原则

app-server 协议会继续演进。当前 SDK 的便捷方法只读取完成操作所需的稳定字段，例如 `thread.id` 和 `turn.id`，其余响应保留为 `JsonNode`，以减少协议增加字段时的破坏性影响。

生产环境建议：

- 固定并记录 Codex CLI 版本；
- 在升级 CLI 前运行 SDK 集成测试；
- 对使用原始 JSON-RPC 的字段进行显式存在性检查；
- 不假设所有版本返回完全相同的可选字段。

---

[返回目录](README.md) · [下一章：快速入门](02-quickstart.md)
