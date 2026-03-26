package com.openmanus.saa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmanus.sandbox")
public class SandboxProperties {

    private boolean enabled = false;
    private String image = "python:3.12-slim";
    private String workingDirectory = "/workspace";
    private boolean mountWorkspace = true;
    private String hostWorkspacePath = "";
    private String workspaceMountPath = "/workspace";
    private boolean autoPullImage = true;
    private String shellExecutable = "sh";
    private String shellOption = "-lc";
    private String memoryLimit = "512m";
    private int timeoutSeconds = 30;
    private boolean networkEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public boolean isMountWorkspace() {
        return mountWorkspace;
    }

    public void setMountWorkspace(boolean mountWorkspace) {
        this.mountWorkspace = mountWorkspace;
    }

    public String getHostWorkspacePath() {
        return hostWorkspacePath;
    }

    public void setHostWorkspacePath(String hostWorkspacePath) {
        this.hostWorkspacePath = hostWorkspacePath;
    }

    public String getWorkspaceMountPath() {
        return workspaceMountPath;
    }

    public void setWorkspaceMountPath(String workspaceMountPath) {
        this.workspaceMountPath = workspaceMountPath;
    }

    public boolean isAutoPullImage() {
        return autoPullImage;
    }

    public void setAutoPullImage(boolean autoPullImage) {
        this.autoPullImage = autoPullImage;
    }

    public String getShellExecutable() {
        return shellExecutable;
    }

    public void setShellExecutable(String shellExecutable) {
        this.shellExecutable = shellExecutable;
    }

    public String getShellOption() {
        return shellOption;
    }

    public void setShellOption(String shellOption) {
        this.shellOption = shellOption;
    }

    public String getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isNetworkEnabled() {
        return networkEnabled;
    }

    public void setNetworkEnabled(boolean networkEnabled) {
        this.networkEnabled = networkEnabled;
    }
}
