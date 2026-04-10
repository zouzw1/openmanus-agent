package com.openmanus.saa.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope（通义千问）模型配置
 *
 * <p>当 openmanus.model.provider=dashscope 或未指定时激活。
 * 创建统一的 "chatModel" 和 "embeddingModel" Bean，支持通过配置切换 Provider。
 *
 * <p>注意：Spring AI Alibaba 自动配置会创建名为 "dashScopeChatModel" 和
 * "dashscopeEmbeddingModel" 的 Bean，此配置类将它们重命名为统一名称。
 */
@Configuration
@ConditionalOnClass(DashScopeApi.class)
@ConditionalOnProperty(
    name = "openmanus.model.provider",
    havingValue = "dashscope",
    matchIfMissing = true
)
public class DashScopeModelConfiguration {

    /**
     * 创建 DashScope ChatModel
     * Bean 名称为 "chatModel"（统一接口）
     */
    @Bean("chatModel")
    @ConditionalOnMissingBean(name = "chatModel")
    public ChatModel chatModel(DashScopeChatModel dashScopeChatModel) {
        return dashScopeChatModel;
    }

    /**
     * 创建 DashScope EmbeddingModel
     * Bean 名称为 "embeddingModel"（统一接口）
     */
    @Bean("embeddingModel")
    @ConditionalOnMissingBean(name = "embeddingModel")
    public EmbeddingModel embeddingModel(DashScopeEmbeddingModel dashScopeEmbeddingModel) {
        return dashScopeEmbeddingModel;
    }
}
