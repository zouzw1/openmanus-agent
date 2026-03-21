package com.openmanus.saa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmanus.skills")
public class SkillsProperties {

    private boolean enabled = true;
    private boolean lazyLoad = true;
    private String projectSkillsDirectory = "./skills/project";
    private String userSkillsDirectory = "./skills/user";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public String getProjectSkillsDirectory() {
        return projectSkillsDirectory;
    }

    public void setProjectSkillsDirectory(String projectSkillsDirectory) {
        this.projectSkillsDirectory = projectSkillsDirectory;
    }

    public String getUserSkillsDirectory() {
        return userSkillsDirectory;
    }

    public void setUserSkillsDirectory(String userSkillsDirectory) {
        this.userSkillsDirectory = userSkillsDirectory;
    }
}
