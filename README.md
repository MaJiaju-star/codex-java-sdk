# Unofficial Codex Java SDK

Java 25 SDK for embedding the Codex agent in JVM applications. It launches
`codex app-server`, performs the JSON-RPC v2 handshake, and exchanges JSONL
messages over standard input and output.

> [!IMPORTANT]
> This is an unofficial, community-maintained SDK. It is not affiliated with,
> endorsed by, or maintained by OpenAI.

Detailed Chinese documentation: [中文教程](docs/README.md).

Complete Solon Web SSE example: [多会话流式对话案例](examples/solon-sse-chat/README.md).

## Requirements

- JDK 25
- Maven 3.9+
- A `codex` executable on `PATH`, or an explicit executable path

## Build

```shell
cd sdk/java
mvn test
```

The SDK checks in Java records and enums generated from the repository's
app-server v2 schemas:

```shell
mvn -Pgenerate-protocol generate-sources
mvn -Pcheck-protocol validate
```

## Quick start

```java
import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexThread;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.turn.TurnResult;
import java.nio.file.Path;

try (CodexClient codex = CodexClient.create()) {
    CodexThread thread = codex.startThread(ThreadOptions.builder()
            .workingDirectory(Path.of("/path/to/project"))
            .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
            .build());

    TurnResult result = thread.run("Explain this repository in three bullets.");
    System.out.println(result.finalResponse());
}
```

## Package layout

The small root package contains client and runtime handles. Domain APIs are
grouped by responsibility:

```text
io.github.majiajustar.codex            client, configuration, thread/turn handles
io.github.majiajustar.codex.thread     thread options and list options
io.github.majiajustar.codex.turn       turn options, inputs, results, and usage
io.github.majiajustar.codex.event      events, notifications, and items
io.github.majiajustar.codex.model      model catalog types
io.github.majiajustar.codex.tool       approvals, interceptors, and tool lifecycle
io.github.majiajustar.codex.sandbox    sandbox policies
io.github.majiajustar.codex.exception  SDK and JSON-RPC exceptions
io.github.majiajustar.codex.generated  generated app-server protocol types
io.github.majiajustar.codex.internal   implementation details; not public API
```

Use a specific Codex binary when it is not on `PATH`:

```java
CodexClientConfig config = CodexClientConfig.builder()
        .codexExecutable(Path.of("/path/to/codex"))
        .build();

try (CodexClient codex = CodexClient.create(config)) {
    // ...
}
```

For an internal OpenAI-compatible endpoint, configure the endpoint, API key,
and optional raw Codex configuration overrides directly on the client:

```java
CodexClientConfig config = CodexClientConfig.builder()
        .baseUrl("http://codex-api.internal/v1")
        .apiKey(System.getenv("INTERNAL_CODEX_API_KEY"))
        .modelProvider("internal")
        .model("gpt-internal")
        .modelReasoningEffort(CodexClientConfig.ReasoningEffort.HIGH)
        .webSearch(CodexClientConfig.WebSearchMode.DISABLED)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
        .workspaceNetworkAccess(false)
        .build();
```

`apiKey(...)` passes the credential as `OPENAI_API_KEY` only to the app-server
child process. It is not placed on the command line.
Named settings take precedence over matching raw `configOverride(...)` entries.

## Streaming

Turn events implement the JDK `Flow` API. Subscribe before awaiting the result;
events that arrived before `turn/start` returned are retained for the subscriber.

```java
CodexTurn turn = thread.startTurn("Run the tests and explain any failures.");
turn.events().subscribe(new Flow.Subscriber<>() {
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }
    public void onNext(CodexEvent event) {
        System.out.println(event.type() + ": " + event.params());
    }
    public void onError(Throwable error) { error.printStackTrace(); }
    public void onComplete() {}
});

TurnResult result = turn.await();
```

Known notifications and completed items have sealed, strongly typed views. Raw
JSON remains available for forward compatibility:

```java
CodexNotification notification = event.notification();
if (notification instanceof CodexNotification.ItemCompleted completed
        && completed.item() instanceof CodexItem.AgentMessage message
        && message.phase() == CodexItem.MessagePhase.FINAL_ANSWER) {
    System.out.println(message.text());
}

TokenUsage usage = result.typedUsage();
List<CodexItem> items = result.typedItems();
CodexModelList models = codex.listModels();
```

Unrecognized notification methods and future item variants become
`CodexNotification.Unknown` and `CodexItem.Unknown` instead of failing
deserialization. The original payload is available through `raw()`.

`CodexTurn` also supports `steer(...)`, `interrupt()`, and `resultAsync()`.
Use `CancellationToken` for AbortSignal-style cooperative cancellation:

```java
CancellationToken cancellation = new CancellationToken();
CodexTurn turn = thread.startTurn("Run the test suite", cancellation);

// From another request, timeout, or UI callback:
cancellation.cancel();
```

Additional project roots use the app-server v2 runtime-workspace-roots field:

```java
ThreadOptions options = ThreadOptions.builder()
        .workingDirectory(Path.of("/workspace/project"))
        .additionalDirectory(Path.of("/workspace/shared"))
        .skipGitRepoCheck(true)
        .build();
```

Additional directories must be absolute. `skipGitRepoCheck` is accepted for
configuration parity; app-server does not run the `codex exec` Git preflight,
so no wire field is necessary.

`CodexClient.request(...)` exposes raw JSON-RPC methods for app-server APIs that
do not yet have a convenience wrapper.

Thread listing is cursor-paginated and strongly typed through
`ThreadListOptions`/`ThreadListResponse`; archive and unarchive have dedicated
client and thread methods. Server overload responses are retried automatically
with configurable exponential backoff, while non-retryable JSON-RPC errors are
mapped to specific exception classes.

## Approval handling

The default approval handler accepts command execution and file-change requests.
Production applications should explicitly configure `approvalHandler(...)` and
can register typed `ToolInterceptor` and `ToolObserver` extensions. The original
`serverRequestHandler(...)` remains available for raw and forward-compatible
server requests.
