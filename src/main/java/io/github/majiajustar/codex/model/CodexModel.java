package io.github.majiajustar.codex.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Model metadata returned by {@code model/list}. */
public record CodexModel(
        String id,
        String model,
        String displayName,
        String description,
        String modelSpecialty,
        boolean hidden,
        boolean isDefault,
        String defaultReasoningEffort,
        List<ReasoningEffort> supportedReasoningEfforts,
        List<String> inputModalities,
        boolean supportsPersonality,
        String multiAgentVersion,
        List<ServiceTier> serviceTiers,
        String defaultServiceTier,
        String upgrade,
        UpgradeInfo upgradeInfo,
        String availabilityMessage,
        JsonNode raw) {
    public CodexModel {
        supportedReasoningEfforts = List.copyOf(supportedReasoningEfforts);
        inputModalities = List.copyOf(inputModalities);
        serviceTiers = List.copyOf(serviceTiers);
    }

    /** Parse one model while retaining its complete original payload. */
    public static CodexModel from(JsonNode value) {
        var efforts = new ArrayList<ReasoningEffort>();
        value.path("supportedReasoningEfforts").forEach(option -> efforts.add(new ReasoningEffort(
                option.path("reasoningEffort").asText(), option.path("description").asText())));
        var modalities = new ArrayList<String>();
        value.path("inputModalities").forEach(item -> modalities.add(item.asText()));
        var tiers = new ArrayList<ServiceTier>();
        value.path("serviceTiers").forEach(tier -> tiers.add(new ServiceTier(
                tier.path("id").asText(),
                tier.path("name").asText(),
                tier.path("description").asText())));
        var upgradeNode = value.get("upgradeInfo");
        var upgradeInfo = upgradeNode == null || upgradeNode.isNull()
                ? null
                : new UpgradeInfo(
                        upgradeNode.path("model").asText(),
                        nullableText(upgradeNode, "upgradeCopy"),
                        nullableText(upgradeNode, "modelLink"),
                        nullableText(upgradeNode, "migrationMarkdown"),
                        nullableLong(upgradeNode, "retirementAt"));
        var nux = value.get("availabilityNux");
        return new CodexModel(
                value.path("id").asText(),
                value.path("model").asText(),
                value.path("displayName").asText(),
                value.path("description").asText(),
                nullableText(value, "modelSpecialty"),
                value.path("hidden").asBoolean(),
                value.path("isDefault").asBoolean(),
                value.path("defaultReasoningEffort").asText(),
                efforts,
                modalities,
                value.path("supportsPersonality").asBoolean(),
                nullableText(value, "multiAgentVersion"),
                tiers,
                nullableText(value, "defaultServiceTier"),
                nullableText(value, "upgrade"),
                upgradeInfo,
                nux == null || nux.isNull() ? null : nullableText(nux, "message"),
                value);
    }

    /** One supported reasoning-effort choice. */
    public record ReasoningEffort(String id, String description) {}

    /** One service tier advertised for this model. */
    public record ServiceTier(String id, String name, String description) {}

    /** Optional migration metadata for a replacement model. */
    public record UpgradeInfo(
            String model,
            String upgradeCopy,
            String modelLink,
            String migrationMarkdown,
            Long retirementAt) {}

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long nullableLong(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.longValue();
    }
}
