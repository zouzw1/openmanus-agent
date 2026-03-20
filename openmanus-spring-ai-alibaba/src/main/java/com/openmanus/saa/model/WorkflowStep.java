package com.openmanus.saa.model;

import java.util.List;
import java.util.Map;

/**
 * 增强的 Workflow Step，包含工具 Schema 信息
 */
public record WorkflowStep(
        String agent,
        String description,
        List<String> requiredTools,           // 该步骤需要的工具列表
        Map<String, Object> parameterContext  // 从上下文中提取的参数值
) {
    // 向后兼容的构造函数
    public WorkflowStep(String agent, String description) {
        this(agent, description, null, null);
    }
    
    /**
     * 获取所需的第一个工具（主要工具）
     */
    public String primaryTool() {
        return (requiredTools != null && !requiredTools.isEmpty()) 
            ? requiredTools.get(0) 
            : null;
    }
}
