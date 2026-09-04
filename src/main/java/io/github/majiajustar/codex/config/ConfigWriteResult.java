package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

/** app-server 完成配置写入后返回的版本及覆盖信息。 */
public record ConfigWriteResult(
        Path filePath,
        WriteStatus status,
        String version,
        OverriddenMetadata overriddenMetadata,
        JsonNode raw) {

    static ConfigWriteResult from(JsonNode value) {
        var overridden = value.path("overriddenMetadata");
        return new ConfigWriteResult(
                Path.of(value.path("filePath").asText()),
                WriteStatus.fromWireValue(value.path("status").asText()),
                value.path("version").asText(""),
                overridden.isObject() ? OverriddenMetadata.from(overridden) : null,
                value);
    }

    /** 写入是否被更高优先级配置覆盖。 */
    public enum WriteStatus {
        OK,
        OK_OVERRIDDEN,
        UNKNOWN;

        static WriteStatus fromWireValue(String value) {
            return switch (value) {
                case "ok" -> OK;
                case "okOverridden" -> OK_OVERRIDDEN;
                default -> UNKNOWN;
            };
        }
    }

    /** 已写入但未最终生效时，描述真正的生效值及覆盖层。 */
    public record OverriddenMetadata(
            JsonNode effectiveValue, String message, ConfigLayerMetadata overridingLayer) {
        static OverriddenMetadata from(JsonNode value) {
            return new OverriddenMetadata(
                    value.path("effectiveValue"),
                    value.path("message").asText(""),
                    ConfigLayerMetadata.from(value.path("overridingLayer")));
        }
    }
}
