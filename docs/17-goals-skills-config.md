# 17. Goal、Skills 与 Config API

[上一章：MCP 运行时 API](16-mcp-runtime.md) · [返回目录](README.md)

这三组 API 面向需要保持长期任务状态、发现工作流能力和提供设置页面的 IDE 或服务端应用。
它们通过 app-server v2 工作，不要求应用直接解析或修改 `config.toml`。

## 1. Thread Goal

Goal 绑定到 Thread，而不是某个 Turn。它记录目标、状态、可选 Token 预算、累计 Token 和时间用量。

```java
ThreadGoal goal = thread.goals().set(GoalUpdate.builder()
        .objective("完成 Java SDK 并确保测试通过")
        .status(ThreadGoalStatus.ACTIVE)
        .tokenBudget(200_000)
        .build());

Optional<ThreadGoal> current = thread.goals().get();
thread.goals().set(GoalUpdate.builder()
        .status(ThreadGoalStatus.PAUSED)
        .build());
boolean cleared = thread.goals().clear();
```

状态包含 `ACTIVE`、`PAUSED`、`BLOCKED`、`USAGE_LIMITED`、`BUDGET_LIMITED` 和
`COMPLETE`。未来新增状态会映射为 `UNKNOWN`。

应用还应订阅 `thread/goal/updated` 和 `thread/goal/cleared`。用量变化也可能触发 updated，
因此 UI 不应只在主动调用 `set` 后刷新。

## 2. Skill 发现与启停

```java
SkillsListResult result = codex.skills().list(SkillsListOptions.builder()
        .workingDirectory(Path.of("D:/workspace/project"))
        .forceReload(false)
        .build());

for (SkillsListResult.Entry entry : result.data()) {
    for (SkillMetadata skill : entry.skills()) {
        System.out.println(skill.name() + " " + skill.scope() + " " + skill.enabled());
    }
}
```

结果按工作目录分组，并包含 Skill 路径、作用域、展示信息、工具依赖和解析错误。

```java
codex.skills().setExtraRoots(List.of(Path.of("D:/company/codex-skills")));
codex.skills().disableByName("unsafe-internal-tool");
codex.skills().enable(Path.of("D:/company/codex-skills/review/SKILL.md"));
```

`skills/changed` 是缓存失效通知，不携带完整的新列表。收到后应使用与当前页面相同的
`SkillsListOptions` 再次调用 `list`。

## 3. 读取分层配置

```java
ConfigSnapshot snapshot = codex.config().read(
        new ConfigReadOptions(Path.of("D:/workspace/project"), true));

String model = snapshot.config().path("model").asText();
ConfigLayerMetadata origin = snapshot.origins().get("model");
```

`config()` 是最终生效配置，`origins()` 说明字段来自用户、项目、企业托管或会话参数等哪一层，
`layers()` 在 `includeLayers=true` 时提供参与合并的原始层。配置主体保留 `JsonNode`，因此新版
Codex 新增字段不会导致旧 SDK 反序列化失败。

## 4. 写配置

替换单个值：

```java
ConfigWriteResult result = codex.config().write(
        ConfigWriteRequest.builder("web_search", "cached")
                .expectedVersion("current-version")
                .build());
```

批量修改：

```java
ConfigWriteResult result = codex.config().batchWrite(
        ConfigBatchWriteRequest.builder()
                .replace("model", "gpt-internal")
                .replace("web_search", "disabled")
                .upsert("features", Map.of("internal_tools", true))
                .expectedVersion("current-version")
                .reloadUserConfig(true)
                .build());
```

省略 `filePath` 时写用户 `config.toml`。`expectedVersion` 提供乐观并发保护；Web 设置页面应在
读取后保存版本，并在版本冲突时重新读取，而不是覆盖其他页面刚写入的设置。

`WriteStatus.OK_OVERRIDDEN` 表示文件已经写入，但更高优先级层覆盖了该值。
`overriddenMetadata()` 会给出真正的生效值和覆盖来源。

## 5. 管理员约束

```java
ConfigRequirements requirements = codex.config().requirements();
Optional<Set<String>> sandboxes = requirements.allowedSandboxModes();
Optional<Set<String>> webSearchModes = requirements.allowedWebSearchModes();
```

WebIDE 可以据此提前禁用管理员不允许的选项。`raw()` 保留完整 requirements 对象，常用的
Sandbox、Web Search、Browser/Computer Use 和附加开发说明提供便捷访问。

不要把可由浏览器任意指定的 `filePath` 直接传给 Config API。服务端应限制可写目录，并把 API Key、
Bearer Token 等秘密保存在环境变量或密钥服务中。
