#!/usr/bin/env python3
"""Generate the Java v2 protocol records used by the SDK from app-server schemas."""

import argparse
import hashlib
import html
import json
import os
import re
import sys
import textwrap
from pathlib import Path


JAVA_PACKAGE = "io.github.majiajustar.codex.generated.v2"
SDK_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = Path(
    os.environ.get("CODEX_REPO_ROOT", SDK_ROOT.parents[1])
).resolve()
SCHEMA_ROOT = REPO_ROOT / "codex-rs/app-server-protocol/schema/json"
V2_SCHEMA_ROOT = SCHEMA_ROOT / "v2"
OUTPUT_ROOT = SDK_ROOT / "src/main/java/io/github/majiajustar/codex/generated/v2"

ENUM_TARGETS = {
    "ApprovalsReviewer": "ThreadStartParams",
    "Personality": "ThreadStartParams",
    "ReasoningSummary": "TurnStartParams",
    "SandboxMode": "ThreadStartParams",
    "SortDirection": "ThreadListParams",
    "ThreadActiveFlag": "ThreadListResponse",
    "ThreadHistoryMode": "ThreadListResponse",
    "ThreadSourceKind": "ThreadListParams",
    "ThreadStartSource": "ThreadStartParams",
    "ThreadSortKey": "ThreadListParams",
    "TurnItemsView": "ThreadListResponse",
    "TurnStatus": "ThreadListResponse",
}

RECORD_TARGETS = {
    "GitInfo": ("ThreadListResponse", "definition"),
    "MisalignmentErrorDetails": ("ThreadListResponse", "definition"),
    "MisalignmentSteer": ("ThreadListResponse", "definition"),
    "Thread": ("ThreadListResponse", "definition"),
    "ThreadArchiveParams": ("ThreadArchiveParams", "root"),
    "ThreadArchiveResponse": ("ThreadArchiveResponse", "root"),
    "ThreadListParams": ("ThreadListParams", "root"),
    "ThreadListResponse": ("ThreadListResponse", "root"),
    "ThreadTurnsListParams": ("ThreadTurnsListParams", "root"),
    "ThreadTurnsListResponse": ("ThreadTurnsListResponse", "root"),
    "ThreadUnarchiveParams": ("ThreadUnarchiveParams", "root"),
    "ThreadUnarchiveResponse": ("ThreadUnarchiveResponse", "root"),
    "Turn": ("ThreadListResponse", "definition"),
    "TurnError": ("ThreadListResponse", "definition"),
}

GENERATED_TYPES = set(ENUM_TARGETS) | set(RECORD_TARGETS) | {
    "ThreadStatus",
    "ThreadStatusType",
}

TYPE_DESCRIPTIONS = {
    "ApprovalsReviewer": "审批请求的审核方。",
    "GitInfo": "创建会话时记录的 Git 仓库信息。",
    "MisalignmentErrorDetails": "目标偏离拦截的公开说明和继续执行建议。",
    "MisalignmentSteer": "确认继续执行时提交给下一轮的用户指令。",
    "Personality": "Codex 回复所采用的表达风格。",
    "ReasoningSummary": (
        "模型推理摘要的输出模式，可用于调试和理解模型的推理过程。详情参见 "
        "https://platform.openai.com/docs/guides/reasoning?api-mode=responses#reasoning-summaries"
    ),
    "SandboxMode": "会话使用的粗粒度沙箱模式。",
    "SortDirection": "会话列表的排序方向。",
    "Thread": "Codex 会话的强类型协议表示。",
    "ThreadActiveFlag": "活动会话当前正在执行的工作类别。",
    "ThreadHistoryMode": "会话历史的存储模式。",
    "ThreadArchiveParams": "归档会话请求的参数。",
    "ThreadArchiveResponse": "归档会话请求的响应。",
    "ThreadListParams": "查询会话列表时使用的过滤和分页参数。",
    "ThreadListResponse": "一页会话列表及其分页游标。",
    "ThreadTurnsListParams": "分页读取指定会话 Turn 历史的请求参数。",
    "ThreadTurnsListResponse": "一页会话 Turn 历史及其分页游标。",
    "ThreadSortKey": "会话列表使用的排序字段。",
    "ThreadSourceKind": "会话的来源类别。",
    "ThreadStartSource": "新会话的启动来源。",
    "ThreadUnarchiveParams": "取消归档会话请求的参数。",
    "ThreadUnarchiveResponse": "取消归档会话请求的响应。",
    "Turn": "Codex 轮次的强类型协议表示。",
    "TurnError": "轮次失败时返回的结构化错误。",
    "TurnItemsView": "轮次载荷中 items 字段的加载范围。",
    "TurnStatus": "Codex 轮次的当前状态。",
}

