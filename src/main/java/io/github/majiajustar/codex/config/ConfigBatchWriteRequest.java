package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 在一次 app-server 请求中写入多项配置。 */
public record ConfigBatchWriteRequest(
        List<ConfigEdit> edits,
        Path filePath,
        String expectedVersion,
        boolean reloadUserConfig) {
    public ConfigBatchWriteRequest {
        edits = List.copyOf(edits);
        if (edits.isEmpty()) throw new IllegalArgumentException("edits must not be empty");
    }

    /** 创建批量写入 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode();
        var values = json.putArray("edits");
        edits.forEach(edit -> values.add(edit.toJson()));
        if (filePath != null) json.put("filePath", filePath.toString());
        if (expectedVersion != null) json.put("expectedVersion", expectedVersion);
        json.put("reloadUserConfig", reloadUserConfig);
        return json;
    }

    /** 批量配置写入构建器。 */
    public static final class Builder {
        private final List<ConfigEdit> edits = new ArrayList<>();
        private Path filePath;
        private String expectedVersion;
        private boolean reloadUserConfig;

        public Builder edit(ConfigEdit edit) {
            edits.add(edit);
            return this;
        }

        public Builder replace(String keyPath, Object value) {
            return edit(ConfigEdit.replace(keyPath, value));
        }

        public Builder upsert(String keyPath, Object value) {
            return edit(ConfigEdit.upsert(keyPath, value));
        }

        public Builder filePath(Path filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder expectedVersion(String expectedVersion) {
            this.expectedVersion = expectedVersion;
            return this;
        }

        public Builder reloadUserConfig(boolean reloadUserConfig) {
            this.reloadUserConfig = reloadUserConfig;
            return this;
        }

        public ConfigBatchWriteRequest build() {
            return new ConfigBatchWriteRequest(edits, filePath, expectedVersion, reloadUserConfig);
        }
    }
}
