package io.github.majiajustar.codex.thread;

import io.github.majiajustar.codex.generated.v2.SortDirection;
import io.github.majiajustar.codex.generated.v2.ThreadTurnsListParams;
import io.github.majiajustar.codex.generated.v2.TurnItemsView;

/**
 * {@code thread/turns/list} 的强类型分页配置。
 *
 * @param cursor 上一页返回的不透明游标
 * @param limit 页大小，取值范围为 1 至 100；{@code null} 表示使用服务器默认值
 * @param sortDirection Turn 排序方向；{@code null} 表示使用服务器默认值
 * @param itemsView 每个 Turn 返回的 Item 详情范围；{@code null} 表示使用服务器默认值
 */
public record ThreadTurnsListOptions(
        String cursor,
        Integer limit,
        SortDirection sortDirection,
        TurnItemsView itemsView) {

    /** 校验分页参数。 */
    public ThreadTurnsListOptions {
        if (limit != null && (limit < 1 || limit > 100)) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }

    /**
     * 返回完全采用 app-server 分页默认设置的选项。
     *
     * @return 默认分页选项
     */
    public static ThreadTurnsListOptions defaults() {
        return builder().build();
    }

    /**
     * 返回空的分页选项构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 转换为 app-server 协议参数。
     *
     * @param threadId 需要读取的 Thread ID
     * @return 强类型协议参数
     */
    public ThreadTurnsListParams toParams(String threadId) {
        return new ThreadTurnsListParams(cursor, itemsView, limit, sortDirection, threadId);
    }

    /** 用于构建不可变分页选项。 */
    public static final class Builder {
        private String cursor;
        private Integer limit;
        private SortDirection sortDirection;
        private TurnItemsView itemsView;

        /**
         * 设置上一页返回的不透明游标。
         *
         * @param value 分页游标
         * @return 当前构建器
         */
        public Builder cursor(String value) {
            cursor = value;
            return this;
        }

        /**
         * 设置页大小，取值范围为 1 至 100。
         *
         * @param value 页大小
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 设置 Turn 排序方向。
         *
         * @param value 排序方向
         * @return 当前构建器
         */
        public Builder sortDirection(SortDirection value) {
            sortDirection = value;
            return this;
        }

        /**
         * 设置每个 Turn 返回的 Item 详情范围。
         *
         * @param value Item 详情范围
         * @return 当前构建器
         */
        public Builder itemsView(TurnItemsView value) {
            itemsView = value;
            return this;
        }

        /**
         * 创建分页选项。
         *
         * @return 不可变分页选项
         */
        public ThreadTurnsListOptions build() {
            return new ThreadTurnsListOptions(cursor, limit, sortDirection, itemsView);
        }
    }
}
