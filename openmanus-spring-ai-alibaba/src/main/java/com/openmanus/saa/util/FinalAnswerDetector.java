package com.openmanus.saa.util;

/**
 * 最终答案检测工具。
 *
 * 通过通用的内容特征判断步骤结果是否已经是完整的最终答案，
 * 而不是需要LLM进一步综合的中间数据。
 *
 * 通用检测指标（不依赖特定业务关键词）：
 * 1. 内容长度：足够长的内容更可能是最终答案
 * 2. 结构化特征：标题、列表、段落等组织结构
 * 3. 内容类型：不是简单的数据列举
 */
public final class FinalAnswerDetector {

    /** 最小内容长度（低于此值不太可能是最终答案） */
    private static final int MIN_LENGTH = 500;

    /** 丰富内容长度阈值 */
    private static final int RICH_LENGTH = 800;

    private FinalAnswerDetector() {
    }

    /**
     * 判断结果是否已经是完整的最终答案。
     *
     * 通过通用的内容特征检测，适用于各种类型的交付物：
     * - 旅行计划、学习计划、工作计划
     * - 分析报告、调研报告
     * - 推荐列表、建议方案
     * - 任何结构化的综合输出
     *
     * @param result 步骤执行结果
     * @return true=已是最终答案，可直接输出；false=需要LLM综合
     */
    public static boolean isCompleteDeliverable(String result) {
        if (result == null || result.isEmpty()) {
            return false;
        }

        // 优先检测结构化特征（如果有明显的结构，长度不重要）
        boolean hasStructure = hasMarkdownStructure(result);
        if (hasStructure) {
            // 有结构化特征，检查是否不是简单数据列举
            return !isSimpleDataListing(result);
        }

        // 无明显结构，需要内容足够丰富
        return result.length() >= RICH_LENGTH && !isSimpleDataListing(result);
    }

    /**
     * 检测是否有Markdown结构（标题、列表、段落）。
     */
    private static boolean hasMarkdownStructure(String text) {
        int headers = 0;
        int lists = 0;
        int paragraphs = 0;
        boolean inParagraph = false;

        for (String line : text.split("\n")) {
            String trimmed = line.trim();

            // 标题检测（#、##、### 都算）
            if (trimmed.startsWith("#")) {
                headers++;
            }

            // 列表检测
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.matches("^\\d+\\.\\s.*")) {
                lists++;
            }

            // 段落检测
            if (!trimmed.isEmpty()) {
                if (!inParagraph) {
                    paragraphs++;
                    inParagraph = true;
                }
            } else {
                inParagraph = false;
            }
        }

        // 有多个标题，或有标题+列表，或有足够多段落
        return headers >= 2
                || (headers >= 1 && lists >= 3)
                || paragraphs >= 6;
    }

    /**
     * 判断是否是简单的数据列举。
     *
     * 特征：大部分行都很短，主要是列表项，没有解释性内容。
     */
    private static boolean isSimpleDataListing(String text) {
        String[] lines = text.split("\n");
        if (lines.length < 5) {
            return true;
        }

        int shortLines = 0;
        int listLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 50) {
                shortLines++;
            }
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.matches("^\\d+\\.\\s.*")) {
                listLines++;
            }
        }

        // 大部分是短行且主要是列表 → 简单数据列举
        double shortRatio = (double) shortLines / lines.length;
        double listRatio = (double) listLines / lines.length;
        return shortRatio > 0.7 && listRatio > 0.5;
    }
}
