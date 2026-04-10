package com.openmanus.saa.prompt;

/**
 * Prompt Section 扩展点接口。
 *
 * <p>接入方实现此接口并注册为 Spring Bean，即可向 Agent 添加自定义 Prompt。
 *
 * <p>示例：
 * <pre>{@code
 * @Bean
 * public PromptSection companyPoliciesSection() {
 *     return new PromptSection() {
 *         @Override
 *         public int order() {
 *             return 30;
 *         }
 *
 *         @Override
 *         public String title() {
 *             return "## Company Policies";
 *         }
 *
 *         @Override
 *         public String render(PromptContext context) {
 *             return "- Follow data privacy guidelines";
 *         }
 *     };
 * }
 * }</pre>
 */
public interface PromptSection {

    /**
     * Section 在 prompt 中的顺序（数值越小越靠前）。
     *
     * <p>建议的顺序范围：
     * <ul>
     *   <li>0-50: 核心身份定义（如 Intro、Agent Profile）</li>
     *   <li>50-100: 项目级指令（如 Instruction Files）</li>
     *   <li>100-200: 工具和能力描述（如 Tools、MCP、Skills）</li>
     *   <li>200-300: 状态信息（如 RAG status）</li>
     *   <li>>=300: 动态内容（如会话历史摘要）</li>
     * </ul>
     *
     * @return section 顺序
     */
    default int order() {
        return 100;
    }

    /**
     * Section 标题。
     *
     * <p>返回 null 表示不添加标题，直接追加内容。
     * 返回空字符串表示标题为空行。
     *
     * @return section 标题（如 "## Tools"），或 null
     */
    default String title() {
        return null;
    }

    /**
     * 是否为动态 section。
     *
     * <p>动态 section 位于 {@link SystemPromptBuilder#DYNAMIC_BOUNDARY} 之后，
     * 用于放置需要运行时计算的内容（如会话历史、项目状态等）。
     *
     * @return true 表示是动态 section
     */
    default boolean isDynamic() {
        return false;
    }

    /**
     * 生成 Section 内容。
     *
     * @param context 渲染上下文
     * @return section 内容文本
     */
    String render(PromptContext context);
}
