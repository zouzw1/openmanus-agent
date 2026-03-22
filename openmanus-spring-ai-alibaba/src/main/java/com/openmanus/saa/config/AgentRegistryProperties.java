package com.openmanus.saa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmanus.agents")
public class AgentRegistryProperties {

    private boolean enabled = true;
    private String configLocation = "./agents";
    private boolean failFast = true;
    private boolean autoReload = false;
    private String defaultChatAgentId = "manus";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigLocation() {
        return configLocation;
    }

    public void setConfigLocation(String configLocation) {
        this.configLocation = configLocation;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isAutoReload() {
        return autoReload;
    }

    public void setAutoReload(boolean autoReload) {
        this.autoReload = autoReload;
    }

    public String getDefaultChatAgentId() {
        return defaultChatAgentId;
    }

    public void setDefaultChatAgentId(String defaultChatAgentId) {
        this.defaultChatAgentId = defaultChatAgentId;
    }
}
