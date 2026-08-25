# 07. 文本、图片、Skill 与结构化输出

[上一章](06-streaming-events.md) · [返回目录](README.md) · [下一章：审批与安全](08-approvals-and-security.md)

## 1. 文本输入

字符串快捷方式：

```java
TurnResult result = thread.run("解释这个项目的入口点。");
```

显式输入项：

```java
TurnResult result = thread.run(
        List.of(UserInput.text("解释这个项目的入口点。")),
        TurnOptions.defaults());
```

多个文本项会作为多个用户输入元素发送：

```java
List<UserInput> inputs = List.of(
        UserInput.text("任务：审查认证模块。"),
        UserInput.text("重点：并发安全和错误处理。"),
        UserInput.text("输出：按严重级别排序。"));
```

## 2. 远程图片或 Data URL

```java
List<UserInput> inputs = List.of(
        UserInput.text("描述这张图片中的界面问题。"),
        UserInput.image("https://example.com/screenshot.png"));

TurnResult result = thread.run(inputs, TurnOptions.defaults());
```

也可传入模型和 app-server 支持的 Data URL。远程 URL 是否可访问取决于网络策略、模型能力和运行环境。

## 3. 本地图片

```java
Path screenshot = Path.of("D:/artifacts/ui-failure.png").toAbsolutePath();

List<UserInput> inputs = List.of(
        UserInput.text("分析截图，并在项目中定位可能对应的组件。"),
        UserInput.localImage(screenshot));

TurnResult result = thread.run(inputs, TurnOptions.defaults());
```

推荐传绝对路径。相对路径的解析会受到 app-server 进程目录和 Thread 工作目录影响，容易在服务部署后失效。

发送图片前应检查：

```java
if (!Files.isRegularFile(screenshot)) {
    throw new IllegalArgumentException("Screenshot does not exist: " + screenshot);
}
```

不要让不受信用户任意指定服务器文件路径，应在业务层校验允许的根目录。

## 4. Skill 输入

```java
List<UserInput> inputs = List.of(
        UserInput.text("使用指定 Skill 检查远程执行兼容性。"),
        UserInput.skill(
                "remote-tests",
                Path.of("D:/code/codex/.codex/skills/remote-tests/SKILL.md")));

TurnResult result = thread.run(inputs, TurnOptions.defaults());
```

`name` 是显示和引用名称，`path` 指向 Skill。路径必须对 app-server 所在运行环境可见；本机 Java 进程能访问并不代表容器或远程执行环境也能访问。

## 5. Mention 输入

```java
List<UserInput> inputs = List.of(
        UserInput.text("结合所提及的设计文档审查当前实现。"),
        UserInput.mention(
                "authentication-design",
                Path.of("D:/docs/authentication-design.md")));
```

Mention 用来给输入附带命名资源引用。实际解释方式由 app-server 和上层能力决定。

## 6. 混合输入

```java
List<UserInput> inputs = List.of(
        UserInput.text("对照设计稿检查实现，输出结构化报告。"),
        UserInput.localImage(Path.of("D:/design/login.png")),
        UserInput.mention("login-spec", Path.of("D:/design/login-spec.md")),
        UserInput.skill("ui-review", Path.of("D:/skills/ui-review/SKILL.md")));

TurnResult result = thread.run(inputs, TurnOptions.builder()
        .reasoningEffort("high")
        .build());
```

## 7. 定义 JSON Schema

`TurnOptions.outputSchema(...)` 要求最终代理消息符合 JSON Schema。

```java
ObjectMapper mapper = new ObjectMapper();

ObjectNode schema = mapper.createObjectNode();
schema.put("type", "object");

ObjectNode properties = schema.putObject("properties");
properties.putObject("summary").put("type", "string");
properties.putObject("riskLevel")
        .put("type", "string")
        .putArray("enum")
        .add("low")
        .add("medium")
        .add("high");
properties.putObject("findings")
        .put("type", "array")
        .putObject("items")
        .put("type", "string");

schema.putArray("required")
        .add("summary")
        .add("riskLevel")
        .add("findings");
schema.put("additionalProperties", false);
```

执行：

```java
TurnOptions options = TurnOptions.builder()
        .outputSchema(schema)
        .build();

TurnResult result = thread.run(
        List.of(UserInput.text("审查当前模块并输出报告。")),
        options);
```

## 8. 解析结构化回答

```java
String response = result.finalResponse();
if (response == null) {
    throw new IllegalStateException("Codex did not return a final response");
}

JsonNode report = mapper.readTree(response);
String summary = report.path("summary").asText();
String riskLevel = report.path("riskLevel").asText();

for (JsonNode finding : report.path("findings")) {
    System.out.println("- " + finding.asText());
}
```

定义业务 record：

```java
record ReviewReport(String summary, String riskLevel, List<String> findings) {}

ReviewReport report = mapper.readValue(result.finalResponse(), ReviewReport.class);
```

即使使用 Schema，也应捕获 JSON 解析错误，并保留原始回答便于诊断。

## 9. Schema 设计建议

- 根节点优先使用 object；
- 明确列出 `required`；
- 设置 `additionalProperties: false`；
- 枚举值保持简短稳定；
- 避免极深的嵌套结构；
- 不要让单个字符串字段承载无限长内容；
- 在应用层再次验证关键业务约束。

JSON Schema 约束模型输出格式，但不替代业务校验。例如 `riskLevel=low` 是否合理仍需应用或人工判断。

## 10. 文件路径安全

本地图片、Skill 和 Mention 都携带路径。服务端接收用户请求时应限制路径：

```java
Path allowedRoot = Path.of("D:/codex-inputs").toAbsolutePath().normalize();
Path requested = allowedRoot.resolve(userProvidedName).normalize();

if (!requested.startsWith(allowedRoot)) {
    throw new SecurityException("Path escapes allowed root");
}
```

同时避免跟随不受信符号链接进入允许目录之外的位置。

---

[上一章](06-streaming-events.md) · [返回目录](README.md) · [下一章：审批与安全](08-approvals-and-security.md)
