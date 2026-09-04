package io.github.majiajustar.codex.skills;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 从 SKILL.md/SKILL.json 发现的 Skill 元数据。 */
public record SkillMetadata(
        String name,
        String description,
        String shortDescription,
        Path path,
        SkillScope scope,
        boolean enabled,
        String pluginId,
        SkillInterface interfaceInfo,
        SkillDependencies dependencies,
        JsonNode raw) {

    static SkillMetadata from(JsonNode value) {
        return new SkillMetadata(
                value.path("name").asText(""),
                value.path("description").asText(""),
                nullableText(value, "shortDescription"),
                Path.of(value.path("path").asText()),
                SkillScope.fromWireValue(value.path("scope").asText()),
                value.path("enabled").asBoolean(),
                nullableText(value, "pluginId"),
                value.path("interface").isObject() ? SkillInterface.from(value.path("interface")) : null,
                value.path("dependencies").isObject()
                        ? SkillDependencies.from(value.path("dependencies"))
                        : null,
                value);
    }

    /** Skill 的可视化展示信息。 */
    public record SkillInterface(
            String displayName,
            String shortDescription,
            String defaultPrompt,
            String brandColor,
            Path iconSmall,
            Path iconLarge,
            String iconSmallUrl,
            String iconLargeUrl) {

        static SkillInterface from(JsonNode value) {
            return new SkillInterface(
                    nullableText(value, "displayName"),
                    nullableText(value, "shortDescription"),
                    nullableText(value, "defaultPrompt"),
                    nullableText(value, "brandColor"),
                    nullablePath(value, "iconSmall"),
                    nullablePath(value, "iconLarge"),
                    nullableText(value, "iconSmallUrl"),
                    nullableText(value, "iconLargeUrl"));
        }
    }

    /** Skill 声明的工具依赖。 */
    public record SkillDependencies(List<SkillToolDependency> tools) {
        public SkillDependencies {
            tools = List.copyOf(tools);
        }

        static SkillDependencies from(JsonNode value) {
            var tools = new ArrayList<SkillToolDependency>();
            value.path("tools").forEach(tool -> tools.add(SkillToolDependency.from(tool)));
            return new SkillDependencies(tools);
        }
    }

    /** 单个 CLI、MCP 或远程工具依赖。 */
    public record SkillToolDependency(
            String type,
            String value,
            String command,
            String transport,
            String url,
            String description) {

        static SkillToolDependency from(JsonNode value) {
            return new SkillToolDependency(
                    value.path("type").asText(""),
                    value.path("value").asText(""),
                    nullableText(value, "command"),
                    nullableText(value, "transport"),
                    nullableText(value, "url"),
                    nullableText(value, "description"));
        }
    }

    private static String nullableText(JsonNode value, String field) {
        var node = value.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Path nullablePath(JsonNode value, String field) {
        var text = nullableText(value, field);
        return text == null ? null : Path.of(text);
    }
}
