package com.openmanus.saa.agent;

public class AgentDefinition {

    private final String id;
    private final String name;
    private final boolean enabled;
    private final String executorType;
    private final String description;
    private final boolean planningVisible;
    private final int priority;
    private final String systemPrompt;
    private final String promptFile;
    private final IdAccessPolicy localTools;
    private final McpAccessPolicy mcp;
    private final IdAccessPolicy skills;

    public AgentDefinition(
            String id,
            String name,
            boolean enabled,
            String executorType,
            String description,
            boolean planningVisible,
            int priority,
            String systemPrompt,
            String promptFile,
            IdAccessPolicy localTools,
            McpAccessPolicy mcp,
            IdAccessPolicy skills
    ) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.executorType = executorType;
        this.description = description;
        this.planningVisible = planningVisible;
        this.priority = priority;
        this.systemPrompt = systemPrompt;
        this.promptFile = promptFile;
        this.localTools = localTools;
        this.mcp = mcp;
        this.skills = skills;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getExecutorType() {
        return executorType;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPlanningVisible() {
        return planningVisible;
    }

    public int getPriority() {
        return priority;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getPromptFile() {
        return promptFile;
    }

    public IdAccessPolicy getLocalTools() {
        return localTools;
    }

    public McpAccessPolicy getMcp() {
        return mcp;
    }

    public IdAccessPolicy getSkills() {
        return skills;
    }
}