FIELD_DESCRIPTIONS = {
    "GitInfo": {
        "branch": "创建会话时所在的 Git 分支。",
        "originUrl": "Git 远程 origin 的 URL。",
        "sha": "创建会话时检出的 Git 提交 SHA。",
    },
    "MisalignmentErrorDetails": {
        "detailedExplanation": "向用户展示的本地化详细说明。",
        "errorType": "开放式目标偏离分类。",
        "steer": "确认继续执行时提交给下一轮的指令。",
    },
    "MisalignmentSteer": {
        "message": "提交给下一轮的用户消息。",
    },
    "Thread": {
        "agentNickname": "由 AgentControl 创建的子 Agent 所获得的可选随机唯一昵称。",
        "agentRole": "由 AgentControl 创建的子 Agent 所获得的可选角色。",
        "cliVersion": "创建该会话的 Codex CLI 版本。",
        "createdAt": "会话创建时间，使用 Unix 秒级时间戳。",
        "cwd": "为该会话记录的工作目录。",
        "ephemeral": "该会话是否为临时会话且不应写入磁盘。",
        "forkedFromId": "该会话由其他会话派生时对应的源会话 ID。",
        "gitInfo": "创建会话时记录的可选 Git 元数据。",
        "historyMode": "会话历史的存储模式。",
        "id": "会话标识；Codex 生成的会话 ID 使用 UUIDv7。",
        "model": "该会话当前使用的模型标识。",
        "modelProvider": "该会话使用的模型提供方，例如 openai。",
        "name": "可选的用户可见会话标题。",
        "parentThreadId": "父会话 ID；只有当前会话是子 Agent 时才会设置。",
        "path": "会话在磁盘上的路径；该字段尚不稳定。",
        "preview": "会话预览，通常是第一条用户消息。",
        "reasoningEffort": "该会话当前使用的推理强度。",
        "recencyAt": "用于会话新旧排序的 Unix 秒级时间戳。",
        "sessionId": "同一会话树中的多个会话共享的 Session ID。",
        "source": "会话来源，例如 CLI、VS Code、codex exec 或 codex app-server。",
        "status": "会话当前的运行状态。",
        "threadSource": "用于分析统计的可选会话来源分类。",
        "turns": (
            "仅在 thread/resume、thread/rollback、thread/fork 和 thread/read "
            "（includeTurns 为 true）响应中填充；其他响应和通知中为空列表。"
        ),
        "updatedAt": "会话最后更新时间，使用 Unix 秒级时间戳。",
    },
    "ThreadArchiveParams": {
        "threadId": "需要归档的会话 ID。",
    },
    "ThreadListParams": {
        "archived": "可选归档过滤器；true 仅返回已归档会话，否则仅返回未归档会话。",
        "cursor": "上一次调用返回的不透明分页游标。",
        "cwd": "可选工作目录过滤条件；只返回工作目录与任一指定路径完全一致的会话。",
        "limit": "可选页大小；省略时使用服务器默认值。",
        "modelProviders": "可选模型提供方过滤条件；存在但为空时包含所有提供方。",
        "searchTerm": "应用于会话标题的可选子字符串过滤条件。",
        "sortDirection": "可选排序方向；默认按降序排列，最新记录优先。",
        "sortKey": "可选排序字段；默认为 created_at。",
        "sourceKinds": "可选来源过滤条件；省略或为空时默认包含交互式来源。",
        "useStateDbOnly": (
            "为 true 时只读取状态数据库，不扫描 JSONL rollout 修复会话元数据；"
            "省略或为 false 时保留扫描及修复行为。"
        ),
    },
    "ThreadListResponse": {
        "backwardsCursor": (
            "反转 sortDirection 时传入 cursor 的不透明游标。页面至少包含一个会话时才会提供；"
            "时间戳排序时会锚定页面起始时间，避免遗漏同一秒内的更新。"
        ),
        "data": "当前页的会话数据。",
        "nextCursor": "下一次调用使用的不透明游标；为 null 时表示没有更多数据。",
    },
    "ThreadTurnsListParams": {
        "threadId": "需要读取 Turn 历史的会话 ID。",
        "cursor": "上一次调用返回的不透明分页游标。",
        "limit": "可选页大小；app-server 0.153.0 最大支持 100。",
        "sortDirection": "可选排序方向；省略时默认按降序排列。",
        "itemsView": "每个 Turn 返回的 Item 详情范围；省略时默认为摘要。",
    },
    "ThreadTurnsListResponse": {
        "data": "当前页的 Turn 数据。",
        "nextCursor": "下一次调用使用的不透明游标；为 null 时表示没有更多 Turn。",
        "backwardsCursor": (
            "反转 sortDirection 后可作为 cursor 使用的不透明游标；页面非空时提供。"
        ),
    },
    "ThreadUnarchiveParams": {
        "threadId": "需要取消归档的会话 ID。",
    },
    "ThreadUnarchiveResponse": {
        "thread": "取消归档后的会话。",
    },
    "Turn": {
        "completedAt": "轮次完成时间，使用 Unix 秒级时间戳。",
        "durationMs": "已知时表示轮次从开始到完成所用的毫秒数。",
        "error": "只在轮次状态为失败时填充。",
        "id": "轮次标识；Codex 生成的轮次 ID 使用 UUIDv7。",
        "items": "当前包含在该轮次载荷中的会话项。",
        "itemsView": "说明该轮次已加载多少 items 数据。",
        "startedAt": "轮次开始时间，使用 Unix 秒级时间戳。",
        "status": "轮次当前状态。",
    },
    "TurnError": {
        "additionalDetails": "额外的错误详情。",
        "codexErrorInfo": "Codex 提供的结构化错误信息。",
        "message": "可读的错误消息。",
        "misalignment": "目标偏离拦截的公开说明和继续执行建议。",
    },
}


