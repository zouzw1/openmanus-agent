package com.openmanus.saa.config;

import com.alibaba.cloud.ai.graph.agent.hook.skills.ReadSkillTool;
import com.alibaba.cloud.ai.graph.skills.SpringAiSkillAdvisor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "openmanus.skills", name = "enabled", havingValue = "true")
    SkillRegistry skillRegistry(SkillsProperties properties) throws IOException {
        Files.createDirectories(Path.of(properties.getProjectSkillsDirectory()));
        Files.createDirectories(Path.of(properties.getUserSkillsDirectory()));
        return FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(properties.getProjectSkillsDirectory())
                .userSkillsDirectory(properties.getUserSkillsDirectory())
                .autoLoad(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "openmanus.skills", name = "enabled", havingValue = "true")
    SpringAiSkillAdvisor springAiSkillAdvisor(SkillRegistry skillRegistry, SkillsProperties properties) {
        return SpringAiSkillAdvisor.builder()
                .skillRegistry(skillRegistry)
                .projectSkillsDirectory(properties.getProjectSkillsDirectory())
                .userSkillsDirectory(properties.getUserSkillsDirectory())
                .lazyLoad(properties.isLazyLoad())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "openmanus.skills", name = "enabled", havingValue = "true")
    ToolCallback readSkillToolCallback(SkillRegistry skillRegistry) {
        return ReadSkillTool.createReadSkillToolCallback(skillRegistry, "read_skill");
    }
}
