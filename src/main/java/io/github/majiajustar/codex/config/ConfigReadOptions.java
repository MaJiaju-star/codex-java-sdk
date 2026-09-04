package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;

/** 读取指定工作目录下最终生效配置的选项。 */
public record ConfigReadOptions(Path workingDirectory, boolean includeLayers) {
    /** 读取当前工作目录的最终配置，但不携带完整原始层。 */
    public static ConfigReadOptions defaults() {
        return new ConfigReadOptions(null, false);
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode().put("includeLayers", includeLayers);
        if (workingDirectory != null) json.put("cwd", workingDirectory.toString());
        return json;
    }
}