def load_schema(name):
    return json.loads((V2_SCHEMA_ROOT / f"{name}.json").read_text(encoding="utf-8"))


SCHEMAS = {
    name: load_schema(name)
    for name in {
        *ENUM_TARGETS.values(),
        *(source for source, _ in RECORD_TARGETS.values()),
    }
}


def java_constant(value):
    separated = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", value)
    separated = re.sub(r"[^A-Za-z0-9]+", "_", separated)
    return separated.strip("_").upper()


def normalized_description(value, fallback):
    description = value or fallback
    return html.escape(" ".join(description.split()), quote=False).replace("*/", "*&#47;")


def javadoc(description, params=None):
    lines = ["/**"]
    lines.extend(
        textwrap.wrap(
            description,
            width=100,
            initial_indent=" * ",
            subsequent_indent=" * ",
        )
    )
    if params:
        lines.append(" *")
        for name, text in params:
            lines.extend(
                textwrap.wrap(
                    text,
                    width=100,
                    initial_indent=f" * @param {name} ",
                    subsequent_indent=" *     ",
                )
            )
    lines.append(" */")
    return "\n".join(lines) + "\n"


def nullable_schema(schema):
    if isinstance(schema.get("type"), list):
        remaining = [item for item in schema["type"] if item != "null"]
        if len(remaining) == 1:
            return {**schema, "type": remaining[0]}
    for keyword in ("anyOf", "oneOf"):
        variants = schema.get(keyword)
        if variants:
            remaining = [item for item in variants if item.get("type") != "null"]
            if len(remaining) == 1:
                return remaining[0]
    return schema


def referenced_name(schema):
    if "$ref" in schema:
        return schema["$ref"].rsplit("/", 1)[-1]
    if len(schema.get("allOf", [])) == 1:
        return referenced_name(schema["allOf"][0])
    return None


def java_type(schema, definitions, required=False):
    schema = nullable_schema(schema)
    reference = referenced_name(schema)
    if reference:
        if reference in GENERATED_TYPES:
            return reference
        target = definitions.get(reference, {})
        if target.get("type") == "string" and "enum" not in target:
            return "String"
        return "JsonNode"

    if len(schema.get("allOf", [])) == 1:
        return java_type(schema["allOf"][0], definitions, required)
    if schema.get("anyOf") or schema.get("oneOf"):
        return "JsonNode"

    schema_type = schema.get("type")
    if schema_type == "array":
        return f"List<{java_type(schema.get('items', {}), definitions)}>"
    if schema_type == "string":
        return "String"
    if schema_type == "boolean":
        return "boolean" if required else "Boolean"
    if schema_type == "integer":
        wide = schema.get("format") in {"int64", "uint64"}
        if required:
            return "long" if wide else "int"
        return "Long" if wide else "Integer"
    if schema_type == "number":
        return "double" if required else "Double"
    return "JsonNode"


