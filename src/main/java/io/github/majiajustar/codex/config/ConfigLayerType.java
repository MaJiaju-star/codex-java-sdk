package io.github.majiajustar.codex.config;

/** Codex 配置层来源。 */
public enum ConfigLayerType {
    PACKAGED_DEFAULTS("packagedDefaults"),
    MDM("mdm"),
    SYSTEM("system"),
    ENTERPRISE_MANAGED("enterpriseManaged"),
    USER("user"),
    PROJECT("project"),
    SESSION_FLAGS("sessionFlags"),
    LEGACY_MANAGED_FILE("legacyManagedConfigTomlFromFile"),
    LEGACY_MANAGED_MDM("legacyManagedConfigTomlFromMdm"),
    UNKNOWN(null);

    private final String wireValue;

    ConfigLayerType(String wireValue) {
        this.wireValue = wireValue;
    }

    static ConfigLayerType fromWireValue(String value) {
        for (var type : values()) {
            if (type.wireValue != null && type.wireValue.equals(value)) return type;
        }
        return UNKNOWN;
    }
}
