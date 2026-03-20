package com.openmanus.saa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmanus.agent")
public class OpenManusProperties {

    private int maxSteps = 8;
    private String workspace = "./workspace";
    private boolean shellEnabled = false;
    private int shellTimeoutSeconds = 15;
    private boolean workflowUseDataAnalysisAgent = true;
    private String systemPrompt = "";

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public boolean isShellEnabled() {
        return shellEnabled;
    }

    public void setShellEnabled(boolean shellEnabled) {
        this.shellEnabled = shellEnabled;
    }

    public int getShellTimeoutSeconds() {
        return shellTimeoutSeconds;
    }

    public void setShellTimeoutSeconds(int shellTimeoutSeconds) {
        this.shellTimeoutSeconds = shellTimeoutSeconds;
    }

    public boolean isWorkflowUseDataAnalysisAgent() {
        return workflowUseDataAnalysisAgent;
    }

    public void setWorkflowUseDataAnalysisAgent(boolean workflowUseDataAnalysisAgent) {
        this.workflowUseDataAnalysisAgent = workflowUseDataAnalysisAgent;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