def header(schema_path):
    return (
        "// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。\n"
        f"// 来源：{schema_path}\n"
        f"package {JAVA_PACKAGE};\n\n"
    )


def enum_source(name, source_name):
    root = SCHEMAS[source_name]
    schema = root["definitions"][name]
    values = schema.get("enum")
    if values is None:
        values = [
            value
            for variant in schema["oneOf"]
            for value in variant.get("enum", [])
        ]
    constants = ",\n".join(
        f'    {java_constant(value)}("{value}")' for value in values
    )
    description = normalized_description(
        TYPE_DESCRIPTIONS.get(name),
        f"Codex app-server v2 协议中的 {name} 枚举值。",
    )
    return (
        header(f"v2/{source_name}.json#/definitions/{name}")
        + "import com.fasterxml.jackson.annotation.JsonCreator;\n"
        + "import com.fasterxml.jackson.annotation.JsonValue;\n\n"
        + javadoc(description)
        + f"public enum {name} {{\n"
        + constants
        + ";\n\n"
        + "    private final String wireValue;\n\n"
        + f"    {name}(String wireValue) {{\n"
        + "        this.wireValue = wireValue;\n"
        + "    }\n\n"
        + "    /** 返回 app-server 协议序列化时使用的准确值。 */\n"
        + "    @JsonValue\n"
        + "    public String wireValue() {\n"
        + "        return wireValue;\n"
        + "    }\n\n"
        + "    /**\n"
        + "     * 解析 app-server 协议值。\n"
        + "     *\n"
        + "     * @param value 准确的协议值\n"
        + "     * @return 匹配的枚举常量\n"
        + "     * @throws IllegalArgumentException 无法识别该值时抛出\n"
        + "     */\n"
        + "    @JsonCreator\n"
        + f"    public static {name} fromWireValue(String value) {{\n"
        + f"        for (var candidate : {name}.values()) {{\n"
        + "            if (candidate.wireValue.equals(value)) return candidate;\n"
        + "        }\n"
        + f'        throw new IllegalArgumentException("Unknown {name} value: " + value);\n'
        + "    }\n"
        + "}\n"
    )


def record_source(name, source_name, location):
    root = SCHEMAS[source_name]
    schema = root if location == "root" else root["definitions"][name]
    required = set(schema.get("required", []))
    components = []
    component_docs = []
    for field_name, field_schema in schema.get("properties", {}).items():
        field_type = java_type(
            field_schema,
            root.get("definitions", {}),
            field_name in required,
        )
        components.append(f"        {field_type} {field_name}")
        component_docs.append(
            (
                field_name,
                normalized_description(
                    FIELD_DESCRIPTIONS.get(name, {}).get(field_name),
                    f"{field_name} 协议字段的值。",
                ),
            )
        )
    joined = ",\n".join(components)
    fragment = "" if location == "root" else f"#/definitions/{name}"
    declaration = f"public record {name}("
    if components:
        declaration += "\n" + joined + "\n"
    declaration += ") {}\n"
    description = normalized_description(
        TYPE_DESCRIPTIONS.get(name),
        f"Codex app-server v2 协议中的 {name} 生成类型。",
    )
    return (
        header(f"v2/{source_name}.json{fragment}")
        + "import com.fasterxml.jackson.annotation.JsonIgnoreProperties;\n"
        + "import com.fasterxml.jackson.annotation.JsonInclude;\n"
        + "import com.fasterxml.jackson.databind.JsonNode;\n"
        + "import java.util.List;\n\n"
        + javadoc(description, component_docs)
        + "@JsonIgnoreProperties(ignoreUnknown = true)\n"
        + "@JsonInclude(JsonInclude.Include.NON_NULL)\n"
        + declaration
    )


