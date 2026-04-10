package com.openmanus.saa.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具权限集，控制Agent可以访问的工具和操作。
 * 支持允许/禁止列表、只读限制和路径约束。
 */
public class ToolPermissionSet {

    private final Set<String> allowedTools;
    private final Set<String> deniedTools;
    private final Set<String> readOnlyTools;
    private final Map<String, ToolConstraints> toolConstraints;
    private final boolean fullAccess;

    /**
     * 工具约束配置
     */
    public record ToolConstraints(
        int maxFileSize,
        List<String> allowedPaths,
        List<String> deniedPaths,
        List<String> deniedCommands
    ) {
        public static ToolConstraints none() {
            return new ToolConstraints(-1, List.of(), List.of(), List.of());
        }

        public static ToolConstraints readOnly() {
            return new ToolConstraints(-1, List.of(), List.of(), List.of());
        }

        public static ToolConstraints restricted(List<String> allowedPaths, List<String> deniedCommands) {
            return new ToolConstraints(-1, allowedPaths, List.of(), deniedCommands);
        }
    }

    private ToolPermissionSet(
            Set<String> allowedTools,
            Set<String> deniedTools,
            Set<String> readOnlyTools,
            Map<String, ToolConstraints> toolConstraints,
            boolean fullAccess
    ) {
        this.allowedTools = new HashSet<>(allowedTools);
        this.deniedTools = new HashSet<>(deniedTools);
        this.readOnlyTools = new HashSet<>(readOnlyTools);
        this.toolConstraints = new HashMap<>(toolConstraints);
        this.fullAccess = fullAccess;
    }

    /**
     * 创建完全访问权限
     */
    public static ToolPermissionSet fullAccess() {
        return new ToolPermissionSet(
            Set.of(), Set.of(), Set.of(), Map.of(), true
        );
    }

    /**
     * 创建只读权限
     */
    public static ToolPermissionSet readOnly() {
        return new ToolPermissionSet(
            Set.of("Read", "Glob", "Grep", "WebFetch"),
            Set.of("Write", "Edit", "Bash", "Delete"),
            Set.of("Read", "Glob", "Grep", "WebFetch"),
            Map.of(),
            false
        );
    }

    /**
     * 创建受限权限（无工具）
     */
    public static ToolPermissionSet restricted() {
        return new ToolPermissionSet(
            Set.of(), Set.of(), Set.of(), Map.of(), false
        );
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 检查是否可以访问指定工具
     */
    public boolean canAccess(String toolName) {
        if (fullAccess) {
            return true;
        }
        if (deniedTools.contains(toolName)) {
            return false;
        }
        return allowedTools.isEmpty() || allowedTools.contains(toolName);
    }

    /**
     * 检查工具是否为只读
     */
    public boolean isReadOnly(String toolName) {
        return readOnlyTools.contains(toolName);
    }

    /**
     * 获取工具约束
     */
    public ToolConstraints getConstraints(String toolName) {
        return toolConstraints.getOrDefault(toolName, ToolConstraints.none());
    }

    /**
     * 添加允许的工具
     */
    public ToolPermissionSet addTools(String... tools) {
        Collections.addAll(allowedTools, tools);
        return this;
    }

    /**
     * 添加禁止的工具
     */
    public ToolPermissionSet denyTools(String... tools) {
        Collections.addAll(deniedTools, tools);
        return this;
    }

    /**
     * 设置工具约束
     */
    public ToolPermissionSet setConstraints(String toolName, ToolConstraints constraints) {
        toolConstraints.put(toolName, constraints);
        return this;
    }

    /**
     * 获取允许的工具集合
     */
    public Set<String> getAllowedTools() {
        return Collections.unmodifiableSet(allowedTools);
    }

    /**
     * 获取禁止的工具集合
     */
    public Set<String> getDeniedTools() {
        return Collections.unmodifiableSet(deniedTools);
    }

    /**
     * 获取只读工具集合
     */
    public Set<String> getReadOnlyTools() {
        return Collections.unmodifiableSet(readOnlyTools);
    }

    /**
     * 是否为完全访问权限
     */
    public boolean isFullAccess() {
        return fullAccess;
    }

    /**
     * Builder类
     */
    public static class Builder {
        private final Set<String> allowedTools = new HashSet<>();
        private final Set<String> deniedTools = new HashSet<>();
        private final Set<String> readOnlyTools = new HashSet<>();
        private final Map<String, ToolConstraints> toolConstraints = new HashMap<>();
        private boolean fullAccess = false;

        public Builder allow(String... tools) {
            Collections.addAll(allowedTools, tools);
            return this;
        }

        public Builder deny(String... tools) {
            Collections.addAll(deniedTools, tools);
            return this;
        }

        public Builder readOnly(String... tools) {
            Collections.addAll(readOnlyTools, tools);
            return this;
        }

        public Builder constraints(String toolName, ToolConstraints constraints) {
            toolConstraints.put(toolName, constraints);
            return this;
        }

        public Builder fullAccess(boolean fullAccess) {
            this.fullAccess = fullAccess;
            return this;
        }

        public ToolPermissionSet build() {
            return new ToolPermissionSet(
                allowedTools, deniedTools, readOnlyTools, toolConstraints, fullAccess
            );
        }
    }
}
