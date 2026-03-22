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
