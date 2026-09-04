package io.github.majiajustar.codex.config;

import io.github.majiajustar.codex.CodexClient;
import java.util.Objects;

/** 读取分层配置、持久化配置值并检查管理员约束。 */
public final class ConfigClient {
    private final CodexClient codex;

    public ConfigClient(CodexClient codex) {
        this.codex = Objects.requireNonNull(codex, "codex");
    }

    /** 读取当前工作目录下最终生效的配置。 */
    public ConfigSnapshot read() {
        return read(ConfigReadOptions.defaults());
    }

    /** 读取指定工作目录下最终生效的配置及可选分层信息。 */
    public ConfigSnapshot read(ConfigReadOptions options) {
        Objects.requireNonNull(options, "options");
        return ConfigSnapshot.from(codex.request("config/read", options.toJson()));
    }

    /** 写入一个配置值。 */
    public ConfigWriteResult write(ConfigWriteRequest request) {
        Objects.requireNonNull(request, "request");
        return ConfigWriteResult.from(codex.request("config/value/write", request.toJson()));
    }

    /** 在一个请求中写入多项配置。 */
    public ConfigWriteResult batchWrite(ConfigBatchWriteRequest request) {
        Objects.requireNonNull(request, "request");
        return ConfigWriteResult.from(codex.request("config/batchWrite", request.toJson()));
    }

    /** 读取企业配置或 MDM 对可选设置施加的限制。 */
    public ConfigRequirements requirements() {
        var requirements = codex.request("configRequirements/read", null).path("requirements");
        return new ConfigRequirements(requirements);
    }
}
