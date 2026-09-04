# 02. 快速入门：运行第一个 Codex 任务

[上一章](01-overview-and-installation.md) · [返回目录](README.md) · [下一章：配置](03-configuration.md)

## 1. 创建示例 Maven 项目

假设已经在 SDK 目录执行过 `mvn install`。新建普通 Maven 项目：

```text
codex-java-demo/
├── pom.xml
└── src/main/java/example/Main.java
```

`pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>example</groupId>
  <artifactId>codex-java-demo</artifactId>
  <version>1.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.github.majiajustar</groupId>
      <artifactId>codex-java-sdk</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>
  </dependencies>
</project>
```

## 2. 编写第一个程序

`Main.java`：

```java
package example;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexThread;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.turn.TurnResult;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        Path project = Path.of("D:/code/my-project").toAbsolutePath().normalize();

        try (CodexClient codex = CodexClient.create()) {
            CodexThread thread = codex.startThread(ThreadOptions.builder()
                    .workingDirectory(project)
                    .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
                    .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
                    .build());

            TurnResult result = thread.run("请阅读这个项目，用五个要点解释其结构，不要修改文件。");

            System.out.println("Thread: " + thread.id());
            System.out.println("Turn: " + result.id());
            System.out.println("Status: " + result.status());
            System.out.println("Answer:\n" + result.finalResponse());
        }
    }
}
```

这个程序执行了以下动作：

1. `CodexClient.create()` 启动 app-server 并完成握手；
2. `startThread(...)` 创建一个以指定目录为工作区的会话；
3. `thread.run(...)` 创建 Turn 并阻塞等待完成；
4. SDK 汇总最终回答、Items 和 token 用量；
5. try-with-resources 自动关闭 stdin、子进程和内部虚拟线程执行器。

## 3. 构建和运行

```shell
mvn package
```

可以使用 IDE 直接运行 `example.Main`，也可以配置 Maven Exec Plugin。运行前确认目标目录存在且 Codex CLI 已登录。

## 4. 连续多轮对话

同一个 `CodexThread` 可以执行多个 Turn，后续 Turn 会继承此前对话上下文：

```java
try (CodexClient codex = CodexClient.create()) {
    CodexThread thread = codex.startThread(ThreadOptions.builder()
            .workingDirectory(Path.of("D:/code/my-project"))
            .sandbox(ThreadOptions.Sandbox.READ_ONLY)
            .build());

    TurnResult analysis = thread.run("找出这个项目最重要的三个模块。");
    System.out.println(analysis.finalResponse());

    TurnResult followUp = thread.run("进一步解释第二个模块的数据流。上一个回答不需要重复。");
    System.out.println(followUp.finalResponse());
}
```

不要为每一个追问都创建新的 Thread，否则模型看不到前一轮的会话上下文。

## 5. 查看完整结果

`TurnResult` 不只有最终文本：

```java
TurnResult result = thread.run("运行测试并总结结果。");

System.out.println("状态: " + result.status());
System.out.println("最终回答: " + result.finalResponse());
System.out.println("Item 数量: " + result.items().size());

for (JsonNode item : result.items()) {
    System.out.println(item.toPrettyString());
}

if (result.usage() != null) {
    System.out.println("Token 用量: " + result.usage().toPrettyString());
}

System.out.println("原始 Turn: " + result.turn().toPrettyString());
```

Items 可能包括代理消息、命令执行、文件修改、推理摘要和工具调用。它们当前以 `JsonNode` 表示，因此在读取字段前应检查 `type`。

## 6. 只读任务与可写任务

分析任务建议使用只读沙箱：

```java
.sandbox(ThreadOptions.Sandbox.READ_ONLY)
```

需要修改项目时使用：

```java
.sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
```

`DANGER_FULL_ACCESS` 会显著扩大子进程权限，只应在隔离、可信且明确需要的环境中使用。

## 7. 保存并恢复 Thread

Thread ID 可以保存到数据库或任务记录中：

```java
String threadId;

try (CodexClient codex = CodexClient.create()) {
    CodexThread thread = codex.startThread();
    threadId = thread.id();
    thread.run("分析当前仓库。");
}

try (CodexClient codex = CodexClient.create()) {
    CodexThread resumed = codex.resumeThread(threadId);
    TurnResult result = resumed.run("继续刚才的分析，列出风险最高的部分。");
    System.out.println(result.finalResponse());
}
```

持久化 Thread 依赖 app-server 使用的 `CODEX_HOME`。如果两次运行使用不同的 Codex Home，即使 Thread ID 相同也可能无法恢复。

## 8. 下一步

快速入门使用了默认客户端配置。实际应用通常还需要配置 CLI 路径、环境变量、超时、客户端身份、Thread 沙箱和 Turn 级覆盖，下一章将逐项说明。

---

[上一章](01-overview-and-installation.md) · [返回目录](README.md) · [下一章：配置](03-configuration.md)
