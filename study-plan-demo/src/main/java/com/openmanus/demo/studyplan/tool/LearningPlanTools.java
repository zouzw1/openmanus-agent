package com.openmanus.demo.studyplan.tool;

import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.rag.model.KnowledgeScope;
import com.openmanus.saa.rag.model.RetrievalHit;
import com.openmanus.saa.rag.model.RetrievalRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class LearningPlanTools {

    private static final String DEFAULT_KNOWLEDGE_BASE = "java_test";

    private final ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider;

    public LearningPlanTools(ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider) {
        this.ragRetrievalServiceProvider = ragRetrievalServiceProvider;
    }

    @Tool(description = "Recommend concrete learning goals, milestones, and staged outcomes for a study topic.")
    public String recommendLearningGoals(
            @ToolParam(description = "Primary topic to study, for example Java, Spring Boot, IELTS, or machine learning.", required = true)
            String topic,
            @ToolParam(description = "Learner level such as beginner, intermediate, or advanced.", required = true)
            String learnerLevel
    ) {
        String normalizedTopic = normalizeTopic(topic);
        String normalizedLevel = normalizeLevel(learnerLevel);
        if (isJavaBeginner(normalizedTopic, learnerLevel)) {
            String base = """
                    # %s（%s）阶段目标与里程碑

                    ## 阶段目标
                    1. 第 1-2 周：完成开发环境搭建，掌握变量、条件、循环、输入输出等基础语法。
                    2. 第 3-4 周：掌握方法、数组和面向对象基础，能够独立编写 200 行以内的小程序。
                    3. 第 5-6 周：掌握继承、多态、接口、字符串、集合和常用 API，能实现简单业务模型。
                    4. 第 7 周：掌握异常处理、文件读写和调试定位，能修复常见运行时问题。
                    5. 第 8 周：完成一个命令行综合项目，并能讲清楚核心设计与代码结构。

                    ## 建议验收里程碑
                    - 第 2 周末：能独立写出包含条件和循环的控制台程序。
                    - 第 4 周末：能设计类、对象、构造器并完成简单建模。
                    - 第 6 周末：能使用集合处理一组对象数据。
                    - 第 8 周末：能交付一个可运行的小项目并完成演示说明。
                    """.formatted(normalizedTopic, normalizedLevel).trim();
            return appendKnowledgeBaseEnhancementSection(
                    base,
                    "知识库提炼的阶段建议",
                    "Java 学习目标 里程碑 成果 复盘",
                    List.of("里程碑", "成果", "复盘", "项目", "阶段")
            );
        }

        return """
                %s（%s）学习目标：
                1. 建立对%s核心知识体系的整体理解。
                2. 每周完成一个明确、可验证的学习产出。
                3. 保持学习、练习、复盘三段式节奏。
                4. 在整个周期结束时完成一次综合应用或测评。

                建议里程碑：
                - 前期：掌握基础概念与术语
                - 中期：完成引导式练习和小型任务
                - 后期：完成综合应用、复盘与查漏补缺
                """.formatted(normalizedTopic, normalizedLevel, normalizedTopic).trim();
    }

    @Tool(description = "Build a concrete week-by-week study schedule with weekly topics, practical tasks, and checkable outputs.")
    public String buildWeeklyStudySchedule(
            @ToolParam(description = "Primary topic to study, for example Java, Spring Boot, IELTS, or machine learning.", required = true)
            String topic,
            @ToolParam(description = "Learner level such as beginner, intermediate, or advanced.", required = true)
            String learnerLevel,
            @ToolParam(description = "Total study duration in weeks.", required = true)
            int durationWeeks,
            @ToolParam(description = "Available study hours per week.", required = true)
            int weeklyHours
    ) {
        String normalizedTopic = normalizeTopic(topic);
        String normalizedLevel = normalizeLevel(learnerLevel);
        int safeWeeks = Math.max(durationWeeks, 1);
        int safeHours = Math.max(weeklyHours, 1);

        if (isJavaBeginner(normalizedTopic, learnerLevel) && safeWeeks == 8) {
            String base = buildJavaBeginnerEightWeekSchedule(normalizedTopic, normalizedLevel, safeHours);
            return appendKnowledgeBaseEnhancementSection(
                    base,
                    "知识库提炼的执行原则",
                    "Java 8周学习路线 周计划 复盘 验收 成果",
                    List.of("每周", "复盘", "验收", "成果", "里程碑", "项目")
            );
        }
        return buildGenericWeeklySchedule(normalizedTopic, normalizedLevel, safeWeeks, safeHours);
    }

    @Tool(description = "Generate a practical weekly checklist with concrete exercises and acceptance criteria for a learning plan.")
    public String generatePracticeChecklist(
            @ToolParam(description = "Primary topic to study, for example Java, Spring Boot, IELTS, or machine learning.", required = true)
            String topic,
            @ToolParam(description = "Learner level such as beginner, intermediate, or advanced.", required = true)
            String learnerLevel,
            @ToolParam(description = "Optional total duration in weeks when the checklist should align to a specific schedule.", required = false)
            Integer durationWeeks
    ) {
        String normalizedTopic = normalizeTopic(topic);
        String normalizedLevel = normalizeLevel(learnerLevel);
        int safeWeeks = durationWeeks == null ? 8 : Math.max(durationWeeks, 1);

        if (isJavaBeginner(normalizedTopic, learnerLevel) && safeWeeks == 8) {
            String base = """
                    # %s（%s）8 周练习与验收清单

                    ## 第 1 周验收
                    - 能解释 JDK、JRE、JVM 的区别。
                    - 能独立编写 HelloWorld、变量演示、四则运算程序。
                    - 能说清楚基本数据类型及类型转换的使用场景。

                    ## 第 2 周验收
                    - 能使用 if/else、switch、for、while 完成分支与循环练习。
                    - 能独立完成九九乘法表、成绩评级、猜数字等 3 个练习。
                    - 能通过调试定位循环边界错误。

                    ## 第 3 周验收
                    - 能封装复用方法并合理设计参数、返回值。
                    - 能使用数组完成统计、查找、排序基础练习。
                    - 能完成一个“学生成绩统计”小程序。

                    ## 第 4 周验收
                    - 能设计类、对象、构造器、成员变量和成员方法。
                    - 能完成“图书”或“学生”实体类建模。
                    - 能解释封装的意义并使用 private + getter/setter。

                    ## 第 5 周验收
                    - 能使用继承、重写、多态和接口组织代码。
                    - 能完成“支付方式”或“员工类型”多态练习。
                    - 能识别抽象类与接口的适用场景。

                    ## 第 6 周验收
                    - 能使用 String、ArrayList、HashMap 处理一组业务数据。
                    - 能完成“学生通讯录”或“商品清单”管理练习。
                    - 能解释泛型的基本作用并避免原始类型集合。

                    ## 第 7 周验收
                    - 能使用 try/catch/finally 处理常见异常。
                    - 能读写文本文件并保存程序数据。
                    - 能完成“成绩文件导入导出”或“日志记录”练习。

                    ## 第 8 周验收
                    - 能交付一个可运行的命令行综合项目。
                    - 能演示核心功能、代码结构和主要类职责。
                    - 能总结 3 个做得好的点和 3 个后续优化点。

                    ## 通用执行要求
                    - 每周至少完成 3 个可运行的代码练习。
                    - 每周保留 1 次复盘，整理错误与易混概念。
                    - 每周输出 1 份可展示成果：代码、笔记、截图或讲解文档。
                    """.formatted(normalizedTopic, normalizedLevel).trim();
            return appendKnowledgeBaseEnhancementSection(
                    base,
                    "知识库提炼的验收与复盘建议",
                    "Java 练习清单 周验收 复盘问题 学习成果",
                    List.of("练习", "验收", "复盘", "成果", "项目", "每周")
            );
        }

        return buildGenericChecklist(normalizedTopic, normalizedLevel, safeWeeks);
    }

    private String buildJavaBeginnerEightWeekSchedule(String topic, String learnerLevel, int weeklyHours) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ")
                .append(topic)
                .append("（")
                .append(learnerLevel)
                .append("）8 周学习路线图\n\n")
                .append("## 总体安排\n")
                .append("- 总周期：8 周\n")
                .append("- 每周投入：")
                .append(weeklyHours)
                .append(" 小时\n")
                .append("- 推荐节奏：2 次概念学习 + 1 次编码练习 + 1 次复盘总结\n\n");

        appendWeekSection(
                builder,
                1,
                "环境搭建与基础语法",
                List.of("理解 JDK、JRE、JVM 的关系", "完成开发环境搭建与项目运行", "掌握变量、数据类型、输入输出"),
                List.of("编写 HelloWorld 程序并成功运行", "完成变量声明、类型转换和控制台输入输出练习", "实现一个简易四则运算计算器"),
                "提交 3 个基础程序，并能口头解释每个程序的运行过程"
        );
        appendWeekSection(
                builder,
                2,
                "条件语句与循环",
                List.of("掌握 if/else、switch 的使用", "掌握 for、while、do-while 循环", "理解 break、continue 的作用"),
                List.of("实现成绩评级程序", "实现九九乘法表或阶乘计算", "实现猜数字或登录重试小程序"),
                "完成至少 3 个控制流练习，并能独立调试循环边界错误"
        );
        appendWeekSection(
                builder,
                3,
                "方法封装与数组处理",
                List.of("理解方法定义、参数、返回值", "掌握数组的声明、遍历、统计和查找", "开始练习模块化思维"),
                List.of("封装最大值、平均值、排序等方法", "完成数组求和、查找最大值、统计及格人数练习", "实现一个学生成绩统计小程序"),
                "提交一个包含多个方法调用的数组练习项目"
        );
        appendWeekSection(
                builder,
                4,
                "面向对象基础",
                List.of("理解类、对象、属性、方法", "掌握构造器、this、封装", "学会建模简单业务实体"),
                List.of("设计 Student、Book 或 Account 类", "为类补充构造器、getter/setter", "编写对象数组或集合形式的基础管理程序"),
                "完成一个包含 2-3 个类的简单对象建模案例"
        );
        appendWeekSection(
                builder,
                5,
                "继承、多态与接口",
                List.of("理解继承、方法重写、多态", "掌握 abstract、interface 的基本使用", "学习通过抽象统一行为"),
                List.of("实现 Employee/Manager 或 Animal/Cat/Dog 继承案例", "实现支付方式或消息通知接口练习", "完成一个包含多态调用的主程序"),
                "能解释多态带来的扩展性，并完成 1 个接口化练习"
        );
        appendWeekSection(
                builder,
                6,
                "字符串、集合与常用 API",
                List.of("掌握 String 常用方法", "掌握 ArrayList、HashMap 的增删改查", "了解泛型基础"),
                List.of("实现字符串处理练习，如统计字符或格式校验", "实现学生通讯录或商品列表管理程序", "使用集合完成对象数据的查找、排序或过滤"),
                "完成一个基于集合的控制台 CRUD 小程序"
        );
        appendWeekSection(
                builder,
                7,
                "异常处理与文件读写",
                List.of("掌握 try/catch/finally、throw、throws", "理解常见异常类型与调试方式", "掌握文本文件读写"),
                List.of("为前几周程序增加异常处理", "实现文件读取和保存功能", "完成一个带数据持久化的练习，如成绩记录或待办清单"),
                "能处理输入错误、文件不存在等异常，并完成 1 个带文件存取的程序"
        );
        appendWeekSection(
                builder,
                8,
                "综合项目与成果复盘",
                List.of("整合前 7 周知识点", "完成一个小型命令行项目", "学会复盘代码结构和问题"),
                List.of("完成学生管理系统、图书管理系统或记账程序三选一", "补充 README：功能说明、运行方式、类结构", "自测所有核心功能并记录 3 个待优化点"),
                "交付一个可运行的综合项目，并能演示主要功能"
        );
        return builder.toString().trim();
    }

    private String buildGenericWeeklySchedule(String topic, String learnerLevel, int durationWeeks, int weeklyHours) {
        StringBuilder builder = new StringBuilder();
        builder.append(topic)
                .append("（")
                .append(learnerLevel)
                .append("）")
                .append(durationWeeks)
                .append(" 周学习路线图：\n")
                .append("- 每周投入：")
                .append(weeklyHours)
                .append(" 小时\n")
                .append("- 输出要求：每周都要有“主题、练习、可验证成果”三部分\n\n");

        for (int week = 1; week <= durationWeeks; week++) {
            String stage = week <= Math.max(1, durationWeeks / 3)
                    ? "基础入门"
                    : week <= Math.max(2, (durationWeeks * 2) / 3) ? "强化练习" : "综合应用";
            builder.append("第 ").append(week).append(" 周：").append(stage).append("\n")
                    .append("- 学习主题：围绕 ").append(topic).append(" 的核心知识点做本周专题学习\n")
                    .append("- 练习任务：完成 2-3 个针对本周主题的练习，并记录关键问题\n")
                    .append("- 本周成果：输出 1 份笔记 + 1 个练习成果 + 1 次复盘总结\n\n");
        }
        return builder.toString().trim();
    }

    private String buildGenericChecklist(String topic, String learnerLevel, int durationWeeks) {
        StringBuilder builder = new StringBuilder();
        builder.append(topic)
                .append("（")
                .append(learnerLevel)
                .append("）")
                .append(durationWeeks)
                .append(" 周执行清单：\n");
        for (int week = 1; week <= durationWeeks; week++) {
            builder.append("\n第 ").append(week).append(" 周检查项：\n")
                    .append("- 明确 1 个本周主题和 1 个可验证成果\n")
                    .append("- 完成至少 2 个练习或 1 个小任务\n")
                    .append("- 整理本周笔记、疑问和错误清单\n")
                    .append("- 通过讲解、自测或演示验证学习效果\n");
        }
        return builder.toString().trim();
    }

    private void appendWeekSection(
            StringBuilder builder,
            int week,
            String theme,
            List<String> focuses,
            List<String> exercises,
            String deliverable
    ) {
        builder.append("## 第 ").append(week).append(" 周：").append(theme).append("\n")
                .append("### 学习重点\n");
        for (String focus : focuses) {
            builder.append("- ").append(focus).append("\n");
        }
        builder.append("\n### 本周练习\n");
        for (int i = 0; i < exercises.size(); i++) {
            builder.append(i + 1).append(". ").append(exercises.get(i)).append("\n");
        }
        builder.append("\n### 本周交付\n")
                .append("- ").append(deliverable).append("\n\n")
                .append("### 本周复盘问题\n");
        for (String question : defaultReviewQuestions(theme)) {
            builder.append("- ").append(question).append("\n");
        }
        builder.append("\n");
    }

    private boolean isJavaBeginner(String topic, String learnerLevel) {
        if (topic == null) {
            return false;
        }
        String normalizedTopic = topic.toLowerCase(Locale.ROOT);
        String normalizedLevel = learnerLevel == null ? "" : learnerLevel.trim().toLowerCase(Locale.ROOT);
        return normalizedTopic.contains("java")
                && (normalizedLevel.isBlank() || "beginner".equals(normalizedLevel) || "初学者".equals(normalizedLevel));
    }

    private String normalizeTopic(String topic) {
        return topic == null || topic.isBlank() ? "该主题" : topic.trim();
    }

    private String normalizeLevel(String learnerLevel) {
        if (learnerLevel == null || learnerLevel.isBlank()) {
            return "初学者";
        }
        String normalized = learnerLevel.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "advanced", "高级" -> "高级";
            case "intermediate", "中级" -> "中级";
            default -> "初学者";
        };
    }

    private String appendKnowledgeBaseEnhancementSection(String base, String sectionTitle, String query, List<String> preferredKeywords) {
        List<String> hints = retrieveKnowledgeHints(query, preferredKeywords);
        if (hints.isEmpty()) {
            return base;
        }
        StringBuilder builder = new StringBuilder(base);
        builder.append("\n\n## ").append(sectionTitle).append("\n");
        for (String hint : hints) {
            builder.append("- ").append(hint).append("\n");
        }
        return builder.toString().trim();
    }

    private List<String> retrieveKnowledgeHints(String query, List<String> preferredKeywords) {
        RagRetrievalService ragRetrievalService = ragRetrievalServiceProvider.getIfAvailable();
        if (ragRetrievalService == null) {
            return List.of();
        }
        List<RetrievalHit> hits = ragRetrievalService.retrieve(new RetrievalRequest(
                query,
                KnowledgeScope.ofKnowledgeBase(DEFAULT_KNOWLEDGE_BASE),
                4,
                true,
                java.util.Map.of()
        )).hits();
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        Set<String> selected = new LinkedHashSet<>();
        for (RetrievalHit hit : hits) {
            String[] lines = hit.text() == null ? new String[0] : hit.text().split("\\r?\\n");
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank()) {
                    continue;
                }
                String normalized = line.replaceFirst("^[-*]\\s*", "").replaceFirst("^\\d+\\.\\s*", "").trim();
                if (normalized.isBlank()) {
                    continue;
                }
                if (!looksLikeUsefulHint(normalized, preferredKeywords)) {
                    continue;
                }
                selected.add(normalized);
                if (selected.size() >= 6) {
                    return new ArrayList<>(selected);
                }
            }
        }
        return new ArrayList<>(selected);
    }

    private boolean looksLikeUsefulHint(String line, List<String> preferredKeywords) {
        if (line.length() < 8 || line.startsWith("#") || line.startsWith("##")) {
            return false;
        }
        if (line.endsWith("：") || line.endsWith(":")) {
            return false;
        }
        if (line.contains("资料用于辅助") || line.contains("文档用途") || line.contains("学习计划知识库")) {
            return false;
        }
        if (preferredKeywords == null || preferredKeywords.isEmpty()) {
            return true;
        }
        for (String keyword : preferredKeywords) {
            if (keyword != null && !keyword.isBlank() && line.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> defaultReviewQuestions(String theme) {
        return List.of(
                "本周关于“" + theme + "”最容易混淆的 2-3 个点是什么？",
                "我这周完成的练习里，哪一个最能证明我已经掌握了核心内容？",
                "如果下周继续学习，我最需要提前补的知识点是什么？"
        );
    }
}
