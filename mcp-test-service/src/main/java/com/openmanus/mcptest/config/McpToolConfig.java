package com.openmanus.mcptest.config;

import com.openmanus.mcptest.service.TestMcpToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    ToolCallbackProvider mcpToolCallbackProvider(TestMcpToolService toolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolService)
                .build();
    }
}
