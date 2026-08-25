package io.github.majiajustar.codex.sandbox;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 完整的 v2 轮次沙箱策略。
 *
 * <p>请使用具名工厂方法，让涉及安全的调用点保持语义清晰。策略会序列化为
 * {@code turn/start} 接受的 {@code sandboxPolicy} 可辨识联合类型。
 */
public sealed interface SandboxPolicy
        permits SandboxPolicy.DangerFullAccess,
                SandboxPolicy.ReadOnly,
                SandboxPolicy.ExternalSandbox,
                SandboxPolicy.WorkspaceWrite {

    /** 返回 app-server v2 接受的 JSON 表示。 */
    ObjectNode toJson();

    /** 允许不受限制的文件系统和网络访问。 */
    static DangerFullAccess dangerFullAccess() {
        return new DangerFullAccess();
    }

    /** 仅允许读取文件系统，并禁用网络访问。 */
    static ReadOnly readOnly() {
        return new ReadOnly(false);
    }

    /** 仅允许读取文件系统，并启用网络访问。 */
    static ReadOnly readOnlyWithNetwork() {
        return new ReadOnly(true);
    }

    /** 使用由 Codex 外部系统实施的沙箱。 */
    static ExternalSandbox externalSandbox(NetworkAccess networkAccess) {
        return new ExternalSandbox(networkAccess);
    }

    /** 创建工作区写入策略构建器。 */
    static WorkspaceWrite.Builder workspaceWrite() {
        return new WorkspaceWrite.Builder();
    }

    /** 不受限制的沙箱策略。 */
    record DangerFullAccess() implements SandboxPolicy {
        @Override
        public ObjectNode toJson() {
            return JsonSupport.MAPPER.createObjectNode().put("type", "dangerFullAccess");
        }
    }

    /**
     * 只读沙箱策略。
     *
     * @param networkAccess 是否允许访问外部网络
     */
    record ReadOnly(boolean networkAccess) implements SandboxPolicy {
        @Override
        public ObjectNode toJson() {
            return JsonSupport.MAPPER.createObjectNode()
                    .put("type", "readOnly")
                    .put("networkAccess", networkAccess);
        }
    }

    /**
     * 声明沙箱约束由嵌入 SDK 的应用负责实施。
     *
     * @param networkAccess 由外部系统实施的网络策略
     */
    record ExternalSandbox(NetworkAccess networkAccess) implements SandboxPolicy {
        public ExternalSandbox {
            if (networkAccess == null) throw new IllegalArgumentException("networkAccess is required");
        }

        @Override
        public ObjectNode toJson() {
            return JsonSupport.MAPPER.createObjectNode()
                    .put("type", "externalSandbox")
                    .put("networkAccess", networkAccess.wireValue);
        }
    }

    /**
     * 允许写入工作区和明确配置的根目录。
     *
     * @param writableRoots 额外的可写根目录
     * @param networkAccess 是否允许访问外部网络
     * @param excludeTmpdirEnvVar 是否排除进程环境变量指定的临时目录
     * @param excludeSlashTmp 在 Unix 系统中是否排除 {@code /tmp}
     */
    record WorkspaceWrite(
            List<Path> writableRoots,
            boolean networkAccess,
            boolean excludeTmpdirEnvVar,
            boolean excludeSlashTmp)
            implements SandboxPolicy {

        public WorkspaceWrite {
            writableRoots = List.copyOf(writableRoots);
        }

        @Override
        public ObjectNode toJson() {
            var json = JsonSupport.MAPPER.createObjectNode()
                    .put("type", "workspaceWrite")
                    .put("networkAccess", networkAccess)
                    .put("excludeTmpdirEnvVar", excludeTmpdirEnvVar)
                    .put("excludeSlashTmp", excludeSlashTmp);
            var roots = json.putArray("writableRoots");
            writableRoots.stream().map(Path::toString).forEach(roots::add);
            return json;
        }

        /** 用于构建工作区写入策略。 */
        public static final class Builder {
            private final List<Path> writableRoots = new ArrayList<>();
            private boolean networkAccess;
            private boolean excludeTmpdirEnvVar;
            private boolean excludeSlashTmp;

            /** 添加一个可写根目录。 */
            public Builder writableRoot(Path value) {
                writableRoots.add(value);
                return this;
            }

            /** 添加所有指定的可写根目录。 */
            public Builder writableRoots(List<Path> values) {
                writableRoots.addAll(values);
                return this;
            }

            /** 启用或禁用外部网络访问。 */
            public Builder networkAccess(boolean value) {
                networkAccess = value;
                return this;
            }

            /** 控制是否排除环境变量选定的临时目录。 */
            public Builder excludeTmpdirEnvVar(boolean value) {
                excludeTmpdirEnvVar = value;
                return this;
            }

            /** 控制在 Unix 系统中是否排除 {@code /tmp}。 */
            public Builder excludeSlashTmp(boolean value) {
                excludeSlashTmp = value;
                return this;
            }

            /** 创建不可变的工作区写入策略。 */
            public WorkspaceWrite build() {
                return new WorkspaceWrite(
                        writableRoots,
                        networkAccess,
                        excludeTmpdirEnvVar,
                        excludeSlashTmp);
            }
        }
    }

    /** 沙箱由外部实施时使用的网络策略。 */
    enum NetworkAccess {
        RESTRICTED("restricted"),
        ENABLED("enabled");

        private final String wireValue;

        NetworkAccess(String wireValue) {
            this.wireValue = wireValue;
        }
    }
}
