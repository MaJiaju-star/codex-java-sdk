package io.github.majiajustar.codex.skills;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 发现、刷新和启停本地 Codex Skills。 */
public final class SkillsClient {
    private final CodexClient codex;

    public SkillsClient(CodexClient codex) {
        this.codex = Objects.requireNonNull(codex, "codex");
    }

    /** 使用 app-server 当前工作目录列出 Skills。 */
    public SkillsListResult list() {
        return list(SkillsListOptions.defaults());
    }

    /** 按指定工作目录列出 Skills。 */
    public SkillsListResult list(SkillsListOptions options) {
        Objects.requireNonNull(options, "options");
        return SkillsListResult.from(codex.request("skills/list", options.toJson()));
    }

    /** 替换本次 app-server 会话使用的额外 Skill 搜索目录。 */
    public void setExtraRoots(List<Path> roots) {
        var params = JsonSupport.MAPPER.createObjectNode();
        var values = params.putArray("extraRoots");
        roots.forEach(path -> values.add(path.toString()));
        codex.request("skills/extraRoots/set", params);
    }

    /** 按稳定名称启用 Skill，并返回最终生效状态。 */
    public boolean enableByName(String name) {
        return setEnabled("name", name, true);
    }

    /** 按稳定名称禁用 Skill，并返回最终生效状态。 */
    public boolean disableByName(String name) {
        return setEnabled("name", name, false);
    }

    /** 按绝对路径启用 Skill，并返回最终生效状态。 */
    public boolean enable(Path path) {
        return setEnabled("path", path.toString(), true);
    }

    /** 按绝对路径禁用 Skill，并返回最终生效状态。 */
    public boolean disable(Path path) {
        return setEnabled("path", path.toString(), false);
    }

    private boolean setEnabled(String selector, String value, boolean enabled) {
        var params = JsonSupport.MAPPER.createObjectNode().put(selector, value).put("enabled", enabled);
        return codex.request("skills/config/write", params)
                .path("effectiveEnabled")
                .asBoolean();
    }
}
