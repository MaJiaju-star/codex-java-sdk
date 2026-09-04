package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.Objects;

/** 写入单个配置值的请求。 */
public record ConfigWriteRequest(
        String keyPath,
        JsonNode value,
        MergeStrategy mergeStrategy,
        Path filePath,
        String expectedVersion) {
    public ConfigWriteRequest {
        if (keyPath == null || keyPath.isBlank()) throw new IllegalArgumentException("keyPath must not be blank");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(mergeStrategy, "mergeStrategy");
    }

    /** 写入用户 config.toml，并替换指定 key 的值。 */
    public static ConfigWriteRequest replace(String keyPath, Object value) {
        return new ConfigWriteRequest(
                keyPath, JsonSupport.MAPPER.valueToTree(value), MergeStrategy.REPLACE, null, null);
    }

    /** 创建完整请求 Builder。 */
    public static Builder builder(String keyPath, Object value) {
        return new Builder(keyPath, JsonSupport.MAPPER.valueToTree(value));
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode()
                .put("keyPath", keyPath)
                .put("mergeStrategy", mergeStrategy.wireValue());
        json.set("value", value);
        if (filePath != null) json.put("filePath", filePath.toString());
        if (expectedVersion != null) json.put("expectedVersion", expectedVersion);
        return json;
    }

    /** 单值配置写入构建器。 */
    public static final class Builder {
        private final String keyPath;
        private final JsonNode value;
        private MergeStrategy mergeStrategy = MergeStrategy.REPLACE;
        private Path filePath;
        private String expectedVersion;

        private Builder(String keyPath, JsonNode value) {
            this.keyPath = keyPath;
            this.value = value;
        }

        public Builder mergeStrategy(MergeStrategy mergeStrategy) {
            this.mergeStrategy = mergeStrategy;
            return this;
        }

        public Builder filePath(Path filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder expectedVersion(String expectedVersion) {
            this.expectedVersion = expectedVersion;
            return this;
        }

        public ConfigWriteRequest build() {
            return new ConfigWriteRequest(keyPath, value, mergeStrategy, filePath, expectedVersion);
        }
    }
}
