package io.github.majiajustar.codex.skills;

/** Skill 的发现范围。 */
public enum SkillScope {
    USER("user"),
    REPO("repo"),
    SYSTEM("system"),
    ADMIN("admin"),
    UNKNOWN(null);

    private final String wireValue;

    SkillScope(String wireValue) {
        this.wireValue = wireValue;
    }

    static SkillScope fromWireValue(String value) {
        for (var scope : values()) {
            if (scope.wireValue != null && scope.wireValue.equals(value)) return scope;
        }
        return UNKNOWN;
    }
}
