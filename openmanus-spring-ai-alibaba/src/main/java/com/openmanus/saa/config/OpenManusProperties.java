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
    private PlanningValidationProperties planningValidation = new PlanningValidationProperties();
    private MultiAgentProperties multiAgent = new MultiAgentProperties();

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

    public PlanningValidationProperties getPlanningValidation() {
        return planningValidation;
    }

    public void setPlanningValidation(PlanningValidationProperties planningValidation) {
        this.planningValidation = planningValidation == null
                ? new PlanningValidationProperties()
                : planningValidation;
    }

    public MultiAgentProperties getMultiAgent() {
        return multiAgent;
    }

    public void setMultiAgent(MultiAgentProperties multiAgent) {
        this.multiAgent = multiAgent == null ? new MultiAgentProperties() : multiAgent;
    }

    public static class MultiAgentProperties {
        private boolean enabled = false;
        private int maxTasks = 6;
        private int maxParallelAgents = 3;
        private int taskTimeoutSeconds = 300;
        private int messageQueueSize = 100;
        private int contextMaxTurns = 10;
        private int contextMaxChars = 8000;
        private DependencyResolution dependencyResolution = DependencyResolution.AUTO;
        private java.util.List<String> lifecycleHooks = java.util.List.of("logging");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxTasks() {
            return maxTasks;
        }

        public void setMaxTasks(int maxTasks) {
            this.maxTasks = maxTasks;
        }

        public int getMaxParallelAgents() {
            return maxParallelAgents;
        }

        public void setMaxParallelAgents(int maxParallelAgents) {
            this.maxParallelAgents = maxParallelAgents;
        }

        public int getTaskTimeoutSeconds() {
            return taskTimeoutSeconds;
        }

        public void setTaskTimeoutSeconds(int taskTimeoutSeconds) {
            this.taskTimeoutSeconds = taskTimeoutSeconds;
        }

        public int getMessageQueueSize() {
            return messageQueueSize;
        }

        public void setMessageQueueSize(int messageQueueSize) {
            this.messageQueueSize = messageQueueSize;
        }

        public int getContextMaxTurns() {
            return contextMaxTurns;
        }

        public void setContextMaxTurns(int contextMaxTurns) {
            this.contextMaxTurns = contextMaxTurns;
        }

        public int getContextMaxChars() {
            return contextMaxChars;
        }

        public void setContextMaxChars(int contextMaxChars) {
            this.contextMaxChars = contextMaxChars;
        }

        public DependencyResolution getDependencyResolution() {
            return dependencyResolution;
        }

        public void setDependencyResolution(DependencyResolution dependencyResolution) {
            this.dependencyResolution = dependencyResolution;
        }

        public java.util.List<String> getLifecycleHooks() {
            return lifecycleHooks;
        }

        public void setLifecycleHooks(java.util.List<String> lifecycleHooks) {
            this.lifecycleHooks = lifecycleHooks == null ? java.util.List.of() : java.util.List.copyOf(lifecycleHooks);
        }
    }

    public enum DependencyResolution {
        AUTO,
        EXPLICIT,
        LLM
    }

    public static class PlanningValidationProperties {
        private boolean enabled = true;
        private boolean failOnErrors = true;
        private boolean validateRequestedDeliverable = true;
        private boolean validateAgentCapabilities = true;
        private boolean validateSkillOutputFormats = true;
        private boolean validateLocalToolParameters = true;
        private boolean validateMcpCapabilities = true;
        private boolean validateMcpArguments = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailOnErrors() {
            return failOnErrors;
        }

        public void setFailOnErrors(boolean failOnErrors) {
            this.failOnErrors = failOnErrors;
        }

        public boolean isValidateRequestedDeliverable() {
            return validateRequestedDeliverable;
        }

        public void setValidateRequestedDeliverable(boolean validateRequestedDeliverable) {
            this.validateRequestedDeliverable = validateRequestedDeliverable;
        }

        public boolean isValidateAgentCapabilities() {
            return validateAgentCapabilities;
        }

        public void setValidateAgentCapabilities(boolean validateAgentCapabilities) {
            this.validateAgentCapabilities = validateAgentCapabilities;
        }

        public boolean isValidateSkillOutputFormats() {
            return validateSkillOutputFormats;
        }

        public void setValidateSkillOutputFormats(boolean validateSkillOutputFormats) {
            this.validateSkillOutputFormats = validateSkillOutputFormats;
        }

        public boolean isValidateLocalToolParameters() {
            return validateLocalToolParameters;
        }

        public void setValidateLocalToolParameters(boolean validateLocalToolParameters) {
            this.validateLocalToolParameters = validateLocalToolParameters;
        }

        public boolean isValidateMcpCapabilities() {
            return validateMcpCapabilities;
        }

        public void setValidateMcpCapabilities(boolean validateMcpCapabilities) {
            this.validateMcpCapabilities = validateMcpCapabilities;
        }

        public boolean isValidateMcpArguments() {
            return validateMcpArguments;
        }

        public void setValidateMcpArguments(boolean validateMcpArguments) {
            this.validateMcpArguments = validateMcpArguments;
        }
    }
}
