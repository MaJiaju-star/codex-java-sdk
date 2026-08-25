# 15. 从 app-server Schema 生成 Java 类型

[上一章](14-solon-sse-complete-example.md) · [返回目录](README.md)

Java SDK 的 v2 协议模型不手工复制字段，而是从当前仓库的 app-server JSON Schema
确定性生成。生成结果提交到源码仓库，因此 SDK 用户只需要 Maven 和 JDK 25，运行时
不依赖 Python。

## 1. 权威 Schema 和生成目录

权威聚合 Schema：

```text
codex-rs/app-server-protocol/schema/json/codex_app_server_protocol.v2.schemas.json
```

生成器读取的独立 v2 Schema：

```text
codex-rs/app-server-protocol/schema/json/v2/*.json
```

生成代码：

```text
sdk/java/src/main/java/com/openai/codex/generated/v2/
```

这里的文件包含清晰的生成标记，请不要直接修改。稳定字符串集合生成 Java `enum`，
对象生成 Java `record`，并使用 Jackson 注解处理协议值和未知响应字段。

## 2. 重新生成

在 `sdk/java` 目录执行：

```shell
python scripts/generate_protocol_types.py
```

Maven 等价命令：

```shell
mvn -Pgenerate-protocol generate-sources
```

生成器会覆盖它管理的 Java 文件，并移除生成目录中已经不属于清单的旧文件。

只检查、不写入：

```shell
python scripts/generate_protocol_types.py --check
```

Maven/CI 等价命令：

```shell
mvn -Pcheck-protocol validate
```

如果 Schema、生成器或已提交文件不一致，命令会列出缺失、过期或变化的文件并返回非
零退出码。升级 app-server 协议后，应把此命令放入 CI。

## 3. 当前生成范围

公开便捷 API 当前生成：

- `ThreadListParams`、`ThreadListResponse`、`Thread`、`ThreadStatus`；
- `Turn`、`TurnError`、`GitInfo`；
- Thread 归档和取消归档的参数/响应；
- 列表排序、来源、状态、Personality、审批 Reviewer、Reasoning Summary 等枚举；
- `SchemaMetadata.SHA256`，记录生成时聚合 v2 Schema 的摘要。

app-server 聚合 Schema 有数百个定义，而且很多 Item 是持续扩展的判别联合。SDK 对
这些开放联合中的稳定外壳使用 record，对 `source`、Turn Item、`codexErrorInfo`
等快速演进的内部数据保留 `JsonNode`。这样既能强类型分页和生命周期 API，也不会
因为服务端新增一种工具 Item 就让旧客户端无法反序列化整页数据。

## 4. 扩大生成范围

编辑：

```text
sdk/java/scripts/generate_protocol_types.py
```

把新的字符串枚举加入 `ENUM_TARGETS`，或把对象加入 `RECORD_TARGETS`。条目包含：

- Java 类型名；
- 定义所在的 v2 Schema 文件；
- 类型位于 Schema 根还是 `definitions`。

然后依次运行：

```shell
python scripts/generate_protocol_types.py
python scripts/generate_protocol_types.py --check
mvn test
```

独立克隆本 SDK 仓库时，生成器还需要 Codex 源码仓库中的 app-server Schema。
通过 `CODEX_REPO_ROOT` 指向 Codex 仓库根目录：

```shell
CODEX_REPO_ROOT=/path/to/codex mvn -Pcheck-protocol validate
```

手写的客户端与运行时句柄放在 `io.github.majiajustar.codex`，Thread、Turn、事件、工具等
领域 API 放在对应子包；纯协议模型放在 `io.github.majiajustar.codex.generated.v2`。这种分层可
避免每次协议再生成时覆盖手写逻辑，也能控制根包的规模。

## 5. 使用生成类型

```java
import io.github.majiajustar.codex.thread.ThreadListOptions;
import io.github.majiajustar.codex.generated.v2.SortDirection;
import io.github.majiajustar.codex.generated.v2.ThreadListResponse;
import io.github.majiajustar.codex.generated.v2.ThreadSortKey;

ThreadListResponse page = codex.listThreads(ThreadListOptions.builder()
        .limit(50)
        .sortKey(ThreadSortKey.RECENCY_AT)
        .sortDirection(SortDirection.DESC)
        .build());

for (io.github.majiajustar.codex.generated.v2.Thread thread : page.data()) {
    System.out.println(thread.id() + " " + thread.status().type());
}
```

枚举通过 `wireValue()` 暴露协议值。例如
`ThreadSortKey.RECENCY_AT.wireValue()` 是 `recency_at`，而不是 Java 常量名。

---

[上一章](14-solon-sse-complete-example.md) · [返回目录](README.md)
