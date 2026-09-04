package io.github.majiajustar.codex.skills;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Skill 扫描的工作目录和缓存策略。 */
public record SkillsListOptions(List<Path> workingDirectories, boolean forceReload) {
    public SkillsListOptions {
        workingDirectories = List.copyOf(workingDirectories);
    }

    /** 使用 app-server 当前工作目录和现有缓存。 */
    public static SkillsListOptions defaults() {
        return new SkillsListOptions(List.of(), false);
    }

    /** 创建查询选项 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode();
        var cwds = json.putArray("cwds");
        workingDirectories.forEach(path -> cwds.add(path.toString()));
        json.put("forceReload", forceReload);
        return json;
    }

    /** Skill 查询选项构建器。 */
    public static final class Builder {
        private final List<Path> workingDirectories = new ArrayList<>();
        private boolean forceReload;

        public Builder workingDirectory(Path path) {
            workingDirectories.add(path);
            return this;
        }

        public Builder workingDirectories(List<Path> paths) {
            workingDirectories.clear();
            workingDirectories.addAll(paths);
            return this;
        }

        public Builder forceReload(boolean forceReload) {
            this.forceReload = forceReload;
            return this;
        }

        public SkillsListOptions build() {
            return new SkillsListOptions(workingDirectories, forceReload);
        }
    }
}
