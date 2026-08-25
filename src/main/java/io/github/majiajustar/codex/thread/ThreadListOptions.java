package io.github.majiajustar.codex.thread;

import io.github.majiajustar.codex.generated.v2.SortDirection;
import io.github.majiajustar.codex.generated.v2.ThreadListParams;
import io.github.majiajustar.codex.generated.v2.ThreadSortKey;
import io.github.majiajustar.codex.generated.v2.ThreadSourceKind;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code thread/list} 的强类型过滤器和游标分页配置。
 *
 * @param archived {@code true} 表示已归档会话；{@code false} 或 {@code null} 表示活动会话
 * @param cursor 上一页返回的不透明游标
 * @param workingDirectories 精确匹配的工作目录过滤条件
 * @param limit 正数页大小；{@code null} 表示使用服务器默认值
 * @param modelProviders 模型提供方过滤条件
 * @param searchTerm 应用于会话标题的子字符串过滤条件
 * @param sectionId 可选的项目分区标识
 * @param sortDirection 结果排序方向
 * @param sortKey 用于排序的字段
 * @param sourceKinds 会话来源过滤条件
 * @param useStateDbOnly 是否跳过 rollout 扫描和状态修复
 */
public record ThreadListOptions(
        Boolean archived,
        String cursor,
        List<Path> workingDirectories,
        Integer limit,
        List<String> modelProviders,
        String searchTerm,
        String sectionId,
        SortDirection sortDirection,
        ThreadSortKey sortKey,
        List<ThreadSourceKind> sourceKinds,
        Boolean useStateDbOnly) {

    public ThreadListOptions {
        workingDirectories = copyOrNull(workingDirectories);
        modelProviders = copyOrNull(modelProviders);
        sourceKinds = copyOrNull(sourceKinds);
        if (limit != null && limit < 1) throw new IllegalArgumentException("limit must be at least 1");
    }

    /** 返回完全采用 app-server 列表默认设置的选项。 */
    public static ThreadListOptions defaults() {
        return builder().build();
    }

    /** 返回空的强类型列表选项构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public ThreadListParams toParams() {
        var cwd = workingDirectories == null
                ? null
                : JsonSupport.MAPPER.valueToTree(
                        workingDirectories.stream().map(Path::toString).toList());
        return new ThreadListParams(
                archived,
                cursor,
                cwd,
                limit,
                modelProviders,
                searchTerm,
                sectionId,
                sortDirection,
                sortKey,
                sourceKinds,
                useStateDbOnly);
    }

    private static <T> List<T> copyOrNull(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    /** 用于构建不可变的会话列表过滤条件。 */
    public static final class Builder {
        private Boolean archived;
        private String cursor;
        private List<Path> workingDirectories;
        private Integer limit;
        private List<String> modelProviders;
        private String searchTerm;
        private String sectionId;
        private SortDirection sortDirection;
        private ThreadSortKey sortKey;
        private List<ThreadSourceKind> sourceKinds;
        private Boolean useStateDbOnly;

        /** 选择已归档或活动会话。 */
        public Builder archived(boolean value) {
            archived = value;
            return this;
        }

        /** 从服务器返回的不透明游标继续分页。 */
        public Builder cursor(String value) {
            cursor = value;
            return this;
        }

        /** 按一个精确工作目录过滤。 */
        public Builder workingDirectory(Path value) {
            workingDirectories = List.of(value);
            return this;
        }

        /** 按任一指定的精确工作目录过滤。 */
        public Builder workingDirectories(List<Path> values) {
            workingDirectories = new ArrayList<>(values);
            return this;
        }

        /** 设置正数的最大页大小。 */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /** 按模型提供方名称过滤。 */
        public Builder modelProviders(List<String> values) {
            modelProviders = new ArrayList<>(values);
            return this;
        }

        /** 按会话标题的子字符串过滤。 */
        public Builder searchTerm(String value) {
            searchTerm = value;
            return this;
        }

        /** 仅返回指定项目分区中的会话。 */
        public Builder sectionId(String value) {
            sectionId = value;
            return this;
        }

        /** 设置结果升序或降序排列。 */
        public Builder sortDirection(SortDirection value) {
            sortDirection = value;
            return this;
        }

        /** 选择用于排序结果的时间戳字段。 */
        public Builder sortKey(ThreadSortKey value) {
            sortKey = value;
            return this;
        }

        /** 按会话来源类别过滤。 */
        public Builder sourceKinds(List<ThreadSourceKind> values) {
            sourceKinds = new ArrayList<>(values);
            return this;
        }

        /** 仅从状态数据库列出会话，不执行 rollout 扫描和修复。 */
        public Builder useStateDbOnly(boolean value) {
            useStateDbOnly = value;
            return this;
        }

        /** 创建不可变的会话列表选项。 */
        public ThreadListOptions build() {
            return new ThreadListOptions(
                    archived,
                    cursor,
                    workingDirectories,
                    limit,
                    modelProviders,
                    searchTerm,
                    sectionId,
                    sortDirection,
                    sortKey,
                    sourceKinds,
                    useStateDbOnly);
        }
    }
}
