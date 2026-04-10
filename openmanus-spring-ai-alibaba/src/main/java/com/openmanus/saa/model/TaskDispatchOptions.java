package com.openmanus.saa.model;

import java.util.List;

/**
 * 任务分发选项，控制任务执行的行为。
 * 借鉴Claude Code的Task工具参数设计。
 */
public record TaskDispatchOptions(
    /**
     * 是否后台执行
     * true: 异步执行，不阻塞主会话
     * false: 同步执行，等待结果返回
     */
    boolean background,

    /**
     * 隔离模式
     * null: 默认执行
     * "worktree": 在独立的Git工作副本中执行，实验性更改不会污染主分支
     */
    String isolation,

    /**
     * 指定使用的模型
     * null: 使用默认模型
     * "haiku": 轻量级模型
     * "sonnet": 中等模型
     * "opus": 高级模型
     */
    String model,

    /**
     * 最大交互轮次
     * 超过此轮次后强制结束任务
     */
    int maxTurns,

    /**
     * 执行模式
     */
    ExecutionMode mode
) {
    /**
     * 执行模式枚举
     */
    public enum ExecutionMode {
        /**
         * 自动选择 - 根据任务类型自动决定
         */
        AUTO,

        /**
         * 仅计划模式 - 只生成计划，不执行实际操作
         */
        PLAN,

        /**
         * 绕过权限 - 跳过工具权限检查（谨慎使用）
         */
        BYPASS_PERMISSIONS
    }

    /**
     * 默认配置
     */
    public static TaskDispatchOptions DEFAULT = new TaskDispatchOptions(
        false, null, null, 10, ExecutionMode.AUTO
    );

    /**
     * 后台执行配置
     */
    public static TaskDispatchOptions BACKGROUND = new TaskDispatchOptions(
        true, null, null, 10, ExecutionMode.AUTO
    );

    /**
     * 隔离模式配置
     */
    public static TaskDispatchOptions ISOLATED = new TaskDispatchOptions(
        false, "worktree", null, 10, ExecutionMode.AUTO
    );

    /**
     * 仅计划模式配置
     */
    public static TaskDispatchOptions PLAN_ONLY = new TaskDispatchOptions(
        false, null, null, 10, ExecutionMode.PLAN
    );

    /**
     * 便捷方法：创建指定模型的后台执行选项
     */
    public TaskDispatchOptions withModel(String model) {
        return new TaskDispatchOptions(
            this.background,
            this.isolation,
            model,
            this.maxTurns,
            this.mode
        );
    }

    /**
     * 便捷方法：创建指定轮次的执行选项
     */
    public TaskDispatchOptions withMaxTurns(int maxTurns) {
        return new TaskDispatchOptions(
            this.background,
            this.isolation,
            this.model,
            maxTurns,
            this.mode
        );
    }

    /**
     * 便捷方法：创建后台执行选项
     */
    public TaskDispatchOptions asBackground() {
        return new TaskDispatchOptions(
            true,
            this.isolation,
            this.model,
            this.maxTurns,
            this.mode
        );
    }

    /**
     * 便捷方法：创建隔离模式选项
     */
    public TaskDispatchOptions withIsolation() {
        return new TaskDispatchOptions(
            this.background,
            "worktree",
            this.model,
            this.maxTurns,
            this.mode
        );
    }

    /**
     * 便捷方法：创建计划模式选项
     */
    public TaskDispatchOptions asPlanOnly() {
        return new TaskDispatchOptions(
            false,
            this.isolation,
            this.model,
            this.maxTurns,
            ExecutionMode.PLAN
        );
    }

    /**
     * 检查是否为计划模式
     */
    public boolean isPlanOnly() {
        return mode == ExecutionMode.PLAN;
    }

    /**
     * 检查是否绕过权限
     */
    public boolean isBypassPermissions() {
        return mode == ExecutionMode.BYPASS_PERMISSIONS;
    }

    /**
     * 检查是否为隔离执行
     */
    public boolean isIsolated() {
        return "worktree".equals(isolation);
    }

    /**
     * Builder类用于更灵活的构造
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean background = false;
        private String isolation = null;
        private String model = null;
        private int maxTurns = 10;
        private ExecutionMode mode = ExecutionMode.AUTO;

        public Builder background(boolean background) {
            this.background = background;
            return this;
        }

        public Builder isolation(String isolation) {
            this.isolation = isolation;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public Builder mode(ExecutionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder planOnly() {
            this.mode = ExecutionMode.PLAN;
            return this;
        }

        public Builder bypassPermissions() {
            this.mode = ExecutionMode.BYPASS_PERMISSIONS;
            return this;
        }

        public TaskDispatchOptions build() {
            return new TaskDispatchOptions(
                background, isolation, model, maxTurns, mode
            );
        }
    }
}
