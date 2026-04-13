package com.openmanus.saa;

import com.openmanus.saa.config.AgentRegistryProperties;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.McpProperties;
import com.openmanus.saa.config.BrowserProperties;
import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.config.SandboxProperties;
import com.openmanus.saa.config.SkillsProperties;
import com.openmanus.saa.config.ModelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiChatAutoConfiguration;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiImageAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        ZhiPuAiChatAutoConfiguration.class,
        ZhiPuAiEmbeddingAutoConfiguration.class,
        ZhiPuAiImageAutoConfiguration.class
})
@EnableConfigurationProperties({
        AgentRegistryProperties.class,
        OpenManusProperties.class,
        McpProperties.class,
        BrowserProperties.class,
        RagProperties.class,
        SandboxProperties.class,
        SkillsProperties.class,
        ModelProperties.class
})
public class OpenManusSpringAiAlibabaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenManusSpringAiAlibabaApplication.class, args);
    }
}
