package com.openmanus.saa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型配置属性，支持切换 DashScope（通义千问）或 GLM（智谱 AI）
 */
@ConfigurationProperties(prefix = "openmanus.model")
public class ModelProperties {

    /**
     * 模型提供者：dashscope（默认）或 glm
     */
    private String provider = "dashscope";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
