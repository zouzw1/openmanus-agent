package com.openmanus.saa.config;

import com.alibaba.cloud.ai.graph.skills.SpringAiSkillAdvisor;
import java.util.List;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(
            ChatModel chatModel,
            ObjectProvider<SpringAiSkillAdvisor> skillAdvisorProvider,
            ObjectProvider<ToolCallback> toolCallbackProvider
    ) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        SpringAiSkillAdvisor skillAdvisor = skillAdvisorProvider.getIfAvailable();
        if (skillAdvisor != null) {
            builder.defaultAdvisors(List.<Advisor>of(skillAdvisor));
        }

        List<ToolCallback> toolCallbacks = toolCallbackProvider.orderedStream().toList();
        if (!toolCallbacks.isEmpty()) {
            builder.defaultToolCallbacks(toolCallbacks);
        }
        return builder.build();
    }
}
