package io.github.majiajustar.codex.turn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Objects;

/** {@code turn/start} 和 {@code turn/steer} 接受的输入项。 */
public interface UserInput {
    /**
     * 将该输入项序列化为 app-server 表示。
     *
     * @param mapper 用于创建 JSON 节点的映射器
     * @return 序列化后的输入项
     */
    ObjectNode toJson(ObjectMapper mapper);

    /** 创建纯文本用户输入项。 */
    static UserInput text(String text) {
        Objects.requireNonNull(text, "text");
        return mapper -> mapper.createObjectNode().put("type", "text").put("text", text);
    }

    /** 使用 URL 创建远程图片输入项。 */
    static UserInput image(String url) {
        Objects.requireNonNull(url, "url");
        return mapper -> mapper.createObjectNode().put("type", "image").put("url", url);
    }

    /** 创建引用本地文件系统图片的输入项。 */
    static UserInput localImage(Path path) {
        Objects.requireNonNull(path, "path");
        return mapper -> mapper.createObjectNode().put("type", "localImage").put("path", path.toString());
    }

    /** 创建明确的 Skill 引用。 */
    static UserInput skill(String name, Path path) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        return mapper -> mapper.createObjectNode()
                .put("type", "skill")
                .put("name", name)
                .put("path", path.toString());
    }

    /** 创建具名文件或目录引用。 */
    static UserInput mention(String name, Path path) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        return mapper -> mapper.createObjectNode()
                .put("type", "mention")
                .put("name", name)
                .put("path", path.toString());
    }
}
