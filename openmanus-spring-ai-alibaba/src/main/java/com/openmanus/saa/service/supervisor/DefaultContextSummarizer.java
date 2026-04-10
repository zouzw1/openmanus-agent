package com.openmanus.saa.service.supervisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认上下文摘要器实现。
 * 使用规则和启发式方法进行摘要。
 */
@Component
public class DefaultContextSummarizer implements ContextSummarizer {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextSummarizer.class);

    // 摘要比例
    private static final double SUMMARY_RATIO = 0.3;

    // 关键词模式
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("[\\w/\\\\.-]+\\.\\w+");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?:def|function|class|interface|public|private|protected)\\s+\\w+");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[-*•]\\s+", Pattern.MULTILINE);

    @Override
    public String summarize(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        if (content.length() <= maxLength) {
            return content;
        }

        try {
            // 1. 移除代码块，保留占位符
            List<String> codeBlocks = new ArrayList<>();
            String processed = CODE_BLOCK_PATTERN.matcher(content).replaceAll(match -> {
                String placeholder = "[代码块 " + (codeBlocks.size() + 1) + "]";
                codeBlocks.add(match.group());
                return placeholder;
            });

            // 2. 提取关键信息
            List<String> keyPoints = extractKeyPoints(processed);

            // 3. 构建摘要
            StringBuilder summary = new StringBuilder();

            // 添加元信息
            summary.append("=== 上下文摘要 ===\n");
            summary.append("原始长度: ").append(content.length()).append(" 字符\n");
            summary.append("代码块数: ").append(codeBlocks.size()).append("\n\n");

            // 添加关键点
            if (!keyPoints.isEmpty()) {
                summary.append("关键信息:\n");
                for (int i = 0; i < Math.min(keyPoints.size(), 10); i++) {
                    summary.append("- ").append(keyPoints.get(i)).append("\n");
                }
                summary.append("\n");
            }

            // 添加代码块摘要
            if (!codeBlocks.isEmpty()) {
                summary.append("代码块摘要:\n");
                for (int i = 0; i < Math.min(codeBlocks.size(), 3); i++) {
                    String block = codeBlocks.get(i);
                    // 提取代码块的语言和前几行
                    String[] lines = block.split("\n");
                    String lang = lines[0].replace("```", "").trim();
                    summary.append("- [").append(lang.isEmpty() ? "code" : lang).append("] ");
                    if (lines.length > 1) {
                        summary.append(lines[1].trim());
                        if (lines.length > 2) {
                            summary.append(" ...");
                        }
                    }
                    summary.append("\n");
                }
                if (codeBlocks.size() > 3) {
                    summary.append("- ... 还有 ").append(codeBlocks.size() - 3).append(" 个代码块\n");
                }
            }

            // 4. 如果摘要超过最大长度，进一步压缩
            String result = summary.toString();
            if (result.length() > maxLength) {
                result = result.substring(0, maxLength - 20) + "...(已截断)";
            }

            return result;

        } catch (Exception e) {
            log.error("Summarization failed", e);
            // 回退到简单截断
            return content.substring(0, maxLength) + "...(已摘要)";
        }
    }

    @Override
    public List<String> extractKeyPoints(String content) {
        List<String> keyPoints = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return keyPoints;
        }

        try {
            // 1. 提取文件路径
            FILE_PATH_PATTERN.matcher(content).results()
                .map(m -> m.group())
                .distinct()
                .limit(5)
                .forEach(path -> keyPoints.add("涉及文件: " + path));

            // 2. 提取函数/方法名
            FUNCTION_PATTERN.matcher(content).results()
                .map(m -> m.group().trim())
                .distinct()
                .limit(5)
                .forEach(func -> keyPoints.add("定义: " + func));

            // 3. 提取列表项（Bullet points）
            BULLET_PATTERN.matcher(content).results()
                .map(m -> m.group())
                .limit(5)
                .forEach(bullet -> {
                    int start = bullet.length();
                    int end = content.indexOf("\n", start);
                    if (end > start && end - start < 100) {
                        keyPoints.add("项: " + content.substring(start, end).trim());
                    }
                });

            // 4. 提取第一句和最后一句
            String[] sentences = content.split("[.!?。！？]+");
            if (sentences.length > 0) {
                String first = sentences[0].trim();
                if (first.length() > 10 && first.length() < 200) {
                    keyPoints.add("首句: " + first);
                }
            }
            if (sentences.length > 1) {
                String last = sentences[sentences.length - 1].trim();
                if (last.length() > 10 && last.length() < 200) {
                    keyPoints.add("末句: " + last);
                }
            }

        } catch (Exception e) {
            log.error("Key point extraction failed", e);
        }

        return keyPoints;
    }

    /**
     * 计算推荐的摘要长度。
     */
    public int calculateSummaryLength(int originalLength) {
        return (int) (originalLength * SUMMARY_RATIO);
    }

    /**
     * 检查内容是否值得摘要。
     */
    public boolean isWorthSummarizing(String content, int minLength) {
        return content != null && content.length() > minLength;
    }
}
