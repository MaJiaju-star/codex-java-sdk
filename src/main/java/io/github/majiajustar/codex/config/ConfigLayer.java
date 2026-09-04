package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;

/** 一个参与合并的原始配置层。 */
public record ConfigLayer(
        ConfigLayerMetadata metadata, JsonNode config, String disabledReason, JsonNode raw) {

    static ConfigLayer from(JsonNode value) {
        var metadata = ConfigLayerMetadata.from(value);
        var disabled = value.path("disabledReason");
        return new ConfigLayer(
                metadata,
                value.path("config"),
                disabled.isTextual() ? disabled.asText() : null,
                value);
    }
}
