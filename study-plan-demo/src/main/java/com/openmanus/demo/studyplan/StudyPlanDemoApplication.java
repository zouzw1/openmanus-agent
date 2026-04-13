package com.openmanus.demo.studyplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiChatAutoConfiguration;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiImageAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = {"com.openmanus.saa", "com.openmanus.demo.studyplan"},
    exclude = {
        ZhiPuAiChatAutoConfiguration.class,
        ZhiPuAiEmbeddingAutoConfiguration.class,
        ZhiPuAiImageAutoConfiguration.class
    }
)
public class StudyPlanDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyPlanDemoApplication.class, args);
    }
}
