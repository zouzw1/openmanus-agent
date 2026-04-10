package com.openmanus.saa.model;

/**
 * Agent类型枚举，定义专业化的Agent类型。
 * 借鉴Claude Code的subagent_type设计，每种类型有不同的工具权限和执行策略。
 */
public enum AgentType {

    /**
     * 代码探索专家 - 快速代码库探索
     * 工具权限：只读操作优先（Read, Glob, Grep, WebFetch）
     * 特点：快速、轻量、不修改文件
     */
    EXPLORE("explore", "代码探索专家", true),

    /**
     * 规划专家 - 生成实施计划
     * 工具权限：只读 + 计划文档写入
     * 特点：禁止直接修改代码，只生成计划
     */
    PLAN("plan", "规划专家", true),

    /**
     * 通用任务 - 通用任务执行
     * 工具权限：完整访问
     * 特点：适用于复杂、多步骤任务
     */
    GENERAL("general", "通用任务", false),

    /**
     * 命令执行专家 - Shell命令执行
     * 工具权限：Bash工具，带安全限制
     * 特点：执行Git、npm、Docker等命令
     */
    BASH("bash", "命令执行专家", false),

    /**
     * 代码审查专家 - Bug与安全审查
     * 工具权限：只读操作 + 评论写入
     * 特点：专注于代码质量分析
     */
    CODE_REVIEW("code-review", "代码审查专家", true);

    private final String code;
    private final String description;
    private final boolean readOnlyPreferred;

    AgentType(String code, String description, boolean readOnlyPreferred) {
        this.code = code;
        this.description = description;
        this.readOnlyPreferred = readOnlyPreferred;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否偏好只读操作
     */
    public boolean isReadOnlyPreferred() {
        return readOnlyPreferred;
    }

    /**
     * 根据code获取AgentType
     */
    public static AgentType fromCode(String code) {
        if (code == null) {
            return GENERAL;
        }
        for (AgentType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return GENERAL;
    }
}