def thread_status_sources():
    root = SCHEMAS["ThreadListResponse"]
    schema = root["definitions"]["ThreadStatus"]
    variants = schema["oneOf"]
    values = [variant["properties"]["type"]["enum"][0] for variant in variants]
    enum_constants = ",\n".join(
        f'    {java_constant(value)}("{value}")' for value in values
    )
    type_source = (
        header("v2/ThreadListResponse.json#/definitions/ThreadStatus")
        + "import com.fasterxml.jackson.annotation.JsonCreator;\n"
        + "import com.fasterxml.jackson.annotation.JsonValue;\n\n"
        + javadoc("Codex 会话的终态或活动状态。")
        + "public enum ThreadStatusType {\n"
        + enum_constants
        + ";\n\n"
        + "    private final String wireValue;\n\n"
        + "    ThreadStatusType(String wireValue) {\n"
        + "        this.wireValue = wireValue;\n"
        + "    }\n\n"
        + "    /** 返回 app-server 协议序列化时使用的准确值。 */\n"
        + "    @JsonValue\n"
        + "    public String wireValue() {\n"
        + "        return wireValue;\n"
        + "    }\n\n"
        + "    /**\n"
        + "     * 解析 app-server 协议值。\n"
        + "     *\n"
        + "     * @param value 准确的协议值\n"
        + "     * @return 匹配的枚举常量\n"
        + "     * @throws IllegalArgumentException 无法识别该值时抛出\n"
        + "     */\n"
        + "    @JsonCreator\n"
        + "    public static ThreadStatusType fromWireValue(String value) {\n"
        + "        for (var candidate : values()) {\n"
        + "            if (candidate.wireValue.equals(value)) return candidate;\n"
        + "        }\n"
        + '        throw new IllegalArgumentException("Unknown ThreadStatusType value: " + value);\n'
        + "    }\n"
        + "}\n"
    )
    status_source = (
        header("v2/ThreadListResponse.json#/definitions/ThreadStatus")
        + "import com.fasterxml.jackson.annotation.JsonIgnoreProperties;\n"
        + "import com.fasterxml.jackson.annotation.JsonInclude;\n"
        + "import java.util.List;\n\n"
        + javadoc(
            "会话状态的可辨识联合类型。",
            [
                ("type", "会话当前状态"),
                (
                    "activeFlags",
                    "活动工作类别；只在会话处于活动状态时提供",
                ),
            ],
        )
        + "@JsonIgnoreProperties(ignoreUnknown = true)\n"
        + "@JsonInclude(JsonInclude.Include.NON_NULL)\n"
        + "public record ThreadStatus(\n"
        + "        ThreadStatusType type,\n"
        + "        List<ThreadActiveFlag> activeFlags\n"
        + ") {}\n"
    )
    return {
        "ThreadStatus.java": status_source,
        "ThreadStatusType.java": type_source,
    }


def metadata_source():
    aggregate = SCHEMA_ROOT / "codex_app_server_protocol.v2.schemas.json"
    digest = hashlib.sha256(aggregate.read_bytes()).hexdigest()
    return (
        header("codex_app_server_protocol.v2.schemas.json")
        + javadoc("生成代码所依据的 app-server v2 Schema 构建时标识。")
        + "public final class SchemaMetadata {\n"
        + "    /** app-server v2 聚合 Schema 的 SHA-256 摘要。 */\n"
        + f'    public static final String SHA256 = "{digest}";\n\n'
        + "    private SchemaMetadata() {}\n"
        + "}\n"
    )


def generated_sources():
    sources = {
        f"{name}.java": enum_source(name, source)
        for name, source in sorted(ENUM_TARGETS.items())
    }
    sources.update(
        {
            f"{name}.java": record_source(name, source, location)
            for name, (source, location) in sorted(RECORD_TARGETS.items())
        }
    )
    sources.update(thread_status_sources())
    sources["SchemaMetadata.java"] = metadata_source()
    return sources


def check(sources):
    actual_names = (
        {path.name for path in OUTPUT_ROOT.glob("*.java")}
        if OUTPUT_ROOT.exists()
        else set()
    )
    expected_names = set(sources)
    stale = sorted(actual_names - expected_names)
    missing = sorted(expected_names - actual_names)
    changed = sorted(
        name
        for name, content in sources.items()
        if (OUTPUT_ROOT / name).exists()
        and (OUTPUT_ROOT / name).read_text(encoding="utf-8") != content
    )
    if stale or missing or changed:
        if stale:
            print("stale generated files:", ", ".join(stale), file=sys.stderr)
        if missing:
            print("missing generated files:", ", ".join(missing), file=sys.stderr)
        if changed:
            print("changed generated files:", ", ".join(changed), file=sys.stderr)
        return False
    return True


def write(sources):
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    expected_names = set(sources)
    for path in OUTPUT_ROOT.glob("*.java"):
        if path.name not in expected_names:
            path.unlink()
    for name, content in sources.items():
        (OUTPUT_ROOT / name).write_text(content, encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail when checked-in generated Java sources are stale",
    )
    args = parser.parse_args()
    sources = generated_sources()
    if args.check:
        return 0 if check(sources) else 1
    write(sources)
    print(f"generated {len(sources)} Java files in {OUTPUT_ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
