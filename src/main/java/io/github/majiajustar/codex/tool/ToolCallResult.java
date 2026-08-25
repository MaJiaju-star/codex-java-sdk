package io.github.majiajustar.codex.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 传递给拦截器和观察器的工具调用完成数据。
 *
 * @param context 为工具调用记录的标识及元数据
 * @param successful 协议项是否在未报告失败的情况下完成
 * @param error 错误文本或序列化后的错误值；可能为 {@code null}
 * @param item 已完成的原始 app-server 项
 */
public record ToolCallResult(ToolCallContext context, boolean successful, String error, JsonNode item) {}
