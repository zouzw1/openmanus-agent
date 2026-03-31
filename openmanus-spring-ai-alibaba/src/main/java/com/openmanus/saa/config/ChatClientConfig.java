package com.openmanus.saa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
