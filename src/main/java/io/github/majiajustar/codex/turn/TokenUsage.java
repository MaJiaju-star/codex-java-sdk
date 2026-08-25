package io.github.majiajustar.codex.turn;

import com.fasterxml.jackson.databind.JsonNode;

/** Token consumption for the current thread, split into cumulative and last-turn values. */
public record TokenUsage(Breakdown total, Breakdown last, Long modelContextWindow, JsonNode raw) {
    /** Parse the {@code tokenUsage} object from a thread usage notification. */
    public static TokenUsage from(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        return new TokenUsage(
                Breakdown.from(value.path("total")),
                Breakdown.from(value.path("last")),
                nullableLong(value, "modelContextWindow"),
                value);
    }

    /** Detailed token counters. */
    public record Breakdown(
            long totalTokens,
            long inputTokens,
            long cachedInputTokens,
            long cacheWriteInputTokens,
            long outputTokens,
            long reasoningOutputTokens) {
        static Breakdown from(JsonNode value) {
            return new Breakdown(
                    value.path("totalTokens").asLong(),
                    value.path("inputTokens").asLong(),
                    value.path("cachedInputTokens").asLong(),
                    value.path("cacheWriteInputTokens").asLong(),
                    value.path("outputTokens").asLong(),
                    value.path("reasoningOutputTokens").asLong());
        }
    }

    private static Long nullableLong(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.longValue();
    }
}
