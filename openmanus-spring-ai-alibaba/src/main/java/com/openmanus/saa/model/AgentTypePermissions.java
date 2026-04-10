package com.openmanus.saa.model;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent类型权限预设工厂。
 * 为每种Agent类型提供预配置的工具权限集。
 */
@Component
public class AgentTypePermissions {

    /**
     * 根据Agent类型获取预设的工具权限集
     */
    public ToolPermissionSet forType(AgentType type) {
        return switch (type) {
            case EXPLORE -> createExplorePermissions();
            case PLAN -> createPlanPermissions();
            case GENERAL -> createGeneralPermissions();
            case BASH -> createBashPermissions();
            case CODE_REVIEW -> createCodeReviewPermissions();
        };
    }

    /**
     * EXPLORE类型权限：只读探索
     * - 允许：Read, Glob, Grep, WebFetch, WebSearch
     * - 禁止：Write, Edit, Bash, Delete
     */
    private ToolPermissionSet createExplorePermissions() {
        return ToolPermissionSet.builder()
            .allow("Read", "Glob", "Grep", "WebFetch", "WebSearch")
            .deny("Write", "Edit", "Bash", "Delete", "NotebookEdit")
            .readOnly("Read", "Glob", "Grep", "WebFetch", "WebSearch")
            .build();
    }

    /**
     * PLAN类型权限：只读 + 计划文档
     * - 允许：Read, Glob, Grep, WebFetch, Write(仅计划文档)
     * - 禁止：Bash, Delete, 代码修改
     */
    private ToolPermissionSet createPlanPermissions() {
        return ToolPermissionSet.builder()
            .allow("Read", "Glob", "Grep", "WebFetch", "WebSearch", "Write")
            .deny("Bash", "Delete", "NotebookEdit")
            .readOnly("Read", "Glob", "Grep", "WebFetch", "WebSearch")
            .constraints("Write", ToolPermissionSet.ToolConstraints.restricted(
                List.of("./plans/", "./docs/"),
                List.of()
            ))
            .build();
    }

    /**
     * GENERAL类型权限：完整访问
     * - 允许：所有工具
     */
    private ToolPermissionSet createGeneralPermissions() {
        return ToolPermissionSet.fullAccess();
    }

    /**
     * BASH类型权限：命令执行
     * - 允许：Bash, Read
     * - 约束：限制危险命令
     */
    private ToolPermissionSet createBashPermissions() {
        ToolPermissionSet.ToolConstraints bashConstraints = new ToolPermissionSet.ToolConstraints(
            -1,
            List.of(),  // 允许所有路径
            List.of(),  // 无禁止路径
            List.of(    // 禁止的危险命令
                "rm -rf /",
                "rm -rf /*",
                "shutdown",
                "reboot",
                "mkfs",
                "dd if=",
                ":(){ :|:& };:",
                "chmod -R 777 /",
                "chown -R",
                "> /dev/sda",
                "mv /* /dev/null"
            )
        );

        return ToolPermissionSet.builder()
            .allow("Bash", "Read", "Glob", "Grep")
            .deny("Write", "Edit", "Delete", "NotebookEdit")
            .constraints("Bash", bashConstraints)
            .build();
    }

    /**
     * CODE_REVIEW类型权限：只读审查
     * - 允许：Read, Glob, Grep, WebFetch
     * - 禁止：所有修改操作
     */
    private ToolPermissionSet createCodeReviewPermissions() {
        return ToolPermissionSet.builder()
            .allow("Read", "Glob", "Grep", "WebFetch", "WebSearch")
            .deny("Write", "Edit", "Bash", "Delete", "NotebookEdit")
            .readOnly("Read", "Glob", "Grep", "WebFetch", "WebSearch")
            .build();
    }

    /**
     * 合并自定义权限和类型预设权限
     */
    public ToolPermissionSet mergeWithCustom(AgentType type, ToolPermissionSet customPermissions) {
        if (customPermissions == null) {
            return forType(type);
        }
        if (customPermissions.isFullAccess()) {
            return ToolPermissionSet.fullAccess();
        }

        ToolPermissionSet basePermissions = forType(type);
        return ToolPermissionSet.builder()
            .allow(
                union(basePermissions.getAllowedTools(), customPermissions.getAllowedTools()).toArray(new String[0])
            )
            .deny(
                union(basePermissions.getDeniedTools(), customPermissions.getDeniedTools()).toArray(new String[0])
            )
            .readOnly(
                union(basePermissions.getReadOnlyTools(), customPermissions.getReadOnlyTools()).toArray(new String[0])
            )
            .fullAccess(false)
            .build();
    }

    private java.util.Set<String> union(java.util.Set<String> set1, java.util.Set<String> set2) {
        java.util.Set<String> result = new java.util.HashSet<>(set1);
        result.addAll(set2);
        return result;
    }
}
