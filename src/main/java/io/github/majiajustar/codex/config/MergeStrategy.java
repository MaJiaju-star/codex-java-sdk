package io.github.majiajustar.codex.config;

/** 写配置时替换整个值，或递归合并对象字段。 */
public enum MergeStrategy {
    REPLACE("replace"),
    UPSERT("upsert");

    private final String wireValue;

    MergeStrategy(String wireValue) {
        this.wireValue = wireValue;
    }

    String wireValue() {
        return wireValue;
    }
}
