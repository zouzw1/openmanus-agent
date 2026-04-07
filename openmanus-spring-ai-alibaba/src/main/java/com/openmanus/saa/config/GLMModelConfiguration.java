package com.openmanus.saa.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * GLM 模型配置类
 * 当 openmanus.model.provider=glm 时激活
 * 使用 Spring AI ZhiPuAI Starter 自动配置的 ChatModel
 * 同时排除 DashScope 自动配置以避免冲突
 */
@Configuration
@ConditionalOnProperty(name = "openmanus.model.provider", havingValue = "glm")
@EnableAutoConfiguration(exclude = {
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration.class
})
public class GlmModelConfiguration {

    /**
     * 创建 GLM ChatModel Bean
     * 使用 @Primary 注解确保在 GLM 模式下优先使用此 Bean
     * 注入自动配置的 zhiPuAiChatModel
     */
    @Bean
    @Primary
    public ChatModel glmChatModel(ZhiPuAiChatModel zhiPuAiChatModel) {
        // 直接使用自动配置的 zhiPuAiChatModel
        return zhiPuAiChatModel;
    }
}
