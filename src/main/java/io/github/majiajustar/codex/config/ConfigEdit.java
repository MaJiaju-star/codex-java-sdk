package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.util.Objects;

/** 一项按点分隔 key path 执行的配置修改。 */
public record ConfigEdit(String keyPath, JsonNode value, MergeStrategy mergeStrategy) {
    public ConfigEdit {
        if (keyPath == null || keyPath.isBlank()) throw new IllegalArgumentException("keyPath must not be blank");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(mergeStrategy, "mergeStrategy");
    }

    /** 创建替换型修改。 */
    public static ConfigEdit replace(String keyPath, Object value) {
        return new ConfigEdit(keyPath, JsonSupport.MAPPER.valueToTree(value), MergeStrategy.REPLACE);
    }

    /** 创建对象合并型修改。 */
    public static ConfigEdit upsert(String keyPath, Object value) {
        return new ConfigEdit(keyPath, JsonSupport.MAPPER.valueToTree(value), MergeStrategy.UPSERT);
    }

    ObjectNode toJson() {
        return JsonSupport.MAPPER.createObjectNode()
                .put("keyPath", keyPath)
                .put("mergeStrategy", mergeStrategy.wireValue())
                .set("value", value);
    }
}
