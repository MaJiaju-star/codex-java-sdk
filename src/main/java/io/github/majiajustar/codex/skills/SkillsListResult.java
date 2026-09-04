package io.github.majiajustar.codex.skills;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 按工作目录分组的 Skill 扫描结果。 */
public record SkillsListResult(List<Entry> data, JsonNode raw) {
    public SkillsListResult {
        data = List.copyOf(data);
    }

    static SkillsListResult from(JsonNode value) {
        var entries = new ArrayList<Entry>();
        value.path("data").forEach(entry -> entries.add(Entry.from(entry)));
        return new SkillsListResult(entries, value);
    }

    /** 一个工作目录下可见的 Skills 和解析错误。 */
    public record Entry(Path workingDirectory, List<SkillMetadata> skills, List<SkillError> errors) {
        public Entry {
            skills = List.copyOf(skills);
            errors = List.copyOf(errors);
        }

        static Entry from(JsonNode value) {
            var skills = new ArrayList<SkillMetadata>();
            value.path("skills").forEach(skill -> skills.add(SkillMetadata.from(skill)));
            var errors = new ArrayList<SkillError>();
            value.path("errors").forEach(error -> errors.add(new SkillError(
                    Path.of(error.path("path").asText()), error.path("message").asText(""))));
            return new Entry(Path.of(value.path("cwd").asText()), skills, errors);
        }
    }

    /** 无法加载某个 Skill 文件时返回的诊断。 */
    public record SkillError(Path path, String message) {}
}
