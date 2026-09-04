package io.github.majiajustar.codex.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** 企业或管理员向 Codex 施加的配置约束。 */
public record ConfigRequirements(JsonNode raw) {
    /** 未配置 requirements.toml 或 MDM 规则时返回空。 */
    public boolean isEmpty() {
        return raw == null || raw.isNull() || raw.isMissingNode();
    }

    /** 返回允许使用的 sandbox mode；未设置限制时返回空 Optional。 */
    public Optional<Set<String>> allowedSandboxModes() {
        return strings("allowedSandboxModes");
    }

    /** 返回允许使用的 web search mode；未设置限制时返回空 Optional。 */
    public Optional<Set<String>> allowedWebSearchModes() {
        return strings("allowedWebSearchModes");
    }

    /** 返回是否允许 Browser/Computer Use；未设置时为空。 */
    public Optional<Boolean> allowBrowserAndComputerUse() {
        var value = raw == null ? null : raw.get("allowBrowserAndComputerUse");
        return value == null || !value.isBoolean() ? Optional.empty() : Optional.of(value.asBoolean());
    }

    /** 返回管理员附加给 Agent 的开发说明。 */
    public Optional<String> additionalDeveloperInstructions() {
        var value = raw == null ? null : raw.get("additionalDeveloperInstructions");
        return value == null || !value.isTextual() ? Optional.empty() : Optional.of(value.asText());
    }

    private Optional<Set<String>> strings(String field) {
        var node = raw == null ? null : raw.get(field);
        if (node == null || !node.isArray()) return Optional.empty();
        var values = new LinkedHashSet<String>();
        node.forEach(value -> values.add(value.asText()));
        return Optional.of(Set.copyOf(values));
    }
}
