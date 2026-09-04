package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;

/** 标识某个生效值来自哪个配置层及该层版本。 */
public record ConfigLayerMetadata(
        ConfigLayerType type, String sourceType, String version, JsonNode source) {

    static ConfigLayerMetadata from(JsonNode value) {
        var source = value.path("name");
        var sourceType = source.path("type").asText("");
        return new ConfigLayerMetadata(
                ConfigLayerType.fromWireValue(sourceType),
                sourceType,
                value.path("version").asText(""),
                source);
    }
}
