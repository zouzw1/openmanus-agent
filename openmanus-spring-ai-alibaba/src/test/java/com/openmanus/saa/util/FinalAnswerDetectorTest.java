package com.openmanus.saa.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalAnswerDetectorTest {

    @Test
    void shortResult_shouldNotBeDeliverable() {
        String result = "已获取15个景点信息";
        assertFalse(FinalAnswerDetector.isCompleteDeliverable(result));
    }

    @Test
    void simpleDataListing_shouldNotBeDeliverable() {
        String result = """
                - 景点1: 故宫
                - 景点2: 天坛
                - 景点3: 颐和园
                - 景点4: 长城
                - 景点5: 圆明园
                - 景点6: 鸟巢
                - 景点7: 水立方
                """;
        assertFalse(FinalAnswerDetector.isCompleteDeliverable(result));
    }

    @Test
    void structuredPlan_shouldBeDeliverable() {
        String result = """
                # 南京三日旅行计划

                ## 第一天：历史文化之旅

                ### 上午
                - 09:00 抵达南京
                - 10:00 游览中山陵
                - 12:00 午餐：南京大牌档

                ### 下午
                - 14:00 游览明孝陵
                - 17:00 前往夫子庙

                ## 第二天：美食探索

                ### 上午
                - 09:00 游览总统府
                - 12:00 午餐：鸭血粉丝汤

                ### 下午
                - 14:00 游览南京博物院
                - 18:00 晚餐：盐水鸭

                ## 第三天：自然风光

                ### 上午
                - 09:00 游览玄武湖
                - 12:00 午餐

                ### 下午
                - 14:00 返程

                ## 费用预算
                - 交通：500元
                - 住宿：600元
                - 餐饮：400元
                - 门票：200元
                - 总计：1700元

                ## 注意事项
                - 提前预订酒店
                - 准备舒适的鞋子
                - 下载南京地铁APP
                """;
        assertTrue(FinalAnswerDetector.isCompleteDeliverable(result));
    }

    @Test
    void analysisReport_shouldBeDeliverable() {
        String result = """
                # 项目架构分析报告

                ## 概述

                本项目采用分层架构设计，主要分为以下几层：
                - 表现层：处理HTTP请求
                - 业务层：核心业务逻辑
                - 数据层：数据访问和持久化

                ## 架构特点

                ### 优点
                1. 职责清晰，各层独立
                2. 易于测试和维护
                3. 支持横向扩展

                ### 缺点
                1. 层间调用有一定开销
                2. 需要严格遵循分层规范

                ## 建议

                1. 增加缓存层提升性能
                2. 引入消息队列解耦
                3. 完善监控告警机制

                ## 结论

                整体架构设计合理，符合企业级应用标准。
                """;
        assertTrue(FinalAnswerDetector.isCompleteDeliverable(result));
    }

    @Test
    void richContent_withoutHeaders_shouldBeDeliverable() {
        StringBuilder sb = new StringBuilder();
        sb.append("基于您的需求，我为您制定了详细的方案。\n\n");
        for (int i = 0; i < 10; i++) {
            sb.append("第").append(i + 1).append("点：这是详细的建议内容，包含具体的执行步骤和注意事项。");
            sb.append("建议您在实施过程中注意以下几点...\n\n");
        }
        assertTrue(FinalAnswerDetector.isCompleteDeliverable(sb.toString()));
    }
}
