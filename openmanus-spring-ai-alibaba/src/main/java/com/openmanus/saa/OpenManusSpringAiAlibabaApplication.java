package com.openmanus.saa;

import com.openmanus.saa.config.AgentRegistryProperties;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.McpProperties;
import com.openmanus.saa.config.BrowserProperties;
import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.config.SandboxProperties;
import com.openmanus.saa.config.SkillsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AgentRegistryProperties.class,
        OpenManusProperties.class,
        McpProperties.class,
        BrowserProperties.class,
        RagProperties.class,
        SandboxProperties.class,
        SkillsProperties.class
})
public class OpenManusSpringAiAlibabaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenManusSpringAiAlibabaApplication.class, args);
    }
}
